package com.sixteenscreener.app.data.remote

import com.sixteenscreener.app.data.remote.auth.AuthenticationException
import com.sixteenscreener.app.data.remote.dto.BalanceSheetResponseDto
import com.sixteenscreener.app.data.remote.dto.CompanyProfileResponseDto
import com.sixteenscreener.app.data.remote.dto.PriceAndRatiosResponseDto
import com.sixteenscreener.app.data.remote.dto.ProblemDto
import com.sixteenscreener.app.data.remote.dto.ProfileCreateDto
import com.sixteenscreener.app.data.remote.dto.ProfileDto
import com.sixteenscreener.app.data.remote.dto.SearchResultsDto
import com.sixteenscreener.app.data.remote.dto.SourcesDto
import com.sixteenscreener.app.data.remote.dto.WatchlistDto
import com.sixteenscreener.app.data.remote.dto.toDomain
import com.sixteenscreener.app.di.AuthenticatedHttpClient
import com.sixteenscreener.app.di.BackendUrl
import com.sixteenscreener.app.di.IoDispatcher
import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.BalanceSheet
import com.sixteenscreener.app.domain.model.CompanyProfileData
import com.sixteenscreener.app.domain.model.CompanySummary
import com.sixteenscreener.app.domain.model.PriceAndRatios
import com.sixteenscreener.app.domain.model.ProblemDetails
import com.sixteenscreener.app.domain.model.Profile
import com.sixteenscreener.app.domain.model.ProfileId
import com.sixteenscreener.app.domain.model.SearchResults
import com.sixteenscreener.app.domain.model.Source
import com.sixteenscreener.app.domain.model.SourceId
import com.sixteenscreener.app.domain.model.Symbol
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
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

    override suspend fun getWatchlist(profileId: ProfileId): ApiResult<List<CompanySummary>> =
        get(profileUrl(profileId, "watchlist")) { json.decodeFromString<WatchlistDto>(it).toDomain() }

    override suspend fun refreshWatchlistRows(profileId: ProfileId, symbols: List<Symbol>): ApiResult<List<CompanySummary>> {
        val url = profileUrl(profileId, "watchlist", "refresh").newBuilder()
            .addEncodedQueryParameter("symbols", symbols.joinToString(",") { it.value.encodedAsQueryValue() })
            .build()
        return get(url) { json.decodeFromString<WatchlistDto>(it).toDomain() }
    }

    override suspend fun addToWatchlist(profileId: ProfileId, symbol: Symbol): ApiResult<Unit> =
        send(Request.Builder().url(profileUrl(profileId, "watchlist", symbol.value)).put(EMPTY_BODY).build()) {}

    override suspend fun removeFromWatchlist(profileId: ProfileId, symbol: Symbol): ApiResult<Unit> =
        send(Request.Builder().url(profileUrl(profileId, "watchlist", symbol.value)).delete().build()) {}

    override suspend fun search(profileId: ProfileId, query: String): ApiResult<SearchResults> {
        val url = profileUrl(profileId, "search").newBuilder().addQueryParameter("query", query).build()
        return get(url) { json.decodeFromString<SearchResultsDto>(it).toDomain() }
    }

    override suspend fun getPriceAndRatios(
        profileId: ProfileId,
        symbol: Symbol,
        source: SourceId?,
        refresh: Boolean,
    ): ApiResult<PriceAndRatios> =
        get(companyDataUrl(profileId, symbol, "price-ratios", source, refresh)) {
            json.decodeFromString<PriceAndRatiosResponseDto>(it).toDomain()
        }

    override suspend fun getBalanceSheet(
        profileId: ProfileId,
        symbol: Symbol,
        source: SourceId?,
        refresh: Boolean,
    ): ApiResult<BalanceSheet> =
        get(companyDataUrl(profileId, symbol, "balance-sheet", source, refresh)) {
            json.decodeFromString<BalanceSheetResponseDto>(it).toDomain()
        }

    override suspend fun getCompanyProfile(
        profileId: ProfileId,
        symbol: Symbol,
        source: SourceId?,
        refresh: Boolean,
    ): ApiResult<CompanyProfileData> =
        get(companyDataUrl(profileId, symbol, "profile", source, refresh)) {
            json.decodeFromString<CompanyProfileResponseDto>(it).toDomain()
        }

    override suspend fun getSources(profileId: ProfileId): ApiResult<List<Source>> =
        get(profileUrl(profileId, "sources")) { json.decodeFromString<SourcesDto>(it).toDomain() }

    private fun url(vararg segments: String): HttpUrl =
        segments.fold(baseUrl.newBuilder()) { builder, segment -> builder.addPathSegment(segment) }.build()

    private fun profileUrl(profileId: ProfileId, vararg segments: String): HttpUrl =
        url("profiles", profileId.value, *segments)

    private fun companyDataUrl(
        profileId: ProfileId,
        symbol: Symbol,
        resource: String,
        source: SourceId?,
        refresh: Boolean,
    ): HttpUrl = profileUrl(profileId, "companies", symbol.value, resource).newBuilder()
        .apply { source?.let { addQueryParameter("source", it.value) } }
        .addQueryParameter("refresh", refresh.toString())
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
    }
}
