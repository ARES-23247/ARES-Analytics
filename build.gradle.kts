plugins {
    kotlin("jvm") version "2.0.21" apply false
    kotlin("plugin.serialization") version "2.0.21" apply false
    id("org.jetbrains.compose") version "1.7.3" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlinx.kover") version "0.7.6"
}

group = "com.ares.analytics"
version = "1.0.0-SNAPSHOT"

subprojects {
    group = rootProject.group
    version = rootProject.version

    tasks.matching { it.name == "run" || it.name == "clean" }.configureEach {
        if (!project.hasProperty("fromRootRun") && !project.hasProperty("skipKill")) {
            dependsOn(":killExisting")
        }
    }

    // Skip the default sequential subproject run tasks when running from the root project
    tasks.matching { it.name == "run" }.configureEach {
        onlyIf {
            val taskNames = gradle.startParameter.taskNames
            val isRootRun = taskNames.any { it == "run" || it == ":run" }
            !isRootRun
        }
    }

    apply(plugin = "org.jetbrains.kotlinx.kover")
}

tasks.register("killExisting") {
    doFirst {
        println("[ARES-Analytics] Checking for existing orphaned app, gateway, or simulator processes...")
        var killedCount = 0
        
        // 1. Clean by Port
        val portsToClean = listOf(8080)
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        for (port in portsToClean) {
            try {
                if (isWindows) {
                    val proc = ProcessBuilder("cmd.exe", "/c", "netstat -ano").start()
                    val reader = java.io.BufferedReader(java.io.InputStreamReader(proc.inputStream))
                    var line: String?
                    val pids = mutableSetOf<Long>()
                    while (reader.readLine().also { line = it } != null) {
                        if (line!!.contains("LISTENING") && line!!.contains(":$port")) {
                            val parts = line!!.split("\\s+".toRegex()).filter { it.isNotEmpty() }
                            if (parts.size >= 5) {
                                val pidStr = parts[4]
                                pidStr.toLongOrNull()?.let { pids.add(it) }
                            }
                        }
                    }
                    proc.waitFor()
                    for (pid in pids) {
                        if (pid != ProcessHandle.current().pid()) {
                            ProcessHandle.of(pid).ifPresent { handle ->
                                println("[ARES-Analytics] Killing process holding port $port (PID $pid)...")
                                handle.destroyForcibly()
                                killedCount++
                            }
                        }
                    }
                } else {
                    val proc = ProcessBuilder("sh", "-c", "lsof -t -i :$port").start()
                    val reader = java.io.BufferedReader(java.io.InputStreamReader(proc.inputStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line!!.trim().toLongOrNull()?.let { pid ->
                            if (pid != ProcessHandle.current().pid()) {
                                ProcessHandle.of(pid).ifPresent { handle ->
                                    println("[ARES-Analytics] Killing process holding port $port (PID $pid)...")
                                    handle.destroyForcibly()
                                    killedCount++
                                }
                            }
                        }
                    }
                    proc.waitFor()
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        // 2. Clean by JPS (fallback/redundancy)
        try {
            val jpsProc = ProcessBuilder("jps", "-l").start()
            val reader = java.io.BufferedReader(java.io.InputStreamReader(jpsProc.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val parts = line!!.split("\\s+".toRegex()).filter { it.isNotEmpty() }
                if (parts.size >= 2) {
                    val pidString = parts[0]
                    val mainClass = parts[1]
                    if (mainClass.contains("com.ares.analytics")) {
                        val pid = pidString.toLongOrNull()
                        if (pid != null && pid != ProcessHandle.current().pid()) {
                            ProcessHandle.of(pid).ifPresent { handle ->
                                println("[ARES-Analytics] Killing orphaned process $mainClass (PID $pid)...")
                                handle.destroyForcibly()
                                killedCount++
                            }
                        }
                    }
                }
            }
            jpsProc.waitFor()
        } catch (e: Exception) {
            println("[ARES-Analytics] Failed to check via JPS: ${e.message}")
        }
        
        if (killedCount > 0) {
            println("[ARES-Analytics] Successfully terminated $killedCount orphaned process(es).")
        } else {
            println("[ARES-Analytics] No orphaned processes found.")
        }
    }
}

tasks.register("run") {
    if (!project.hasProperty("skipKill")) {
        dependsOn("killExisting")
    }
    dependsOn(":shared:jar", ":gateway:classes", ":app:classes")
    doLast {
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val gradlew = if (isWindows) "gradlew.bat" else "./gradlew"
        
        val logDir = java.io.File("C:\\Users\\david\\.gemini\\antigravity\\brain\\ff96eb71-c48c-493c-b8b3-10dbf89fb724\\scratch")
        logDir.mkdirs()
        val gatewayLog = java.io.File(logDir, "gateway.log")
        val appLog = java.io.File(logDir, "app.log")
        
        println("[ARES-Analytics] Launching Gateway in background, logging to gateway.log...")
        val gatewayProcess = ProcessBuilder(
            if (isWindows) listOf("cmd.exe", "/c", gradlew, ":gateway:run", "-PfromRootRun=true")
            else listOf("bash", "-c", "$gradlew :gateway:run -PfromRootRun=true")
        ).redirectOutput(ProcessBuilder.Redirect.to(gatewayLog))
         .redirectError(ProcessBuilder.Redirect.to(gatewayLog))
         .start()
        
        // Wait a brief moment for gateway to initialize ports
        Thread.sleep(1000)
        
        println("[ARES-Analytics] Launching App in foreground, logging to app.log...")
        val appProcess = ProcessBuilder(
            if (isWindows) listOf("cmd.exe", "/c", gradlew, ":app:run", "-PfromRootRun=true")
            else listOf("bash", "-c", "$gradlew :app:run -PfromRootRun=true")
        ).redirectOutput(ProcessBuilder.Redirect.to(appLog))
         .redirectError(ProcessBuilder.Redirect.to(appLog))
         .start()
        
        // Add shutdown hook to kill both processes if the Gradle process is killed
        val shutdownHook = Thread {
            println("[ARES-Analytics] Shutting down Gateway and App processes...")
            gatewayProcess.destroyForcibly()
            appProcess.destroyForcibly()
        }
        Runtime.getRuntime().addShutdownHook(shutdownHook)
        
        appProcess.waitFor()
        gatewayProcess.destroyForcibly()
        Runtime.getRuntime().removeShutdownHook(shutdownHook)
    }
}

