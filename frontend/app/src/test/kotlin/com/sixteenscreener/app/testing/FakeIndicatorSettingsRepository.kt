package com.sixteenscreener.app.testing

import com.sixteenscreener.app.data.repository.IndicatorSettingsRepository
import com.sixteenscreener.app.domain.model.ApiResult
import com.sixteenscreener.app.domain.model.IndicatorSetting

class FakeIndicatorSettingsRepository : IndicatorSettingsRepository {

    var loads = 0
    val saves = mutableListOf<List<IndicatorSetting>>()

    var getResult: suspend () -> ApiResult<List<IndicatorSetting>> = { ApiResult.Success(IndicatorSettings.all) }
    var replaceResult: suspend (List<IndicatorSetting>) -> ApiResult<List<IndicatorSetting>> = { ApiResult.Success(it) }

    override suspend fun getSettings(): ApiResult<List<IndicatorSetting>> {
        loads++
        return getResult()
    }

    override suspend fun replaceSettings(settings: List<IndicatorSetting>): ApiResult<List<IndicatorSetting>> {
        saves += settings
        return replaceResult(settings)
    }
}
