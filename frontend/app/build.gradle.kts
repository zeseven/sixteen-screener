import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val serviceAccountKeyProperty = "sixteen.serviceAccountKey"
val serviceAccountKeyEnvironmentVariable = "SIXTEEN_SERVICE_ACCOUNT_KEY"
val backendUrlEnvironmentVariable = "SIXTEEN_BACKEND_URL"
val serviceAccountKeyAssetName = "service-account-key.json"

val serviceAccountKeyFile: File = resolveServiceAccountKeyFile()
val backendUrl: String = resolveBackendUrl()
val buildNumber: Int = resolveBuildNumber()
val commitHash: String = runGit("rev-parse", "--short", "HEAD")
val isDirtyBuild: Boolean = runGit("status", "--porcelain").isNotEmpty()

fun resolveServiceAccountKeyFile(): File {
    val configuredPath = providers.gradleProperty(serviceAccountKeyProperty)
        .orElse(providers.environmentVariable(serviceAccountKeyEnvironmentVariable))
        .orNull
        ?.takeIf { it.isNotBlank() }
        ?: throw GradleException(
            "Service account key is not configured. Set the Gradle property '$serviceAccountKeyProperty' " +
                "in ~/.gradle/gradle.properties or the environment variable '$serviceAccountKeyEnvironmentVariable' " +
                "to the absolute path of the key JSON file.",
        )
    val keyFile = File(configuredPath)
    if (!keyFile.isFile) {
        throw GradleException(
            "Service account key file '$configuredPath' does not exist. Check the Gradle property " +
                "'$serviceAccountKeyProperty' or the environment variable '$serviceAccountKeyEnvironmentVariable'.",
        )
    }
    return keyFile
}

fun resolveBackendUrl(): String {
    val configuredUrl = providers.environmentVariable(backendUrlEnvironmentVariable).orNull?.trim()
    if (configuredUrl.isNullOrEmpty()) {
        throw GradleException(
            "Backend URL is not configured. Set the environment variable '$backendUrlEnvironmentVariable' " +
                "to the Cloud Run service URL, e.g. https://sixteen-screener-backend-xyz.a.run.app.",
        )
    }
    val uri = runCatching { URI(configuredUrl) }.getOrNull()
    if (uri == null || uri.scheme != "https" || uri.host.isNullOrEmpty()) {
        throw GradleException(
            "Environment variable '$backendUrlEnvironmentVariable' must be an absolute https URL, but is '$configuredUrl'.",
        )
    }
    return configuredUrl.trimEnd('/')
}

fun runGit(vararg arguments: String): String {
    val command = listOf("git", *arguments)
    val execution = providers.exec {
        commandLine(command)
        workingDir(layout.projectDirectory)
        isIgnoreExitValue = true
    }
    val exitValue = runCatching { execution.result.get().exitValue }.getOrElse { failure ->
        throw GradleException(
            "Cannot run '${command.joinToString(" ")}'. The build number is derived from git, so git must be installed " +
                "and on the PATH: ${failure.message}",
            failure,
        )
    }
    if (exitValue != 0) {
        throw GradleException(
            "'${command.joinToString(" ")}' failed with exit code $exitValue. The app must be built from a git " +
                "checkout with at least one commit: ${execution.standardError.asText.get().trim()}",
        )
    }
    return execution.standardOutput.asText.get().trim()
}

fun resolveBuildNumber(): Int {
    val commitCount = runGit("rev-list", "--count", "--first-parent", "HEAD")
    return commitCount.toIntOrNull()?.takeIf { it > 0 }
        ?: throw GradleException(
            "Build number must be a positive commit count, but 'git rev-list --count --first-parent HEAD' returned '$commitCount'.",
        )
}

abstract class CopyServiceAccountKeyTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val keyFile: RegularFileProperty

    @get:Input
    abstract val assetName: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun copy() {
        val target = outputDirectory.get().asFile
        target.deleteRecursively()
        target.mkdirs()
        keyFile.get().asFile.copyTo(target.resolve(assetName.get()))
    }
}

val copyServiceAccountKey = tasks.register<CopyServiceAccountKeyTask>("copyServiceAccountKey") {
    keyFile.set(serviceAccountKeyFile)
    assetName.set(serviceAccountKeyAssetName)
    outputDirectory.set(layout.buildDirectory.dir("generated/sixteen/assets"))
}

android {
    namespace = "com.sixteenscreener.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.sixteenscreener.app"
        minSdk = 26
        targetSdk = 37
        versionCode = buildNumber
        versionName = "0.1.0"
        buildConfigField("String", "BACKEND_URL", "\"$backendUrl\"")
        buildConfigField("String", "SERVICE_ACCOUNT_KEY_ASSET", "\"$serviceAccountKeyAssetName\"")
        buildConfigField("int", "BUILD_NUMBER", "$buildNumber")
        buildConfigField("String", "COMMIT", "\"$commitHash\"")
        buildConfigField("boolean", "DIRTY", "$isDirtyBuild")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copyServiceAccountKey, CopyServiceAccountKeyTask::outputDirectory)
    }
}

kotlin {
    jvmToolchain(21)
}

ksp {
    arg("dagger.fullBindingGraphValidation", "ERROR")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.network.cache.control)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
