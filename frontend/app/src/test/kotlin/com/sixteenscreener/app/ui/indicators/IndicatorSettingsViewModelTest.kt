package com.sixteenscreener.app.ui.indicators

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.IndicatorLevel
import com.sixteenscreener.app.domain.model.IndicatorSetting
import com.sixteenscreener.app.domain.model.RatioCategory
import com.sixteenscreener.app.testing.FakeIndicatorSettingsRepository
import com.sixteenscreener.app.testing.IndicatorSettings
import com.sixteenscreener.app.testing.IndicatorSettings.intervals
import com.sixteenscreener.app.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IndicatorSettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val repository = FakeIndicatorSettingsRepository()
    private val applicationScope = CoroutineScope(SupervisorJob() + mainDispatcherRule.dispatcher)
    private val store = ViewModelStore()
    private val failure = ApiResult.Failure(ApiError.Network(isTimeout = false))
    private val pe = IndicatorSettings.pe

    @After
    fun tearDown() {
        applicationScope.cancel()
    }

    private fun viewModel(): IndicatorSettingsViewModel {
        val factory = viewModelFactory { initializer { IndicatorSettingsViewModel(repository, applicationScope) } }
        return ViewModelProvider.create(store, factory)[IndicatorSettingsViewModel::class]
    }

    private fun runTest(block: suspend TestScope.() -> Unit) = runTest(mainDispatcherRule.dispatcher) { block() }

    private fun IndicatorSettingsViewModel.settingOf(code: String): IndicatorSetting = uiState.value.settings.single { it.ratioCode == code }

    @Test
    fun `opening the screen loads the settings`() = runTest {
        val response = CompletableDeferred<ApiResult<List<IndicatorSetting>>>()
        repository.getResult = { response.await() }

        val viewModel = viewModel()
        runCurrent()
        assertTrue(viewModel.uiState.value.isLoading)

        response.complete(ApiResult.Success(IndicatorSettings.all))
        runCurrent()
        assertEquals(IndicatorSettingsUiState(isLoading = false, settings = IndicatorSettings.all), viewModel.uiState.value)
    }

    @Test
    fun `settings are grouped by category in settings order`() = runTest {
        val viewModel = viewModel()
        runCurrent()

        val groups = viewModel.uiState.value.groups

        assertEquals(listOf(RatioCategory.VALUATION, RatioCategory.PROFITABILITY), groups.map { it.category })
        assertEquals(listOf(listOf("PE"), listOf("ROA", "ROE")), groups.map { group -> group.settings.map { it.ratioCode } })
    }

    @Test
    fun `a failed load shows the error and retry loads again`() = runTest {
        repository.getResult = { failure }
        val viewModel = viewModel()
        runCurrent()
        assertEquals(IndicatorSettingsUiState(isLoading = false, loadError = failure.error), viewModel.uiState.value)

        repository.getResult = { ApiResult.Success(IndicatorSettings.all) }
        viewModel.load()
        assertTrue(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.loadError)
        runCurrent()

        assertEquals(IndicatorSettings.all, viewModel.uiState.value.settings)
        assertEquals(2, repository.loads)
    }

    @Test
    fun `edits apply immediately and are saved 500 ms after the last change`() = runTest {
        val viewModel = viewModel()
        runCurrent()

        viewModel.onEnabledChange("ROE", true)
        assertTrue(viewModel.settingOf("ROE").enabled)
        advanceTimeBy(300)
        viewModel.onIntervalsChange("PE", intervals(10.0, 25.0, 40.0))
        assertEquals(10.0, viewModel.settingOf("PE").intervals.thresholds[0], 0.0)
        advanceTimeBy(499)
        runCurrent()
        assertEquals(0, repository.saves.size)

        advanceTimeBy(2)
        runCurrent()
        assertEquals(1, repository.saves.size)
        assertEquals(viewModel.uiState.value.settings, repository.saves.single())
        assertTrue(repository.saves.single().single { it.ratioCode == "ROE" }.enabled)
    }

    @Test
    fun `the whole set is saved, not only the changed setting`() = runTest {
        val viewModel = viewModel()
        runCurrent()

        viewModel.onEnabledChange("PE", false)
        advanceUntilIdle()

        assertEquals(listOf("PE", "ROA", "ROE"), repository.saves.single().map { it.ratioCode })
    }

    @Test
    fun `an edit without effect is not saved`() = runTest {
        val viewModel = viewModel()
        runCurrent()

        viewModel.onEnabledChange("PE", pe.enabled)
        viewModel.onEnabledChange("UNKNOWN", false)
        advanceUntilIdle()

        assertEquals(0, repository.saves.size)
    }

    @Test
    fun `a failed save keeps the edits, reports the error once and is retried with the next change`() = runTest {
        repository.replaceResult = { failure }
        val viewModel = viewModel()
        runCurrent()

        viewModel.onEnabledChange("PE", false)
        advanceUntilIdle()
        assertEquals(failure.error, viewModel.uiState.value.saveError)
        assertFalse(viewModel.settingOf("PE").enabled)

        viewModel.onSaveErrorShown()
        assertNull(viewModel.uiState.value.saveError)

        repository.replaceResult = { ApiResult.Success(it) }
        viewModel.onIntervalsChange("PE", pe.intervals.copy(belowZeroLevel = IndicatorLevel.GOOD))
        advanceUntilIdle()

        assertEquals(2, repository.saves.size)
        val saved = repository.saves.last().single { it.ratioCode == "PE" }
        assertFalse(saved.enabled)
        assertEquals(IndicatorLevel.GOOD, saved.intervals.belowZeroLevel)
        assertNull(viewModel.uiState.value.saveError)
    }

    @Test
    fun `flush saves pending edits without waiting for the debounce`() = runTest {
        val viewModel = viewModel()
        runCurrent()

        viewModel.onEnabledChange("PE", false)
        viewModel.flush()
        runCurrent()
        assertEquals(1, repository.saves.size)

        advanceUntilIdle()
        assertEquals(1, repository.saves.size)
    }

    @Test
    fun `flush without pending edits saves nothing`() = runTest {
        val viewModel = viewModel()
        runCurrent()

        viewModel.flush()
        advanceUntilIdle()

        assertEquals(0, repository.saves.size)
    }

    @Test
    fun `flush retries a failed save`() = runTest {
        repository.replaceResult = { failure }
        val viewModel = viewModel()
        runCurrent()
        viewModel.onEnabledChange("PE", false)
        advanceUntilIdle()

        repository.replaceResult = { ApiResult.Success(it) }
        viewModel.flush()
        advanceUntilIdle()
        viewModel.flush()
        advanceUntilIdle()

        assertEquals(2, repository.saves.size)
    }

    @Test
    fun `closing the screen saves pending edits in the application scope`() = runTest {
        val viewModel = viewModel()
        runCurrent()

        viewModel.onEnabledChange("PE", false)
        store.clear()
        advanceUntilIdle()

        assertEquals(1, repository.saves.size)
        assertFalse(repository.saves.single().single { it.ratioCode == "PE" }.enabled)
    }

    @Test
    fun `closing the screen during a running save saves the edits again`() = runTest {
        val running = CompletableDeferred<ApiResult<List<IndicatorSetting>>>()
        repository.replaceResult = { running.await() }
        val viewModel = viewModel()
        runCurrent()
        viewModel.onEnabledChange("PE", false)
        advanceTimeBy(501)
        runCurrent()
        assertEquals(1, repository.saves.size)

        repository.replaceResult = { ApiResult.Success(it) }
        store.clear()
        advanceUntilIdle()

        assertEquals(2, repository.saves.size)
    }

    @Test
    fun `closing the screen without pending edits saves nothing`() = runTest {
        viewModel()
        runCurrent()

        store.clear()
        advanceUntilIdle()

        assertEquals(0, repository.saves.size)
    }

    @Test
    fun `edits made while a save runs are saved afterwards`() = runTest {
        val first = CompletableDeferred<ApiResult<List<IndicatorSetting>>>()
        repository.replaceResult = { first.await() }
        val viewModel = viewModel()
        runCurrent()
        viewModel.onEnabledChange("PE", false)
        advanceTimeBy(501)
        runCurrent()

        repository.replaceResult = { ApiResult.Success(it) }
        viewModel.onEnabledChange("ROE", true)
        first.complete(ApiResult.Success(emptyList()))
        advanceUntilIdle()

        assertEquals(2, repository.saves.size)
        assertTrue(repository.saves.last().single { it.ratioCode == "ROE" }.enabled)
    }
}
