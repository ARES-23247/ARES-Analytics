package com.ares.analytics.viewmodel.project

import com.areslib.catalog.CapabilityCatalogDocument
import com.areslib.controls.ControlSchemeDocument
import com.areslib.controls.ControlValidationContext
import com.areslib.controls.ControlValidationSeverity
import com.areslib.controls.ControlTargetKind
import com.areslib.controls.ControllerProfileDocument
import com.areslib.controls.ControllerInputPlatform
import com.areslib.controls.learnedControlIds
import com.areslib.controls.validateControlScheme
import com.areslib.routine.AutonomousCatalogDocument
import com.areslib.routine.RoutineDocument
import com.areslib.project.AresProjectMetadataDocument

/** One consistent, entirely offline view of the selected robot project's authoring files. */
data class AresProjectDocumentSnapshot(
    val projectRoot: String,
    val routines: List<RoutineDocument>,
    val controlSchemes: List<ControlSchemeDocument>,
    val controllerProfiles: List<ControllerProfileDocument>,
    val capabilityCatalog: CapabilityCatalogDocument?,
    val autonomousCatalog: AutonomousCatalogDocument?,
    val projectMetadata: AresProjectMetadataDocument?,
    val diagnostics: List<ProjectDocumentDiagnostic>
)

/**
 * Loads all canonical GUI/codegen inputs from the selected directory without network, robot, or
 * cloud state. Cross-document references are validated only after every readable file is loaded.
 */
class AresProjectDocuments(
    val routines: RoutineProjectRepository = RoutineProjectRepository(),
    val controls: ControlSchemeProjectRepository = ControlSchemeProjectRepository(),
    val controllers: ControllerProfileProjectRepository = ControllerProfileProjectRepository(),
    val capabilities: CapabilityCatalogProjectRepository = CapabilityCatalogProjectRepository(),
    val metadata: ProjectMetadataRepository = ProjectMetadataRepository(),
    val autonomous: AutonomousCatalogProjectRepository = AutonomousCatalogProjectRepository(routines)
) {
    fun load(
        projectPath: String,
        targetPlatform: ControllerInputPlatform? = null
    ): AresProjectDocumentSnapshot {
        val root = requireProjectRoot(projectPath)
        val routineListing = routines.list(root.path)
        val controlsListing = controls.list(root.path)
        val profileListing = controllers.list(root.path)
        val diagnostics = buildList {
            addAll(routineListing.diagnostics)
            addAll(controlsListing.diagnostics)
            addAll(profileListing.diagnostics)
        }.toMutableList()

        val catalog = capabilities.load(root.path).fold(
            onSuccess = { it },
            onFailure = { error ->
                capabilities.file(root.path).takeIf { it.isFile }?.let { file ->
                    diagnostics += ProjectDocumentDiagnostic(
                        ProjectDocumentKind.CAPABILITY_CATALOG,
                        file,
                        error.message ?: "Capability catalog could not be decoded"
                    )
                }
                null
            }
        )
        val autonomousCatalog = autonomous.load(root.path).fold(
            onSuccess = { it },
            onFailure = { error ->
                val file = resolveProjectPath(root.path, ".ares/autonomous-catalog.json")
                if (file.isFile) {
                    diagnostics += ProjectDocumentDiagnostic(
                        ProjectDocumentKind.AUTONOMOUS_CATALOG,
                        file,
                        error.message ?: "Autonomous catalog could not be decoded"
                    )
                }
                null
            }
        )
        val projectMetadata = metadata.load(root.path).fold(
            onSuccess = { it },
            onFailure = { error ->
                diagnostics += ProjectDocumentDiagnostic(
                    ProjectDocumentKind.PROJECT_METADATA,
                    metadata.file(root.path),
                    error.message ?: "Canonical project metadata could not be decoded"
                )
                null
            }
        )

        if (catalog != null) {
            val profileById = profileListing.documents.associateBy { it.documentId }
            val profileControls = profileById.mapValues { (_, profile) ->
                if (targetPlatform == null) {
                    profile.controls.filter { it.mappings.isNotEmpty() }.mapTo(linkedSetOf()) { it.controlId }
                } else {
                    profile.learnedControlIds(targetPlatform)
                }
            }
            val routineIds = routineListing.documents.mapTo(linkedSetOf()) { it.documentId }
            val actionKeys = catalog.actions.mapTo(linkedSetOf()) { it.key }
            val context = ControlValidationContext.fromCatalog(
                catalog,
                routineIds,
                profileControls
            )
            controlsListing.documents.forEach { scheme ->
                val crossReferenceErrors = mutableListOf<String>()
                scheme.controllers.filter { it.profileId !in profileById }.forEach { controller ->
                    crossReferenceErrors += "Unknown controller profile '${controller.profileId}'"
                }
                val profileBySlot = scheme.controllers.associate { it.slot to it.profileId }
                scheme.bindings.filter { it.enabled }.forEach { binding ->
                    val profileId = profileBySlot[binding.source.controllerSlot]
                    if (profileId != null && binding.source.controlIds.any { it !in profileControls[profileId].orEmpty() }) {
                        crossReferenceErrors += "Binding '${binding.bindingId}' uses an unlearned control for profile '$profileId'"
                    }
                    when (binding.target.kind) {
                        ControlTargetKind.ACTION -> if (binding.target.key !in actionKeys) {
                            crossReferenceErrors += "Unknown action '${binding.target.key}'"
                        }
                        ControlTargetKind.ROUTINE, ControlTargetKind.CANCEL_ROUTINE ->
                            if (binding.target.key !in routineIds) {
                                crossReferenceErrors += "Unknown routine '${binding.target.key}'"
                            }
                    }
                }
                val validationErrors = validateControlScheme(scheme, context)
                    .filter { it.severity == ControlValidationSeverity.ERROR }
                    .map { it.message }
                val errors = (crossReferenceErrors + validationErrors).distinct()
                if (errors.isNotEmpty()) {
                    diagnostics += ProjectDocumentDiagnostic(
                        ProjectDocumentKind.CONTROL_SCHEME,
                        resolveProjectPath(root.path, ".ares/controls/${scheme.documentId}.arescontrols"),
                        errors.joinToString("; ")
                    )
                }
            }
        }

        return AresProjectDocumentSnapshot(
            projectRoot = root.path,
            routines = routineListing.documents,
            controlSchemes = controlsListing.documents,
            controllerProfiles = profileListing.documents,
            capabilityCatalog = catalog,
            autonomousCatalog = autonomousCatalog,
            projectMetadata = projectMetadata,
            diagnostics = diagnostics.distinctBy { it.kind to it.file.canonicalPath }
                .sortedWith(compareBy<ProjectDocumentDiagnostic> { it.kind.ordinal }.thenBy { it.file.name.lowercase() })
        )
    }
}
