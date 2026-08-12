package com.ares.analytics.ui.help

data class DeveloperReference(
    val id: String,
    val title: String,
    val category: String,
    val responsibility: String,
    val sourcePath: String,
    val units: String,
    val invariants: List<String>,
    val relatedTests: String,
    val keywords: Set<String> = emptySet(),
)

/**
 * Small, source-backed map of concepts students commonly need to locate.
 *
 * This is deliberately not presented as complete generated API documentation. The linked source
 * and its tests remain authoritative, which prevents curated examples from masquerading as a
 * current compile-checked API surface.
 */
object DeveloperReferenceCatalog {
    val entries = listOf(
        DeveloperReference(
            id = "redux",
            title = "Redux state flow",
            category = "State & control",
            responsibility = "Purely reduce RobotAction values into immutable RobotState snapshots.",
            sourcePath = "ARESLib-Kotlin/core/src/main/kotlin/com/areslib/reducer/RootReducer.kt",
            units = "The action and state fields declare their own physical units.",
            invariants = listOf(
                "Input and controllers dispatch actions; they do not mutate state directly.",
                "Reducers are pure and season reducers compose over rootReducer.",
                "Controllers read one immutable state snapshot before writing outputs.",
            ),
            relatedTests = "core/src/test/kotlin/com/areslib/reducer/",
            keywords = setOf("action", "reducer", "state", "store", "immutable"),
        ),
        DeveloperReference(
            id = "pose-estimator",
            title = "Pose estimation",
            category = "Localization",
            responsibility = "Fuse odometry and accepted vision measurements while retaining bounded pose history.",
            sourcePath = "ARESLib-Kotlin/core/src/main/kotlin/com/areslib/math/estimation/PoseEstimator.kt",
            units = "Position: meters; heading: radians, CCW-positive; timestamps: milliseconds.",
            invariants = listOf(
                "Heading uses 0 = +X and π/2 = +Y.",
                "Vision quality and rejection are handled before correction is trusted.",
                "Hot rewind/fusion paths use preallocated storage.",
            ),
            relatedTests = "core/src/test/kotlin/com/areslib/math/estimation/",
            keywords = setOf("ekf", "vision", "odometry", "pose", "kalman"),
        ),
        DeveloperReference(
            id = "drive-facades",
            title = "Student-facing drive facades",
            category = "Drivetrain",
            responsibility = "Expose Mecanum and Swerve drive intent through the shared Redux drivetrain boundary.",
            sourcePath = "ARESLib-Kotlin/core/src/main/kotlin/com/areslib/subsystem/{MecanumDriveFacade,SwerveDriveFacade}.kt",
            units = "Translation: meters/second; rotation: radians/second; heading: radians, CCW-positive.",
            invariants = listOf(
                "Alliance mirroring belongs at the season input boundary, not inside the facade.",
                "FTC and FRC may use different driver-origin conventions.",
                "Swerve X-brake dispatches an explicit X_BRAKE drive mode.",
            ),
            relatedTests = "core/src/test/kotlin/com/areslib/subsystem/",
            keywords = setOf("mecanum", "swerve", "field centric", "brake", "drive"),
        ),
        DeveloperReference(
            id = "hardware-registry",
            title = "Hardware lifecycle registry",
            category = "Hardware & IO",
            responsibility = "Own registered refresh, topology, safety, telemetry, polling, and close lifecycles.",
            sourcePath = "ARESLib-Kotlin/core/src/main/kotlin/com/areslib/hardware/HardwareRegistry.kt",
            units = "Device-specific; cached readings must retain declared units and validity.",
            invariants = listOf(
                "Hardware reads happen once and are cached for the loop.",
                "Getters and writeOutputs do not perform direct hardware reads.",
                "Safety and close passes isolate device exceptions best-effort.",
            ),
            relatedTests = "core/src/test/kotlin/com/areslib/hardware/HardwareRegistryTest.kt",
            keywords = setOf("cache", "poll", "topology", "safe", "close", "device"),
        ),
        DeveloperReference(
            id = "robot-clock",
            title = "Deterministic robot time",
            category = "Runtime",
            responsibility = "Provide one clock boundary for live code, tests, replay, and simulation.",
            sourcePath = "ARESLib-Kotlin/core/src/main/kotlin/com/areslib/util/RobotClock.kt",
            units = "Milliseconds for currentTimeMillis; nanoseconds for nanoTime.",
            invariants = listOf(
                "Library code does not call system clocks directly.",
                "Mock time advances only when its lifecycle owner changes it.",
                "Tests and replay restore system time after use.",
            ),
            relatedTests = "Search ARESLib tests for RobotClock.useMockTime.",
            keywords = setOf("time", "simulation", "replay", "deterministic", "mock"),
        ),
        DeveloperReference(
            id = "theta-star",
            title = "Theta* path planning",
            category = "Autonomous",
            responsibility = "Plan an any-angle route around costmap obstacles using line-of-sight shortcuts.",
            sourcePath = "ARESLib-Kotlin/core/src/main/kotlin/com/areslib/pathing/ThetaStarPlanner.kt",
            units = "Field coordinates: meters; costmap resolution: meters/cell.",
            invariants = listOf(
                "Invalid or out-of-bounds inputs return no route.",
                "A route preview is not proof of physical robot clearance.",
                "Path expansion uses pooled planner state.",
            ),
            relatedTests = "core/src/test/kotlin/com/areslib/pathing/ThetaStarPlannerTest.kt",
            keywords = setOf("path", "costmap", "obstacle", "route", "theta star"),
        ),
        DeveloperReference(
            id = "subsystem-controller",
            title = "Subsystem controller boundary",
            category = "State & control",
            responsibility = "Translate immutable desired state into safe IO commands for one subsystem.",
            sourcePath = "ARESLib-Kotlin/core/src/main/kotlin/com/areslib/subsystem/SubsystemControllerBase.kt",
            units = "Declared by each state field, sensor snapshot, and output command.",
            invariants = listOf(
                "State, controller, IO contract, hardware adapter, and simulator adapter stay separate.",
                "Invalid or stale feedback fails toward the declared neutral output.",
                "Fault latches require explicit successful neutral recovery where applicable.",
            ),
            relatedTests = "Generated subsystem contract tests plus season subsystem safety tests.",
            keywords = setOf("subsystem", "controller", "io", "neutral", "fault", "redux"),
        ),
    )

    val categories: List<String> = listOf("All") + entries.map(DeveloperReference::category).distinct().sorted()

    fun search(query: String, category: String = "All"): List<DeveloperReference> {
        val normalized = query.trim().lowercase()
        return entries.filter { entry ->
            (category == "All" || entry.category == category) &&
                (normalized.isBlank() || listOf(
                    entry.title,
                    entry.responsibility,
                    entry.sourcePath,
                    entry.units,
                    entry.invariants.joinToString(" "),
                    entry.keywords.joinToString(" "),
                ).any { normalized in it.lowercase() })
        }
    }
}
