package com.sixteenscreener.config

import com.sixteenscreener.domain.model.Capability
import com.sixteenscreener.domain.model.LimitInterval
import com.sixteenscreener.domain.model.LimitUnit
import com.sixteenscreener.domain.model.RequestLimit
import com.sixteenscreener.domain.model.SourceId
import com.typesafe.config.ConfigFactory
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.HoconApplicationConfig
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val FULL_ENV = mapOf(
    "GCP_PROJECT_ID" to "test-project",
    "LOGO_BUCKET" to "test-bucket",
    "FINNHUB_API_KEY" to "finnhub-key",
    "SEC_EDGAR_USER_AGENT" to "Sixteen Screener test@example.com",
)

private const val SEC_FILES = "additionalBaseUrls = { files = \"https://www.sec.test\" }"

private fun hocon(text: String): ApplicationConfig = HoconApplicationConfig(ConfigFactory.parseString(text))

private fun source(
    id: String,
    enabled: Boolean = true,
    selectable: Boolean = false,
    capabilities: String = "QUOTE",
    extra: String = "",
) = """
    {
      id = "$id", name = "Name of $id", enabled = $enabled, baseUrl = "https://example.com/$id"
      capabilities = [$capabilities], selectableAsDefault = $selectable
      $extra
    }
""".trimIndent()

private fun configWith(vararg sources: String, defaultSource: String = "finnhub"): ApplicationConfig =
    hocon("defaultSource = \"$defaultSource\"\nsources = [${sources.joinToString(",\n")}]")

private fun loadErrors(config: ApplicationConfig, env: Map<String, String> = FULL_ENV): List<String> =
    shouldThrow<InvalidConfigurationException> { AppConfigLoader.load(config, env) }.errors

class AppConfigLoaderTest : FunSpec({

    test("loads the bundled application.conf with all secrets set") {
        val config = AppConfigLoader.load(ApplicationConfig("application.conf"), FULL_ENV)

        config.port shouldBe 8080
        config.gcpProjectId shouldBe "test-project"
        config.logoBucket shouldBe "test-bucket"
        config.defaultSource shouldBe SourceId.of("finnhub")
        config.sources.map { it.id.value } shouldContainExactly listOf("finnhub", "sec-edgar", "logoapi")
        config.secrets.finnhubApiKey shouldBe "finnhub-key"
        config.secrets.secEdgarUserAgent shouldBe "Sixteen Screener test@example.com"

        val finnhub = config.source(SourceId.of("finnhub"))!!
        finnhub.name shouldBe "Finnhub"
        finnhub.enabled shouldBe true
        finnhub.baseUrl shouldBe "https://finnhub.io/api/v1"
        finnhub.selectableAsDefault shouldBe true
        finnhub.capabilities shouldBe setOf(
            Capability.SEARCH, Capability.QUOTE, Capability.RATIOS, Capability.BALANCE_SHEET, Capability.COMPANY_PROFILE,
        )
        finnhub.limits shouldContainExactly listOf(RequestLimit(LimitInterval.MINUTE, 60))
        finnhub.timeout shouldBe 5.seconds

        config.source(SourceId.of("logoapi"))!!.limits shouldContainExactly listOf(RequestLimit(LimitInterval.DAY, 1000))

        val secEdgar = config.source(SourceId.of("sec-edgar"))!!
        secEdgar.baseUrl shouldBe "https://data.sec.gov"
        secEdgar.additionalBaseUrls shouldBe mapOf("files" to "https://www.sec.gov")
        finnhub.additionalBaseUrls shouldBe emptyMap()
    }

    test("environment variables override configuration values") {
        val config = AppConfigLoader.load(
            ApplicationConfig("application.conf"),
            FULL_ENV + mapOf("PORT" to "9090", "DEFAULT_SOURCE" to "finnhub"),
        )
        config.port shouldBe 9090
    }

    test("defaults for optional source fields") {
        val config = AppConfigLoader.load(
            hocon(
                """
                defaultSource = finnhub
                sources = [{ id = finnhub, name = Finnhub, baseUrl = "https://x", capabilities = [QUOTE], selectableAsDefault = true }]
                """.trimIndent(),
            ),
            FULL_ENV,
        )
        val source = config.sources.single()
        source.enabled shouldBe true
        source.limits shouldBe emptyList()
        source.timeout shouldBe 5000.milliseconds
    }

    test("parses several limits with units") {
        val config = AppConfigLoader.load(
            configWith(
                source(
                    "finnhub",
                    selectable = true,
                    extra = "limits = [{ interval = HOUR, limit = 50 }, { interval = MONTH, limit = 500, unit = UNIQUE_SYMBOLS }]",
                ),
            ),
            FULL_ENV,
        )
        config.sources.single().limits shouldContainExactly listOf(
            RequestLimit(LimitInterval.HOUR, 50),
            RequestLimit(LimitInterval.MONTH, 500, LimitUnit.UNIQUE_SYMBOLS),
        )
    }

    test("fails when the secret of an enabled source is missing") {
        val errors = loadErrors(ApplicationConfig("application.conf"), FULL_ENV - "FINNHUB_API_KEY")
        errors shouldContainExactly listOf("Source 'finnhub' is enabled but environment variable FINNHUB_API_KEY is not set")
    }

    test("a blank secret counts as missing") {
        val errors = loadErrors(ApplicationConfig("application.conf"), FULL_ENV + ("SEC_EDGAR_USER_AGENT" to "  "))
        errors shouldContainExactly listOf(
            "Source 'sec-edgar' is enabled but environment variable SEC_EDGAR_USER_AGENT is not set",
        )
    }

    test("a disabled source needs no secret") {
        val config = AppConfigLoader.load(
            configWith(source("finnhub", selectable = true), source("sec-edgar", enabled = false)),
            FULL_ENV - "SEC_EDGAR_USER_AGENT",
        )
        config.enabledSources.map { it.id.value } shouldContainExactly listOf("finnhub")
    }

    test("logoapi needs no secret") {
        AppConfigLoader.load(
            configWith(source("finnhub", selectable = true), source("logoapi", capabilities = "LOGO")),
            FULL_ENV,
        ).sources shouldHaveSize 2
    }

    test("fails when an enabled SEC EDGAR source has no files base URL") {
        val errors = loadErrors(configWith(source("finnhub", selectable = true), source("sec-edgar")))
        errors shouldContainExactly listOf("Source 'sec-edgar' is enabled but additionalBaseUrls.files is not set")
    }

    test("fails for a blank additional base URL") {
        val errors = loadErrors(
            configWith(source("finnhub", selectable = true, extra = "additionalBaseUrls = { files = \" \", other = \"https://y\" }")),
        )
        errors shouldContainExactly listOf("Source 'finnhub': invalid additionalBaseUrls.files")
    }

    test("fails for additional base URLs that are not an object") {
        val errors = loadErrors(configWith(source("finnhub", selectable = true, extra = "additionalBaseUrls = \"https://y\"")))
        errors shouldContainExactly listOf("Source 'finnhub': invalid additionalBaseUrls")
    }

    test("fails for an unknown default source") {
        val errors = loadErrors(configWith(source("finnhub", selectable = true), defaultSource = "tiingo"))
        errors shouldContainExactly listOf("Default source 'tiingo' is not a configured source")
    }

    test("fails for a default source that is not selectable as default") {
        val errors = loadErrors(
            configWith(source("finnhub", selectable = true), source("sec-edgar", extra = SEC_FILES), defaultSource = "sec-edgar"),
        )
        errors shouldContainExactly listOf("Default source 'sec-edgar' is not selectable as default")
    }

    test("fails for a disabled default source") {
        val errors = loadErrors(configWith(source("finnhub", enabled = false, selectable = true)))
        errors shouldContainExactly listOf("Default source 'finnhub' is not enabled")
    }

    test("fails for duplicate source IDs") {
        val errors = loadErrors(configWith(source("finnhub", selectable = true), source("finnhub", selectable = true)))
        errors shouldContainExactly listOf("Duplicate source ID 'finnhub'")
    }

    test("fails for an unknown capability") {
        val errors = loadErrors(configWith(source("finnhub", selectable = true, capabilities = "QUOTE, TELEPATHY")))
        errors.single() shouldContain "Source 'finnhub': unknown capability 'TELEPATHY'"
    }

    test("fails for an unknown limit interval and an invalid limit") {
        val errors = loadErrors(
            configWith(source("finnhub", selectable = true, extra = "limits = [{ interval = WEEK, limit = 5 }, { interval = DAY, limit = -1 }]")),
        )
        errors shouldHaveSize 2
        errors[0] shouldContain "unknown limit interval 'WEEK'"
        errors[1] shouldContain "invalid limit '-1'"
    }

    test("fails for an invalid source ID, missing name and invalid timeout") {
        val errors = loadErrors(
            hocon(
                """
                defaultSource = finnhub
                sources = [
                  { id = finnhub, name = Finnhub, baseUrl = "https://x", selectableAsDefault = true },
                  { id = "Bad_Id", baseUrl = "https://x", timeoutMillis = 0 }
                ]
                """.trimIndent(),
            ),
        )
        errors shouldContainExactly listOf(
            "Source 'Bad_Id': missing or invalid id",
            "Source 'Bad_Id': missing name",
            "Source 'Bad_Id': invalid timeoutMillis '0'",
        )
    }

    test("fails for missing GCP settings and an invalid port") {
        val errors = loadErrors(
            configWith(source("finnhub", selectable = true)),
            FULL_ENV - "GCP_PROJECT_ID" - "LOGO_BUCKET" + ("PORT" to "70000"),
        )
        errors shouldContainExactly listOf(
            "Port '70000' is not a valid port number",
            "Missing configuration 'gcpProjectId' (environment variable GCP_PROJECT_ID)",
            "Missing configuration 'logoBucket' (environment variable LOGO_BUCKET)",
        )
    }

    test("fails when no sources are configured") {
        loadErrors(hocon("defaultSource = finnhub\nsources = []")) shouldContain "No sources configured"
    }

    test("reports all errors in one message") {
        val exception = shouldThrow<InvalidConfigurationException> {
            AppConfigLoader.load(configWith(source("finnhub", selectable = true), defaultSource = "x"), emptyMap())
        }
        exception.errors shouldHaveSize 4
        exception.message shouldContain "Invalid configuration:"
        exception.message shouldContain "- Default source 'x' is not a configured source"
    }

    test("secrets are masked in toString") {
        val config = AppConfigLoader.load(ApplicationConfig("application.conf"), FULL_ENV)
        config.toString() shouldNotContain "finnhub-key"
    }

    test("storage emulator host is taken from STORAGE_EMULATOR_HOST") {
        val config = AppConfigLoader.load(
            ApplicationConfig("application.conf"),
            FULL_ENV + ("STORAGE_EMULATOR_HOST" to "http://localhost:4443"),
        )
        config.storageEmulatorHost shouldBe "http://localhost:4443"
    }

    test("storage emulator host is absent when STORAGE_EMULATOR_HOST is unset or blank") {
        AppConfigLoader.load(ApplicationConfig("application.conf"), FULL_ENV).storageEmulatorHost shouldBe null
        AppConfigLoader.load(
            ApplicationConfig("application.conf"),
            FULL_ENV + ("STORAGE_EMULATOR_HOST" to " "),
        ).storageEmulatorHost shouldBe null
    }
})
