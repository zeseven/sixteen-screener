package com.sixteenscreener.config

import com.sixteenscreener.domain.model.Capability
import com.sixteenscreener.domain.model.RequestLimit
import com.sixteenscreener.domain.model.SourceId
import kotlin.time.Duration

data class AppConfig(
    val port: Int,
    val gcpProjectId: String,
    val logoBucket: String,
    val defaultSource: SourceId,
    val sources: List<SourceConfig>,
    val secrets: SourceSecrets,
    val storageEmulatorHost: String? = null,
) {
    val enabledSources: List<SourceConfig> get() = sources.filter { it.enabled }

    fun source(id: SourceId): SourceConfig? = sources.firstOrNull { it.id == id }
}

data class SourceConfig(
    val id: SourceId,
    val name: String,
    val enabled: Boolean,
    val baseUrl: String,
    val additionalBaseUrls: Map<String, String> = emptyMap(),
    val capabilities: Set<Capability>,
    val selectableAsDefault: Boolean,
    val limits: List<RequestLimit>,
    val timeout: Duration,
)

data class SourceSecrets(
    val finnhubApiKey: String?,
    val secEdgarUserAgent: String?,
) {
    override fun toString(): String =
        "SourceSecrets(finnhubApiKey=${mask(finnhubApiKey)}, secEdgarUserAgent=${mask(secEdgarUserAgent)})"

    private fun mask(secret: String?): String = if (secret == null) "null" else "***"
}

class InvalidConfigurationException(val errors: List<String>) :
    RuntimeException("Invalid configuration:\n" + errors.joinToString("\n") { "- $it" })
