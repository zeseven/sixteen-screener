import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

group = "com.sixteenscreener"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

application {
    mainClass = "com.sixteenscreener.ApplicationKt"
}

val emulatorTest: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
}

configurations[emulatorTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[emulatorTest.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.logback.classic)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(platform(libs.google.cloud.bom))
    implementation(libs.google.cloud.firestore)
    implementation(libs.google.cloud.storage)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.google.cloud.nio)
}

tasks.test {
    useJUnitPlatform()
}

val firestoreEmulatorHost: Provider<String> = providers.environmentVariable("FIRESTORE_EMULATOR_HOST")

tasks.register<Test>("emulatorTest") {
    description = "Runs the Firestore adapter tests against the emulator at FIRESTORE_EMULATOR_HOST."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = emulatorTest.output.classesDirs
    classpath = emulatorTest.runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter(tasks.test)
    inputs.property("firestoreEmulatorHost", firestoreEmulatorHost.orElse(""))
    doFirst {
        check(firestoreEmulatorHost.isPresent) {
            "Set FIRESTORE_EMULATOR_HOST (e.g. localhost:8085) to a running Firestore emulator"
        }
    }
}
