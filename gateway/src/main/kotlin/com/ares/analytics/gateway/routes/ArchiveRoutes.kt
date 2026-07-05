package com.ares.analytics.gateway.routes

import com.ares.analytics.shared.*
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FirestoreOptions
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.HttpMethod
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import com.ares.analytics.gateway.auth.FirebasePrincipal
import java.util.concurrent.TimeUnit

fun Route.archiveRoutes(
    customStorage: Storage? = null,
    customFirestore: Firestore? = null
) {
    val storage = customStorage ?: StorageOptions.getDefaultInstance().service
    val bucketName = System.getenv("GCS_BUCKET_NAME") ?: "ares-analytics-telemetry"

    authenticate("firebase") {
        post("/api/archive/upload-url") {
            val principal = call.principal<FirebasePrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
            val req = call.receive<UploadUrlRequest>()

            try {
                val db = customFirestore ?: FirestoreOptions.getDefaultInstance().service
                
                // Verify teamId against user's githubOrgs
                val userDoc = db.collection("users").document(principal.uid).get().get()
                val githubOrgs = (userDoc.get("githubOrgs") as? List<*>)?.map { it.toString() } ?: emptyList()
                if (!githubOrgs.contains(req.summary.teamId)) {
                    return@post call.respond(HttpStatusCode.Forbidden, "User is not a member of the requested team")
                }

                // 1. Save summary metadata to Firestore
                val docRef = db.collection("summaries").document(req.sessionId)
                docRef.set(req.summary.toMap()).get()

                // 2. Generate pre-signed GCS URL
                val blobInfo = BlobInfo.newBuilder(bucketName, "${req.summary.teamId}/telemetry/${req.sessionId}.parquet").build()
                val uploadUrl = storage.signUrl(
                    blobInfo,
                    15,
                    TimeUnit.MINUTES,
                    Storage.SignUrlOption.httpMethod(HttpMethod.PUT),
                    Storage.SignUrlOption.withV4Signature()
                )

                val expiresAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(15)
                call.respond(UploadUrlResponse(uploadUrl.toString(), expiresAt))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Failed to provision upload: ${e.message}")
            }
        }

        post("/api/archive/sync") {
            val principal = call.principal<FirebasePrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
            val req = call.receive<SyncRequest>()

            try {
                val db = customFirestore ?: FirestoreOptions.getDefaultInstance().service
                
                // Verify teamId against user's githubOrgs
                val userDoc = db.collection("users").document(principal.uid).get().get()
                val githubOrgs = (userDoc.get("githubOrgs") as? List<*>)?.map { it.toString() } ?: emptyList()
                if (!githubOrgs.contains(req.teamId)) {
                    return@post call.respond(HttpStatusCode.Forbidden, "User is not a member of the requested team")
                }

                // Query all summaries matching teamId and seasonId
                val querySnapshot = db.collection("summaries")
                    .whereEqualTo("teamId", req.teamId)
                    .whereEqualTo("seasonId", req.seasonId)
                    .get()
                    .get()

                val cloudSummaries = querySnapshot.documents.map { doc ->
                    doc.data.toSessionSummary()
                }

                // Filter to only include those not in local knownSessionIds
                val missingSummaries = cloudSummaries.filter { summary ->
                    !req.knownSessionIds.contains(summary.sessionId)
                }

                call.respond(SyncResponse(missingSummaries))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Sync processing error: ${e.message}")
            }
        }

        post("/api/archive/delete") {
            val principal = call.principal<FirebasePrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
            val req = call.receive<DeleteSessionRequest>()

            try {
                val db = customFirestore ?: FirestoreOptions.getDefaultInstance().service

                // Only admins/coaches can delete cloud sessions
                if (!isUserAdmin(db, principal.uid)) {
                    return@post call.respond(HttpStatusCode.Forbidden, "Only admins and coaches can delete cloud sessions")
                }

                // Verify the user belongs to the team
                val userDoc = db.collection("users").document(principal.uid).get().get()
                val githubOrgs = (userDoc.get("githubOrgs") as? List<*>)?.map { it.toString() } ?: emptyList()
                if (!githubOrgs.contains(req.teamId)) {
                    return@post call.respond(HttpStatusCode.Forbidden, "User is not a member of the requested team")
                }

                // 1. Delete Firestore summary document
                db.collection("summaries").document(req.sessionId).delete().get()

                // 2. Delete GCS parquet blob (best-effort, may not exist)
                try {
                    val blobId = com.google.cloud.storage.BlobId.of(bucketName, "${req.teamId}/telemetry/${req.sessionId}.parquet")
                    storage.delete(blobId)
                } catch (_: Exception) {
                    // Blob may not exist if upload failed — that's fine
                }

                call.respond(HttpStatusCode.OK, "Session deleted")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Failed to delete session: ${e.message}")
            }
        }

        get("/api/team/{teamId}/robots") {
            val teamId = call.parameters["teamId"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing teamId")
            try {
                val db = customFirestore ?: FirestoreOptions.getDefaultInstance().service
                val querySnapshot = db.collection("teams").document(teamId).collection("robots").get().get()
                val robotsList = querySnapshot.documents.map { doc ->
                    val data = doc.data
                    RobotProfile(
                        robotId = doc.id,
                        league = League.valueOf(data["league"] as? String ?: "FTC"),
                        seasonId = data["seasonId"] as? String ?: "",
                        name = data["name"] as? String ?: doc.id
                    )
                }
                call.respond(TeamRobotsResponse(robotsList))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Failed to load team robots: ${e.message}")
            }
        }

        post("/api/team/robots/add") {
            val principal = call.principal<FirebasePrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
            val req = call.receive<AddRobotRequest>()
            try {
                val db = customFirestore ?: FirestoreOptions.getDefaultInstance().service
                if (!isUserAdmin(db, principal.uid)) {
                    return@post call.respond(HttpStatusCode.Forbidden, "Only mentors and administrators can register robot profiles.")
                }
                val docRef = db.collection("teams").document(req.teamId).collection("robots").document(req.robot.robotId)
                val robotMap = mapOf(
                    "league" to req.robot.league.name,
                    "seasonId" to req.robot.seasonId,
                    "name" to req.robot.name
                )
                docRef.set(robotMap).get()
                call.respond(HttpStatusCode.OK, "Robot profile added successfully")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Failed to add robot: ${e.message}")
            }
        }

        post("/api/team/robots/delete") {
            val principal = call.principal<FirebasePrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
            val req = call.receive<DeleteRobotRequest>()
            try {
                val db = customFirestore ?: FirestoreOptions.getDefaultInstance().service
                if (!isUserAdmin(db, principal.uid)) {
                    return@post call.respond(HttpStatusCode.Forbidden, "Only mentors and administrators can delete robot profiles.")
                }
                val docRef = db.collection("teams").document(req.teamId).collection("robots").document(req.robotId)
                docRef.delete().get()
                call.respond(HttpStatusCode.OK, "Robot profile deleted successfully")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Failed to delete robot: ${e.message}")
            }
        }
    }
}

private suspend fun isUserAdmin(db: Firestore, uid: String): Boolean {
    val userDoc = db.collection("users").document(uid).get().get()
    if (!userDoc.exists()) return false
    val role = userDoc.getString("role")?.lowercase()
    return role == "admin" || role == "coach"
}

// ────────────────────────────────────────────────────────────────────────────
// Helper Serialization Extensions for Firestore compatibility
// ────────────────────────────────────────────────────────────────────────────

private fun SessionSummary.toMap(): Map<String, Any?> {
    return mapOf(
        "sessionId" to sessionId,
        "teamId" to teamId,
        "seasonId" to seasonId,
        "robotId" to robotId,
        "createdAt" to createdAt,
        "durationMs" to durationMs,
        "minBatteryVoltage" to minBatteryVoltage,
        "maxEkfDrift" to maxEkfDrift,
        "avgLoopTimeMs" to avgLoopTimeMs,
        "p95LoopTimeMs" to p95LoopTimeMs,
        "motorCurrentAverages" to motorCurrentAverages,
        "visionAcceptanceRate" to visionAcceptanceRate,
        "tags" to tags,
        "matchNumber" to matchNumber,
        "allianceColor" to allianceColor
    )
}

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.toSessionSummary(): SessionSummary {
    val motorCurrents = (get("motorCurrentAverages") as? Map<*, *>)?.map {
        it.key.toString() to (it.value as? Number)?.toDouble()!!
    }?.toMap() ?: emptyMap()

    return SessionSummary(
        sessionId = get("sessionId") as String,
        teamId = get("teamId") as String,
        seasonId = get("seasonId") as String,
        robotId = get("robotId") as String,
        createdAt = (get("createdAt") as Number).toLong(),
        durationMs = (get("durationMs") as Number).toLong(),
        minBatteryVoltage = (get("minBatteryVoltage") as Number).toDouble(),
        maxEkfDrift = (get("maxEkfDrift") as Number).toDouble(),
        avgLoopTimeMs = (get("avgLoopTimeMs") as Number).toDouble(),
        p95LoopTimeMs = (get("p95LoopTimeMs") as Number).toDouble(),
        motorCurrentAverages = motorCurrents,
        visionAcceptanceRate = (get("visionAcceptanceRate") as Number).toDouble(),
        tags = (get("tags") as? List<*>)?.map { it.toString() } ?: emptyList(),
        matchNumber = (get("matchNumber") as? Number)?.toInt(),
        allianceColor = get("allianceColor") as? String
    )
}
