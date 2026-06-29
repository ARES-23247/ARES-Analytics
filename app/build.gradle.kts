import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("app.cash.sqldelight")
}

sqldelight {
    databases {
        create("AresDatabase") {
            packageName.set("com.ares.analytics.database")
            verifyMigrations.set(false)
        }
    }
}

dependencies {
    // Compose Desktop
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    // Shared module
    implementation(project(":shared"))

    // Database — SQLite via SQLDelight & JDBC
    implementation("app.cash.sqldelight:sqlite-driver:2.0.2")
    implementation("org.xerial:sqlite-jdbc:3.46.1.0")

    // Networking — Ktor client
    implementation("io.ktor:ktor-client-cio:3.0.3")
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

    // Parquet export
    implementation("org.apache.parquet:parquet-avro:1.14.4")
    implementation("org.apache.hadoop:hadoop-common:3.4.1") {
        // Exclude heavyweight transitive deps not needed for local Parquet writing
        exclude(group = "org.apache.curator")
        exclude(group = "org.apache.zookeeper")
        exclude(group = "org.apache.kerby")
    }

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

tasks.configureEach {
    if (name.contains("verify", ignoreCase = true) && name.contains("migration", ignoreCase = true)) {
        enabled = false
    }
}
