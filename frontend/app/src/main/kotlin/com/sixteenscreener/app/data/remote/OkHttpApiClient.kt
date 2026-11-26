package com.sixteenscreener.app.data.remote

import com.sixteenscreener.app.data.remote.auth.AuthenticationException
import com.sixteenscreener.app.data.remote.dto.BalanceSheetResponseDto
import com.sixteenscreener.app.data.remote.dto.CompanyProfileResponseDto
import com.sixteenscreener.app.data.remote.dto.CompanySourcesDto
import com.sixteenscreener.app.data.remote.dto.DividendsResponseDto
import com.sixteenscreener.app.data.remote.dto.ExchangesDto
import com.sixteenscreener.app.data.remote.dto.IndicatorSettingsDto
import com.sixteenscreener.app.data.remote.dto.PriceAndRatiosResponseDto
import com.sixteenscreener.app.data.remote.dto.ProblemDto
import com.sixteenscreener.app.data.remote.dto.ProfileCreateDto
import com.sixteenscreener.app.data.remote.dto.ProfileDto
import com.sixteenscreener.app.data.remote.dto.SearchResultsDto
import com.sixteenscreener.app.data.remote.dto.SourcesDto
import com.sixteenscreener.app.data.remote.dto.TagListDto
import com.sixteenscreener.app.data.remote.dto.WatchlistAddDto
import com.sixteenscreener.app.data.remote.dto.WatchlistDto
import com.sixteenscreener.app.data.remote.dto.WatchlistItemDto
import com.sixteenscreener.app.data.remote.dto.WatchlistMarketDataDto
import com.sixteenscreener.app.data.remote.dto.toDomain
import com.sixteenscreener.app.data.remote.dto.toTagListDto
import com.sixteenscreener.app.data.remote.dto.toUpdateDto
import com.sixteenscreener.app.di.AuthenticatedHttpClient
import com.sixteenscreener.app.di.BackendUrl
import com.sixteenscreener.app.di.IoDispatcher
import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.BalanceSheet
import com.sixteenscreener.app.domain.model.CompanyProfileData
import com.sixteenscreener.app.domain.model.CompanySources
import com.sixteenscreener.app.domain.model.Dividends
import com.sixteenscreener.app.domain.model.Exchange
import com.sixteenscreener.app.domain.model.IndicatorSetting
import com.sixteenscreener.app.domain.model.MarketData
import com.sixteenscreener.app.domain.model.PriceAndRatios
import com.sixteenscreener.app.domain.model.ProblemDetails
import com.sixteenscreener.app.domain.model.Profile
import com.sixteenscreener.app.domain.model.ProfileId
import com.sixteenscreener.app.domain.model.RequestTrigger
import com.sixteenscreener.app.domain.model.SearchResults
import com.sixteenscreener.app.domain.model.Source
import com.sixteenscreener.app.domain.model.SourceId
import com.sixteenscreener.app.domain.model.Symbol
import com.sixteenscreener.app.domain.model.Tag
import com.sixteenscreener.app.domain.model.WatchlistItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InterruptedIOException
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import java.net.URLEncoder
import java.time.DateTimeException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OkHttpApiClient @Inject constructor(
    @param:AuthenticatedHttpClient private val httpClient: OkHttpClient,
    @BackendUrl backendUrl: String,
    private val json: Json,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ApiClient {

    private val baseUrl: HttpUrl = backendUrl.toHttpUrl()

    override suspend fun getOrCreateProfile(name: String): ApiResult<Profile> {
        val body = json.encodeToString(ProfileCreateDto(name)).toRequestBody(JSON_MEDIA_TYPE)
        return send(Request.Builder().url(url("profiles")).post(body).build()) {
            json.decodeFromString<ProfileDto>(it).toDomain()
        }
    }

    override suspend fun getProfile(profileId: ProfileId): ApiResult<Profile> =
        get(profileUrl(profileId)) { json.decodeFromString<ProfileDto>(it).toDomain() }

    override suspend fun getWatchlist(profileId: ProfileId): ApiResult<List<WatchlistItem>> =
        get(profileUrl(profileId, "watchlist")) { json.decodeFromString<WatchlistDto>(it).toDomain() }

    override suspend fun getWatchlistMarketData(profileId: ProfileId, symbols: List<Symbol>): ApiResult<List<MarketData>> {
        val url = profileUrl(profileId, "watchlist", "market-data").newBuilder()
            .addEncodedQueryParameter("symbols", symbols.joinToString(",") { it.value.encodedAsQueryValue() })
            .build()
        return get(url) { json.decodeFromString<WatchlistMarketDataDto>(it).toDomain() }
    }

    override suspend fun addToWatchlist(profileId: ProfileId, symbol: Symbol, name: String): ApiResult<WatchlistItem> =
        send(Request.Builder().url(profileUrl(profileId, "watchlist", symbol.value)).put(watchlistAddBody(name)).build()) {
            json.decodeFromString<WatchlistItemDto>(it).toDomain()
        }

    override suspend fun removeFromWatchlist(profileId: ProfileId, symbol: Symbol): ApiResult<Unit> =
        send(Request.Builder().url(profileUrl(profileId, "watchlist", symbol.value)).delete().build()) {}

    override suspend fun search(profileId: ProfileId, query: String): ApiResult<SearchResults> {
        val url = profileUrl(profileId, "search").newBuilder().addQueryParameter("query", query).build()
        return get(url) { json.decodeFromString<SearchResultsDto>(it).toDomain() }
    }

    override suspend fun getCompanySources(profileId: ProfileId, symbol: Symbol): ApiResult<CompanySources> =
        get(profileUrl(profileId, "companies", symbol.value, "sources")) { json.decodeFromString<CompanySourcesDto>(it).toDomain() }

    override suspend fun getPriceAndRatios(
        profileId: ProfileId,
        symbol: Symbol,
        source: SourceId?,
        trigger: RequestTrigger,
    ): ApiResult<PriceAndRatios> =
        get(companyDataUrl(profileId, symbol, "price-ratios", source, trigger)) {
            json.decodeFromString<PriceAndRatiosResponseDto>(it).toDomain()
        }

    override suspend fun getDividends(
        profileId: ProfileId,
        symbol: Symbol,
        source: SourceId?,
        trigger: RequestTrigger,
    ): ApiResult<Dividends> =
        get(companyDataUrl(profileId, symbol, "dividends", source, trigger)) {
            json.decodeFromString<DividendsResponseDto>(it).toDomain()
        }

    override suspend fun getBalanceSheet(
        profileId: ProfileId,
        symbol: Symbol,
        source: SourceId?,
        trigger: RequestTrigger,
    ): ApiResult<BalanceSheet> =
        get(companyDataUrl(profileId, symbol, "balance-sheet", source, trigger)) {
            json.decodeFromString<BalanceSheetResponseDto>(it).toDomain()
        }

    override suspend fun getCompanyProfile(
        profileId: ProfileId,
        symbol: Symbol,
        source: SourceId?,
        trigger: RequestTrigger,
    ): ApiResult<CompanyProfileData> =
        get(companyDataUrl(profileId, symbol, "profile", source, trigger)) {
            json.decodeFromString<CompanyProfileResponseDto>(it).toDomain()
        }

    override suspend fun setCompanyTags(profileId: ProfileId, symbol: Symbol, tags: List<Tag>): ApiResult<List<Tag>> {
        val body = json.encodeToString(tags.toTagListDto()).toRequestBody(JSON_MEDIA_TYPE)
        return send(Request.Builder().url(profileUrl(profileId, "companies", symbol.value, "tags")).put(body).build()) {
            json.decodeFromString<TagListDto>(it).toDomain()
        }
    }

    override suspend fun getProfileTags(profileId: ProfileId): ApiResult<List<Tag>> =
        get(profileUrl(profileId, "tags")) { json.decodeFromString<TagListDto>(it).toDomain() }

    override suspend fun getSources(profileId: ProfileId): ApiResult<List<Source>> =
        get(profileUrl(profileId, "sources")) { json.decodeFromString<SourcesDto>(it).toDomain() }

    override suspend fun getIndicatorSettings(profileId: ProfileId): ApiResult<List<IndicatorSetting>> =
        get(profileUrl(profileId, "indicator-settings")) { json.decodeFromString<IndicatorSettingsDto>(it).toDomain() }

    override suspend fun replaceIndicatorSettings(
        profileId: ProfileId,
        settings: List<IndicatorSetting>,
    ): ApiResult<List<IndicatorSetting>> {
        val body = json.encodeToString(settings.toUpdateDto()).toRequestBody(JSON_MEDIA_TYPE)
        return send(Request.Builder().url(profileUrl(profileId, "indicator-settings")).put(body).build()) {
            json.decodeFromString<IndicatorSettingsDto>(it).toDomain()
        }
    }

    override suspend fun getExchanges(): ApiResult<List<Exchange>> =
        get(url("exchanges")) { json.decodeFromString<ExchangesDto>(it).toDomain() }

    private fun watchlistAddBody(name: String): RequestBody =
        if (name.isBlank()) {
            EMPTY_BODY
        } else {
            json.encodeToString(WatchlistAddDto(name.take(MAX_WATCHLIST_NAME_LENGTH))).toRequestBody(JSON_MEDIA_TYPE)
        }

    private fun url(vararg segments: String): HttpUrl =
        segments.fold(baseUrl.newBuilder()) { builder, segment -> builder.addPathSegment(segment) }.build()

    private fun profileUrl(profileId: ProfileId, vararg segments: String): HttpUrl =
        url("profiles", profileId.value, *segments)

    private fun companyDataUrl(
        profileId: ProfileId,
        symbol: Symbol,
        resource: String,
        source: SourceId?,
        trigger: RequestTrigger,
    ): HttpUrl = profileUrl(profileId, "companies", symbol.value, resource).newBuilder()
        .apply { source?.let { addQueryParameter("source", it.value) } }
        .addQueryParameter("trigger", trigger.name)
        .build()

    /** Percent-encodes this value so that commas inside it don't split a comma-separated query parameter. */
    private fun String.encodedAsQueryValue(): String = URLEncoder.encode(this, "UTF-8").replace("+", "%20")

    private suspend fun <T> get(url: HttpUrl, decode: (String) -> T): ApiResult<T> =
        send(Request.Builder().url(url).get().build(), decode)

    private suspend fun <T> send(request: Request, decode: (String) -> T): ApiResult<T> = withContext(ioDispatcher) {
        try {
            httpClient.newCall(request).await().use { response ->
                val body = response.body.string()
                if (response.isSuccessful) ApiResult.Success(decode(body)) else ApiResult.Failure(errorOf(response.code, body))
            }
        } catch (_: AuthenticationException) {
            ApiResult.Failure(ApiError.Unauthorized)
        } catch (_: InterruptedIOException) {
            ApiResult.Failure(ApiError.Network(isTimeout = true))
        } catch (_: IOException) {
            ApiResult.Failure(ApiError.Network(isTimeout = false))
        } catch (exception: SerializationException) {
            ApiResult.Failure(ApiError.Parse(exception.message))
        } catch (exception: DateTimeException) {
            ApiResult.Failure(ApiError.Parse(exception.message))
        }
    }

    private fun errorOf(status: Int, body: String): ApiError = when (status) {
        HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> ApiError.Unauthorized
        HTTP_NOT_FOUND -> ApiError.NotFound(problemOf(body))
        else -> ApiError.Http(status, problemOf(body))
    }

    private fun problemOf(body: String): ProblemDetails? = try {
        body.takeIf { it.isNotBlank() }?.let { json.decodeFromString<ProblemDto>(it).toDomain() }
    } catch (_: SerializationException) {
        null
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
        val EMPTY_BODY = ByteArray(0).toRequestBody()
        const val MAX_WATCHLIST_NAME_LENGTH = 200
    }
}
