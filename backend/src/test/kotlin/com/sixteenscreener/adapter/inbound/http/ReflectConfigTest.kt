package com.sixteenscreener.adapter.inbound.http

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension

private const val DTO_PACKAGE = "com.sixteenscreener.adapter.inbound.http"
private const val REFLECT_CONFIG =
    "META-INF/native-image/com.sixteenscreener/sixteen-screener-backend/reflect-config.json"

@Serializable
private data class ReflectMember(val name: String, val parameterTypes: List<String> = emptyList())

@Serializable
private data class ReflectEntry(
    val name: String,
    val fields: List<ReflectMember> = emptyList(),
    val methods: List<ReflectMember> = emptyList(),
)

private val ReflectConfigJson = Json { ignoreUnknownKeys = true }

fun missingSerializerRegistrations(classNames: Collection<String>, reflectConfig: String): List<String> {
    val entries = ReflectConfigJson.decodeFromString<List<ReflectEntry>>(reflectConfig).associateBy { it.name }
    fun hasCompanionField(className: String) =
        entries[className]?.fields.orEmpty().any { it.name == "Companion" }
    fun hasSerializerMethod(className: String) =
        entries["$className\$Companion"]?.methods.orEmpty()
            .any { it.name == "serializer" && it.parameterTypes.isEmpty() }
    return classNames.filterNot { hasCompanionField(it) && hasSerializerMethod(it) }.sorted()
}

private fun hasGeneratedSerializer(type: Class<*>): Boolean =
    runCatching { type.getField("Companion").type.getMethod("serializer") }.isSuccess

private fun serializableTopLevelDtoClassNames(): List<String> {
    val classLoader = Problem::class.java.classLoader
    val mainOutput = Path.of(Problem::class.java.protectionDomain.codeSource.location.toURI())
    return mainOutput.resolve(DTO_PACKAGE.replace('.', '/'))
        .listDirectoryEntries("*.class")
        .map { it.nameWithoutExtension }
        .filterNot { '$' in it }
        .map { "$DTO_PACKAGE.$it" }
        .filter { hasGeneratedSerializer(Class.forName(it, false, classLoader)) }
        .sorted()
}

private fun reflectConfigText(): String =
    requireNotNull(Problem::class.java.classLoader.getResource(REFLECT_CONFIG)) { "$REFLECT_CONFIG not found" }
        .readText()

private fun config(vararg entries: String) = entries.joinToString(prefix = "[", postfix = "]")

private fun companionField(className: String) =
    """{"name": "$className", "fields": [{"name": "Companion"}]}"""

private fun serializerMethod(className: String, parameterTypes: String = "") =
    """{"name": "$className${'$'}Companion", "methods": [{"name": "serializer", "parameterTypes": [$parameterTypes]}]}"""

class ReflectConfigTest : FunSpec({
    test("every serializable top-level DTO of the HTTP adapter is registered for reflection") {
        missingSerializerRegistrations(serializableTopLevelDtoClassNames(), reflectConfigText()) shouldBe emptyList()
    }

    test("DTO discovery finds the serializable classes of the HTTP adapter") {
        val classNames = serializableTopLevelDtoClassNames()

        classNames shouldContain "$DTO_PACKAGE.WatchlistAddDto"
        classNames shouldContain "$DTO_PACKAGE.ProfileCreateDto"
        classNames.none { it.endsWith("RoutesKt") } shouldBe true
    }

    context("missingSerializerRegistrations") {
        test("reports nothing when the class has both the Companion field and the serializer method") {
            missingSerializerRegistrations(listOf("a.Dto"), config(companionField("a.Dto"), serializerMethod("a.Dto")))
                .shouldBeEmpty()
        }

        test("reports nothing for an empty class list") {
            missingSerializerRegistrations(emptyList(), config()).shouldBeEmpty()
        }

        test("reports a class missing from the config entirely") {
            missingSerializerRegistrations(listOf("a.Dto"), config()) shouldContainExactly listOf("a.Dto")
        }

        test("reports a class with the Companion field but no serializer method") {
            missingSerializerRegistrations(listOf("a.Dto"), config(companionField("a.Dto")))
                .shouldContainExactly("a.Dto")
        }

        test("reports a class with the serializer method but no Companion field") {
            missingSerializerRegistrations(listOf("a.Dto"), config(serializerMethod("a.Dto")))
                .shouldContainExactly("a.Dto")
        }

        test("reports a class whose class entry lists other fields only") {
            val otherField = """{"name": "a.Dto", "fields": [{"name": "INSTANCE"}]}"""

            missingSerializerRegistrations(listOf("a.Dto"), config(otherField, serializerMethod("a.Dto")))
                .shouldContainExactly("a.Dto")
        }

        test("reports a class whose serializer method is registered with parameters only") {
            val config = config(companionField("a.Dto"), serializerMethod("a.Dto", "\"kotlinx.serialization.KSerializer\""))

            missingSerializerRegistrations(listOf("a.Dto"), config).shouldContainExactly("a.Dto")
        }

        test("lists all missing classes sorted and skips registered ones") {
            val config = config(companionField("a.Ok"), serializerMethod("a.Ok"), companionField("a.Half"))

            missingSerializerRegistrations(listOf("a.Missing", "a.Ok", "a.Half"), config)
                .shouldContainExactly("a.Half", "a.Missing")
        }
    }
})
