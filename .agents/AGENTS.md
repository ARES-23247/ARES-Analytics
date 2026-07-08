# ARES-Analytics Agent Rules

## Dashboard Telemetry Key Mapping

The dashboard receives telemetry from the robot/simulator over NT4 (NetworkTables 4). Keys are published by `ARESNetworkStatePublisher` (in ARESLib-Kotlin) and `TelemetryPublisher` (in the simulator). Understanding the key mapping is critical to avoid display bugs.

### NT4 Topic → Dashboard Variable Mapping

| NT4 Topic | Dashboard Variable | Component | Description |
|---|---|---|---|
| `ARES/EstimatedPose/0` | `robotX` | PoseViewerCard, FieldViewerViewModel | X position (meters) |
| `ARES/EstimatedPose/1` | `robotY` | PoseViewerCard, FieldViewerViewModel | Y position (meters) |
| `ARES/EstimatedPose/2` | `robotHeading` | PoseViewerCard, FieldViewerViewModel | Heading (radians, CCW+) |
| `Drive/Pose_X` | `robotX` | PoseViewerCard, FieldViewerViewModel | EKF X (meters) |
| `Drive/Pose_Y` | `robotY` | PoseViewerCard, FieldViewerViewModel | EKF Y (meters) |
| `Drive/Drive_Heading` | `robotHeading` | PoseViewerCard, FieldViewerViewModel | EKF heading (radians, CCW+) |
| `Drive/Odom_X` | `pinpointX` / `ekfX` | PoseViewerCard, FieldViewerViewModel | Raw odometry X |
| `Drive/Odom_Y` | `pinpointY` / `ekfY` | PoseViewerCard, FieldViewerViewModel | Raw odometry Y |
| `Drive/Odom_Heading` | `pinpointHeading` / `ekfHeading` | PoseViewerCard, FieldViewerViewModel | Raw odometry heading |
| `Hardware/Motors/{name}/Power` | `velocities[i]` | MecanumVisualizer | Motor power (-1 to 1) |
| `Hardware/Motors/{name}/Velocity` | `velocities[i]` | MecanumVisualizer | Motor velocity (ticks/s) |
| `Hardware/Motors/{name}/CurrentAmps` | `currents[i]` | MecanumVisualizer | Motor current (amps) |

> [!WARNING]
> Both `ARES/EstimatedPose/2` and `Drive/Drive_Heading` map to `robotHeading`. The last-arriving value wins per render frame. Ensure both sources publish consistent data.

### Motor Name Convention
The FTC robot registers motors with hardware names `fl`, `fr`, `rl`, `rr` (front-left, front-right, **rear-left**, **rear-right**). The MecanumVisualizer MUST check for BOTH `bl`/`br` AND `rl`/`rr` naming patterns.

## Field Canvas Coordinate Transform

The FTC field is rendered with a non-obvious axis swap:

```kotlin
// FieldCanvasUtils.kt — getCanvasOffsetBase()
canvasX = (-fieldY / fieldWidth + 0.5) * canvasWidth   // +fieldY → LEFT
canvasY = (-fieldX / fieldHeight + 0.5) * canvasHeight  // +fieldX → UP
```

### Robot Icon Heading Compensation
The robot icon arrow points RIGHT (+canvasX) when rotation is 0. Since heading 0° means +fieldX which maps to UP on canvas, a **-90° offset** is applied in `PathRenderer.kt`:

```kotlin
// PathRenderer.kt — all 4 robot types (actual, estimated, playback, vision)
rotate(degrees = -Math.toDegrees(heading).toFloat() - 90f, pivot = robotOffset)
```

> [!CAUTION]
> Do NOT remove or change the `-90f` offset without also changing the robot icon drawing or the field-to-canvas transform. They are coupled.

## Heading Convention

All heading values in the dashboard are in **radians, CCW-positive** (math convention):
- **0 rad**: Facing +X (toward audience wall)
- **π/2 rad**: Facing +Y (toward red alliance wall)
- **π rad**: Facing -X
- **-π/2 rad**: Facing -Y

The `PoseViewerCard` converts to degrees for display only: `Math.toDegrees(heading)`.

## Build & Run
- Compile: `.\gradlew.bat :app:compileKotlin`
- Run: `.\gradlew.bat :app:run`
- The app depends on `ARESLib-Kotlin` via local Maven. After changing ARESLib, always run `.\gradlew.bat publishToMavenLocal` in that repo first.
