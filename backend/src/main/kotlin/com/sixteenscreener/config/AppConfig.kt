package com.sixteenscreener.config

import com.sixteenscreener.domain.model.Capability
import com.sixteenscreener.domain.model.DefaultSources
import com.sixteenscreener.domain.model.ExchangeCode
import com.sixteenscreener.domain.model.RequestLimit
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceRole
import kotlin.time.Duration

data class AppConfig(
    val port: Int,
    val gcpProjectId: String,
    val firestoreDatabase: String,
    val logoBucket: String,
    val defaultSources: DefaultSources,
    val sources: List<SourceConfig>,
    val secrets: SourceSecrets,
    val storageEmulatorHost: String? = null,
) {
    val enabledSources: List<SourceConfig> get() = sources.filter { it.enabled }

    val totalCountedSources: Set<SourceId> get() = sources.filter { it.countTotalCalls }.map { it.id }.toSet()

    val manualOnlySources: Set<SourceId> get() = enabledSources.filterNot { it.automaticRequests }.map { it.id }.toSet()

    fun source(id: SourceId): SourceConfig? = sources.firstOrNull { it.id == id }
}

data class SourceConfig(
    val id: SourceId,
    val name: String,
    val enabled: Boolean,
    val baseUrl: String,
    val additionalBaseUrls: Map<String, String> = emptyMap(),
    val capabilities: Set<Capability>,
    val roles: Set<SourceRole> = emptySet(),
    val limits: List<RequestLimit>,
    val timeout: Duration,
    val countTotalCalls: Boolean = false,
    val directoryExchanges: Set<ExchangeCode>? = null,
    val automaticRequests: Boolean = true,
)

data class SourceSecrets(
    val finnhubApiKey: String?,
    val secEdgarUserAgent: String?,
    val eodhdApiKey: String? = null,
) {
    override fun toString(): String =
        "SourceSecrets(finnhubApiKey=${mask(finnhubApiKey)}, secEdgarUserAgent=${mask(secEdgarUserAgent)}, " +
            "eodhdApiKey=${mask(eodhdApiKey)})"

    private fun mask(secret: String?): String = if (secret == null) "null" else "***"
}

class InvalidConfigurationException(val errors: List<String>) :
    RuntimeException("Invalid configuration:\n" + errors.joinToString("\n") { "- $it" })
