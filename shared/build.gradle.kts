plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    val aresVersion = providers.gradleProperty("aresVersion").orElse("6.2.0").get()
    api(platform("org.aresfirst.ares:ares-bom:$aresVersion"))
    api("org.aresfirst.ares:core")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation(kotlin("test"))
}
