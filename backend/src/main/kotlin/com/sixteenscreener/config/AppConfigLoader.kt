package com.sixteenscreener.config

import com.sixteenscreener.domain.model.Capability
import com.sixteenscreener.domain.model.LimitInterval
import com.sixteenscreener.domain.model.LimitUnit
import com.sixteenscreener.domain.model.RequestLimit
import com.sixteenscreener.domain.model.SourceId
import io.ktor.server.config.ApplicationConfig
import kotlin.time.Duration.Companion.milliseconds

object AppConfigLoader {

    const val ENV_PORT = "PORT"
    const val ENV_GCP_PROJECT_ID = "GCP_PROJECT_ID"
    const val ENV_FIRESTORE_DATABASE = "FIRESTORE_DATABASE"
    const val ENV_LOGO_BUCKET = "LOGO_BUCKET"
    const val ENV_DEFAULT_SOURCE = "DEFAULT_SOURCE"
    const val ENV_FINNHUB_API_KEY = "FINNHUB_API_KEY"
    const val ENV_SEC_EDGAR_USER_AGENT = "SEC_EDGAR_USER_AGENT"
    const val ENV_STORAGE_EMULATOR_HOST = "STORAGE_EMULATOR_HOST"

    private const val DEFAULT_TIMEOUT_MILLIS = 5000L
    private const val MAX_PORT = 65535
    private val FIRESTORE_DATABASE_ID = Regex("^[a-z][a-z0-9-]{2,61}[a-z0-9]$")

    const val SEC_EDGAR_FILES_BASE_URL = "files"

    private const val ADDITIONAL_BASE_URLS = "additionalBaseUrls"

    private val REQUIRED_ADDITIONAL_BASE_URLS: Map<String, List<String>> = mapOf(
        "sec-edgar" to listOf(SEC_EDGAR_FILES_BASE_URL),
    )

    private val REQUIRED_SECRETS: Map<String, String> = mapOf(
        "finnhub" to ENV_FINNHUB_API_KEY,
        "sec-edgar" to ENV_SEC_EDGAR_USER_AGENT,
    )

    fun load(config: ApplicationConfig, env: Map<String, String>): AppConfig {
        val reader = ConfigReader(config, env)
        val port = reader.port()
        val gcpProjectId = reader.requiredString("gcpProjectId", ENV_GCP_PROJECT_ID)
        val firestoreDatabase = reader.firestoreDatabase()
        val logoBucket = reader.requiredString("logoBucket", ENV_LOGO_BUCKET)
        val defaultSource = reader.requiredString("defaultSource", ENV_DEFAULT_SOURCE)
        val sources = reader.sources()
        val secrets = SourceSecrets(
            finnhubApiKey = env.secret(ENV_FINNHUB_API_KEY),
            secEdgarUserAgent = env.secret(ENV_SEC_EDGAR_USER_AGENT),
        )
        val errors = reader.errors +
            duplicateIdErrors(sources) +
            defaultSourceErrors(defaultSource.takeUnless { it in reader.invalidSourceIds }, sources) +
            missingSecretErrors(sources, env) +
            missingBaseUrlErrors(sources)

        if (errors.isNotEmpty()) throw InvalidConfigurationException(errors)

        return AppConfig(
            port = checkNotNull(port),
            gcpProjectId = checkNotNull(gcpProjectId),
            firestoreDatabase = checkNotNull(firestoreDatabase),
            logoBucket = checkNotNull(logoBucket),
            defaultSource = SourceId.of(checkNotNull(defaultSource)),
            sources = sources,
            secrets = secrets,
            storageEmulatorHost = env.secret(ENV_STORAGE_EMULATOR_HOST),
        )
    }

    private fun Map<String, String>.secret(name: String): String? = this[name]?.takeIf { it.isNotBlank() }

    private fun duplicateIdErrors(sources: List<SourceConfig>): List<String> =
        sources.groupingBy { it.id }.eachCount()
            .filterValues { it > 1 }
            .keys
            .map { "Duplicate source ID '$it'" }

    private fun defaultSourceErrors(defaultSource: String?, sources: List<SourceConfig>): List<String> {
        if (defaultSource == null) return emptyList()
        val source = sources.firstOrNull { it.id.value == defaultSource }
        return when {
            source == null -> listOf("Default source '$defaultSource' is not a configured source")
            !source.enabled -> listOf("Default source '$defaultSource' is not enabled")
            !source.selectableAsDefault -> listOf("Default source '$defaultSource' is not selectable as default")
            else -> emptyList()
        }
    }

    private fun missingSecretErrors(sources: List<SourceConfig>, env: Map<String, String>): List<String> =
        sources.filter { it.enabled }
            .mapNotNull { source -> REQUIRED_SECRETS[source.id.value]?.let { source to it } }
            .filter { (_, variable) -> env.secret(variable) == null }
            .map { (source, variable) -> "Source '${source.id}' is enabled but environment variable $variable is not set" }

    private fun missingBaseUrlErrors(sources: List<SourceConfig>): List<String> =
        sources.filter { it.enabled }.flatMap { source ->
            REQUIRED_ADDITIONAL_BASE_URLS[source.id.value].orEmpty()
                .filterNot { it in source.additionalBaseUrls }
                .map { "Source '${source.id}' is enabled but $ADDITIONAL_BASE_URLS.$it is not set" }
        }

    private class ConfigReader(private val config: ApplicationConfig, private val env: Map<String, String>) {
        val errors = mutableListOf<String>()
        val invalidSourceIds = mutableSetOf<String>()

        fun port(): Int? {
            val raw = env[ENV_PORT]?.takeIf { it.isNotBlank() } ?: config.stringOrNull("port") ?: return 8080
            return raw.toIntOrNull()?.takeIf { it in 1..MAX_PORT }
                ?: null.also { errors += "Port '$raw' is not a valid port number" }
        }

        fun requiredString(key: String, envName: String): String? =
            (env[envName]?.takeIf { it.isNotBlank() } ?: config.stringOrNull(key)?.takeIf { it.isNotBlank() })
                ?: null.also { errors += "Missing configuration '$key' (environment variable $envName)" }

        fun firestoreDatabase(): String? {
            val raw = requiredString("firestoreDatabase", ENV_FIRESTORE_DATABASE) ?: return null
            return raw.takeIf { FIRESTORE_DATABASE_ID.matches(it) } ?: null.also {
                errors += "Firestore database '$raw' is not a valid database ID " +
                    "(4-63 lowercase letters, digits or hyphens, starting with a letter and not ending with a hyphen)"
            }
        }

        fun sources(): List<SourceConfig> {
            val blocks = runCatching { config.configList("sources") }.getOrElse {
                errors += "Missing or invalid configuration 'sources'"
                return emptyList()
            }
            if (blocks.isEmpty()) errors += "No sources configured"
            return blocks.mapIndexedNotNull { index, block -> source(index, block) }
        }

        private fun source(index: Int, block: ApplicationConfig): SourceConfig? {
            val rawId = block.stringOrNull("id")
            val id = rawId?.let(SourceId::parseOrNull)
            val label = rawId?.let { "Source '$it'" } ?: "sources[$index]"
            val errorCount = errors.size

            if (id == null) errors += "$label: missing or invalid id"
            val name = block.stringOrNull("name")?.takeIf { it.isNotBlank() }
                ?: null.also { errors += "$label: missing name" }
            val baseUrl = block.stringOrNull("baseUrl")?.takeIf { it.isNotBlank() }
                ?: null.also { errors += "$label: missing baseUrl" }
            val additionalBaseUrls = additionalBaseUrls(block, label)
            val enabled = boolean(block, "enabled", default = true, label)
            val selectableAsDefault = boolean(block, "selectableAsDefault", default = false, label)
            val countTotalCalls = boolean(block, "countTotalCalls", default = false, label)
            val capabilities = capabilities(block, label)
            val limits = limits(block, label)
            val timeoutMillis = block.stringOrNull("timeoutMillis")?.let { raw ->
                raw.toLongOrNull()?.takeIf { it > 0 } ?: null.also { errors += "$label: invalid timeoutMillis '$raw'" }
            } ?: DEFAULT_TIMEOUT_MILLIS

            if (errors.size > errorCount) {
                rawId?.let(invalidSourceIds::add)
                return null
            }
            return SourceConfig(
                id = checkNotNull(id),
                name = checkNotNull(name),
                enabled = enabled,
                baseUrl = checkNotNull(baseUrl),
                additionalBaseUrls = additionalBaseUrls,
                capabilities = capabilities,
                selectableAsDefault = selectableAsDefault,
                limits = limits,
                timeout = timeoutMillis.milliseconds,
                countTotalCalls = countTotalCalls,
            )
        }

        private fun additionalBaseUrls(block: ApplicationConfig, label: String): Map<String, String> {
            val raw = block.toMap()[ADDITIONAL_BASE_URLS] ?: return emptyMap()
            val entries = raw as? Map<*, *> ?: return emptyMap<String, String>().also {
                errors += "$label: invalid $ADDITIONAL_BASE_URLS"
            }
            return entries.mapNotNull { (name, url) ->
                (url as? String)?.takeIf { it.isNotBlank() }?.let { name.toString() to it }
                    ?: null.also { errors += "$label: invalid $ADDITIONAL_BASE_URLS.$name" }
            }.toMap()
        }

        private fun boolean(block: ApplicationConfig, key: String, default: Boolean, label: String): Boolean {
            val raw = block.stringOrNull(key) ?: return default
            return raw.toBooleanStrictOrNull() ?: default.also { errors += "$label: invalid $key '$raw'" }
        }

        private fun capabilities(block: ApplicationConfig, label: String): Set<Capability> =
            block.stringListOrNull("capabilities").orEmpty()
                .mapNotNull { raw ->
                    enumValueOrNull<Capability>(raw)
                        ?: null.also { errors += "$label: unknown capability '$raw' (known: ${Capability.entries.joinToString()})" }
                }
                .toSet()

        private fun limits(block: ApplicationConfig, label: String): List<RequestLimit> {
            if ("limits" !in block.toMap()) return emptyList()
            val entries = runCatching { block.configList("limits") }.getOrElse {
                errors += "$label: invalid limits"
                return emptyList()
            }
            return entries.mapNotNull { limit(it, label) }
        }

        private fun limit(block: ApplicationConfig, label: String): RequestLimit? {
            val rawInterval = block.stringOrNull("interval")
            val interval = rawInterval?.let { enumValueOrNull<LimitInterval>(it) }
                ?: null.also { errors += "$label: unknown limit interval '$rawInterval' (known: ${LimitInterval.entries.joinToString()})" }
            val rawLimit = block.stringOrNull("limit")
            val limit = rawLimit?.toIntOrNull()?.takeIf { it >= 0 }
                ?: null.also { errors += "$label: invalid limit '$rawLimit'" }
            val rawUnit = block.stringOrNull("unit")
            val unit = if (rawUnit == null) {
                LimitUnit.REQUESTS
            } else {
                enumValueOrNull<LimitUnit>(rawUnit) ?: null.also { errors += "$label: unknown limit unit '$rawUnit'" }
            }
            if (interval == null || limit == null || unit == null) return null
            return RequestLimit(interval, limit, unit)
        }
    }

    private fun ApplicationConfig.stringOrNull(key: String): String? =
        runCatching { propertyOrNull(key)?.getString() }.getOrNull()

    private fun ApplicationConfig.stringListOrNull(key: String): List<String>? =
        runCatching { propertyOrNull(key)?.getList() }.getOrNull()

    private inline fun <reified E : Enum<E>> enumValueOrNull(raw: String): E? =
        enumValues<E>().firstOrNull { it.name == raw }
}
