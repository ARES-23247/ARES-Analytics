// Gradle configuration must never mutate user-wide settings. Toolchains and the launcher own JDK
// selection; this check only fails with actionable, cross-platform guidance.
val currentJava = JavaVersion.current()
if (!currentJava.isCompatibleWith(JavaVersion.VERSION_17)) {
    throw GradleException(
        "ARES Robotics Studio requires a Gradle JVM on Java 17 or newer " +
            "(currently ${System.getProperty("java.version")}). Set JAVA_HOME or select a compatible " +
            "Gradle JVM in the IDE, then run the command again. No user-level Gradle files were changed.",
    )
}

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

val aresRepositoryUrl = providers.gradleProperty("aresRepository").orNull
dependencyResolutionManagement {
    repositories {
        if (!aresRepositoryUrl.isNullOrBlank()) {
            maven(aresRepositoryUrl)
        }
        if (providers.gradleProperty("aresUseMavenLocal").map(String::toBoolean).getOrElse(false)) {
            mavenLocal()
        }
        google()
        mavenCentral()
        maven("https://raw.githubusercontent.com/ARES-23247/ARESLib-Kotlin/maven")
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

rootProject.name = "ARES-Analytics"

include(":shared")
include(":app")
include(":gateway")

// Keep the dashboard and its shared robotics models in lockstep during local
// development. CI/release builds can still resolve the published artifact when
// this sibling checkout is absent.
val useSiblingAresLib = providers.gradleProperty("aresUseSiblingLib")
    .map(String::toBoolean)
    .getOrElse(false)
if (useSiblingAresLib && file("../ARESLib-Kotlin").exists()) {
    includeBuild("../ARESLib-Kotlin")
}
