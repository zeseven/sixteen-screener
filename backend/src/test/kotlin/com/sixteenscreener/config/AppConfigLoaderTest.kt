package com.sixteenscreener.config

import com.sixteenscreener.domain.model.Capability
import com.sixteenscreener.domain.model.DefaultSources
import com.sixteenscreener.domain.model.ExchangeCode
import com.sixteenscreener.domain.model.LimitInterval
import com.sixteenscreener.domain.model.LimitUnit
import com.sixteenscreener.domain.model.RequestLimit
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.SourceRole
import com.typesafe.config.ConfigFactory
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
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
    "EODHD_API_KEY" to "eodhd-key",
)

private const val SEC_FILES = "additionalBaseUrls = { files = \"https://www.sec.test\" }"

private const val FIRESTORE_DATABASE = "firestoreDatabase = sixteen-screener"

private fun hocon(text: String): ApplicationConfig = HoconApplicationConfig(ConfigFactory.parseString(text))

private fun source(
    id: String,
    enabled: Boolean = true,
    roles: String = "",
    capabilities: String = "QUOTE",
    extra: String = "",
) = """
    {
      id = "$id", name = "Name of $id", enabled = $enabled, baseUrl = "https://example.com/$id"
      capabilities = [$capabilities], roles = [$roles]
      $extra
    }
""".trimIndent()

private const val ALL_ROLES = "SEARCH, US, INTERNATIONAL"

private fun defaultSources(search: String, us: String = search, international: String = search) =
    "defaultSources { search = \"$search\", us = \"$us\", international = \"$international\" }"

private fun configWith(vararg sources: String, defaultSource: String = "finnhub"): ApplicationConfig =
    configWith(sources.toList(), defaultSources(defaultSource))

private fun configWith(sources: List<String>, defaultSources: String): ApplicationConfig =
    hocon("$FIRESTORE_DATABASE\n$defaultSources\nsources = [${sources.joinToString(",\n")}]")

private fun loadErrors(config: ApplicationConfig, env: Map<String, String> = FULL_ENV): List<String> =
    shouldThrow<InvalidConfigurationException> { AppConfigLoader.load(config, env) }.errors

class AppConfigLoaderTest : FunSpec({

    test("loads the bundled application.conf with all secrets set") {
        val config = AppConfigLoader.load(ApplicationConfig("application.conf"), FULL_ENV)

        config.port shouldBe 8080
        config.gcpProjectId shouldBe "test-project"
        config.logoBucket shouldBe "test-bucket"
        config.defaultSources shouldBe DefaultSources(
            search = SourceId.of("finnhub"),
            us = SourceId.of("finnhub"),
            international = SourceId.of("eodhd"),
        )
        config.sources.map { it.id.value } shouldContainExactly listOf("finnhub", "eodhd", "sec-edgar", "logoapi", "eodhd-logos", "finnhub-logos")
        config.secrets.finnhubApiKey shouldBe "finnhub-key"
        config.secrets.secEdgarUserAgent shouldBe "Sixteen Screener test@example.com"
        config.secrets.eodhdApiKey shouldBe "eodhd-key"

        val finnhub = config.source(SourceId.of("finnhub"))!!
        finnhub.name shouldBe "Finnhub"
        finnhub.enabled shouldBe true
        finnhub.baseUrl shouldBe "https://finnhub.io/api/v1"
        finnhub.roles shouldBe setOf(SourceRole.SEARCH, SourceRole.US)
        finnhub.capabilities shouldBe setOf(
            Capability.SEARCH, Capability.QUOTE, Capability.RATIOS, Capability.BALANCE_SHEET, Capability.COMPANY_PROFILE,
        )
        finnhub.limits shouldContainExactly listOf(RequestLimit(LimitInterval.MINUTE, 60))
        finnhub.timeout shouldBe 5.seconds
        finnhub.directoryExchanges shouldBe setOf(ExchangeCode.US)

        config.source(SourceId.of("logoapi"))!!.limits shouldContainExactly listOf(RequestLimit(LimitInterval.DAY, 1000))

        val eodhdLogos = config.source(SourceId.of("eodhd-logos"))!!
        eodhdLogos.name shouldBe "EODHD logos"
        eodhdLogos.enabled shouldBe true
        eodhdLogos.baseUrl shouldBe "https://eodhd.com/img/logos"
        eodhdLogos.capabilities shouldBe setOf(Capability.LOGO)
        eodhdLogos.roles shouldBe emptySet()
        eodhdLogos.limits shouldBe emptyList()
        eodhdLogos.countTotalCalls shouldBe true
        eodhdLogos.timeout shouldBe 5.seconds

        val finnhubLogos = config.source(SourceId.of("finnhub-logos"))!!
        finnhubLogos.name shouldBe "Finnhub logos"
        finnhubLogos.enabled shouldBe true
        finnhubLogos.baseUrl shouldBe "https://static2.finnhub.io/file/publicdatany/finnhubimage/stock_logo"
        finnhubLogos.capabilities shouldBe setOf(Capability.LOGO)
        finnhubLogos.roles shouldBe emptySet()
        finnhubLogos.limits shouldBe emptyList()
        finnhubLogos.countTotalCalls shouldBe true
        finnhubLogos.timeout shouldBe 5.seconds
        config.totalCountedSources shouldBe setOf(SourceId.of("eodhd-logos"), SourceId.of("finnhub-logos"))

        finnhub.automaticRequests shouldBe true

        val eodhd = config.source(SourceId.of("eodhd"))!!
        eodhd.name shouldBe "EODHD"
        eodhd.enabled shouldBe true
        eodhd.baseUrl shouldBe "https://eodhd.com/api"
        eodhd.capabilities shouldBe setOf(Capability.QUOTE, Capability.DIVIDENDS)
        eodhd.automaticRequests shouldBe false
        eodhd.roles shouldBe setOf(SourceRole.INTERNATIONAL)
        config.source(SourceId.of("sec-edgar"))!!.roles shouldBe emptySet()
        eodhd.limits shouldContainExactly listOf(RequestLimit(LimitInterval.DAY, 20), RequestLimit(LimitInterval.MINUTE, 20))
        eodhd.directoryExchanges shouldBe emptySet()
        config.manualOnlySources shouldBe setOf(SourceId.of("eodhd"))

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
                $FIRESTORE_DATABASE
                ${defaultSources("finnhub")}
                sources = [{ id = finnhub, name = Finnhub, baseUrl = "https://x", capabilities = [QUOTE], roles = [$ALL_ROLES] }]
                """.trimIndent(),
            ),
            FULL_ENV,
        )
        val source = config.sources.single()
        source.enabled shouldBe true
        source.limits shouldBe emptyList()
        source.timeout shouldBe 5000.milliseconds
        source.countTotalCalls shouldBe false
        source.directoryExchanges shouldBe null
        source.roles shouldBe setOf(SourceRole.SEARCH, SourceRole.US, SourceRole.INTERNATIONAL)
        source.automaticRequests shouldBe true
        config.totalCountedSources shouldBe emptySet()
        config.manualOnlySources shouldBe emptySet()
    }

    test("parses directory exchanges") {
        val config = AppConfigLoader.load(
            configWith(source("finnhub", roles = ALL_ROLES, extra = "directoryExchanges = [US, DE, L]")),
            FULL_ENV,
        )

        config.sources.single().directoryExchanges shouldBe setOf(ExchangeCode.US, ExchangeCode("DE"), ExchangeCode("L"))
    }

    test("an empty directory exchange list covers no exchange") {
        val config = AppConfigLoader.load(
            configWith(source("finnhub", roles = ALL_ROLES, extra = "directoryExchanges = []")),
            FULL_ENV,
        )

        config.sources.single().directoryExchanges shouldBe emptySet()
    }

    test("fails for unknown directory exchange codes") {
        loadErrors(configWith(source("finnhub", roles = ALL_ROLES, extra = "directoryExchanges = [US, XX, de, \"\"]"))) shouldContainExactly
            listOf(
                "Source 'finnhub': unknown exchange code 'XX' in directoryExchanges",
                "Source 'finnhub': unknown exchange code 'de' in directoryExchanges",
                "Source 'finnhub': unknown exchange code '' in directoryExchanges",
            )
    }

    test("fails for directory exchanges that are not a list") {
        loadErrors(configWith(source("finnhub", roles = ALL_ROLES, extra = "directoryExchanges = { US = true }"))) shouldContainExactly
            listOf("Source 'finnhub': invalid directoryExchanges")
    }

    test("a source with an empty limit list and a total call counter") {
        val config = AppConfigLoader.load(
            configWith(
                source("finnhub", roles = ALL_ROLES),
                source("finnhub-logos", capabilities = "LOGO", extra = "limits = []\ncountTotalCalls = true"),
            ),
            FULL_ENV,
        )

        val logos = config.source(SourceId.of("finnhub-logos"))!!
        logos.limits shouldBe emptyList()
        logos.countTotalCalls shouldBe true
        config.totalCountedSources shouldBe setOf(SourceId.of("finnhub-logos"))
    }

    test("fails for an invalid total call counter flag") {
        loadErrors(configWith(source("finnhub", roles = ALL_ROLES, extra = "countTotalCalls = maybe"))) shouldContainExactly
            listOf("Source 'finnhub': invalid countTotalCalls 'maybe'")
    }

    test("a source with automatic requests off is manual only") {
        val config = AppConfigLoader.load(
            configWith(
                source("finnhub", roles = ALL_ROLES, extra = "automaticRequests = true"),
                source("eodhd", extra = "automaticRequests = false"),
            ),
            FULL_ENV,
        )

        config.source(SourceId.of("finnhub"))!!.automaticRequests shouldBe true
        config.source(SourceId.of("eodhd"))!!.automaticRequests shouldBe false
        config.manualOnlySources shouldBe setOf(SourceId.of("eodhd"))
    }

    test("a disabled source with automatic requests off is not listed as manual only") {
        val config = AppConfigLoader.load(
            configWith(source("finnhub", roles = ALL_ROLES), source("eodhd", enabled = false, extra = "automaticRequests = false")),
            FULL_ENV,
        )

        config.manualOnlySources shouldBe emptySet()
    }

    test("fails for an invalid automatic requests flag") {
        loadErrors(configWith(source("finnhub", roles = ALL_ROLES, extra = "automaticRequests = sometimes"))) shouldContainExactly
            listOf("Source 'finnhub': invalid automaticRequests 'sometimes'")
    }

    test("parses several limits with units") {
        val config = AppConfigLoader.load(
            configWith(
                source(
                    "finnhub",
                    roles = ALL_ROLES,
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

    test("a limit entry without a limit is a counter without a limit") {
        val config = AppConfigLoader.load(
            configWith(source("finnhub", roles = ALL_ROLES, extra = "limits = [{ interval = DAY }, { interval = MINUTE, limit = 5 }]")),
            FULL_ENV,
        )

        config.sources.single().limits shouldContainExactly listOf(
            RequestLimit(LimitInterval.DAY, null),
            RequestLimit(LimitInterval.MINUTE, 5),
        )
    }

    test("minimum request interval of a source; absent by default") {
        val config = AppConfigLoader.load(
            configWith(source("finnhub", roles = ALL_ROLES), source("yahoo", extra = "minRequestIntervalMillis = 1000")),
            FULL_ENV,
        )

        config.source(SourceId.of("finnhub"))!!.minRequestInterval shouldBe null
        config.source(SourceId.of("yahoo"))!!.minRequestInterval shouldBe 1000.milliseconds
    }

    context("fails for an invalid minimum request interval") {
        withData("0", "-5", "soon") { raw ->
            loadErrors(configWith(source("finnhub", roles = ALL_ROLES, extra = "minRequestIntervalMillis = \"$raw\""))) shouldContainExactly
                listOf("Source 'finnhub': invalid minRequestIntervalMillis '$raw'")
        }
    }

    test("fails when the EODHD API key is missing") {
        loadErrors(ApplicationConfig("application.conf"), FULL_ENV - "EODHD_API_KEY") shouldContainExactly
            listOf("Source 'eodhd' is enabled but environment variable EODHD_API_KEY is not set")
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
            configWith(source("finnhub", roles = ALL_ROLES), source("sec-edgar", enabled = false)),
            FULL_ENV - "SEC_EDGAR_USER_AGENT",
        )
        config.enabledSources.map { it.id.value } shouldContainExactly listOf("finnhub")
    }

    test("logoapi needs no secret") {
        AppConfigLoader.load(
            configWith(source("finnhub", roles = ALL_ROLES), source("logoapi", capabilities = "LOGO")),
            FULL_ENV,
        ).sources shouldHaveSize 2
    }

    test("fails when an enabled SEC EDGAR source has no files base URL") {
        val errors = loadErrors(configWith(source("finnhub", roles = ALL_ROLES), source("sec-edgar")))
        errors shouldContainExactly listOf("Source 'sec-edgar' is enabled but additionalBaseUrls.files is not set")
    }

    test("fails for a blank additional base URL") {
        val errors = loadErrors(
            configWith(source("finnhub", roles = ALL_ROLES, extra = "additionalBaseUrls = { files = \" \", other = \"https://y\" }")),
        )
        errors shouldContainExactly listOf("Source 'finnhub': invalid additionalBaseUrls.files")
    }

    test("fails for additional base URLs that are not an object") {
        val errors = loadErrors(configWith(source("finnhub", roles = ALL_ROLES, extra = "additionalBaseUrls = \"https://y\"")))
        errors shouldContainExactly listOf("Source 'finnhub': invalid additionalBaseUrls")
    }

    test("fails for an unknown default source") {
        val errors = loadErrors(configWith(listOf(source("finnhub", roles = ALL_ROLES)), defaultSources("finnhub", international = "tiingo")))
        errors shouldContainExactly listOf("Default international source 'tiingo' is not a configured source")
    }

    test("fails for a default source that is not configured for its role") {
        val errors = loadErrors(
            configWith(
                listOf(source("finnhub", roles = "SEARCH, US"), source("eodhd", roles = "INTERNATIONAL"), source("sec-edgar", extra = SEC_FILES)),
                defaultSources(search = "eodhd", us = "sec-edgar", international = "finnhub"),
            ),
        )
        errors shouldContainExactly listOf(
            "Default search source 'eodhd' is not configured for the role SEARCH",
            "Default us source 'sec-edgar' is not configured for the role US",
            "Default international source 'finnhub' is not configured for the role INTERNATIONAL",
        )
    }

    test("default sources of the roles may differ") {
        val config = AppConfigLoader.load(
            configWith(
                listOf(source("finnhub", roles = "SEARCH, US"), source("eodhd", roles = "INTERNATIONAL")),
                defaultSources(search = "finnhub", international = "eodhd"),
            ),
            FULL_ENV,
        )

        config.defaultSources shouldBe DefaultSources(SourceId.of("finnhub"), SourceId.of("finnhub"), SourceId.of("eodhd"))
    }

    test("fails for missing default sources") {
        val errors = loadErrors(hocon("$FIRESTORE_DATABASE\ndefaultSources { us = finnhub }\nsources = [${source("finnhub", roles = ALL_ROLES)}]"))
        errors shouldContainExactly listOf("Missing configuration 'defaultSources.search'", "Missing configuration 'defaultSources.international'")
    }

    test("the default sources are not taken from the environment") {
        val config = AppConfigLoader.load(configWith(source("finnhub", roles = ALL_ROLES)), FULL_ENV + ("DEFAULT_SOURCE" to "tiingo"))

        config.defaultSources.search shouldBe SourceId.of("finnhub")
    }

    test("fails for an unknown role") {
        loadErrors(configWith(source("finnhub", roles = "SEARCH, US, INTERNATIONAL, EU"))) shouldContainExactly
            listOf("Source 'finnhub': unknown role 'EU' (known: SEARCH, US, INTERNATIONAL)")
    }

    test("fails for a disabled default source") {
        val errors = loadErrors(configWith(source("finnhub", enabled = false, roles = ALL_ROLES)))
        errors shouldContainExactly listOf(
            "Default search source 'finnhub' is not enabled",
            "Default us source 'finnhub' is not enabled",
            "Default international source 'finnhub' is not enabled",
        )
    }

    test("fails for duplicate source IDs") {
        val errors = loadErrors(configWith(source("finnhub", roles = ALL_ROLES), source("finnhub", roles = ALL_ROLES)))
        errors shouldContainExactly listOf("Duplicate source ID 'finnhub'")
    }

    test("fails for an unknown capability") {
        val errors = loadErrors(configWith(source("finnhub", roles = ALL_ROLES, capabilities = "QUOTE, TELEPATHY")))
        errors.single() shouldContain "Source 'finnhub': unknown capability 'TELEPATHY'"
    }

    test("fails for an unknown limit interval and an invalid limit") {
        val errors = loadErrors(
            configWith(source("finnhub", roles = ALL_ROLES, extra = "limits = [{ interval = WEEK, limit = 5 }, { interval = DAY, limit = -1 }]")),
        )
        errors shouldHaveSize 2
        errors[0] shouldContain "unknown limit interval 'WEEK'"
        errors[1] shouldContain "invalid limit '-1'"
    }

    test("fails for an invalid source ID, missing name and invalid timeout") {
        val errors = loadErrors(
            hocon(
                """
                $FIRESTORE_DATABASE
                ${defaultSources("finnhub")}
                sources = [
                  { id = finnhub, name = Finnhub, baseUrl = "https://x", roles = [$ALL_ROLES] },
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
            configWith(source("finnhub", roles = ALL_ROLES)),
            FULL_ENV - "GCP_PROJECT_ID" - "LOGO_BUCKET" + ("PORT" to "70000"),
        )
        errors shouldContainExactly listOf(
            "Port '70000' is not a valid port number",
            "Missing configuration 'gcpProjectId' (environment variable GCP_PROJECT_ID)",
            "Missing configuration 'logoBucket' (environment variable LOGO_BUCKET)",
        )
    }

    test("fails when no sources are configured") {
        loadErrors(hocon("$FIRESTORE_DATABASE\n${defaultSources("finnhub")}\nsources = []")) shouldContain "No sources configured"
    }

    test("reports all errors in one message") {
        val exception = shouldThrow<InvalidConfigurationException> {
            AppConfigLoader.load(configWith(source("finnhub", roles = ALL_ROLES), defaultSource = "x"), emptyMap())
        }
        exception.errors shouldHaveSize 6
        exception.message shouldContain "Invalid configuration:"
        exception.message shouldContain "- Default search source 'x' is not a configured source"
    }

    test("secrets are masked in toString") {
        val config = AppConfigLoader.load(ApplicationConfig("application.conf"), FULL_ENV)
        config.toString() shouldNotContain "finnhub-key"
        config.toString() shouldNotContain "eodhd-key"
    }

    test("firestore database defaults to sixteen-screener") {
        AppConfigLoader.load(ApplicationConfig("application.conf"), FULL_ENV).firestoreDatabase shouldBe "sixteen-screener"
    }

    test("firestore database is overridden by FIRESTORE_DATABASE") {
        AppConfigLoader.load(
            ApplicationConfig("application.conf"),
            FULL_ENV + ("FIRESTORE_DATABASE" to "screener-test"),
        ).firestoreDatabase shouldBe "screener-test"
    }

    test("a blank FIRESTORE_DATABASE falls back to the configured database") {
        AppConfigLoader.load(
            ApplicationConfig("application.conf"),
            FULL_ENV + ("FIRESTORE_DATABASE" to " "),
        ).firestoreDatabase shouldBe "sixteen-screener"
    }

    test("fails when no firestore database is configured") {
        val errors = loadErrors(hocon("${defaultSources("finnhub")}\nsources = [${source("finnhub", roles = ALL_ROLES)}]"))
        errors shouldContainExactly listOf("Missing configuration 'firestoreDatabase' (environment variable FIRESTORE_DATABASE)")
    }

    context("accepts valid firestore database IDs") {
        withData(
            nameFn = { "accepts '$it'" },
            "abcd",
            "a" + "b".repeat(62),
            "db-2",
            "sixteen-screener",
        ) { databaseId ->
            AppConfigLoader.load(
                ApplicationConfig("application.conf"),
                FULL_ENV + ("FIRESTORE_DATABASE" to databaseId),
            ).firestoreDatabase shouldBe databaseId
        }
    }

    context("rejects invalid firestore database IDs") {
        withData(
            nameFn = { "rejects '$it'" },
            "abc",
            "a" + "b".repeat(63),
            "Sixteen-screener",
            "1screener",
            "screener-",
            "sixteen_screener",
            "(default)",
        ) { databaseId ->
            val errors = loadErrors(ApplicationConfig("application.conf"), FULL_ENV + ("FIRESTORE_DATABASE" to databaseId))
            errors shouldContainExactly listOf(
                "Firestore database '$databaseId' is not a valid database ID " +
                    "(4-63 lowercase letters, digits or hyphens, starting with a letter and not ending with a hyphen)",
            )
        }
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
