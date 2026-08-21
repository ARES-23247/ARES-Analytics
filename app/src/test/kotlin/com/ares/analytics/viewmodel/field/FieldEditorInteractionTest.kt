package com.ares.analytics.viewmodel.field

import com.ares.analytics.shared.AprilTagPlacement
import com.ares.analytics.shared.FieldWaypoint
import com.ares.analytics.shared.GamePiece
import com.ares.analytics.shared.GamePieceType
import com.ares.analytics.shared.League
import com.ares.analytics.shared.Obstacle
import com.ares.analytics.viewmodel.FieldEditorIntent
import com.ares.analytics.viewmodel.FieldEditorViewModel
import com.areslib.state.FieldType
import com.areslib.state.RobotFieldDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FieldEditorInteractionTest {
    @Test
    fun undoRedoAndDuplicateOperateOnWholeEditorTransactions() {
        val viewModel = FieldEditorViewModel(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        viewModel.onIntent(FieldEditorIntent.LoadConfig(null, League.FTC))
        val obstacle = Obstacle.Rectangle("barrier", "Barrier", 0.0, 0.0, 0.5, 0.25)

        viewModel.onIntent(FieldEditorIntent.AddObstacle(obstacle))
        viewModel.onIntent(FieldEditorIntent.SelectElement(obstacle.id))
        viewModel.onIntent(FieldEditorIntent.DuplicateSelection)

        assertEquals(2, viewModel.state.value.obstacles.size)
        assertEquals(1, viewModel.state.value.selectedElementIds.size)
        assertTrue(viewModel.state.value.canUndo)

        viewModel.onIntent(FieldEditorIntent.Undo)
        assertEquals(listOf(obstacle), viewModel.state.value.obstacles)
        assertTrue(viewModel.state.value.canRedo)

        viewModel.onIntent(FieldEditorIntent.Redo)
        assertEquals(2, viewModel.state.value.obstacles.size)
    }

    @Test
    fun lockedItemsAreNotDeletedOrNudged() {
        val viewModel = FieldEditorViewModel(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        viewModel.onIntent(FieldEditorIntent.LoadConfig(null, League.FTC))
        val locked = GamePiece("locked", "Locked", 0.2, 0.3, locked = true)
        viewModel.onIntent(FieldEditorIntent.AddGamePiece(locked))
        viewModel.onIntent(FieldEditorIntent.SelectElement(locked.id))

        viewModel.onIntent(FieldEditorIntent.NudgeSelection(0.1, 0.1))
        viewModel.onIntent(FieldEditorIntent.DeleteSelection)

        assertEquals(locked, viewModel.state.value.gamePieces.single())
    }

    @Test
    fun validationFindsDuplicateTagsInvalidGeometryAndOutOfBoundsItems() {
        val issues = FieldEditorValidator.validate(
            league = League.FRC,
            widthMeters = 16.541,
            heightMeters = 8.211,
            obstacles = listOf(Obstacle.Circle("bad", "Bad circle", 2.0, 2.0, -0.1)),
            gamePieces = listOf(GamePiece("outside", "Outside note", -1.0, 1.0)),
            aprilTags = listOf(
                AprilTagPlacement("tag-a", 4, 1.0, 1.0),
                AprilTagPlacement("tag-b", 4, 2.0, 1.0)
            ),
            waypoints = listOf(FieldWaypoint("waypoint", "Score", 1.0, 9.0, 0.0))
        )

        assertTrue(issues.any { it.message.contains("positive radius") })
        assertTrue(issues.any { it.message.contains("outside the field") })
        assertTrue(issues.any { it.message.contains("ID 4") && it.elementIds == setOf("tag-a", "tag-b") })
        assertFalse(issues.isEmpty())
    }

    @Test
    fun FTCEditorSurfacesRuntimeRequirementForAnAprilTagLayout() {
        val viewModel = FieldEditorViewModel(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))

        viewModel.onIntent(FieldEditorIntent.LoadConfig(null, League.FTC))

        assertTrue(viewModel.state.value.validationIssues.any { it.message.contains("AprilTag layout") })
    }

    @Test
    fun simulatorPublishReportsTransportFailureAndUsesOneCanonicalMessage() {
        val payloads = mutableListOf<String>()
        val viewModel = FieldEditorViewModel(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            fieldConfigPublisher = { payload ->
                payloads += payload
                false
            },
        )
        viewModel.onIntent(FieldEditorIntent.LoadConfig(null, League.FRC))

        viewModel.onIntent(FieldEditorIntent.PushToSimulator)

        assertEquals(1, payloads.size)
        assertEquals(FieldType.FRC, RobotFieldDocument.decode(payloads.single()).fieldType)
        assertTrue(viewModel.state.value.simulatorStatus.contains("not accepted"))
    }

    @Test
    fun validationUsesRotatedRectangleExtentsNotOnlyItsCenter() {
        val issues = FieldEditorValidator.validate(
            league = League.FRC,
            widthMeters = 16.541,
            heightMeters = 8.211,
            obstacles = listOf(
                Obstacle.Rectangle(
                    id = "edge",
                    name = "Edge barrier",
                    centerX = 16.4,
                    centerY = 4.0,
                    width = 1.0,
                    height = 0.5,
                    rotation = 45.0
                )
            ),
            gamePieces = emptyList(),
            aprilTags = emptyList(),
            waypoints = emptyList()
        )

        assertTrue(issues.any { it.elementIds == setOf("edge") && it.message.contains("extends outside") })
    }

    @Test
    fun prefabCatalogIsLeagueSpecific() {
        assertTrue(FieldPrefabCatalog.forLeague(League.FTC).any { it.id == "decode-ball" })
        assertFalse(FieldPrefabCatalog.forLeague(League.FRC).any { it.id == "decode-ball" })
        assertTrue(FieldPrefabCatalog.forLeague(League.FRC).any { it.id == "note" })
    }

    @Test
    fun gamePieceCatalogUsesStableIdsAndParticipatesInUndo() {
        val viewModel = FieldEditorViewModel(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        viewModel.onIntent(FieldEditorIntent.LoadConfig(null, League.FTC))
        val original = viewModel.state.value.gamePieceTypes
        val custom = GamePieceType(
            id = "practice-puck",
            name = "Practice Puck",
            shape = "cylinder",
            diameter = 0.18,
            width = 0.18,
            height = 0.04,
            colorHex = "#7E57C2",
            massKg = 0.25,
            friction = 0.55,
            restitution = 0.15,
        )

        viewModel.onIntent(FieldEditorIntent.SetGamePieceTypes(original + custom))

        assertEquals(custom, viewModel.state.value.gamePieceTypes.last())
        assertTrue(viewModel.state.value.canUndo)
        viewModel.onIntent(FieldEditorIntent.Undo)
        assertEquals(original, viewModel.state.value.gamePieceTypes)
        viewModel.onIntent(FieldEditorIntent.Redo)
        assertEquals(custom, viewModel.state.value.gamePieceTypes.last())
    }

    @Test
    fun catalogCannotDeleteATypeUsedByAPlacedPiece() {
        val viewModel = FieldEditorViewModel(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        viewModel.onIntent(FieldEditorIntent.LoadConfig(null, League.FTC))
        val catalog = viewModel.state.value.gamePieceTypes
        val usedType = catalog.first()
        viewModel.onIntent(
            FieldEditorIntent.AddGamePiece(
                GamePiece(
                    id = "placed-piece",
                    name = "Placed ${usedType.name}",
                    type = usedType.name,
                    x = 1.0,
                    y = 1.0,
                    typeId = usedType.id,
                )
            )
        )

        viewModel.onIntent(FieldEditorIntent.SetGamePieceTypes(catalog.drop(1)))

        assertEquals(catalog, viewModel.state.value.gamePieceTypes)
        assertTrue(viewModel.state.value.errorMessage.orEmpty().contains("still uses"))
    }
}
