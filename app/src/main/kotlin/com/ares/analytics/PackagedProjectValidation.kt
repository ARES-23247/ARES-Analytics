package com.ares.analytics

import com.ares.analytics.viewmodel.project.AresProjectDocuments

internal const val PACKAGED_PROJECT_VALIDATION_COMMAND = "--verify-packaged-project"

internal data class PackagedProjectValidationResult(
    val routineCount: Int,
    val subsystemCount: Int,
    val errors: List<String>,
) {
    val isValid: Boolean get() = errors.isEmpty()
}

/**
 * Exercises every canonical ARES project-document codec without starting Compose or robot services.
 *
 * The native-distribution workflow invokes this through the generated application launcher. That
 * matters: a normal JVM test cannot detect Java modules accidentally omitted from the jlink image.
 */
internal fun validatePackagedProject(projectPath: String): PackagedProjectValidationResult {
    val snapshot = AresProjectDocuments().load(projectPath)
    val errors = buildList {
        addAll(snapshot.diagnostics.map { diagnostic ->
            "${diagnostic.kind}: ${diagnostic.file.name}: ${diagnostic.message}"
        })
        if (snapshot.projectMetadata == null) add("Project metadata did not load")
        if (snapshot.capabilityCatalog == null) add("Capability catalog did not load")
        if (snapshot.autonomousCatalog == null) add("Autonomous catalog did not load")
        if (snapshot.routines.isEmpty()) add("No routine document loaded")
        if (snapshot.subsystems.isEmpty()) add("No subsystem document loaded")
    }
    return PackagedProjectValidationResult(
        routineCount = snapshot.routines.size,
        subsystemCount = snapshot.subsystems.size,
        errors = errors,
    )
}

/** Returns null when the desktop application should launch normally. */
internal fun runPackagedProjectValidationCommand(args: Array<String>): Int? {
    if (args.firstOrNull() != PACKAGED_PROJECT_VALIDATION_COMMAND) return null
    if (args.size != 2) {
        System.err.println("Usage: $PACKAGED_PROJECT_VALIDATION_COMMAND <project-directory>")
        return 64
    }

    val result = runCatching { validatePackagedProject(args[1]) }
        .onFailure { error ->
            System.err.println("PACKAGED_PROJECT_VALIDATION_FAILED: ${error.message}")
        }
        .getOrNull() ?: return 1

    if (!result.isValid) {
        result.errors.forEach { System.err.println("PACKAGED_PROJECT_VALIDATION_FAILED: $it") }
        return 1
    }

    println(
        "PACKAGED_PROJECT_VALIDATION_OK " +
            "routines=${result.routineCount} subsystems=${result.subsystemCount}"
    )
    return 0
}
