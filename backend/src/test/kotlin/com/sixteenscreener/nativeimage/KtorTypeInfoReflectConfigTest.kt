package com.sixteenscreener.nativeimage

import com.sixteenscreener.adapter.outbound.eodhd.EodhdClient
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.readBytes

private val mainClassLoader = EodhdClient::class.java.classLoader

private fun mainClassesDirectory(): Path = Path.of(EodhdClient::class.java.protectionDomain.codeSource.location.toURI())

private fun isSerializableClass(className: String): Boolean =
    runCatching { hasGeneratedSerializer(Class.forName(className, false, mainClassLoader)) }.getOrDefault(false)

private fun serializableClassConstantsOfMainCode(): Set<String> =
    Files.walk(mainClassesDirectory()).use { paths ->
        paths.filter { it.isRegularFile() && it.extension == "class" }.toList()
    }
        .flatMap { LdcClassConstants.binaryNamesIn(it.readBytes()) }
        .filter(::isSerializableClass)
        .toSortedSet()

class KtorTypeInfoReflectConfigTest : FunSpec({
    test("every serializable class the main code hands to Ktor by type is registered for reflection") {
        val missing = missingSerializerRegistrations(serializableClassConstantsOfMainCode(), reflectConfigText())

        withClue(
            "Ktor looks these serializers up by reflection in the native image. Add to reflect-config.json, " +
                "for each class X listed below, an entry for X with the field Companion and an entry for X\$Companion " +
                "with the method serializer and empty parameterTypes",
        ) {
            missing shouldBe emptyList()
        }
    }

    test("discovery finds library, outbound and inbound serializable classes and skips other classes") {
        val classNames = serializableClassConstantsOfMainCode()

        classNames shouldContainAll listOf(
            "kotlinx.serialization.json.JsonElement",
            "com.sixteenscreener.adapter.outbound.eodhd.DividendDto",
            "com.sixteenscreener.adapter.outbound.finnhub.QuoteDto",
            "com.sixteenscreener.adapter.inbound.http.WatchlistAddDto",
        )
        classNames shouldNotContain "com.sixteenscreener.adapter.outbound.http.SourceHttpCall"
    }
})
