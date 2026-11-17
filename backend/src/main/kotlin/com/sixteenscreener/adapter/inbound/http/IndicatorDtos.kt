package com.sixteenscreener.adapter.inbound.http

import com.sixteenscreener.domain.model.Indicator
import com.sixteenscreener.domain.model.IndicatorLevel
import com.sixteenscreener.domain.model.IndicatorSetting
import com.sixteenscreener.domain.model.InvalidInputException
import com.sixteenscreener.domain.model.RatioCode
import com.sixteenscreener.port.inbound.IndicatorSettingDetails
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

fun Indicator.toDto() = IndicatorDto(ratioCode = ratioCode.name, name = name, level = level.name)

fun List<IndicatorSettingDetails>.toDto() = IndicatorSettingsDto(map { it.toDto() })

fun IndicatorSettingDetails.toDto() = IndicatorSettingDto(
    ratioCode = setting.ratioCode.name,
    enabled = setting.enabled,
    thresholds = setting.thresholds,
    levels = setting.levels.map { it.name },
    belowZeroLevel = setting.belowZeroLevel.name,
    name = setting.ratioCode.displayName,
    shortName = setting.ratioCode.shortName,
    unit = setting.ratioCode.unit.name,
    category = setting.ratioCode.category.name,
    scaleMax = scale.scaleMax,
    step = scale.step,
)

fun IndicatorSettingValuesDto.toDomain() = IndicatorSetting(
    ratioCode = RatioCode.parseOrNull(ratioCode) ?: throw InvalidInputException("Unknown ratio code '$ratioCode'"),
    enabled = enabled,
    thresholds = thresholds,
    levels = levels.map(::levelOf),
    belowZeroLevel = levelOf(belowZeroLevel),
)

private fun levelOf(raw: String): IndicatorLevel =
    IndicatorLevel.entries.firstOrNull { it.name == raw } ?: throw InvalidInputException("Unknown indicator level '$raw'")
