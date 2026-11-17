package com.sixteenscreener.app.data.remote.dto

import com.sixteenscreener.app.domain.model.IndicatorIntervals
import com.sixteenscreener.app.domain.model.IndicatorLevel
import com.sixteenscreener.app.domain.model.IndicatorScale
import com.sixteenscreener.app.domain.model.IndicatorSetting
import com.sixteenscreener.app.domain.model.RatioCategory
import com.sixteenscreener.app.domain.model.RatioUnit
import kotlinx.serialization.Serializable

@Serializable
data class IndicatorSettingDto(
    val ratioCode: String,
    val enabled: Boolean,
    val thresholds: List<Double>,
    val levels: List<String>,
    val belowZeroLevel: String,
    val name: String,
    val shortName: String,
    val unit: String,
    val category: String,
    val scaleMax: Double,
    val step: Double,
)

@Serializable
data class IndicatorSettingsDto(
    val settings: List<IndicatorSettingDto>,
)

@Serializable
data class IndicatorSettingValuesDto(
    val ratioCode: String,
    val enabled: Boolean,
    val thresholds: List<Double>,
    val levels: List<String>,
    val belowZeroLevel: String,
)

@Serializable
data class IndicatorSettingsUpdateDto(
    val settings: List<IndicatorSettingValuesDto>,
)

fun String?.toRatioUnit(): RatioUnit = this?.toEnumOr(RatioUnit.UNKNOWN) ?: RatioUnit.UNKNOWN

/**
 * Settings in backend order. A setting without exactly 3 thresholds and 4 levels is dropped, so it is never sent back;
 * an unknown level of a setting is read as `NEUTRAL`, as the backend accepts only known levels.
 */
fun IndicatorSettingsDto.toDomain(): List<IndicatorSetting> = settings.mapNotNull { it.toDomainOrNull() }

private fun IndicatorSettingDto.toDomainOrNull(): IndicatorSetting? {
    if (thresholds.size != IndicatorIntervals.THRESHOLD_COUNT || levels.size != IndicatorIntervals.INTERVAL_COUNT) return null
    return IndicatorSetting(
        ratioCode = ratioCode,
        name = name,
        shortName = shortName,
        unit = unit.toRatioUnit(),
        category = category.toEnumOr(RatioCategory.UNKNOWN),
        scale = IndicatorScale(max = scaleMax, step = step),
        enabled = enabled,
        intervals = IndicatorIntervals(
            thresholds = thresholds,
            levels = levels.map { it.toSettingLevel() },
            belowZeroLevel = belowZeroLevel.toSettingLevel(),
        ),
    )
}

private fun String.toSettingLevel(): IndicatorLevel =
    toEnumOr(IndicatorLevel.UNKNOWN).takeUnless { it == IndicatorLevel.UNKNOWN } ?: IndicatorLevel.NEUTRAL

private fun IndicatorLevel.toSettingDto(): String = takeUnless { it == IndicatorLevel.UNKNOWN }?.name ?: IndicatorLevel.NEUTRAL.name

fun List<IndicatorSetting>.toUpdateDto() = IndicatorSettingsUpdateDto(
    settings = map { setting ->
        IndicatorSettingValuesDto(
            ratioCode = setting.ratioCode,
            enabled = setting.enabled,
            thresholds = setting.intervals.thresholds,
            levels = setting.intervals.levels.map { it.toSettingDto() },
            belowZeroLevel = setting.intervals.belowZeroLevel.toSettingDto(),
        )
    },
)
