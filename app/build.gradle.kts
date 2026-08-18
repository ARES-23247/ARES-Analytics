import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test

// Single source of truth for the application version. Consumed both by the native
// distribution packaging below and by the generated BuildConfig (see generateBuildConfig).
val aresAnalyticsVersion = providers.gradleProperty("aresAnalyticsVersion").orElse("1.2.2").get()
val googleOAuthClientId = providers.gradleProperty("googleOAuthClientId")
    .orElse(providers.environmentVariable("ARES_GOOGLE_OAUTH_CLIENT_ID"))
    .orElse("")
    .get()
    .trim()
val googleOAuthBrokerUrl = providers.gradleProperty("googleOAuthBrokerUrl")
    .orElse(providers.environmentVariable("ARES_GOOGLE_OAUTH_BROKER_URL"))
    .orElse("")
    .get()
    .trimEnd('/')

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}


dependencies {
    val aresVersion = providers.gradleProperty("aresVersion").orElse("9.2.1").get()

    // Compose Desktop
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    // Shared module
    implementation(project(":shared"))
    
    // Versioned ARES libraries from Maven Central (or -ParesRepository for release validation).
    implementation(platform("org.aresfirst.ares:ares-bom:$aresVersion"))
    implementation("org.aresfirst.ares:core")
    implementation("org.aresfirst.ares:codegen")

    // Database — DuckDB via JDBC
    implementation("org.duckdb:duckdb_jdbc:1.1.3")

    // Networking — Ktor client
    implementation("io.ktor:ktor-client-cio:3.5.2")
    implementation("io.ktor:ktor-client-java:3.5.2")
    implementation("io.ktor:ktor-client-okhttp:3.5.2")
    implementation("io.ktor:ktor-client-websockets:3.5.2")
    implementation("io.ktor:ktor-client-content-negotiation:3.5.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.2")

    // Embedded OAuth loopback server
    implementation("io.ktor:ktor-server-core:3.5.2")
    implementation("io.ktor:ktor-server-cio:3.5.2")

    // Serialization
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")

    // Windows Credential Protection (DPAPI) for OAuth refresh-token persistence.
    implementation("net.java.dev.jna:jna-platform:5.15.0")

    // Math & Signal Processing
    implementation("org.ejml:ejml-simple:0.46.1")
    implementation("org.apache.commons:commons-math3:3.6.1")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.12")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("io.ktor:ktor-client-mock-jvm:3.5.2")
    testImplementation("org.mockito:mockito-core:5.23.0")
    
    // Compression
    implementation("org.tukaani:xz:1.12")

    // Gamepad Support (LWJGL / GLFW — no external SDL dependency)
    val lwjglVersion = "3.3.4"
    val lwjglNatives = "natives-windows"
    implementation("org.lwjgl:lwjgl:$lwjglVersion")
    implementation("org.lwjgl:lwjgl-glfw:$lwjglVersion")
    runtimeOnly("org.lwjgl:lwjgl:$lwjglVersion:$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-glfw:$lwjglVersion:$lwjglNatives")
}

// Generate BuildConfig.kt from gradle so the update-checker reads the real package version
// instead of a hand-maintained constant that drifts (AUDIT H12). The hand-maintained
// app/src/main/kotlin/.../BuildConfig.kt was deleted in favor of this generated file.
val generatedBuildConfigDir = layout.buildDirectory.dir("generated/buildconfig/src/main/kotlin")
tasks.register("generateBuildConfig") {
    val version = aresAnalyticsVersion
    val oauthClientId = googleOAuthClientId
    val oauthBrokerUrl = googleOAuthBrokerUrl
    inputs.property("aresAnalyticsVersion", version)
    inputs.property("googleOAuthClientId", oauthClientId)
    inputs.property("googleOAuthBrokerUrl", oauthBrokerUrl)
    outputs.dir(generatedBuildConfigDir)
    doLast {
        val pkgDir = generatedBuildConfigDir.get().asFile.resolve("com/ares/analytics")
        pkgDir.mkdirs()
        val escapedOAuthClientId = oauthClientId
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("$", "\\$")
        val escapedOAuthBrokerUrl = oauthBrokerUrl
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("$", "\\$")
        pkgDir.resolve("BuildConfig.kt").writeText(
            """
            |package com.ares.analytics
            |
            |object BuildConfig {
            |    const val VERSION = "$version"
            |    const val GOOGLE_OAUTH_CLIENT_ID = "$escapedOAuthClientId"
            |    const val GOOGLE_OAUTH_BROKER_URL = "$escapedOAuthBrokerUrl"
            |}
            """.trimMargin()
        )
    }
}

sourceSets {
    main {
        kotlin.srcDir(generatedBuildConfigDir)
    }
}

tasks.named("compileKotlin") {
    dependsOn("generateBuildConfig")
}

// A local validation repository is an explicit developer choice. Forward only a file URI to the
// running desktop app so its nested robot-project Gradle wrappers resolve the same ARES binaries.
// Native installers never embed this machine-local path, and mavenLocal is intentionally unused.
val nestedAresRepositoryUri = providers.gradleProperty("aresRepository").map { configured ->
    rootProject.uri(configured)
}.map { configuredUri ->
    require(configuredUri.scheme.equals("file", ignoreCase = true)) {
        "Nested robot builds require -ParesRepository to resolve to a local file URI"
    }
    configuredUri.toASCIIString()
}
tasks.withType<JavaExec>().configureEach {
    nestedAresRepositoryUri.orNull?.let { uri ->
        systemProperty("ares.repository.uri", uri)
    }
}

// Opt-in official-template checks run in a forked test JVM. Forward only these reviewed
// release-check properties so a successful Gradle invocation cannot silently mean the test was
// skipped because its inputs were visible to Gradle but not to the test process.
listOf(
    "ares.officialTemplateArchiveDir",
    "ares.officialTemplateOutputDir",
    "ares.officialTemplateValidationRepository",
).forEach { propertyName ->
    providers.systemProperty(propertyName).orNull?.let { value ->
        tasks.withType<Test>().configureEach {
            systemProperty(propertyName, value)
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.ares.analytics.MainKt"
        jvmArgs("-Dorg.jetbrains.skiko.renderApi=OPENGL", "-Dorg.jetbrains.skiko.renderApi.fallback=SOFTWARE")

        // The desktop app intentionally carries reflective and platform-specific libraries (DuckDB,
        // Ktor, JNA, and LWJGL). ProGuard cannot prove those optional entry points and aborts release
        // packaging with thousands of false unresolved-reference warnings. Keep the verified jlink
        // runtime image, but do not bytecode-shrink the release jars; packaged-project loading below
        // remains the executable release gate.
        buildTypes.release.proguard {
            isEnabled.set(false)
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "ARES-Analytics"
            packageVersion = aresAnalyticsVersion
            description = "ARES Robotics Mission Control Suite"
            vendor = "ARES Robotics"
            // Gson constructs immutable Kotlin project documents through sun.misc.Unsafe when
            // they do not expose a no-argument JVM constructor. jlink cannot discover this
            // reflective dependency, so the module must remain explicit in every native image.
            modules("java.sql", "java.naming", "jdk.unsupported")

            windows {
                msiPackageVersion = aresAnalyticsVersion
                menuGroup = "ARES"
                upgradeUuid = "a3e52324-7000-4224-8700-1c7b8d9e2a3c"
                iconFile.set(project.file("src/main/resources/brand/ares.ico"))
            }

            macOS {
                dmgPackageVersion = aresAnalyticsVersion
            }

            linux {
                debPackageVersion = aresAnalyticsVersion
            }
        }
    }
}

val packagedProjectFixture = layout.projectDirectory.dir("src/test/resources/packaged-runtime-project")
val mainDistributableRoot = layout.buildDirectory.dir("compose/binaries/main/app")

val verifyDistributableProjectLoading = tasks.register<Exec>("verifyDistributableProjectLoading") {
    group = "verification"
    description = "Loads every canonical ARES project document through the trimmed native runtime."
    dependsOn("createDistributable")
    inputs.dir(packagedProjectFixture)

    doFirst {
        val root = mainDistributableRoot.get().asFile
        val osName = System.getProperty("os.name").lowercase()
        val executable = when {
            osName.contains("win") -> root.resolve("ARES-Analytics/ARES-Analytics.exe")
            osName.contains("mac") -> root.resolve("ARES-Analytics.app/Contents/MacOS/ARES-Analytics")
            else -> root.resolve("ARES-Analytics/bin/ARES-Analytics")
        }
        require(executable.isFile) { "Native ARES Analytics launcher was not created at $executable" }
        commandLine(
            executable.absolutePath,
            "--verify-packaged-project",
            packagedProjectFixture.asFile.absolutePath,
        )
    }
}

tasks.matching { task ->
    task.name in setOf(
        "packageMsi", "packageDmg", "packageDeb",
        "packageReleaseMsi", "packageReleaseDmg", "packageReleaseDeb",
    )
}.configureEach {
    dependsOn(verifyDistributableProjectLoading)
    doFirst {
        require(
            googleOAuthClientId.length in 30..256 &&
                googleOAuthClientId.endsWith(".apps.googleusercontent.com") &&
                googleOAuthClientId.none(Char::isWhitespace)
        ) {
            "Official packages require -PgoogleOAuthClientId (or ARES_GOOGLE_OAUTH_CLIENT_ID) with a valid Google Desktop OAuth client ID"
        }
        require(googleOAuthBrokerUrl.startsWith("https://") && googleOAuthBrokerUrl.length <= 512) {
            "Official packages require an HTTPS Google OAuth broker URL"
        }
    }
}

private val validationPropertyNames = listOf(
    "simulatedSeconds",
    "sampleRateHz",
    "topicCount",
    "batchSize",
    "queryIterations",
    "minIngestionFramesPerSecond",
    "maxQueryP95Ms",
    "maxReplayLoadMs",
    "maxReplayScrubP95Ms",
    "maxParquetOperationMs",
    "maxHeapGrowthMb",
    "maxDropRate",
    "hardwareEnabled",
    "hardwareHost",
    "hardwarePort",
    "hardwareObservationSeconds",
    "hardwareConnectTimeoutSeconds",
    "hardwareMinFrames",
    "hardwareMinTopics",
    "hardwareRequiredKeys"
)

fun Test.configureDashboardValidation(profile: String) {
    group = "verification"
    description = "Runs the $profile dashboard telemetry and performance validation profile."
    maxParallelForks = 1
    outputs.upToDateWhen { false }
    systemProperty("java.awt.headless", "true")
    systemProperty("ares.validation.profile", profile)
    systemProperty(
        "ares.validation.reportDir",
        project.layout.buildDirectory.dir("reports/dashboard-validation").get().asFile.absolutePath
    )
    validationPropertyNames.forEach { name ->
        project.providers.gradleProperty("validation.$name").orNull?.let { value ->
            systemProperty("ares.validation.$name", value)
        }
    }
}

tasks.register<Test>("dashboardSmoke") {
    configureDashboardValidation("smoke")
    filter {
        includeTestsMatching("com.ares.analytics.validation.DashboardValidationTest")
        includeTestsMatching("com.ares.analytics.service.AppSimE2EPipelineTest")
        includeTestsMatching("com.ares.analytics.service.DatabaseServiceIntegrationTest")
        includeTestsMatching("com.ares.analytics.service.ExportServiceTest")
        includeTestsMatching("com.ares.analytics.service.ReplayEngineServiceTest")
        includeTestsMatching("com.ares.analytics.service.AlertEngineServiceTest")
        includeTestsMatching("com.ares.analytics.service.AlertEngineCompositeTest")
    }
}

tasks.register<Test>("dashboardSoak") {
    configureDashboardValidation("soak")
    maxHeapSize = "2g"
    filter {
        includeTestsMatching("com.ares.analytics.validation.DashboardValidationTest")
        includeTestsMatching("com.ares.analytics.service.AppSimE2EPipelineTest")
    }
}

tasks.register<Test>("dashboardPerformanceBaseline") {
    configureDashboardValidation("baseline")
    description = "Compares the generated smoke report with the checked-in performance baseline."
    dependsOn("dashboardSmoke")
    systemProperty(
        "ares.validation.baselineFile",
        rootProject.file("config/dashboard-performance-baseline.json").absolutePath
    )
    filter {
        includeTestsMatching("com.ares.analytics.validation.DashboardPerformanceBaselineTest")
    }
}

tasks.register<Test>("dashboardHardware") {
    configureDashboardValidation("hardware")
    description = "Validates dashboard telemetry against a physical robot or external simulator."
    filter {
        includeTestsMatching("com.ares.analytics.validation.HardwareDashboardValidationTest")
    }
}

