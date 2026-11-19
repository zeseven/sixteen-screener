package com.sixteenscreener.nativeimage

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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

fun hasGeneratedSerializer(type: Class<*>): Boolean =
    runCatching { type.getField("Companion").type.getMethod("serializer") }.isSuccess

fun reflectConfigText(): String =
    requireNotNull(ReflectEntry::class.java.classLoader.getResource(REFLECT_CONFIG)) { "$REFLECT_CONFIG not found" }
        .readText()
