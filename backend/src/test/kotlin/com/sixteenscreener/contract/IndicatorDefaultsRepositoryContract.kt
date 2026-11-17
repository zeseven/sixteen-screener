package com.sixteenscreener.contract

import com.sixteenscreener.domain.model.IndicatorLevel
import com.sixteenscreener.domain.model.IndicatorScale
import com.sixteenscreener.domain.service.IndicatorDefaults
import com.sixteenscreener.port.outbound.IndicatorDefaultsRepository
import io.kotest.core.spec.style.funSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

val tunedDefaults = IndicatorDefaults.all.map { default ->
    default.copy(
        setting = default.setting.copy(enabled = !default.setting.enabled, belowZeroLevel = IndicatorLevel.NEUTRAL),
        scale = IndicatorScale(default.scale.scaleMax * 2, default.scale.step),
    )
}

fun indicatorDefaultsRepositoryContract(newRepository: suspend () -> IndicatorDefaultsRepository) = funSpec {
    test("missing defaults are created from the initial values and returned") {
        val repository = newRepository()

        repository.getOrCreate(IndicatorDefaults.all) shouldContainExactly IndicatorDefaults.all
        repository.getOrCreate(IndicatorDefaults.all) shouldContainExactly IndicatorDefaults.all
    }

    test("stored defaults are never overwritten") {
        val repository = newRepository()
        repository.getOrCreate(tunedDefaults)

        repository.getOrCreate(IndicatorDefaults.all) shouldContainExactly tunedDefaults
    }

    test("concurrent first reads store one set of defaults") {
        val repository = newRepository()

        val results = coroutineScope {
            listOf(IndicatorDefaults.all, tunedDefaults, IndicatorDefaults.all, tunedDefaults)
                .map { initial -> async { repository.getOrCreate(initial) } }
                .awaitAll()
        }

        results.toSet().size shouldBe 1
        repository.getOrCreate(IndicatorDefaults.all) shouldBe results.first()
    }
}
