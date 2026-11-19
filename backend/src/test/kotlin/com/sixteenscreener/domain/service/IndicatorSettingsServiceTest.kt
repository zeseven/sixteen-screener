package com.sixteenscreener.domain.service

import com.sixteenscreener.contract.tunedDefaults
import com.sixteenscreener.domain.model.IndicatorDefault
import com.sixteenscreener.domain.model.IndicatorLevel
import com.sixteenscreener.domain.model.IndicatorScale
import com.sixteenscreener.domain.model.IndicatorSetting
import com.sixteenscreener.domain.model.InvalidInputException
import com.sixteenscreener.domain.model.Profile
import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.domain.model.ProfileName
import com.sixteenscreener.domain.model.RatioCode
import com.sixteenscreener.domain.model.ResourceNotFoundException
import com.sixteenscreener.fakes.InMemoryIndicatorDefaultsRepository
import com.sixteenscreener.fakes.InMemoryProfileRepository
import com.sixteenscreener.fakes.MutableClock
import com.sixteenscreener.port.inbound.IndicatorSettingDetails
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.time.Duration

private val codeSettings = IndicatorDefaults.all.map { it.setting }

private val customPe = IndicatorSetting(
    ratioCode = RatioCode.PE,
    enabled = false,
    thresholds = listOf(10.0, 20.0, 30.0),
    levels = listOf(IndicatorLevel.GOOD, IndicatorLevel.GOOD, IndicatorLevel.WARNING, IndicatorLevel.BAD),
    belowZeroLevel = IndicatorLevel.WARNING,
)

private val customBeta = IndicatorDefaults.of(RatioCode.BETA).setting.copy(enabled = true, thresholds = listOf(0.5, 1.0, 1.5))

private class IndicatorSettingsFixture(
    storedDefaults: List<IndicatorDefault>? = null,
    settings: List<IndicatorSetting> = emptyList(),
) {
    val clock = MutableClock()
    val profiles = InMemoryProfileRepository()
    val defaults = InMemoryIndicatorDefaultsRepository(storedDefaults)
    val service = IndicatorSettingsService(profiles, defaults, clock)
    val profile = Profile(
        id = ProfileId.random(),
        name = ProfileName.of("Alice"),
        watchlist = emptyList(),
        createdAt = clock.now(),
        updatedAt = clock.now(),
        indicatorSettings = settings,
    ).also(profiles::save)

    suspend fun stored(): List<IndicatorSetting> = checkNotNull(profiles.findById(profile.id)).indicatorSettings
}

class IndicatorSettingsServiceTest : FunSpec({

    context("settings") {
        test("a profile without settings gets the defaults of code stored, with the defaults document created") {
            val f = IndicatorSettingsFixture()

            val settings = f.service.settings(f.profile.id)

            settings.map { it.setting } shouldContainExactly codeSettings
            settings.map { it.scale } shouldContainExactly IndicatorDefaults.all.map { it.scale }
            f.stored() shouldContainExactly codeSettings
            f.defaults.stored shouldBe IndicatorDefaults.all
            f.profiles.indicatorSettingWrites shouldBe 1
        }

        test("a profile with all settings is read without writes") {
            val f = IndicatorSettingsFixture()
            f.service.settings(f.profile.id)

            f.service.settings(f.profile.id).map { it.setting } shouldContainExactly codeSettings

            f.profiles.indicatorSettingWrites shouldBe 1
        }

        test("stored defaults are copied with their scales") {
            val f = IndicatorSettingsFixture(storedDefaults = tunedDefaults)

            val settings = f.service.settings(f.profile.id)

            settings.map { it.setting } shouldContainExactly tunedDefaults.map { it.setting }
            settings.map { it.scale } shouldContainExactly tunedDefaults.map { it.scale }
        }

        test("ratios missing in the profile are added from the defaults, existing settings are kept, in settings order") {
            val f = IndicatorSettingsFixture(storedDefaults = tunedDefaults, settings = listOf(customBeta, customPe))

            val settings = f.service.settings(f.profile.id).map { it.setting }

            settings.map { it.ratioCode } shouldContainExactly RatioCode.indicators
            settings.first() shouldBe customPe
            settings.last() shouldBe customBeta
            settings.subList(1, settings.size - 1) shouldContainExactly tunedDefaults.map { it.setting }.subList(1, 30)
            f.stored() shouldContainExactly settings
        }

        test("existing settings are never changed by tuned defaults") {
            val f = IndicatorSettingsFixture(settings = codeSettings)
            f.defaults.stored = tunedDefaults

            val settings = f.service.settings(f.profile.id)

            settings.map { it.setting } shouldContainExactly codeSettings
            settings.map { it.scale } shouldContainExactly tunedDefaults.map { it.scale }
            f.profiles.indicatorSettingWrites shouldBe 0
        }

        test("stored settings with a broken shape are replaced by the defaults") {
            val broken = codeSettings.map { if (it.ratioCode == RatioCode.ROA) it.copy(thresholds = listOf(1.0, 2.0)) else it }
            val f = IndicatorSettingsFixture(settings = broken)

            f.service.settings(f.profile.id).map { it.setting } shouldContainExactly codeSettings
        }

        test("stored settings above a reduced scale maximum are kept") {
            val wide = codeSettings.map { if (it.ratioCode == RatioCode.PE) it.copy(thresholds = listOf(50.0, 70.0, 90.0)) else it }
            val f = IndicatorSettingsFixture(settings = wide)

            f.service.settings(f.profile.id).map { it.setting } shouldContainExactly wide
        }

        test("invalid, per share and missing stored defaults fall back to the defaults of code") {
            val invalidPe = IndicatorDefaults.of(RatioCode.PE)
                .let { it.copy(setting = it.setting.copy(thresholds = listOf(30.0, 20.0, 10.0))) }
            val invalidScale = IndicatorDefaults.of(RatioCode.ROE).copy(scale = IndicatorScale(0.0, 1.0))
            val perShare = IndicatorDefault(customPe.copy(ratioCode = RatioCode.EPS), IndicatorScale(10.0, 1.0))
            val tunedPb = tunedDefaults[1]
            val f = IndicatorSettingsFixture(storedDefaults = listOf(invalidPe, invalidScale, perShare, tunedPb))

            val settings = f.service.settings(f.profile.id)

            settings.map { it.setting.ratioCode } shouldContainExactly RatioCode.indicators
            settings[0] shouldBe settingDetails(IndicatorDefaults.of(RatioCode.PE))
            settings[1] shouldBe settingDetails(tunedPb)
            settings[8] shouldBe settingDetails(IndicatorDefaults.of(RatioCode.ROE))
        }

        test("unknown profile is not found") {
            val f = IndicatorSettingsFixture()

            shouldThrow<ResourceNotFoundException> { f.service.settings(ProfileId.random()) }
        }
    }

    context("settingsOf") {
        test("complete settings are used without reading the defaults") {
            val f = IndicatorSettingsFixture(settings = codeSettings)

            f.service.settingsOf(f.profile) shouldContainExactly codeSettings

            f.defaults.reads shouldBe 0
            f.profiles.indicatorSettingWrites shouldBe 0
        }

        test("missing settings are completed from the defaults and stored") {
            val f = IndicatorSettingsFixture(settings = listOf(customPe))

            val settings = f.service.settingsOf(f.profile)

            settings.first() shouldBe customPe
            settings.drop(1) shouldContainExactly codeSettings.drop(1)
            f.stored() shouldContainExactly settings
        }

        test("a profile deleted in between is not found") {
            val f = IndicatorSettingsFixture()
            val gone = f.profile.copy(id = ProfileId.random())

            shouldThrow<ResourceNotFoundException> { f.service.settingsOf(gone) }
        }
    }

    context("replace") {
        test("replaces the given ratios and keeps the others") {
            val f = IndicatorSettingsFixture(settings = codeSettings.map { if (it.ratioCode == RatioCode.BETA) customBeta else it })
            f.clock.advanceBy(Duration.ofMinutes(5))

            val settings = f.service.replace(f.profile.id, listOf(customPe)).map { it.setting }

            settings.first() shouldBe customPe
            settings.last() shouldBe customBeta
            settings.subList(1, 30) shouldContainExactly codeSettings.subList(1, 30)
            f.stored() shouldContainExactly settings
            f.profiles.findById(f.profile.id)?.updatedAt shouldBe f.clock.now()
        }

        test("a profile without settings gets the defaults for the other ratios") {
            val f = IndicatorSettingsFixture(storedDefaults = tunedDefaults)

            val settings = f.service.replace(f.profile.id, listOf(customPe))

            settings.first().setting shouldBe customPe
            settings.first().scale shouldBe tunedDefaults.first().scale
            settings.drop(1).map { it.setting } shouldContainExactly tunedDefaults.drop(1).map { it.setting }
        }

        test("an empty replacement stores the completed settings") {
            val f = IndicatorSettingsFixture()

            f.service.replace(f.profile.id, emptyList()).map { it.setting } shouldContainExactly codeSettings
        }

        test("thresholds up to the stored scale maximum are accepted") {
            val f = IndicatorSettingsFixture(storedDefaults = tunedDefaults)

            val wide = customPe.copy(thresholds = listOf(0.0, 60.0, 120.0))

            f.service.replace(f.profile.id, listOf(wide)).first().setting shouldBe wide
        }

        withData(
            nameFn = { it.first },
            "per share ratio" to listOf(customPe.copy(ratioCode = RatioCode.EPS)),
            "repeated ratio" to listOf(customPe, customPe.copy(enabled = true)),
            "2 thresholds" to listOf(customPe.copy(thresholds = listOf(1.0, 2.0))),
            "3 levels" to listOf(customPe.copy(levels = customPe.levels.drop(1))),
            "unordered thresholds" to listOf(customPe.copy(thresholds = listOf(20.0, 10.0, 30.0))),
            "threshold above the scale maximum" to listOf(customPe.copy(thresholds = listOf(10.0, 20.0, 61.0))),
            "negative threshold" to listOf(customBeta.copy(thresholds = listOf(-0.5, 1.0, 1.5))),
            "infinite threshold" to listOf(customPe.copy(thresholds = listOf(10.0, 20.0, Double.POSITIVE_INFINITY))),
        ) { (_, invalid) ->
            val f = IndicatorSettingsFixture(settings = codeSettings)

            shouldThrow<InvalidInputException> { f.service.replace(f.profile.id, invalid) }

            f.stored() shouldContainExactly codeSettings
        }

        test("the error names the invalid ratio") {
            val f = IndicatorSettingsFixture()

            shouldThrow<InvalidInputException> {
                f.service.replace(f.profile.id, listOf(customPe, customBeta.copy(thresholds = listOf(2.0, 1.0, 3.0))))
            }.message shouldContain "BETA"
        }

        test("unknown profile is not found") {
            val f = IndicatorSettingsFixture()

            shouldThrow<ResourceNotFoundException> { f.service.replace(ProfileId.random(), listOf(customPe)) }
        }
    }
})

private fun settingDetails(default: IndicatorDefault) = IndicatorSettingDetails(default.setting, default.scale)
