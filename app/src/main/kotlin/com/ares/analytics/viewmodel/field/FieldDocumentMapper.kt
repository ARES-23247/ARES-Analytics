package com.ares.analytics.viewmodel.field

import com.ares.analytics.shared.AprilTagPlacement
import com.ares.analytics.shared.FTCCoordinateSystem
import com.ares.analytics.shared.FieldImageConfig
import com.ares.analytics.shared.FieldWaypoint
import com.ares.analytics.shared.GamePiece
import com.ares.analytics.shared.League
import com.ares.analytics.shared.Obstacle
import com.ares.analytics.shared.PathPoint
import com.areslib.state.FieldType
import com.areslib.state.FtcFieldCoordinateSystem
import com.areslib.state.RobotFieldAprilTag
import com.areslib.state.RobotFieldConfig
import com.areslib.state.RobotFieldElementInstance
import com.areslib.state.RobotFieldElementType
import com.areslib.state.RobotFieldImageConfig
import com.areslib.state.RobotFieldObstacle
import com.areslib.state.RobotFieldPoint
import com.areslib.state.RobotFieldWaypoint

/** Lossless-enough adapter between the editor presentation models and the canonical ARES field document. */
internal object FieldDocumentMapper {
    fun newDocument(league: League, image: FieldImageConfig = defaultImageConfig(league)): RobotFieldConfig =
        RobotFieldConfig(
            id = "${league.name.lowercase()}-field",
            name = "${league.name} Field",
            fieldType = league.toFieldType(),
            widthMeters = image.widthMeters,
            heightMeters = image.heightMeters,
            image = image.toCanonical()
        )

    fun withEditorData(
        base: RobotFieldConfig,
        league: League,
        image: FieldImageConfig,
        obstacles: List<Obstacle>,
        gamePieces: List<GamePiece>,
        aprilTags: List<AprilTagPlacement>,
        fieldWaypoints: List<FieldWaypoint>
    ): RobotFieldConfig {
        val existingTypes = base.elementTypes.associateBy { it.id }.toMutableMap()
        val typesByName = base.elementTypes.associateBy { it.name.lowercase() }.toMutableMap()
        val existingElements = base.elements.associateBy { it.id }

        val elements = gamePieces.map { piece ->
            val prior = existingElements[piece.id]
            val priorType = prior?.let { existingTypes[it.elementTypeId] }
            val type = if (priorType?.name == piece.type) {
                priorType
            } else {
                typesByName[piece.type.lowercase()] ?: defaultElementType(piece.type).also {
                    existingTypes[it.id] = it
                    typesByName[it.name.lowercase()] = it
                }
            }
            RobotFieldElementInstance(
                id = piece.id,
                elementTypeId = type.id,
                name = piece.name,
                x = piece.x,
                y = piece.y,
                rotation = prior?.rotation ?: 0.0,
                locked = piece.locked
            )
        }

        return base.copy(
            revision = base.revision + 1L,
            fieldType = league.toFieldType(),
            widthMeters = image.widthMeters,
            heightMeters = image.heightMeters,
            image = image.toCanonical(),
            obstacles = obstacles.map { it.toCanonical() },
            apriltags = aprilTags.map { it.toCanonical() },
            elementTypes = existingTypes.values.sortedBy { it.id },
            elements = elements,
            fieldWaypoints = fieldWaypoints.map { it.toCanonical() }
        )
    }

    fun image(document: RobotFieldConfig): FieldImageConfig {
        val config = document.image
        return FieldImageConfig(
            imagePath = config?.imagePath.orEmpty(),
            rotationDegrees = config?.rotationDegrees ?: 0.0,
            cropLeft = config?.cropLeft ?: 0.0,
            cropRight = config?.cropRight ?: 1.0,
            cropTop = config?.cropTop ?: 0.0,
            cropBottom = config?.cropBottom ?: 1.0,
            widthMeters = document.resolvedWidthMeters,
            heightMeters = document.resolvedHeightMeters,
            ftcCoordinateSystem = when (config?.ftcCoordinateSystem) {
                FtcFieldCoordinateSystem.SQUARE -> FTCCoordinateSystem.SQUARE
                else -> FTCCoordinateSystem.DIAMOND
            }
        )
    }

    fun obstacles(document: RobotFieldConfig): List<Obstacle> = document.obstacles.map { obstacle ->
        when (obstacle.shape.lowercase()) {
            "circle" -> Obstacle.Circle(
                id = obstacle.id,
                name = obstacle.name,
                centerX = obstacle.x,
                centerY = obstacle.y,
                radius = obstacle.width,
                locked = obstacle.locked,
                colorHex = obstacle.color
            )
            "polygon" -> Obstacle.Polygon(
                id = obstacle.id,
                name = obstacle.name,
                vertices = obstacle.points.map { PathPoint(it.x, it.y) },
                locked = obstacle.locked,
                colorHex = obstacle.color
            )
            else -> Obstacle.Rectangle(
                id = obstacle.id,
                name = obstacle.name,
                centerX = obstacle.x,
                centerY = obstacle.y,
                width = obstacle.width,
                height = obstacle.height,
                rotation = obstacle.rotation,
                locked = obstacle.locked,
                colorHex = obstacle.color
            )
        }
    }

    fun gamePieces(document: RobotFieldConfig): List<GamePiece> {
        val types = document.elementTypes.associateBy { it.id }
        return document.elements.map { element ->
            GamePiece(
                id = element.id,
                name = element.name.ifBlank { element.id },
                x = element.x,
                y = element.y,
                type = types[element.elementTypeId]?.name ?: element.elementTypeId,
                locked = element.locked
            )
        }
    }

    fun aprilTags(document: RobotFieldConfig): List<AprilTagPlacement> = document.apriltags.map { tag ->
        AprilTagPlacement(
            id = tag.editorId.ifBlank { "apriltag_${tag.id}" },
            tagId = tag.id,
            x = tag.x,
            y = tag.y,
            z = tag.z,
            yawDegrees = tag.yaw,
            locked = tag.locked
        )
    }

    fun fieldWaypoints(document: RobotFieldConfig): List<FieldWaypoint> = document.fieldWaypoints.map { waypoint ->
        FieldWaypoint(
            id = waypoint.id,
            name = waypoint.name,
            x = waypoint.x,
            y = waypoint.y,
            headingDegrees = waypoint.headingDegrees,
            locked = waypoint.locked
        )
    }

    fun defaultImageConfig(league: League): FieldImageConfig = when (league) {
        League.FTC -> FieldImageConfig(widthMeters = 3.6576, heightMeters = 3.6576)
        League.FRC -> FieldImageConfig(widthMeters = 16.541, heightMeters = 8.211)
    }

    private fun League.toFieldType(): FieldType = if (this == League.FTC) FieldType.FTC else FieldType.FRC

    private fun FieldImageConfig.toCanonical(): RobotFieldImageConfig = RobotFieldImageConfig(
        imagePath = imagePath.ifBlank { "field_image.png" },
        rotationDegrees = rotationDegrees,
        cropLeft = cropLeft,
        cropRight = cropRight,
        cropTop = cropTop,
        cropBottom = cropBottom,
        ftcCoordinateSystem = when (ftcCoordinateSystem) {
            FTCCoordinateSystem.DIAMOND -> FtcFieldCoordinateSystem.DIAMOND
            FTCCoordinateSystem.SQUARE -> FtcFieldCoordinateSystem.SQUARE
        }
    )

    private fun Obstacle.toCanonical(): RobotFieldObstacle = when (this) {
        is Obstacle.Circle -> RobotFieldObstacle(
            id = id,
            name = name,
            x = centerX,
            y = centerY,
            width = radius,
            height = radius,
            shape = "circle",
            locked = locked,
            color = colorHex
        )
        is Obstacle.Rectangle -> RobotFieldObstacle(
            id = id,
            name = name,
            x = centerX,
            y = centerY,
            width = width,
            height = height,
            shape = "rectangle",
            rotation = rotation,
            locked = locked,
            color = colorHex
        )
        is Obstacle.Polygon -> RobotFieldObstacle(
            id = id,
            name = name,
            shape = "polygon",
            points = vertices.map { RobotFieldPoint(it.x, it.y) },
            locked = locked,
            color = colorHex
        )
    }

    private fun AprilTagPlacement.toCanonical(): RobotFieldAprilTag = RobotFieldAprilTag(
        id = tagId,
        x = x,
        y = y,
        z = z,
        yaw = yawDegrees,
        editorId = id,
        locked = locked
    )

    private fun FieldWaypoint.toCanonical(): RobotFieldWaypoint = RobotFieldWaypoint(
        id = id,
        name = name,
        x = x,
        y = y,
        headingDegrees = headingDegrees,
        locked = locked
    )

    private fun defaultElementType(name: String): RobotFieldElementType {
        val normalized = name.lowercase()
        val isNote = "note" in normalized
        val isBall = "ball" in normalized
        val isSample = "sample" in normalized || "specimen" in normalized
        return RobotFieldElementType(
            id = "game-piece-${normalized.replace(Regex("[^a-z0-9]+"), "-").trim('-')}",
            name = name,
            shape = when {
                isSample -> "box"
                isBall -> "sphere"
                else -> "cylinder"
            },
            width = if (isSample) 0.15 else 0.10,
            height = if (isSample) 0.05 else 0.10,
            depth = if (isSample) 0.15 else 0.10,
            diameter = when {
                isNote -> 0.3556
                isBall -> 0.15
                else -> null
            },
            color = when {
                "yellow" in normalized -> "#FDD835"
                "red" in normalized -> "#E53935"
                "blue" in normalized -> "#1E88E5"
                isNote -> "#F57C00"
                else -> "#FFFFFF"
            },
            massKg = when {
                isNote -> 0.235
                isSample -> 0.20
                else -> 0.24
            },
            movable = true
        )
    }
}
