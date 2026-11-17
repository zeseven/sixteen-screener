package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.IndicatorDefault
import com.sixteenscreener.domain.model.IndicatorScale
import com.sixteenscreener.domain.model.IndicatorSetting
import com.sixteenscreener.domain.model.InvalidInputException
import com.sixteenscreener.domain.model.Profile
import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.domain.model.RatioCode
import com.sixteenscreener.port.inbound.IndicatorSettingDetails
import com.sixteenscreener.port.inbound.IndicatorSettingsUseCase
import com.sixteenscreener.port.outbound.Clock
import com.sixteenscreener.port.outbound.IndicatorDefaultsRepository
import com.sixteenscreener.port.outbound.ProfileRepository
import org.slf4j.LoggerFactory

class IndicatorSettingsService(
    private val profiles: ProfileRepository,
    private val defaults: IndicatorDefaultsRepository,
    private val clock: Clock,
) : IndicatorSettingsUseCase {

    override suspend fun settings(profileId: ProfileId): List<IndicatorSettingDetails> {
        val profile = profiles.existing(profileId)
        val effectiveDefaults = effectiveDefaults()
        val settings = if (isComplete(profile)) profile.indicatorSettings else completeStored(profileId, effectiveDefaults)
        return detailsOf(settings, effectiveDefaults)
    }

    override suspend fun replace(profileId: ProfileId, settings: List<IndicatorSetting>): List<IndicatorSettingDetails> {
        val effectiveDefaults = effectiveDefaults()
        validate(settings, effectiveDefaults)
        val replacements = settings.associateBy { it.ratioCode }
        val profile = profiles.changeIndicatorSettings(profileId, clock.now()) { stored ->
            completed(stored, effectiveDefaults).map { replacements[it.ratioCode] ?: it }
        } ?: throw profileNotFound(profileId)
        return detailsOf(profile.indicatorSettings, effectiveDefaults)
    }

    /**
     * Settings of [profile] in settings order, used to calculate indicators. Defaults are read and copied into the
     * profile only when the profile misses settings of some ratios.
     */
    suspend fun settingsOf(profile: Profile): List<IndicatorSetting> =
        if (isComplete(profile)) profile.indicatorSettings else completeStored(profile.id, effectiveDefaults())

    private suspend fun completeStored(profileId: ProfileId, effectiveDefaults: List<IndicatorDefault>): List<IndicatorSetting> =
        profiles.changeIndicatorSettings(profileId, clock.now()) { stored -> completed(stored, effectiveDefaults) }
            ?.indicatorSettings
            ?: throw profileNotFound(profileId)

    private suspend fun effectiveDefaults(): List<IndicatorDefault> {
        val stored = defaults.getOrCreate(IndicatorDefaults.all)
        stored.mapNotNull { it.validationError() }.forEach { logger.warn("Ignoring stored indicator default: {}", it) }
        val validByCode = stored.filter { it.validationError() == null }.associateBy { it.ratioCode }
        return IndicatorDefaults.all.map { validByCode[it.ratioCode] ?: it }
    }

    private fun validate(settings: List<IndicatorSetting>, effectiveDefaults: List<IndicatorDefault>) {
        val scales = effectiveDefaults.associate { it.ratioCode to it.scale }
        settings.groupingBy { it.ratioCode }.eachCount().filterValues { it > 1 }.keys.firstOrNull()?.let {
            throw InvalidInputException("Ratio $it is repeated")
        }
        settings.firstNotNullOfOrNull { setting ->
            val scale = scales[setting.ratioCode] ?: return@firstNotNullOfOrNull "${setting.ratioCode} can't be an indicator"
            setting.validationError(scale)
        }?.let { throw InvalidInputException(it) }
    }

    private fun detailsOf(
        settings: List<IndicatorSetting>,
        effectiveDefaults: List<IndicatorDefault>,
    ): List<IndicatorSettingDetails> {
        val scales = effectiveDefaults.associate { it.ratioCode to it.scale }
        return settings.map { IndicatorSettingDetails(it, scales.getValue(it.ratioCode)) }
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(IndicatorSettingsService::class.java)

        private val UNBOUNDED = IndicatorScale(Double.MAX_VALUE, Double.MAX_VALUE)

        fun isUsable(setting: IndicatorSetting): Boolean = setting.validationError(UNBOUNDED) == null

        fun isComplete(profile: Profile): Boolean =
            profile.indicatorSettings.map { it.ratioCode } == RatioCode.indicators && profile.indicatorSettings.all(::isUsable)

        fun completed(stored: List<IndicatorSetting>, effectiveDefaults: List<IndicatorDefault>): List<IndicatorSetting> {
            val usable = stored.filter(::isUsable).associateBy { it.ratioCode }
            return effectiveDefaults.map { usable[it.ratioCode] ?: it.setting }
        }
    }
}
