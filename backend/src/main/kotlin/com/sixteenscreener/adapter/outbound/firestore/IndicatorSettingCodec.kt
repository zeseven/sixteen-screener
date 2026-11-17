package com.sixteenscreener.adapter.outbound.firestore

import com.sixteenscreener.domain.model.IndicatorDefault
import com.sixteenscreener.domain.model.IndicatorLevel
import com.sixteenscreener.domain.model.IndicatorScale
import com.sixteenscreener.domain.model.IndicatorSetting
import com.sixteenscreener.domain.model.RatioCode

object IndicatorSettingCodec {
    private const val RATIO_CODE = "ratioCode"
    private const val ENABLED = "enabled"
    private const val THRESHOLDS = "thresholds"
    private const val LEVELS = "levels"
    private const val BELOW_ZERO_LEVEL = "belowZeroLevel"
    private const val SCALE_MAX = "scaleMax"
    private const val STEP = "step"

    fun encode(setting: IndicatorSetting): FirestoreMap = mapOf(
        RATIO_CODE to setting.ratioCode.name,
        ENABLED to setting.enabled,
        THRESHOLDS to setting.thresholds,
        LEVELS to setting.levels.map { it.name },
        BELOW_ZERO_LEVEL to setting.belowZeroLevel.name,
    )

    fun encodeDefault(default: IndicatorDefault): FirestoreMap =
        encode(default.setting) + mapOf(SCALE_MAX to default.scale.scaleMax, STEP to default.scale.step)

    fun decodeOrNull(stored: Any?): IndicatorSetting? {
        val map = stored as? Map<*, *> ?: return null
        val thresholds = (map[THRESHOLDS] as? List<*>)?.map { (it as? Number)?.toDouble() ?: return null } ?: return null
        val levels = (map[LEVELS] as? List<*>)?.map { levelOrNull(it) ?: return null } ?: return null
        return IndicatorSetting(
            ratioCode = (map[RATIO_CODE] as? String)?.let(RatioCode::parseOrNull) ?: return null,
            enabled = map[ENABLED] as? Boolean ?: return null,
            thresholds = thresholds,
            levels = levels,
            belowZeroLevel = levelOrNull(map[BELOW_ZERO_LEVEL]) ?: return null,
        )
    }

    fun decodeDefaultOrNull(stored: Any?): IndicatorDefault? {
        val map = stored as? Map<*, *> ?: return null
        val setting = decodeOrNull(map) ?: return null
        val scaleMax = (map[SCALE_MAX] as? Number)?.toDouble() ?: return null
        val step = (map[STEP] as? Number)?.toDouble() ?: return null
        return IndicatorDefault(setting, IndicatorScale(scaleMax, step))
    }

    private fun levelOrNull(stored: Any?): IndicatorLevel? = IndicatorLevel.entries.firstOrNull { it.name == stored }
}
