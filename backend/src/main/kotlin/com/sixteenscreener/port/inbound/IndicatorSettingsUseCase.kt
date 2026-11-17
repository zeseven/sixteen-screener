package com.sixteenscreener.port.inbound

import com.sixteenscreener.domain.model.IndicatorScale
import com.sixteenscreener.domain.model.IndicatorSetting
import com.sixteenscreener.domain.model.ProfileId

/**
 * Indicator setting of a ratio with its fixed scale; names, unit and category come from the ratio code.
 *
 * @property setting the profile's setting of the ratio
 * @property scale fixed scale of the ratio, from the defaults
 */
data class IndicatorSettingDetails(
    val setting: IndicatorSetting,
    val scale: IndicatorScale,
)

/**
 * Indicator settings of a profile (ADR-071, ADR-072).
 */
interface IndicatorSettingsUseCase {
    /**
     * Settings of every indicator ratio of profile [profileId] in settings order. A profile without settings gets a
     * copy of the defaults stored first; ratios missing in the profile are added from the defaults the same way.
     *
     * Throws [com.sixteenscreener.domain.model.ResourceNotFoundException] for an unknown profile.
     */
    suspend fun settings(profileId: ProfileId): List<IndicatorSettingDetails>

    /**
     * Replaces the settings of the ratios in [settings] for profile [profileId]; other ratios keep their stored
     * settings, or get the defaults. Returns all settings as [IndicatorSettingsUseCase.settings] does.
     *
     * Throws [com.sixteenscreener.domain.model.InvalidInputException] when a ratio can't be an indicator, a ratio is
     * repeated or a setting is invalid against the ratio's scale, and
     * [com.sixteenscreener.domain.model.ResourceNotFoundException] for an unknown profile.
     */
    suspend fun replace(profileId: ProfileId, settings: List<IndicatorSetting>): List<IndicatorSettingDetails>
}
