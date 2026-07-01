plugins {
    kotlin("jvm") version "2.0.21" apply false
    kotlin("plugin.serialization") version "2.0.21" apply false
    id("org.jetbrains.compose") version "1.7.3" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("app.cash.sqldelight") version "2.0.2" apply false
}

group = "com.ares.analytics"
version = "1.0.0-SNAPSHOT"

subprojects {
    group = rootProject.group
    version = rootProject.version

    tasks.matching { it.name == "run" || it.name == "clean" }.configureEach {
        dependsOn(":killExisting")
    }
}

tasks.register("killExisting") {
    doFirst {
        println("[ARES-Analytics] Checking for existing orphaned app or gateway processes...")
        var killedCount = 0
        java.lang.ProcessHandle.allProcesses().forEach { handle ->
            val info = handle.info()
            val command = info.command().orElse("")
            val args = info.arguments().orElse(emptyArray())
            val cmdLine = command + " " + args.joinToString(" ")
            if (cmdLine.contains("java", ignoreCase = true)) {
                if (cmdLine.contains("com.ares.analytics.MainKt") || 
                    cmdLine.contains("com.ares.analytics.gateway.ApplicationKt") ||
                    cmdLine.contains("com.ares.analytics.gateway.Application")
                ) {
                    val pid = handle.pid()
                    println("[ARES-Analytics] Killing orphaned process ID $pid...")
                    handle.destroyForcibly()
                    killedCount++
                }
            }
        }
        if (killedCount > 0) {
            println("[ARES-Analytics] Successfully terminated $killedCount orphaned process(es).")
        } else {
            println("[ARES-Analytics] No orphaned processes found.")
        }
    }
}
