package com.sixteenscreener.app.data.repository

import com.sixteenscreener.app.domain.model.ActiveProfile
import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.ProfileId
import com.sixteenscreener.app.domain.model.SourceId
import com.sixteenscreener.app.domain.model.Symbol
import com.sixteenscreener.app.testing.CompanyData
import com.sixteenscreener.app.testing.FakeApiClient
import com.sixteenscreener.app.testing.FakeProfileRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultCompanyRepositoryTest {

    private val apiClient = FakeApiClient()
    private val profileRepository = FakeProfileRepository(ActiveProfile(ProfileId("p1"), "Viktor"))
    private val repository = DefaultCompanyRepository(apiClient, profileRepository)
    private val apple = Symbol("AAPL")

    @Test
    fun `price and ratios are requested for the active profile from the default source`() = runTest {
        val data = CompanyData.priceAndRatios()
        apiClient.getPriceAndRatios = { _, _, _, _ -> ApiResult.Success(data) }

        val result = repository.getPriceAndRatios(apple, refresh = true)

        assertEquals(ApiResult.Success(data), result)
        assertEquals(listOf("getPriceAndRatios(p1, AAPL, null, true)"), apiClient.calls)
    }

    @Test
    fun `balance sheet is requested from the given source`() = runTest {
        val data = CompanyData.balanceSheet(source = SourceId.SEC_EDGAR)
        apiClient.getBalanceSheet = { _, _, _, _ -> ApiResult.Success(data) }

        val result = repository.getBalanceSheet(apple, SourceId.SEC_EDGAR)

        assertEquals(ApiResult.Success(data), result)
        assertEquals(listOf("getBalanceSheet(p1, AAPL, sec-edgar, false)"), apiClient.calls)
    }

    @Test
    fun `company profile is requested without refresh by default`() = runTest {
        val data = CompanyData.companyProfileData()
        apiClient.getCompanyProfile = { _, _, _, _ -> ApiResult.Success(data) }

        val result = repository.getCompanyProfile(apple)

        assertEquals(ApiResult.Success(data), result)
        assertEquals(listOf("getCompanyProfile(p1, AAPL, null, false)"), apiClient.calls)
    }

    @Test
    fun `not found keeps the stored profile because it can mean an unknown symbol`() = runTest {
        apiClient.getCompanyProfile = { _, _, _, _ -> ApiResult.Failure(ApiError.NotFound(null)) }

        val result = repository.getCompanyProfile(apple)

        assertEquals(ApiResult.Failure(ApiError.NotFound(null)), result)
        assertEquals(ProfileId("p1"), profileRepository.currentProfile.value?.id)
    }

    @Test
    fun `no request without an active profile`() = runTest {
        profileRepository.currentProfile.value = null

        val result = repository.getBalanceSheet(apple)

        assertEquals(ApiResult.Failure(ApiError.NoProfile), result)
        assertTrue(apiClient.calls.isEmpty())
    }
}
