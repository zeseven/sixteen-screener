package com.sixteenscreener.app.data.repository

import com.sixteenscreener.app.data.local.WatchlistStore
import com.sixteenscreener.app.data.remote.ApiClient
import com.sixteenscreener.app.di.ApplicationScope
import com.sixteenscreener.app.domain.WatchlistRowRefresh
import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.MarketData
import com.sixteenscreener.app.domain.model.ProfileId
import com.sixteenscreener.app.domain.model.RowMarketData
import com.sixteenscreener.app.domain.model.Symbol
import com.sixteenscreener.app.domain.model.Tag
import com.sixteenscreener.app.domain.model.WatchlistItem
import com.sixteenscreener.app.domain.model.failed
import com.sixteenscreener.app.domain.model.loadedWith
import com.sixteenscreener.app.domain.model.loading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Watchlist of the active profile, shared by the watchlist, search and detailed screens (ADR-076).
 * The entries of each profile are stored on the device and shown as soon as the profile is known, until `GET /watchlist` replaces them.
 * Adds and removes are applied optimistically: each one is numbered in a change log and replayed on top of every backend snapshot until
 * a snapshot requested after its confirmation contains it; a failed change leaves the log. Only confirmed changes are stored on the
 * device. When changes outlive a snapshot, the watchlist is requested again once none of them is pending. State is dropped when the
 * active profile changes.
 * Market data is kept per row separately from the entries, survives watchlist reloads and is dropped for removed rows (ADR-058).
 */
interface WatchlistRepository {
    /** Entries of the watchlist in backend order, `null` until the stored or the loaded watchlist of the active profile is known. */
    val watchlist: StateFlow<List<WatchlistItem>?>

    /** Symbols known to be in the watchlist: those of the stored or loaded watchlist plus companies added or removed since. */
    val watchlistSymbols: StateFlow<Set<Symbol>>

    /** Whether [watchlist] is the list stored on the device, not yet replaced by a backend snapshot. */
    val isWatchlistCached: StateFlow<Boolean>

    /** Market data of the watchlist rows that have been requested at least once. */
    val marketData: StateFlow<Map<Symbol, RowMarketData>>

    /** Loads the watchlist with `GET /watchlist`, keeping changes not contained in it; the previous list is kept when the call fails. */
    suspend fun refresh(): ApiResult<List<WatchlistItem>>

    /**
     * Loads the market data of 1 to [WatchlistRowRefresh.MAX_SYMBOLS_PER_REQUEST] [symbols] with `GET /watchlist/market-data`.
     * The rows are marked as loading at once; a failed call marks them as failed and keeps their previous values.
     */
    suspend fun loadMarketData(symbols: List<Symbol>): ApiResult<List<MarketData>>

    /**
     * Adds [item] with `PUT`, sending its name, and shows it at [index] (or at the end) until the call fails; on success the
     * stored item replaces it.
     */
    suspend fun add(item: WatchlistItem, index: Int? = null): ApiResult<WatchlistItem>

    /** Removes the company with `DELETE`, hiding it until the call fails. */
    suspend fun remove(symbol: Symbol): ApiResult<Unit>

    /**
     * Replaces the tags of the company in the watchlist of [profileId] held in memory and stored on the device, after the backend
     * stored them; ignored when [profileId] is no longer active or the company is not in the watchlist.
     */
    fun updateTags(profileId: ProfileId, symbol: Symbol, tags: List<Tag>)
}

@Singleton
class DefaultWatchlistRepository @Inject constructor(
    private val apiClient: ApiClient,
    private val profileRepository: ProfileRepository,
    private val watchlistStore: WatchlistStore,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : WatchlistRepository {

    /** Add or remove issued under [number]; [confirmedAt] is the number taken when the backend confirmed it, `null` while pending. */
    private sealed interface Change {
        val number: Long
        val symbol: Symbol
        val confirmedAt: Long?

        fun appliedTo(list: List<WatchlistItem>): List<WatchlistItem>

        fun appliedTo(known: Set<Symbol>): Set<Symbol>

        data class Add(
            override val number: Long,
            override val symbol: Symbol,
            val item: WatchlistItem,
            val index: Int?,
            override val confirmedAt: Long? = null,
        ) : Change {
            override fun appliedTo(list: List<WatchlistItem>): List<WatchlistItem> {
                val existing = list.indexOfFirst { it.symbol == symbol }
                return when {
                    existing < 0 -> list.toMutableList().apply { add(index?.coerceIn(0, size) ?: size, item) }
                    confirmedAt != null -> list.toMutableList().apply { set(existing, item) }
                    else -> list
                }
            }

            override fun appliedTo(known: Set<Symbol>): Set<Symbol> = known + symbol
        }

        data class Remove(
            override val number: Long,
            override val symbol: Symbol,
            val marketData: RowMarketData?,
            override val confirmedAt: Long? = null,
        ) : Change {
            override fun appliedTo(list: List<WatchlistItem>): List<WatchlistItem> = list.filterNot { it.symbol == symbol }

            override fun appliedTo(known: Set<Symbol>): Set<Symbol> = known - symbol
        }
    }

    /**
     * Watchlist of [owner]: [base] is the last applied backend snapshot (requested under [snapshotNumber]) or the stored list
     * ([isBaseCached]); [changes] are replayed on top of it in order.
     */
    private data class WatchlistState(
        val owner: ProfileId? = null,
        val base: List<WatchlistItem>? = null,
        val isBaseCached: Boolean = false,
        val snapshotNumber: Long? = null,
        val changes: List<Change> = emptyList(),
        val isFollowUpNeeded: Boolean = false,
    ) {
        val displayed: List<WatchlistItem>? get() = base?.let { changes.fold(it) { list, change -> change.appliedTo(list) } }

        val knownSymbols: Set<Symbol>
            get() = changes.fold(base?.mapTo(LinkedHashSet()) { it.symbol }.orEmpty()) { known, change -> change.appliedTo(known) }

        val confirmed: List<WatchlistItem>?
            get() = base?.let { changes.filter { it.confirmedAt != null }.fold(it) { list, change -> change.appliedTo(list) } }

        val isFollowUpDue: Boolean get() = isFollowUpNeeded && changes.isNotEmpty() && changes.all { it.confirmedAt != null }

        fun ownedBy(profileId: ProfileId): WatchlistState = if (owner == profileId) this else WatchlistState(owner = profileId)
    }

    /** Pending device write; `null` [items] clears the stored watchlist. */
    private class CacheWrite(val items: List<WatchlistItem>?)

    private val items = MutableStateFlow<List<WatchlistItem>?>(null)
    private val symbols = MutableStateFlow<Set<Symbol>>(emptySet())
    private val cached = MutableStateFlow(false)
    private val rowMarketData = MutableStateFlow<Map<Symbol, RowMarketData>>(emptyMap())
    private val pendingCacheWrites = MutableStateFlow<Map<ProfileId, CacheWrite>>(emptyMap())
    private val stateLock = Any()
    private val mutationMutex = Mutex()
    private var state = WatchlistState()
    private var lastEventNumber = 0L

    override val watchlist: StateFlow<List<WatchlistItem>?> = items.asStateFlow()
    override val watchlistSymbols: StateFlow<Set<Symbol>> = symbols.asStateFlow()
    override val isWatchlistCached: StateFlow<Boolean> = cached.asStateFlow()
    override val marketData: StateFlow<Map<Symbol, RowMarketData>> = rowMarketData.asStateFlow()

    init {
        applicationScope.launch { writeCache() }
        applicationScope.launch {
            profileRepository.currentProfile
                .map { it?.id }
                .distinctUntilChanged()
                .collectLatest { profileId ->
                    resetUnlessOwnedBy(profileId)
                    if (profileId != null) restoreCache(profileId)
                }
        }
    }

    override suspend fun refresh(): ApiResult<List<WatchlistItem>> = profileRepository.withActiveProfile { profileId -> load(profileId) }

    override suspend fun loadMarketData(symbols: List<Symbol>): ApiResult<List<MarketData>> {
        require(symbols.size in 1..WatchlistRowRefresh.MAX_SYMBOLS_PER_REQUEST) { "1 to 50 symbols expected, got ${symbols.size}" }
        return profileRepository.withActiveProfile { profileId ->
            updateMarketData(profileId, symbols) { _, previous -> previous.loading() }
            apiClient.getWatchlistMarketData(profileId, symbols)
                .clearProfileIfNotFound(profileRepository, profileId)
                .also { result ->
                    if (isActive(profileId)) {
                        when (result) {
                            is ApiResult.Success -> applyMarketData(profileId, symbols, result.value)
                            is ApiResult.Failure -> updateMarketData(profileId, symbols) { _, previous -> previous.failed() }
                        }
                    }
                }
        }
    }

    override suspend fun add(item: WatchlistItem, index: Int?): ApiResult<WatchlistItem> = profileRepository.withActiveProfile { profileId ->
        val change = record(profileId) { number -> Change.Add(number, item.symbol, item, index) }
        mutationMutex.withLock { apiClient.addToWatchlist(profileId, item.symbol, item.name) }
            .also { result ->
                when (result) {
                    is ApiResult.Success -> confirm(profileId, change) { confirmedAt ->
                        copy(item = result.value, confirmedAt = confirmedAt)
                    }
                    is ApiResult.Failure -> drop(profileId, change)
                }
            }
    }

    override suspend fun remove(symbol: Symbol): ApiResult<Unit> = profileRepository.withActiveProfile { profileId ->
        val change = record(profileId) { number -> Change.Remove(number, symbol, rowMarketData.value[symbol]) }
        mutationMutex.withLock { apiClient.removeFromWatchlist(profileId, symbol) }
            .also { result ->
                when (result) {
                    is ApiResult.Success -> confirm(profileId, change) { confirmedAt -> copy(confirmedAt = confirmedAt) }
                    is ApiResult.Failure -> drop(profileId, change)
                }
            }
    }

    override fun updateTags(profileId: ProfileId, symbol: Symbol, tags: List<Tag>) {
        fun WatchlistItem.withTags() = if (this.symbol == symbol) copy(tags = tags) else this
        synchronized(stateLock) {
            if (state.owner != profileId) return
            update(
                state.copy(
                    base = state.base?.map { it.withTags() },
                    changes = state.changes.map { change -> if (change is Change.Add) change.copy(item = change.item.withTags()) else change },
                ),
            )
            saveConfirmed()
        }
    }

    private suspend fun isActive(profileId: ProfileId) = profileRepository.activeProfileId() == profileId

    private fun nextEventNumber(): Long = synchronized(stateLock) { ++lastEventNumber }

    /** Requests the watchlist of [profileId] and applies it unless the profile is no longer active; `404` deletes its stored list. */
    private suspend fun load(profileId: ProfileId): ApiResult<List<WatchlistItem>> {
        val number = nextEventNumber()
        return apiClient.getWatchlist(profileId)
            .clearProfileIfNotFound(profileRepository, profileId)
            .also { result ->
                when (result) {
                    is ApiResult.Success -> if (isActive(profileId)) applySnapshot(profileId, number, result.value)
                    is ApiResult.Failure -> if (result.error is ApiError.NotFound) queueCacheWrite(profileId, null)
                }
            }
    }

    /**
     * Replaces the base with [snapshot] requested under [number] unless a later requested one was applied; changes confirmed before the
     * request are contained in it and leave the log, the others are replayed on top of it.
     */
    private fun applySnapshot(profileId: ProfileId, number: Long, snapshot: List<WatchlistItem>) {
        val followUp = synchronized(stateLock) {
            val current = state.ownedBy(profileId)
            if (current.snapshotNumber != null && number < current.snapshotNumber) return
            val remaining = current.changes.filterNot { change -> change.confirmedAt?.let { it < number } == true }
            update(
                current.copy(
                    base = snapshot,
                    isBaseCached = false,
                    snapshotNumber = number,
                    changes = remaining,
                    isFollowUpNeeded = remaining.isNotEmpty(),
                ),
            )
            saveConfirmed()
            takeDueFollowUp()
        }
        followUp?.let(::launchFollowUp)
    }

    /** Fills the base of [profileId] with its stored watchlist unless a snapshot or the stored list is already there. */
    private suspend fun restoreCache(profileId: ProfileId) {
        val stored = watchlistStore.read(profileId) ?: return
        synchronized(stateLock) {
            if (state.owner == profileId && state.base == null) update(state.copy(base = stored, isBaseCached = true))
        }
    }

    /** Appends the change created by [create] with the next event number to the log of [profileId]. */
    private fun <C : Change> record(profileId: ProfileId, create: (Long) -> C): C = synchronized(stateLock) {
        val change = create(++lastEventNumber)
        val current = state.ownedBy(profileId)
        update(current.copy(changes = current.changes + change))
        change
    }

    /** Marks [change] as confirmed with the next event number, unless it left the log with a profile change. */
    private fun <C : Change> confirm(profileId: ProfileId, change: C, confirmed: C.(Long) -> Change) {
        val followUp = synchronized(stateLock) {
            if (state.owner != profileId || state.changes.none { it.number == change.number }) return
            val confirmedChange = change.confirmed(++lastEventNumber)
            update(state.copy(changes = state.changes.map { if (it.number == change.number) confirmedChange else it }))
            saveConfirmed()
            takeDueFollowUp()
        }
        followUp?.let(::launchFollowUp)
    }

    /** Removes the failed [change] from the log, which reverts it; a removed row gets its market data back. */
    private fun drop(profileId: ProfileId, change: Change) {
        val followUp = synchronized(stateLock) {
            if (state.owner != profileId || state.changes.none { it.number == change.number }) return
            update(state.copy(changes = state.changes.filterNot { it.number == change.number }))
            val restoredMarketData = (change as? Change.Remove)?.marketData
            if (restoredMarketData != null && change.symbol in symbols.value && change.symbol !in rowMarketData.value) {
                rowMarketData.value += change.symbol to restoredMarketData
            }
            takeDueFollowUp()
        }
        followUp?.let(::launchFollowUp)
    }

    /** Returns the profile whose watchlist must be requested again now that none of the changes left by a snapshot is pending. */
    private fun takeDueFollowUp(): ProfileId? {
        val due = state.isFollowUpDue
        if (state.isFollowUpNeeded && (due || state.changes.isEmpty())) state = state.copy(isFollowUpNeeded = false)
        return if (due) state.owner else null
    }

    private fun launchFollowUp(profileId: ProfileId) {
        applicationScope.launch { if (isActive(profileId)) load(profileId) }
    }

    /** Stores the confirmed part of the watchlist; nothing is stored before the base is known. */
    private fun saveConfirmed() {
        val owner = state.owner ?: return
        state.confirmed?.let { queueCacheWrite(owner, it) }
    }

    private fun queueCacheWrite(profileId: ProfileId, items: List<WatchlistItem>?) =
        pendingCacheWrites.update { it + (profileId to CacheWrite(items)) }

    /** Performs the queued device writes one at a time; only the latest write of each profile is kept while another one runs. */
    private suspend fun writeCache() {
        pendingCacheWrites.collect { pending ->
            pending.forEach { (profileId, write) ->
                if (write.items != null) watchlistStore.save(profileId, write.items) else watchlistStore.clear(profileId)
                pendingCacheWrites.update { current -> if (current[profileId] === write) current - profileId else current }
            }
        }
    }

    /** Applies [transform] to the market data of the [requested] rows still in the watchlist owned by [profileId]. */
    private fun updateMarketData(
        profileId: ProfileId,
        requested: List<Symbol>,
        transform: (Symbol, RowMarketData?) -> RowMarketData,
    ) = synchronized(stateLock) {
        if (state.owner == profileId) {
            val current = rowMarketData.value
            rowMarketData.value = current + requested.filter { it in symbols.value }.associateWith { transform(it, current[it]) }
        }
    }

    /** Stores the received [rows]; [requested] rows the backend skipped are marked as failed. */
    private fun applyMarketData(profileId: ProfileId, requested: List<Symbol>, rows: List<MarketData>) {
        val rowsBySymbol = rows.associateBy { it.symbol }
        updateMarketData(profileId, requested) { symbol, previous ->
            rowsBySymbol[symbol]?.let(previous::loadedWith) ?: previous.failed()
        }
    }

    /** Drops the state of the previous profile, unless the new profile's watchlist was already loaded before the change was observed. */
    private fun resetUnlessOwnedBy(profileId: ProfileId?) = synchronized(stateLock) {
        if (state.owner != profileId) update(WatchlistState(owner = profileId))
    }

    /**
     * Publishes [newState]; market data is dropped for rows no longer known to be in the watchlist, and entirely when the owner changes.
     * Called under [stateLock].
     */
    private fun update(newState: WatchlistState) {
        if (newState.owner != state.owner) rowMarketData.value = emptyMap()
        state = newState
        val known = newState.knownSymbols
        items.value = newState.displayed
        symbols.value = known
        cached.value = newState.isBaseCached
        if (rowMarketData.value.keys.any { it !in known }) rowMarketData.value = rowMarketData.value.filterKeys { it in known }
    }
}
