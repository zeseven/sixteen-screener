package com.sixteenscreener.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sixteenscreener.app.domain.model.Market
import com.sixteenscreener.app.domain.model.ProfileId
import com.sixteenscreener.app.domain.model.Tag
import com.sixteenscreener.app.domain.model.WatchlistItem
import com.sixteenscreener.app.testing.Companies.item
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreWatchlistStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val profileId = ProfileId("3f2b8c1e-6d4a-4f7e-9b1a-2c5d8e7f9a10")
    private val otherProfileId = ProfileId("other")

    private val apple = item("AAPL", tags = listOf(Tag("chess-king", "Core"), Tag("coin", "Dividend")))
    private val sap = item("SAP.DE", name = "SAP SE", market = Market.EU, sector = null)
    private val toyota = item("7203.T", name = "Toyota", market = null, type = null)
    private val nestleAdr = item("NSRGY", name = "Nestle SA", market = Market.US, type = "ADR", stock = false)

    private lateinit var dataStore: DataStore<Preferences>

    private fun TestScope.store(): DataStoreWatchlistStore {
        dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { folder.root.resolve("watchlist.preferences_pb") },
        )
        return DataStoreWatchlistStore(dataStore)
    }

    private suspend fun storeRaw(profileId: ProfileId, value: String) {
        dataStore.edit { it[stringPreferencesKey(profileId.value)] = value }
    }

    @Test
    fun `no watchlist is stored on first start`() = runTest {
        assertNull(store().read(profileId))
    }

    @Test
    fun `saved watchlist is read back with all fields`() = runTest {
        val store = store()

        assertTrue(store.save(profileId, listOf(apple, sap, toyota, nestleAdr)))

        assertEquals(listOf(apple, sap, toyota, nestleAdr), store.read(profileId))
    }

    @Test
    fun `watchlist stored by the previous app version is read as stocks of unknown type`() = runTest {
        val store = store()
        storeRaw(
            profileId,
            "[{\"symbol\":\"SAP.DE\",\"ticker\":\"SAP\",\"exchange\":\"XETRA\",\"name\":\"SAP SE\",\"market\":\"EU\"," +
                "\"logoUrl\":\"/logos/SAP.DE\",\"sector\":\"Technology\",\"tags\":[\"core\"]}]",
        )

        assertEquals(listOf(item("SAP.DE", name = "SAP SE", exchange = "XETRA", market = Market.EU, type = null)), store.read(profileId))
    }

    @Test
    fun `text tags stored by the previous app version are dropped and the rest of the entry is kept`() = runTest {
        val store = store()
        storeRaw(
            profileId,
            "[{\"symbol\":\"AAPL\",\"ticker\":\"AAPL\",\"exchange\":\"NASDAQ\",\"name\":\"Apple Inc.\",\"market\":\"US\"," +
                "\"type\":\"Common Stock\",\"logoUrl\":\"/logos/AAPL\",\"sector\":\"Technology\"," +
                "\"tags\":[\"core\",{\"icon\":\"coin\",\"text\":\"Income\"},{\"icon\":\"gem\"},{\"icon\":1,\"text\":\"x\"}]}]",
        )

        assertEquals(listOf(item("AAPL", tags = listOf(Tag("coin", "Income")))), store.read(profileId))
    }

    @Test
    fun `entry stored without tags is read with no tags`() = runTest {
        val store = store()
        storeRaw(
            profileId,
            "[{\"symbol\":\"AAPL\",\"ticker\":\"AAPL\",\"exchange\":\"NASDAQ\",\"name\":\"Apple Inc.\",\"market\":\"US\"," +
                "\"type\":\"Common Stock\",\"logoUrl\":\"/logos/AAPL\",\"sector\":\"Technology\"}]",
        )

        assertEquals(listOf(item("AAPL")), store.read(profileId))
    }

    @Test
    fun `empty watchlist is read back as empty`() = runTest {
        val store = store()

        store.save(profileId, emptyList())

        assertEquals(emptyList<WatchlistItem>(), store.read(profileId))
    }

    @Test
    fun `saving replaces the stored watchlist of the profile`() = runTest {
        val store = store()

        store.save(profileId, listOf(apple))
        store.save(profileId, listOf(sap))

        assertEquals(listOf(sap), store.read(profileId))
    }

    @Test
    fun `watchlists of different profiles are kept apart`() = runTest {
        val store = store()

        store.save(profileId, listOf(apple))
        store.save(otherProfileId, listOf(sap))

        assertEquals(listOf(apple), store.read(profileId))
        assertEquals(listOf(sap), store.read(otherProfileId))
    }

    @Test
    fun `clear removes only the watchlist of the profile`() = runTest {
        val store = store()
        store.save(profileId, listOf(apple))
        store.save(otherProfileId, listOf(sap))

        assertTrue(store.clear(profileId))

        assertNull(store.read(profileId))
        assertEquals(listOf(sap), store.read(otherProfileId))
    }

    @Test
    fun `corrupt stored data is treated as no watchlist`() = runTest {
        val store = store()
        storeRaw(profileId, "[{\"symbol\":")

        assertNull(store.read(profileId))
    }

    @Test
    fun `stored data of another shape is treated as no watchlist`() = runTest {
        val store = store()
        storeRaw(profileId, "{\"symbol\":\"AAPL\"}")

        assertNull(store.read(profileId))
    }

    @Test
    fun `unknown market is read as no market`() = runTest {
        val store = store()
        storeRaw(
            profileId,
            "[{\"symbol\":\"AAPL\",\"ticker\":\"AAPL\",\"exchange\":\"NASDAQ\",\"name\":\"Apple Inc.\",\"market\":\"ASIA\"," +
                "\"logoUrl\":\"/logos/AAPL\",\"sector\":null,\"tags\":[],\"extra\":1}]",
        )

        assertEquals(listOf(null), store.read(profileId)?.map { it.market })
    }
}
