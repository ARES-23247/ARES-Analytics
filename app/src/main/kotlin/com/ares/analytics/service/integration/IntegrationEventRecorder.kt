package com.ares.analytics.service.integration

import com.ares.analytics.service.ImportReport
import com.ares.analytics.shared.AlertRecord
import com.ares.analytics.shared.Session
import com.ares.analytics.shared.SessionSummary
import com.ares.analytics.shared.models.AnalysisReady
import com.ares.analytics.shared.models.CloudUploadCommitted
import com.ares.analytics.shared.models.IntegrationEvent
import com.ares.analytics.shared.models.IntegrationEventType
import com.ares.analytics.shared.models.IntegrationIssueSeverity
import com.ares.analytics.shared.models.IntegrationWorkspaceIdentity
import com.ares.analytics.shared.models.EngineeringNotebookEntry
import com.ares.analytics.shared.models.NotebookDraftReady
import com.ares.analytics.shared.models.RobotIssueOpened
import com.ares.analytics.shared.models.RobotIssueResolved
import com.ares.analytics.shared.models.SessionImported
import com.ares.analytics.shared.models.SoftwareDigestReady
import com.ares.analytics.shared.models.eventType
import java.util.concurrent.atomic.AtomicReference

/** Runtime routing is mutable so provider settings can change without rebuilding import services. */
class IntegrationRoutingPolicy {
    private val routes = AtomicReference<Map<IntegrationEventType, Set<String>>>(emptyMap())

    fun replace(newRoutes: Map<IntegrationEventType, Set<String>>) {
        routes.set(newRoutes.mapValues { (_, providers) -> providers.toSet() })
    }

    fun providersFor(type: IntegrationEventType): Set<String> = routes.get()[type].orEmpty()
}

/** Records typed events after their owning transaction has committed. */
class IntegrationEventRecorder(
    private val store: IntegrationStore,
    private val routingPolicy: IntegrationRoutingPolicy,
) {
    suspend fun sessionImported(session: Session, reports: List<ImportReport>) = recordSafely(
        IntegrationEvent(
            eventId = "session-imported:${session.sessionId}",
            occurredAtMs = session.createdAt,
            payload = SessionImported(
                workspace = session.workspaceIdentity(),
                sessionId = session.sessionId,
                sourceNames = reports.map(ImportReport::sourceName).distinct().sorted(),
                sourceSha256 = reports.map(ImportReport::sourceSha256).distinct().sorted(),
            ),
        )
    )

    suspend fun analysisReady(summary: SessionSummary, analysisVersion: String = "summary-v1") = recordSafely(
        IntegrationEvent(
            eventId = "analysis-ready:${summary.sessionId}:$analysisVersion",
            occurredAtMs = summary.createdAt,
            payload = AnalysisReady(
                workspace = summary.workspaceIdentity(),
                sessionId = summary.sessionId,
                analysisVersion = analysisVersion,
            ),
        )
    )

    suspend fun alertPersisted(alert: AlertRecord, workspace: IntegrationWorkspaceIdentity): Boolean {
        val resolvedAtMs = alert.resolveTimestampMs
        return recordSafely(
            if (resolvedAtMs == null) {
            IntegrationEvent(
                eventId = "robot-issue-opened:${alert.alertId}",
                occurredAtMs = alert.triggerTimestampMs,
                payload = RobotIssueOpened(
                    workspace = workspace,
                    issueId = alert.alertId,
                    sessionId = alert.sessionId,
                    ruleKey = alert.ruleKey,
                    severity = IntegrationIssueSeverity.WARNING,
                    summary = "Alert rule ${alert.ruleKey} opened",
                ),
            )
            } else {
                IntegrationEvent(
                    eventId = "robot-issue-resolved:${alert.alertId}",
                    occurredAtMs = resolvedAtMs,
                    payload = RobotIssueResolved(
                        workspace = workspace,
                        issueId = alert.alertId,
                        sessionId = alert.sessionId,
                        resolution = "Alert rule ${alert.ruleKey} resolved",
                    ),
                )
            }
        )
    }

    suspend fun cloudUploadCommitted(
        workspace: IntegrationWorkspaceIdentity,
        sessionId: String,
        remoteObjectId: String,
        manifestRevision: String,
        occurredAtMs: Long,
        remoteUrl: String? = null,
    ) = recordSafely(
        IntegrationEvent(
            eventId = "cloud-upload-committed:$sessionId:$remoteObjectId",
            occurredAtMs = occurredAtMs,
            payload = CloudUploadCommitted(
                workspace = workspace,
                sessionId = sessionId,
                remoteObjectId = remoteObjectId,
                manifestRevision = manifestRevision,
                remoteUrl = remoteUrl,
            ),
        )
    )

    suspend fun notebookDraftReady(entry: EngineeringNotebookEntry) = recordSafely(
        IntegrationEvent(
            eventId = "notebook-draft-ready:${entry.entryId}:${entry.contentHash}",
            occurredAtMs = entry.updatedAtMs,
            payload = NotebookDraftReady(
                workspace = entry.workspace,
                entryId = entry.entryId,
                revision = entry.revision,
                contentHash = entry.contentHash,
            ),
        )
    )

    suspend fun softwareDigestReady(entry: EngineeringNotebookEntry, commitRange: String) = recordSafely(
        IntegrationEvent(
            eventId = "software-digest-ready:${entry.entryId}:${entry.contentHash}",
            occurredAtMs = entry.updatedAtMs,
            payload = SoftwareDigestReady(
                workspace = entry.workspace,
                entryId = entry.entryId,
                revision = entry.revision,
                contentHash = entry.contentHash,
                commitRange = commitRange,
            ),
        )
    )

    suspend fun recordSafely(event: IntegrationEvent): Boolean = runCatching {
        store.enqueue(event, routingPolicy.providersFor(event.payload.eventType()))
        true
    }.getOrElse { failure ->
        System.err.println(
            "[ARES-Analytics] Durable integration event ${event.eventId} could not be recorded; " +
                "the owning operation remains committed: " +
                (failure.message ?: failure::class.java.simpleName)
        )
        false
    }

    private fun Session.workspaceIdentity() = IntegrationWorkspaceIdentity(teamId, seasonId, robotId)

    private fun SessionSummary.workspaceIdentity() = IntegrationWorkspaceIdentity(teamId, seasonId, robotId)
}
