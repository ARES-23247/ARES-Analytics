package com.ares.analytics.viewmodel.runanalysis

import com.ares.analytics.service.GuidedRunAnalysisReport
import com.ares.analytics.service.GuidedRunAnalysisRepository
import com.ares.analytics.service.GuidedRunConfidence
import com.ares.analytics.service.GuidedRunEvidenceContext
import com.ares.analytics.service.RunEvidenceSourceKind
import com.ares.analytics.service.RunSourceEvidence
import com.ares.analytics.shared.League
import com.ares.analytics.shared.Session
import com.ares.analytics.shared.WorkspaceConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GuidedRunAnalysisViewModelTest {
    @Test
    fun `a delayed old workspace cannot replace the selected workspace review`() = runTest {
        val oldSessions = CompletableDeferred<List<Session>>()
        val newSessions = CompletableDeferred<List<Session>>()
        val repository = FakeRepository(
            sessionLoads = mapOf("old" to oldSessions, "new" to newSessions),
        )
        val viewModel = GuidedRunAnalysisViewModel(repository, this)

        viewModel.load(workspace("old", "old-robot"))
        runCurrent()
        viewModel.load(workspace("new", "new-robot"))
        runCurrent()
        newSessions.complete(listOf(session("new-run", "new-robot")))
        advanceUntilIdle()

        assertEquals(listOf("new-run"), viewModel.state.value.sessions.map(Session::sessionId))
        assertEquals("new-run", viewModel.state.value.report?.session?.sessionId)

        oldSessions.complete(listOf(session("old-run", "old-robot")))
        advanceUntilIdle()

        assertEquals(listOf("new-run"), viewModel.state.value.sessions.map(Session::sessionId))
        assertEquals("new-run", viewModel.state.value.report?.session?.sessionId)
    }

    @Test
    fun `selection outside the workspace is rejected without running analysis`() = runTest {
        val sessions = CompletableDeferred<List<Session>>().apply {
            complete(listOf(session("mine", "practice")))
        }
        val repository = FakeRepository(mapOf("workspace" to sessions))
        val viewModel = GuidedRunAnalysisViewModel(repository, this)
        viewModel.load(workspace("workspace", "practice"))
        advanceUntilIdle()
        repository.analyzedSessionIds.clear()

        viewModel.selectSession("another-team")

        assertTrue(viewModel.state.value.error.orEmpty().contains("not part of the selected workspace"))
        assertTrue(repository.analyzedSessionIds.isEmpty())
        assertEquals("mine", viewModel.state.value.selectedSessionId)
    }

    @Test
    fun `empty workspace has a stable empty state instead of an analysis error`() = runTest {
        val sessions = CompletableDeferred<List<Session>>().apply { complete(emptyList()) }
        val repository = FakeRepository(mapOf("empty" to sessions))
        val viewModel = GuidedRunAnalysisViewModel(repository, this)

        viewModel.load(workspace("empty", "practice"))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.sessions.isEmpty())
        assertNull(viewModel.state.value.selectedSessionId)
        assertNull(viewModel.state.value.report)
        assertNull(viewModel.state.value.error)
    }

    private class FakeRepository(
        private val sessionLoads: Map<String, CompletableDeferred<List<Session>>>,
    ) : GuidedRunAnalysisRepository {
        val analyzedSessionIds = mutableListOf<String>()

        override suspend fun listWorkspaceSessions(workspace: WorkspaceConfig): List<Session> =
            sessionLoads.getValue(workspace.id).await()

        override suspend fun analyze(workspace: WorkspaceConfig, sessionId: String): GuidedRunAnalysisReport {
            analyzedSessionIds += sessionId
            return report(session(sessionId, workspace.robotId))
        }

        override suspend fun exportMarkdown(report: GuidedRunAnalysisReport, destination: File) {
            destination.writeText(report.session.sessionId)
        }
    }

    companion object {
        private fun workspace(id: String, robotId: String) = WorkspaceConfig(
            id = id,
            teamId = "23247",
            seasonId = "decode",
            robotId = robotId,
            projectPath = "C:/projects/$id",
            league = League.FTC,
        )

        private fun session(id: String, robotId: String) = Session(
            sessionId = id,
            teamId = "23247",
            seasonId = "decode",
            robotId = robotId,
            createdAt = 1_000L,
        )

        private fun report(session: Session) = GuidedRunAnalysisReport(
            session = session,
            source = RunSourceEvidence(
                kind = RunEvidenceSourceKind.LOCAL_SESSION_WITHOUT_REPORT,
                explanation = "Test source",
            ),
            evidenceContext = GuidedRunEvidenceContext(
                freshnessStatus = "Historical test recording",
                startTimestampMs = 0L,
                endTimestampMs = 1_000L,
                confidence = GuidedRunConfidence.LIMITED,
                confidenceExplanation = "Test coverage",
            ),
            summary = null,
            metrics = emptyList(),
            alerts = emptyList(),
            findings = emptyList(),
            comparison = null,
            regressions = emptyList(),
            driverReview = null,
            missingSignals = emptyList(),
            limitations = emptyList(),
            nextActions = emptyList(),
        )
    }
}
