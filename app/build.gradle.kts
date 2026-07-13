import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}


dependencies {
    // Compose Desktop
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    // Shared module
    implementation(project(":shared"))
    
    // Robot Core Math & Physics (Local Publish)
    implementation("com.areslib:core:1.0-SNAPSHOT")

    // Database — DuckDB via JDBC
    implementation("org.duckdb:duckdb_jdbc:1.1.3")

    // Networking — Ktor client
    implementation("io.ktor:ktor-client-cio:3.0.3")
    implementation("io.ktor:ktor-client-java:3.0.3")
    implementation("io.ktor:ktor-client-websockets:3.0.3")
    implementation("io.ktor:ktor-client-content-negotiation:3.0.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")

    // Embedded OAuth loopback server
    implementation("io.ktor:ktor-server-core:3.0.3")
    implementation("io.ktor:ktor-server-cio:3.0.3")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // Math & Signal Processing
    implementation("org.ejml:ejml-simple:0.43.1")
    implementation("org.apache.commons:commons-math3:3.6.1")


    // Firebase client
    implementation("com.google.firebase:firebase-admin:9.4.2")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.12")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("io.ktor:ktor-client-mock-jvm:3.0.3")
    testImplementation("org.mockito:mockito-core:5.12.0")
    
    // Compression
    implementation("org.tukaani:xz:1.10")

    // Gamepad Support (LWJGL / GLFW — no external SDL dependency)
    val lwjglVersion = "3.3.4"
    val lwjglNatives = "natives-windows"
    implementation("org.lwjgl:lwjgl:$lwjglVersion")
    implementation("org.lwjgl:lwjgl-glfw:$lwjglVersion")
    runtimeOnly("org.lwjgl:lwjgl:$lwjglVersion:$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-glfw:$lwjglVersion:$lwjglNatives")
}

compose.desktop {
    application {
        mainClass = "com.ares.analytics.MainKt"
        jvmArgs("-Dorg.jetbrains.skiko.renderApi=OPENGL", "-Dorg.jetbrains.skiko.renderApi.fallback=SOFTWARE")

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "ARES-Analytics"
            packageVersion = "1.0.3"
            description = "ARES Robotics Mission Control Suite"
            vendor = "ARES Robotics"
            modules("java.sql", "java.naming")

            windows {
                menuGroup = "ARES"
                upgradeUuid = "a3e52324-7000-4224-8700-1c7b8d9e2a3c"
            }
        }
    }
}

