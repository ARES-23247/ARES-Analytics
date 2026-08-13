package com.ares.analytics.service

import com.ares.analytics.shared.AppJson
import com.areslib.controls.ControlSchemeCodec
import com.areslib.controls.ControlSchemeDocument
import com.areslib.controls.ControlValidationContext
import com.areslib.controls.ControlValidationSeverity
import com.areslib.controls.validateControlScheme
import com.areslib.drivetrain.DrivetrainDocument
import com.areslib.drivetrain.DrivetrainDocumentCodec
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class DrivebaseDesignProposal(
    val summary: String,
    val explanations: List<String>,
    val candidate: DrivetrainDocument,
)

fun interface DrivebaseDesignAssistant {
    suspend fun propose(current: DrivetrainDocument, studentRequest: String): DrivebaseDesignProposal
}

data class ControlsDesignContext(
    val actionKeys: Set<String>,
    val routineIds: Set<String>,
    val profileControls: Map<String, Set<String>>,
)

data class ControlsDesignProposal(
    val summary: String,
    val explanations: List<String>,
    val candidate: ControlSchemeDocument,
)

fun interface ControlsDesignAssistant {
    suspend fun propose(
        current: ControlSchemeDocument,
        context: ControlsDesignContext,
        studentRequest: String,
    ): ControlsDesignProposal
}

internal fun parseDrivebaseDesignProposalResponse(
    current: DrivetrainDocument,
    responseText: String,
): DrivebaseDesignProposal {
    val envelope = proposalEnvelope(responseText)
    val proposed = DrivetrainDocumentCodec.decode(proposedDocumentJson(envelope))
    val candidate = proposed.copy(
        schemaVersion = current.schemaVersion,
        uid = current.uid,
        drivebaseId = current.drivebaseId,
        kind = current.kind,
        platform = current.platform,
        canonicalProfileUid = current.canonicalProfileUid,
        parameters = current.parameters,
        calibrationProvenance = current.calibrationProvenance,
        ctreImport = current.ctreImport,
    )
    DrivetrainDocumentCodec.encode(candidate)
    return DrivebaseDesignProposal(
        summary = proposalSummary(envelope, "Gemini proposed drivebase form changes."),
        explanations = proposalExplanations(envelope),
        candidate = candidate,
    )
}

internal fun parseControlsDesignProposalResponse(
    current: ControlSchemeDocument,
    context: ControlsDesignContext,
    responseText: String,
): ControlsDesignProposal {
    val envelope = proposalEnvelope(responseText)
    val proposed = ControlSchemeCodec.decode(proposedDocumentJson(envelope))
    val candidate = proposed.copy(
        schemaVersion = current.schemaVersion,
        documentId = current.documentId,
        revision = current.revision,
        parentContentHash = current.parentContentHash,
        controllers = current.controllers,
    )
    val errors = validateControlScheme(
        candidate,
        ControlValidationContext(context.actionKeys, context.routineIds, context.profileControls),
    ).filter { it.severity == ControlValidationSeverity.ERROR }
    require(errors.isEmpty()) {
        errors.joinToString("; ") { "${it.path}: ${it.message}" }
    }
    return ControlsDesignProposal(
        summary = proposalSummary(envelope, "Gemini proposed controller-binding changes."),
        explanations = proposalExplanations(envelope),
        candidate = candidate,
    )
}

private fun proposalEnvelope(responseText: String) = AppJson.parseToJsonElement(
    responseText.replace(Regex("```(?:json)?\\n?(.*?)\\n?```", RegexOption.DOT_MATCHES_ALL), "$1").trim()
).jsonObject

private fun proposedDocumentJson(envelope: kotlinx.serialization.json.JsonObject): String {
    val value = requireNotNull(envelope["proposedDocument"]) {
        "Gemini response did not contain proposedDocument."
    }
    return if (value is JsonPrimitive && value.isString) value.content else value.toString()
}

private fun proposalSummary(envelope: kotlinx.serialization.json.JsonObject, fallback: String): String =
    envelope["summary"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().ifBlank { fallback }

private fun proposalExplanations(envelope: kotlinx.serialization.json.JsonObject): List<String> =
    envelope["explanations"]?.jsonArray
        ?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotBlank) }
        .orEmpty()
        .take(12)
