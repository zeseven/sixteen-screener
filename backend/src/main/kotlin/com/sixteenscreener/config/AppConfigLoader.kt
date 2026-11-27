package com.sixteenscreener.config

import com.sixteenscreener.domain.model.Capability
import com.sixteenscreener.domain.model.DefaultSources
import com.sixteenscreener.domain.model.ExchangeCode
import com.sixteenscreener.domain.model.KnownExchangeCodes
import com.sixteenscreener.domain.model.LimitInterval
import com.sixteenscreener.domain.model.LimitUnit
import com.sixteenscreener.domain.model.RequestLimit
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceRole
import io.ktor.server.config.ApplicationConfig
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

object AppConfigLoader {

    const val ENV_PORT = "PORT"
    const val ENV_GCP_PROJECT_ID = "GCP_PROJECT_ID"
    const val ENV_FIRESTORE_DATABASE = "FIRESTORE_DATABASE"
    const val ENV_LOGO_BUCKET = "LOGO_BUCKET"
    const val ENV_FINNHUB_API_KEY = "FINNHUB_API_KEY"
    const val ENV_SEC_EDGAR_USER_AGENT = "SEC_EDGAR_USER_AGENT"
    const val ENV_EODHD_API_KEY = "EODHD_API_KEY"
    const val ENV_STORAGE_EMULATOR_HOST = "STORAGE_EMULATOR_HOST"
    const val ENV_YAHOO_PROXY_URL = "YAHOO_PROXY_URL"

    private const val DEFAULT_TIMEOUT_MILLIS = 5000L
    private const val MAX_PORT = 65535
    private val FIRESTORE_DATABASE_ID = Regex("^[a-z][a-z0-9-]{2,61}[a-z0-9]$")

    const val SEC_EDGAR_FILES_BASE_URL = "files"

    private const val ADDITIONAL_BASE_URLS = "additionalBaseUrls"
    private const val DIRECTORY_EXCHANGES = "directoryExchanges"
    private const val DEFAULT_SOURCES = "defaultSources"
    private const val MIN_REQUEST_INTERVAL_MILLIS = "minRequestIntervalMillis"
    private const val PAUSE_AFTER_RATE_LIMIT_MINUTES = "pauseAfterRateLimitMinutes"

    private val REQUIRED_ADDITIONAL_BASE_URLS: Map<String, List<String>> = mapOf(
        "sec-edgar" to listOf(SEC_EDGAR_FILES_BASE_URL),
    )

    private val BASE_URL_VARIABLES: Map<String, String> = mapOf(
        "yahoo" to ENV_YAHOO_PROXY_URL,
    )

    private val HTTP_URL = Regex("^https?://[^/\\s]+.*$")

    private val REQUIRED_SECRETS: Map<String, String> = mapOf(
        "finnhub" to ENV_FINNHUB_API_KEY,
        "sec-edgar" to ENV_SEC_EDGAR_USER_AGENT,
        "eodhd" to ENV_EODHD_API_KEY,
    )

    fun load(config: ApplicationConfig, env: Map<String, String>): AppConfig {
        val reader = ConfigReader(config, env)
        val port = reader.port()
        val gcpProjectId = reader.requiredString("gcpProjectId", ENV_GCP_PROJECT_ID)
        val firestoreDatabase = reader.firestoreDatabase()
        val logoBucket = reader.requiredString("logoBucket", ENV_LOGO_BUCKET)
        val defaultSources = reader.defaultSources()
        val sources = reader.sources()
        val secrets = SourceSecrets(
            finnhubApiKey = env.secret(ENV_FINNHUB_API_KEY),
            secEdgarUserAgent = env.secret(ENV_SEC_EDGAR_USER_AGENT),
            eodhdApiKey = env.secret(ENV_EODHD_API_KEY),
        )
        val errors = reader.errors +
            duplicateIdErrors(sources) +
            defaultSourcesErrors(defaultSources.filterValues { it !in reader.invalidSourceIds }, sources) +
            missingSecretErrors(sources, env) +
            missingBaseUrlErrors(sources) +
            baseUrlVariableErrors(sources)

        if (errors.isNotEmpty()) throw InvalidConfigurationException(errors)

        return AppConfig(
            port = checkNotNull(port),
            gcpProjectId = checkNotNull(gcpProjectId),
            firestoreDatabase = checkNotNull(firestoreDatabase),
            logoBucket = checkNotNull(logoBucket),
            defaultSources = DefaultSources(
                search = SourceId.of(defaultSources.getValue(SourceRole.SEARCH)),
                us = SourceId.of(defaultSources.getValue(SourceRole.US)),
                international = SourceId.of(defaultSources.getValue(SourceRole.INTERNATIONAL)),
            ),
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

    private fun defaultSourcesErrors(defaultSources: Map<SourceRole, String>, sources: List<SourceConfig>): List<String> =
        defaultSources.mapNotNull { (role, defaultSource) ->
            val source = sources.firstOrNull { it.id.value == defaultSource }
            val label = "Default ${role.configKey} source '$defaultSource'"
            when {
                source == null -> "$label is not a configured source"
                !source.enabled -> "$label is not enabled"
                role !in source.roles -> "$label is not configured for the role ${role.name}"
                else -> null
            }
        }

    private val SourceRole.configKey: String get() = name.lowercase()

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

    private fun baseUrlVariableErrors(sources: List<SourceConfig>): List<String> =
        sources.filter { it.enabled }.mapNotNull { source ->
            val variable = BASE_URL_VARIABLES[source.id.value] ?: return@mapNotNull null
            when {
                source.baseUrl.isBlank() -> "Source '${source.id}' is enabled but environment variable $variable is not set"
                !HTTP_URL.matches(source.baseUrl) ->
                    "Source '${source.id}': $variable '${source.baseUrl}' is not an http or https URL"
                else -> null
            }
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

        fun defaultSources(): Map<SourceRole, String> =
            SourceRole.entries.mapNotNull { role ->
                val key = "$DEFAULT_SOURCES.${role.configKey}"
                config.stringOrNull(key)?.takeIf { it.isNotBlank() }?.let { role to it }
                    ?: null.also { errors += "Missing configuration '$key'" }
            }.toMap()

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
            val baseUrlVariable = rawId?.let(BASE_URL_VARIABLES::get)
            val baseUrl = baseUrlVariable?.let { env.secret(it) }
                ?: block.stringOrNull("baseUrl")?.takeIf { it.isNotBlank() }
                ?: (if (baseUrlVariable != null) "" else null)
                ?: null.also { errors += "$label: missing baseUrl" }
            val additionalBaseUrls = additionalBaseUrls(block, label)
            val enabled = boolean(block, "enabled", default = true, label)
            val roles = roles(block, label)
            val countTotalCalls = boolean(block, "countTotalCalls", default = false, label)
            val automaticRequests = boolean(block, "automaticRequests", default = true, label)
            val capabilities = capabilities(block, label)
            val limits = limits(block, label)
            val directoryExchanges = directoryExchanges(block, label)
            val timeoutMillis = block.stringOrNull("timeoutMillis")?.let { raw ->
                raw.toLongOrNull()?.takeIf { it > 0 } ?: null.also { errors += "$label: invalid timeoutMillis '$raw'" }
            } ?: DEFAULT_TIMEOUT_MILLIS
            val minRequestIntervalMillis = block.stringOrNull(MIN_REQUEST_INTERVAL_MILLIS)?.let { raw ->
                raw.toLongOrNull()?.takeIf { it > 0 }
                    ?: null.also { errors += "$label: invalid $MIN_REQUEST_INTERVAL_MILLIS '$raw'" }
            }
            val pauseAfterRateLimitMinutes = block.stringOrNull(PAUSE_AFTER_RATE_LIMIT_MINUTES)?.let { raw ->
                raw.toLongOrNull()?.takeIf { it > 0 }
                    ?: null.also { errors += "$label: invalid $PAUSE_AFTER_RATE_LIMIT_MINUTES '$raw'" }
            }

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
                roles = roles,
                limits = limits,
                timeout = timeoutMillis.milliseconds,
                countTotalCalls = countTotalCalls,
                directoryExchanges = directoryExchanges,
                automaticRequests = automaticRequests,
                minRequestInterval = minRequestIntervalMillis?.milliseconds,
                pauseAfterRateLimit = pauseAfterRateLimitMinutes?.minutes,
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

        private fun roles(block: ApplicationConfig, label: String): Set<SourceRole> =
            block.stringListOrNull("roles").orEmpty()
                .mapNotNull { raw ->
                    enumValueOrNull<SourceRole>(raw)
                        ?: null.also { errors += "$label: unknown role '$raw' (known: ${SourceRole.entries.joinToString()})" }
                }
                .toSet()

        private fun directoryExchanges(block: ApplicationConfig, label: String): Set<ExchangeCode>? {
            if (DIRECTORY_EXCHANGES !in block.toMap()) return null
            val codes = block.stringListOrNull(DIRECTORY_EXCHANGES) ?: return null.also {
                errors += "$label: invalid $DIRECTORY_EXCHANGES"
            }
            return codes.mapNotNull { raw ->
                raw.takeIf(KnownExchangeCodes::isKnown)?.let(::ExchangeCode)
                    ?: null.also { errors += "$label: unknown exchange code '$raw' in $DIRECTORY_EXCHANGES" }
            }.toSet()
        }

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
            val limit = rawLimit?.let { raw ->
                raw.toIntOrNull()?.takeIf { it >= 0 } ?: null.also { errors += "$label: invalid limit '$raw'" }
            }
            val rawUnit = block.stringOrNull("unit")
            val unit = if (rawUnit == null) {
                LimitUnit.REQUESTS
            } else {
                enumValueOrNull<LimitUnit>(rawUnit) ?: null.also { errors += "$label: unknown limit unit '$rawUnit'" }
            }
            if (interval == null || (rawLimit != null && limit == null) || unit == null) return null
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
