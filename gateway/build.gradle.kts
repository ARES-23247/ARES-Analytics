plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("io.ktor.plugin") version "3.0.3"
}

application {
    mainClass.set("com.ares.analytics.gateway.ApplicationKt")
}

dependencies {
    // Shared module
    implementation(project(":shared"))

    // Ktor server
    implementation("io.ktor:ktor-server-core:3.0.3")
    implementation("io.ktor:ktor-server-netty:3.0.3")
    implementation("io.ktor:ktor-server-content-negotiation:3.0.3")
    implementation("io.ktor:ktor-server-auth:3.0.3")
    implementation("io.ktor:ktor-server-cors:3.0.3")
    implementation("io.ktor:ktor-server-status-pages:3.0.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")
    implementation("io.ktor:ktor-server-rate-limit:3.0.3")
    implementation("io.ktor:ktor-server-forwarded-header:3.0.3")
    implementation("io.ktor:ktor-server-request-validation:3.0.3")
    implementation("io.ktor:ktor-server-body-limit:3.0.3")

    // Ktor HTTP client (for GitHub API, Vertex AI)
    implementation("io.ktor:ktor-client-cio:3.0.3")
    implementation("io.ktor:ktor-client-content-negotiation:3.0.3")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    // Coroutines Extensions
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.11.0")

    // Google OIDC ID-token verification
    implementation("com.google.api-client:google-api-client:2.7.0")

    // Google Cloud SDKs
    implementation("com.google.cloud:google-cloud-vertexai:1.12.0")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.12")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-client-mock-jvm:3.0.3")
    testImplementation("io.ktor:ktor-server-test-host:3.0.3")
    testImplementation("org.mockito:mockito-core:5.23.0")
}

ktor {
    docker {
        jreVersion.set(JavaVersion.VERSION_17)
        localImageName.set("ares-analytics-gateway")
    }
}

tasks.test {
    environment("DEV_MODE", "true")
}
