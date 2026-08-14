package com.ares.analytics.viewmodel.routine

import com.ares.analytics.shared.League
import com.ares.analytics.ui.components.pathplanner.Waypoint
import com.ares.analytics.viewmodel.pathing.RobotDimensions
import com.ares.analytics.viewmodel.pathing.legalCenterBounds
import com.areslib.catalog.ActionDescriptor
import com.areslib.catalog.CapabilityCatalogDocument
import com.areslib.catalog.CapabilityParameterDescriptor
import com.areslib.catalog.CapabilityParameterType
import com.areslib.catalog.ConditionDescriptor
import com.areslib.routine.AutonomousCatalogEntry
import com.areslib.routine.RoutineDocument
import com.areslib.routine.RoutineDriveStep
import com.areslib.routine.RoutinePose
import com.areslib.routine.RoutineStep
import com.areslib.routine.RoutineStepKind
import com.areslib.routine.RoutineValidationContext
import com.areslib.routine.RoutineValidationIssue
import com.areslib.routine.RoutineValidationSeverity
import com.areslib.routine.validateRoutine

/** Creates a valid novice-friendly starting payload for every supported routine node. */
fun defaultRoutineStep(
    kind: RoutineStepKind,
    pose: RoutinePose,
    actionKey: String?,
    conditionKey: String?,
    calledRoutineId: String?
): RoutineStep = when (kind) {
    RoutineStepKind.ACTION -> RoutineStep.action(actionKey ?: "select.action")
    RoutineStepKind.DRIVE_TO -> RoutineStep.driveTo(RoutineDriveStep(pose))
    RoutineStepKind.WAIT -> RoutineStep.wait(1.0)
    RoutineStepKind.WAIT_UNTIL -> RoutineStep.waitUntil(conditionKey ?: "select.condition", 2.0)
    RoutineStepKind.TOGETHER -> RoutineStep.together(listOf(RoutineStep.wait(0.25)))
    RoutineStepKind.FIRST_TO_FINISH -> RoutineStep.firstToFinish(listOf(RoutineStep.wait(0.25)))
    RoutineStepKind.DEADLINE -> RoutineStep.deadline(RoutineStep.wait(1.0), emptyList())
    RoutineStepKind.CALL -> RoutineStep.call(calledRoutineId ?: "select-routine")
    RoutineStepKind.REPEAT -> RoutineStep.repeat(2, listOf(RoutineStep.wait(0.25)))
    RoutineStepKind.BRANCH -> RoutineStep.branch(
        conditionKey ?: "select.condition",
        whenTrue = listOf(RoutineStep.wait(0.25))
    )
}

fun clampRoutinePose(pose: RoutinePose, league: League, dimensions: RobotDimensions): RoutinePose {
    if (!pose.xMeters.isFinite() || !pose.yMeters.isFinite() || !pose.headingRadians.isFinite()) return pose
    val bounds = legalCenterBounds(league, dimensions, pose.headingRadians)
    return pose.copy(
        xMeters = pose.xMeters.coerceIn(bounds.minX, bounds.maxX),
        yMeters = pose.yMeters.coerceIn(bounds.minY, bounds.maxY)
    )
}

fun RoutineStep.clampDriveTargets(league: League, dimensions: RobotDimensions): RoutineStep {
    val driveStep = drive
    return copy(
        drive = driveStep?.copy(target = clampRoutinePose(driveStep.target, league, dimensions)),
        children = children.map { it.clampDriveTargets(league, dimensions) },
        deadline = deadline?.clampDriveTargets(league, dimensions),
        elseChildren = elseChildren.map { it.clampDriveTargets(league, dimensions) }
    )
}

fun List<RoutineStep>.lastRoutineDriveTarget(): RoutinePose? = asReversed().firstNotNullOfOrNull { step ->
    step.elseChildren.lastRoutineDriveTarget()
        ?: step.children.lastRoutineDriveTarget()
        ?: step.deadline?.let { listOf(it).lastRoutineDriveTarget() }
        ?: step.drive?.target
}

fun List<RoutineStep>.routineDriveStepsInExecutionOrder(): List<RoutineDriveStep> = buildList {
    this@routineDriveStepsInExecutionOrder.forEach { step ->
        step.drive?.let(::add)
        step.deadline?.let { addAll(listOf(it).routineDriveStepsInExecutionOrder()) }
        addAll(step.children.routineDriveStepsInExecutionOrder())
        addAll(step.elseChildren.routineDriveStepsInExecutionOrder())
    }
}

/** Replaces exactly one nested node without depending on its current list position. */
fun List<RoutineStep>.updateStepById(stepId: String, transform: (RoutineStep) -> RoutineStep): List<RoutineStep> =
    map { step ->
        if (step.stepId == stepId) transform(step).copy(stepId = stepId) else step.copy(
            children = step.children.updateStepById(stepId, transform),
            deadline = step.deadline?.let { listOf(it).updateStepById(stepId, transform).single() },
            elseChildren = step.elseChildren.updateStepById(stepId, transform)
        )
    }

/** Removes one nested node while preserving every unaffected node identity. */
fun List<RoutineStep>.removeStepById(stepId: String): List<RoutineStep> = mapNotNull { step ->
    if (step.stepId == stepId) null else step.copy(
        children = step.children.removeStepById(stepId),
        deadline = step.deadline?.takeUnless { it.stepId == stepId }?.let {
            listOf(it).removeStepById(stepId).singleOrNull()
        },
        elseChildren = step.elseChildren.removeStepById(stepId)
    )
}

/** Moves a node only within its owning sibling lane. */
fun List<RoutineStep>.moveStepById(stepId: String, direction: Int): List<RoutineStep> {
    val index = indexOfFirst { it.stepId == stepId }
    if (index >= 0) {
        val destination = index + direction
        if (destination !in indices) return this
        return toMutableList().apply { add(destination, removeAt(index)) }
    }
    return map { step -> step.copy(
        children = step.children.moveStepById(stepId, direction),
        deadline = step.deadline,
        elseChildren = step.elseChildren.moveStepById(stepId, direction)
    ) }
}

/** Reorders a root step from [fromIndex] to [toIndex]. */
fun List<RoutineStep>.reorderStep(fromIndex: Int, toIndex: Int): List<RoutineStep> {
    if (fromIndex !in indices || toIndex !in indices || fromIndex == toIndex) return this
    return toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
}

fun List<RoutineStep>.withRoutineRouteWaypoints(
    waypoints: Iterator<Waypoint>,
    league: League,
    dimensions: RobotDimensions
): List<RoutineStep> = map { step ->
    val updatedDrive = step.drive?.let { drive ->
        if (!waypoints.hasNext()) return@let drive
        val waypoint = waypoints.next()
        drive.copy(
            target = clampRoutinePose(
                RoutinePose(
                    waypoint.x,
                    waypoint.y,
                    waypoint.rotationDeg?.let(Math::toRadians) ?: waypoint.headingRad ?: 0.0
                ),
                league,
                dimensions
            )
        )
    }
    val updatedDeadline = step.deadline?.let { deadline ->
        listOf(deadline).withRoutineRouteWaypoints(waypoints, league, dimensions).single()
    }
    step.copy(
        drive = updatedDrive,
        deadline = updatedDeadline,
        children = step.children.withRoutineRouteWaypoints(waypoints, league, dimensions),
        elseChildren = step.elseChildren.withRoutineRouteWaypoints(waypoints, league, dimensions)
    )
}

/** Validation assembled from the same offline documents that code generation consumes. */
fun routineEditorValidation(
    routine: RoutineDocument,
    catalog: CapabilityCatalogDocument?,
    projectRoutines: List<RoutineDocument>,
    league: League,
    dimensions: RobotDimensions,
    autonomousEntry: AutonomousCatalogEntry?
): List<RoutineValidationIssue> {
    val actions = catalog?.actions.orEmpty().associateBy(ActionDescriptor::key)
    val conditions = catalog?.conditions.orEmpty().associateBy(ConditionDescriptor::key)
    val documents = (projectRoutines + routine).associateBy(RoutineDocument::documentId)
    val context = RoutineValidationContext(
        documents = documents,
        requireResolvedCalls = true,
        hasAction = catalog?.let { { key: String -> key in actions } },
        hasCondition = catalog?.let { { key: String -> key in conditions } },
        resourcesForAction = { key -> actions[key]?.resources.orEmpty().mapTo(linkedSetOf()) { it.resourceKey } }
    )
    return buildList {
        addAll(validateRoutine(routine, context))
        validateStepFields(routine, routine.steps, "steps", actions, conditions, league, dimensions, this)
        autonomousEntry?.let { entry ->
            if (entry.routineId != routine.documentId) {
                add(routineIssue(routine, "autonomousEntry.routineId", "wrong_routine", "Autonomous choice points to another routine"))
            }
            if (entry.startingPose != clampRoutinePose(entry.startingPose, league, dimensions)) {
                add(routineIssue(routine, "autonomousEntry.startingPose", "robot_outside_field", "Starting robot footprint crosses the field boundary"))
            }
        }
    }.distinctBy { listOf(it.path, it.code, it.message) }
}

private fun validateStepFields(
    routine: RoutineDocument,
    steps: List<RoutineStep>,
    path: String,
    actions: Map<String, ActionDescriptor>,
    conditions: Map<String, ConditionDescriptor>,
    league: League,
    dimensions: RobotDimensions,
    issues: MutableList<RoutineValidationIssue>
) {
    steps.forEach { step ->
        val stepPath = "$path/${step.stepId}"
        step.drive?.let { drive ->
            if (drive.target != clampRoutinePose(drive.target, league, dimensions)) {
                issues += routineIssue(routine, "$stepPath.drive.target", "robot_outside_field", "Drive goal robot footprint crosses the field boundary")
            }
            drive.markers.forEachIndexed { index, marker ->
                if (marker.progress !in 0.0..1.0) {
                    issues += routineIssue(routine, "$stepPath.drive.markers[$index]", "invalid_marker_progress", "Marker progress must be between 0% and 100% (0.0 to 1.0)")
                }
                if (actions.isNotEmpty() && marker.actionKey !in actions) {
                    issues += routineIssue(routine, "$stepPath.drive.markers[$index].actionKey", "unknown_marker_action", "Marker references unknown action '${marker.actionKey}'")
                }
            }
            if (actions.isNotEmpty()) {
                drive.duringActionKeys.filter { it !in actions }.forEach { missing ->
                    issues += routineIssue(routine, "$stepPath.drive.duringActionKeys", "unknown_during_action", "During-motion action '$missing' is not declared in action catalog")
                }
                drive.arrivalActionKeys.filter { it !in actions }.forEach { missing ->
                    issues += routineIssue(routine, "$stepPath.drive.arrivalActionKeys", "unknown_arrival_action", "Arrival action '$missing' is not declared in action catalog")
                }
            }
        }
        step.durationSeconds?.let { dur ->
            if (dur < 0.0 || dur > 120.0 || !dur.isFinite()) {
                issues += routineIssue(routine, "$stepPath.durationSeconds", "invalid_duration", "Duration must be between 0.0 and 120.0 seconds")
            }
        }
        step.timeoutSeconds?.let { timeout ->
            if (timeout <= 0.0 || timeout > 120.0 || !timeout.isFinite()) {
                issues += routineIssue(routine, "$stepPath.timeoutSeconds", "invalid_timeout", "Timeout must be between 0.0 and 120.0 seconds")
            }
        }
        when (step.kind) {
            RoutineStepKind.ACTION -> actions[step.actionKey]?.let { descriptor ->
                validateArguments(routine, stepPath, step.arguments, descriptor.parameters, issues)
            }
            RoutineStepKind.WAIT_UNTIL,
            RoutineStepKind.BRANCH -> conditions[step.conditionKey]?.let { descriptor ->
                validateArguments(routine, stepPath, step.arguments, descriptor.parameters, issues)
            }
            else -> Unit
        }
        step.deadline?.let {
            validateStepFields(routine, listOf(it), "$stepPath.deadline", actions, conditions, league, dimensions, issues)
        }
        validateStepFields(routine, step.children, "$stepPath.children", actions, conditions, league, dimensions, issues)
        validateStepFields(routine, step.elseChildren, "$stepPath.elseChildren", actions, conditions, league, dimensions, issues)
    }
}

private fun validateArguments(
    routine: RoutineDocument,
    path: String,
    arguments: Map<String, String>,
    parameters: List<CapabilityParameterDescriptor>,
    issues: MutableList<RoutineValidationIssue>
) {
    val declared = parameters.associateBy(CapabilityParameterDescriptor::key)
    arguments.keys.filter { it !in declared }.forEach { key ->
        issues += routineIssue(routine, "$path.arguments.$key", "unknown_argument", "'$key' is not a declared parameter")
    }
    parameters.forEach { parameter ->
        val value = arguments[parameter.key]
        val hasDefault = parameter.defaultNumber != null || parameter.defaultBoolean != null || parameter.defaultText != null
        if (value == null) {
            if (parameter.required && !hasDefault) {
                issues += routineIssue(routine, "$path.arguments.${parameter.key}", "missing_argument", "${parameter.displayName} is required")
            }
            return@forEach
        }
        val valid = when (parameter.type) {
            CapabilityParameterType.NUMBER -> {
                val minimum = parameter.minimum
                val maximum = parameter.maximum
                value.toDoubleOrNull()?.takeIf(Double::isFinite)?.let { number ->
                    (minimum == null || number >= minimum) &&
                        (maximum == null || number <= maximum)
                } == true
            }
            CapabilityParameterType.BOOLEAN -> value == "true" || value == "false"
            CapabilityParameterType.TEXT -> true
            CapabilityParameterType.ENUM -> value in parameter.options
        }
        if (!valid) {
            issues += routineIssue(routine, "$path.arguments.${parameter.key}", "invalid_argument", "${parameter.displayName} has an invalid value")
        }
    }
}

private fun routineIssue(
    routine: RoutineDocument,
    path: String,
    code: String,
    message: String
) = RoutineValidationIssue(RoutineValidationSeverity.ERROR, routine.documentId, path, code, message)
