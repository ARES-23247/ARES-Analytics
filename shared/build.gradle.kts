plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    val aresVersion = providers.gradleProperty("aresVersion").orElse("9.2.1").get()
    api(platform("org.aresfirst.ares:ares-bom:$aresVersion"))
    api("org.aresfirst.ares:core")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    testImplementation(kotlin("test"))
}
