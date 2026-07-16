# ARES-Analytics

A desktop mission control suite for ARES Robotics (FTC and FRC), built with Kotlin Compose Multiplatform.

## Overview

`ARES-Analytics` serves as the unified dashboard for telemetry visualization, control loop tuning, and autonomous path tracking. It pulls raw telemetry via NT4 (NetworkTables 4) and offline HTTP endpoints from robots running `ARESLib-Kotlin`.

### Key Features
- **Real-Time Telemetry Dashboard**: Visualizes robot pose, EKF states, battery health, swerve module states, and motor data via specialized widgets (e.g., `PoseViewerCard`, `ControlLoopProfilerCard`, `FieldViewerCard`).
- **Control Loop Profiler**: Live plotting of Target vs. Actual motor states via standard `Canvas` rendering to rapidly tune `kP/kI/kD` errors in real-time.
- **Offline-First Data Sync**: Pulls logged `.jsonl` files from the robot's embedded `LogManagerServer` over local Wi-Fi, parses them into DuckDB, and then manages Google Cloud (GCS) sync once the driver station laptop connects to the internet.
- **Path Tuning Visualizer**: Graphically displays any-angle pathfinding and trajectory optimizations across the FTC/FRC field coordinate spaces.

## Architecture

- **UI Framework**: Jetpack Compose for Desktop
- **Database**: Embedded DuckDB for local telemetry log querying
- **Networking**: Ktor client for HTTP log retrieval, NT4 client for real-time pub/sub
- **Coordinate Systems**: The `FieldViewerCard` and pathing renderers apply necessary transformations (e.g., swapping axes and applying a -90° rotation offset) to map standard CCW-positive math coordinates onto the visual canvas.

## Building and Running

Ensure you have published the latest `ARESLib-Kotlin` to your local Maven repository before building ARES-Analytics.

```powershell
# In ARESLib-Kotlin:
.\gradlew.bat publishToMavenLocal

# In ARES-Analytics:
# Compile the Kotlin codebase
.\gradlew.bat :app:compileKotlin

# Run the desktop application
.\gradlew.bat :app:run
```

## Dashboard Telemetry Keys
The dashboard strictly listens to standard NT4 topics published by `ARESLib-Kotlin`. For instance:
- `ARES/EstimatedPose/0` (X)
- `ARES/EstimatedPose/1` (Y)
- `ARES/EstimatedPose/2` (Heading - CCW+)
- `Hardware/Motors/{name}/TargetPosition` and `ActualPosition` (Used by Control Loop Profiler)
