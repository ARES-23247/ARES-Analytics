# Architectural Specification: Multi-League Robotics Telemetry, Simulation, Path Planning & Analytics Suite

## 1. System Overview & Core Objectives
This document defines the production-ready technical specification for a cross-platform desktop mission control dashboard and cloud analytics platform. The suite bridges edge telemetry ingestion, offline mechanical simulation, dynamic physics modeling, control-loop profiling, and automated AI fault forensics across multiple robotics platforms and competitive leagues simultaneously.

### 1.1 Core Architectural Goals
* **Hierarchical Workspace Isolation:** Restrict data leakage by mapping all configurations, paths, and time-series summaries down a structural data tree: team_id -> season_id -> robot_id -> session_id.
* **Universal NT4 Telemetry Ingestion:** Leverage NetworkTables (NT4) over WebSockets as the single, real-time streaming layer for both FRC targets and custom FTC Kotlin (ARESLib) implementations.
* **Edge-First Local Analytics:** Execute heavy time-series regression models and fast Fourier transforms locally within the JVM runtime using Kotlin coroutines and structured concurrency to ensure full pit functionality in connectivity dead zones.
* **Multi-Client Summary Synchronization:** Reconcile distributed user nodes by syncing lightweight, pre-computed session summary documents via a cloud delta broker, bypassing the need to distribute multi-gigabyte raw binaries between peer laptops.
* **Passive Physical Characterization:** Extract true empirical system matrices (kS, kV, kA) and control-loop dampening recommendations entirely from passive, post-run match telemetry.
* **Frictionless Roster Governance:** Automate database access provisioning and administrative role validation by cross-referencing federated Google OAuth sessions against secure GitHub Organization listings.
* **Unified Language Architecture:** The entire system — robot code, desktop application, cloud gateway, and shared math libraries — is written in Kotlin, eliminating cross-language serialization overhead and enabling direct code sharing through Gradle module dependencies.
* **Robot-Sourced Hardware Topology:** Extract physical hardware topology directly from the robot codebase's self-registering `HardwareRegistry`, broadcast over NT4, and render as an interactive node graph for AI fault correlation.

---

## 2. Technology Stack & Component Architecture

### 2.1 Component Responsibilities
| Layer | Core Technology | Primary Mandate |
| :--- | :--- | :--- |
| **Desktop Application** | Kotlin, Compose Multiplatform, Skia | Render GPU-accelerated Canvas visuals, interactive configuration panels, real-time telemetry charts, and field coordinate views. All computation, I/O, and UI execute within a single JVM process with zero IPC serialization overhead. |
| **Shared Core Module** | Kotlin, ARESLib types | Provide shared kinematics types (`Pose2d`, `Rotation2d`, `PIDController`), NT4 serialization schemas, coordinate transforms, hardware topology data models, and mathematical utilities directly between the robot codebase and the desktop application via Gradle module dependencies. |
| **Secure Cloud Gateway** | Kotlin, Ktor, Cloud Run | Broker short-lived Google Cloud Storage (GCS) Pre-Signed URLs, execute upstream token handshakes, evaluate GitHub organization lists, and orchestrate Vertex AI prompts. |
| **Analytics Storage** | BigQuery, Storage Write API | Ingest high-throughput flattened logging matrices using high-performance gRPC streaming. Partition data blocks explicitly by day indices and cluster tables by unified tenant metadata components. |
| **Operational NoSQL** | Cloud Firestore | Host system operational states, configuration templates, synchronized multi-client summary collections, user profiles, and interactive AI chat histories. |

### 2.2 League-Agnostic Structural Layer Abstractions
* **Telemetry Ingestion:** Unified NT4 WebSockets via Ktor WebSocket Client (RoboRIO Engine / ARESLib Node)
* **Project Compilation:** Local OS Child Process Branching (Local Gradle vs. Android Gradle Toolchains) via Kotlin `ProcessBuilder` with coroutine wrappers
* **Field Space Mappings:** Dimension Resolution Matrix (32' x 59' FRC Grid vs. 12' x 12' FTC Coordinate Space) — loaded from `RobotFieldConfig` JSON matching active `season_id`
* **Robot Connection Auto-Discovery:** Smart connection defaults by league: FTC Control Hub at `192.168.43.1:5810`, FRC RoboRIO at `10.TE.AM.2:5810`, with manual override for custom setups

### 2.3 Unified Kotlin Gradle Project Structure
```
ARES-Analytics/
├── app/                          # Compose Multiplatform desktop application
│   ├── src/main/kotlin/
│   │   ├── ui/                   # Compose UI layer (screens, components, theme)
│   │   │   ├── theme/            # Material 3 theme, color system, typography
│   │   │   ├── screens/          # Top-level screen composables
│   │   │   ├── components/       # Reusable UI components
│   │   │   └── widgets/          # Dashboard widget system
│   │   ├── viewmodel/            # MVI ViewModels (StateFlow + Intent)
│   │   ├── service/              # Business logic services (analytics, telemetry, database)
│   │   ├── di/                   # Dependency injection configuration
│   │   └── Main.kt              # Application entry point
│   └── build.gradle.kts
├── gateway/                      # Ktor Cloud Run server
│   ├── src/main/kotlin/
│   │   ├── routes/               # HTTP route handlers
│   │   ├── auth/                 # Firebase + GitHub auth
│   │   └── Application.kt       # Ktor entry point
│   ├── Dockerfile
│   └── build.gradle.kts
├── shared/                       # Shared types, math, serialization, topology models
│   ├── src/main/kotlin/
│   └── build.gradle.kts
├── areslib-kotlin/               # Git submodule — robot code
├── build.gradle.kts              # Root build file
├── settings.gradle.kts
└── ARCHITECTURE.md
```

### 2.4 Key Dependencies
```kotlin
// app/build.gradle.kts
dependencies {
    implementation(compose.desktop.currentOs)
    implementation(project(":shared"))
    // Database
    implementation("org.xerial:sqlite-jdbc:3.46.0")
    implementation("app.cash.sqldelight:sqlite-driver:2.0.2")
    // Networking
    implementation("io.ktor:ktor-client-cio:3.0.0")
    implementation("io.ktor:ktor-client-websockets:3.0.0")
    implementation("io.ktor:ktor-client-content-negotiation:3.0.0")
    implementation("io.ktor:ktor-server-core:3.0.0")       // Embedded OAuth loopback
    implementation("io.ktor:ktor-server-cio:3.0.0")
    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    // Math & Signal Processing
    implementation("org.ejml:ejml-simple:0.43.1")           // Linear algebra, OLS, L-M solver
    implementation("org.apache.commons:commons-math3:3.6.1") // FFT, statistics
    // Parquet export
    implementation("org.apache.parquet:parquet-avro:1.14.1")
    implementation("org.apache.hadoop:hadoop-common:3.4.0")
    // Firebase client
    implementation("com.google.firebase:firebase-admin:9.3.0")
    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.6")
}

// gateway/build.gradle.kts
dependencies {
    implementation(project(":shared"))
    implementation("io.ktor:ktor-server-core:3.0.0")
    implementation("io.ktor:ktor-server-netty:3.0.0")
    implementation("io.ktor:ktor-server-content-negotiation:3.0.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.0")
    implementation("com.google.firebase:firebase-admin:9.3.0")
    implementation("com.google.cloud:google-cloud-storage:2.40.0")
    implementation("com.google.cloud:google-cloud-firestore:3.21.0")
    implementation("com.google.cloud:google-cloud-vertexai:1.5.0")
}
```

### 2.5 State Management Architecture
The application follows a strict **MVI (Model-View-Intent)** pattern:
* Every screen gets a `ViewModel` that exposes a single `StateFlow<ScreenState>` sealed class.
* User interactions emit sealed `Intent` classes consumed by the ViewModel.
* Business logic services are constructor-injected into ViewModels, never accessed directly from composables.
* Side effects (database writes, network calls, process spawning) execute in `viewModelScope` on `Dispatchers.IO`.
* Compose UI observes `StateFlow` via `collectAsState()` — purely declarative, no imperative UI mutations.

### 2.6 Application Logging
All application-level logging uses SLF4J with Logback:
* Structured log output to `~/.ares-analytics/logs/ares-analytics.log`
* Rolling file appender (10MB max, 5 backups)
* Log levels: `ERROR` for unrecoverable failures, `WARN` for degraded operation, `INFO` for lifecycle events, `DEBUG` for diagnostic tracing
* Crash handler writes unhandled exceptions to `crash-<timestamp>.log` for post-mortem analysis during tournaments

---

## 3. Data Protocols & Ingestion Pipelines

### 3.1 Two-Tier Data Strategy
The system maintains two distinct data tiers with clear separation of concerns:

| Tier | Storage | Contents | Lifecycle | Purpose |
| :--- | :--- | :--- | :--- | :--- |
| **Local Telemetry Frames** | SQLite (WAL mode) | Individual `TelemetryFrame(timestampMs, sessionId, key, value)` records captured from the live NT4 stream | Retained locally until the linked log file is confirmed synced to cloud; then eligible for automatic purge | **Instant replay** — scrub through a session the moment it ends without waiting for file import |
| **Canonical Log Files** | Disk → Cloud (GCS) | Robot/simulator-produced log files: `.wpilog` (WPILib DataLogManager), ARES `.csv` (ARESDataLogger), `.hoot` (CTRE Phoenix 6) | Permanent — the authoritative record of every session | **Cloud backup & re-import** — can regenerate local frames on any machine at any time |

#### Session–Log File Linkage
The robot or simulator publishes its active log file path as an NT4 topic:
* **Topic:** `ARES/Session/LogFilePath` (string)
* **Published:** Once at the start of each logging session by `TelemetryPublisher` (simulator) or `ARESNetworkStatePublisher` (on-robot)
* **Captured by:** `Nt4ClientService.dispatchValue()` — stored in the `Session` database record alongside the `sessionId`

This creates a direct mapping: `sessionId` → `logFilePath`, enabling the cloud sync engine to locate and upload the correct file, and enabling the local cleanup engine to know when local frames can be safely purged.

### 3.2 Structural Data Flow Architecture
```
┌──────────────┐       NT4 WebSocket        ┌──────────────────────────┐
│  ROBOT / SIM │ ──────────────────────────► │  LAPTOP A (COMPOSE APP)  │
│              │  Telemetry + LogFilePath    │                          │
│  Writes:     │                            │  1. Live NT4 frames      │
│  .wpilog     │                            │     → SQLite (instant    │
│  .csv        │                            │       replay)            │
│  .hoot       │                            │  2. Session record links │
└──────┬───────┘                            │     sessionId → logFile  │
       │                                    │  3. Summary metrics      │
       │  Log file on disk                  │     computed edge-first  │
       ▼                                    └────────────┬─────────────┘
┌──────────────┐                                         │
│  Cloud (GCS) │ ◄───── Log file upload ─────────────────┘
│  + Firestore │ ◄───── Summary JSON ────────────────────┘
└──────┬───────┘
       │  Delta sync (summaries only, no raw frames)
       ▼
┌──────────────────────────┐
│  LAPTOP B (COMPOSE APP)  │
│  Pulls summaries from    │
│  Firestore; re-imports   │
│  log files from GCS to   │
│  regenerate local frames │
└──────────────────────────┘
```

### 3.3 Real-Time Streaming & Stateful Latching Pipeline
1. The application launches a background Kotlin coroutine using `CoroutineScope(Dispatchers.IO)` to establish a persistent NT4 WebSocket client connection via the Ktor WebSocket client.
2. **NT4 Key Normalization Convention:** All incoming and outgoing NT4 topic names are stripped of leading `/` characters (e.g. at the source in `Nt4ClientService.dispatchValue()` and during client topic publication) to ensure a single canonical key format (e.g. `ARES/Input/vx`, `Drive/Pose_X`, not `/ARES/Input/vx`, `/Drive/Pose_X`) across live telemetry, replay frames, database storage, and widget matching. This avoids duplicate topic registration on the C++ `ntcore` server, which treats keys with and without leading slashes as distinct.
3. Inbound telemetry streams are filtered against immediate safety rules defined in a local, active `thresholds.json` profile.
4. Detected anomalies invoke a Stateful UI Latching Mechanism:
   * **Active Invariant:** While a signal breaks a boundary, the dashboard ticker triggers a persistent flashing warning via Compose `animateColorAsState`. An audible alert tone plays through `javax.sound.sampled` to notify pit crew not actively watching the screen.
   * **Transient Capture Latch:** When a signal recovers, the alert transitions to a static amber latch tracking the exact starting timestamp and duration metrics.
   * **Visual Triage vs. Data Retention:** Clicking "Clear Panel" toggles an isolated visual state flag (`triaged = 1`) within the UI state. The underlying raw telemetry, alert records, and time-stamped tags are structurally locked inside the local database to preserve diagnostic integrity.
5. All real-time telemetry elements are spooled locally into a high-performance SQLite database configured in Write-Ahead Logging (WAL) mode via SQLDelight.

### 3.4 Post-Run Log Archival & Multi-Client Sync
1. Upon run termination, the user imports raw metrics (FRC `.wpilog` binary arrays or FTC text traces), or the application auto-locates the log file via the `ARES/Session/LogFilePath` linkage.
2. The application parses the structures using Kotlin I/O streams, flattens relational lines, and exports a compressed Apache Parquet columnar data file locally using the Apache Parquet Java SDK.
3. **The Edge-First Extraction Loop:** Before running cloud communications, the application runs single-pass mathematical parsing routines over the raw log array using Kotlin coroutines, calculating high-level performance signatures and writing the indices to a local `session_summaries` table.
4. **Cloud Archival Handshake:**
   * The app authenticates against the Ktor gateway via a Firebase ID token, passing the pre-computed summary JSON payload.
   * The Ktor gateway writes the pre-computed summary map directly to Firestore under the team's account node.
   * The gateway returns a secure GCS Pre-Signed URL.
   * The application performs a raw chunked HTTP PUT request (Ktor HTTP client) to stream the **canonical log file** (`.wpilog`, `.csv`, or `.hoot`) straight to Google Cloud Storage. Raw telemetry frames from SQLite are **never** uploaded — only the compact, canonical log file.
5. **Local Frame Cleanup:** Once the log file upload is confirmed (HTTP 200 from GCS), local `TelemetryFrame` rows for that session are eligible for automatic purge. The cleanup runs on a background coroutine, freeing SQLite storage while the canonical log file remains safely in the cloud.
6. **Multi-Client Desktop Delta Sync Engine:** On boot or project switch, the client sends an array of its known `session_id` keys to the Ktor gateway. The gateway matches the list against Firestore, extracts missing documents, and down-syncs a compressed JSON array containing the missing records. To reconstruct telemetry frames, the client downloads the canonical log file from GCS and re-imports it via `LogParserService` / `HootDecoderService`.

### 3.5 Session Annotations & Tagging
* Sessions support free-form text annotations (notes, observations, experiment descriptions) stored in a `session_annotations` SQLite table.
* Custom tags (`#autonomous`, `#quals`, `#practice`, `#calibration`, `#broken-intake`) enable filtering and grouping across sessions.
* Annotations and tags sync to Firestore alongside session summaries for multi-client access.

### 3.6 ADB Device Management & Logcat Streaming
* **ADB Connection Management:** The application manages Android Debug Bridge connections for FTC deployment workflows:
  * Auto-connect to Control Hub at `192.168.43.1:5555` when FTC league is selected.
  * Connection status displayed in the sidebar alongside NT4 status.
  * `adb install -r` for APK deployment after successful Gradle builds.
  * `adb kill-server` / `adb start-server` auto-retry on connection failures.
* **Live Logcat Streaming:** A parallel `adb logcat` process streams Android runtime logs into a dedicated tab within the terminal drawer, filtered by the `TeamCode` process ID. This runs alongside NT4 telemetry to provide full-stack debugging visibility.

### 3.7 CTRE Phoenix 6 Hoot Log Ingestion & Post-Processing
* **Hoot log decoding:** The application decodes CTRE `.hoot` logs into the SQLite database. It auto-discovers AdvantageScope's downloaded `owlet` CLI binaries (checking AppData directories on Windows, Library/Application Support on macOS, `.config` or `.ctre` folders on Linux) and spawns it in a background process (`owlet hootPath tempCsvPath -f csv`).
* **Low-memory parsing:** The output CSV is parsed line-by-line using a `BufferedReader` and written to the SQLite database in transaction batches of 5000 telemetry frames to keep heap allocations constant.
* **Post-processing diagnostic sweeps:** Once ingestion completes, a diagnostic pipeline is automatically executed:
  1. **OLS Characterization (SysId):** Solves ordinary least squares ($V = kS \cdot \text{sgn}(v) + kV \cdot v + kA \cdot a$) across motor voltage, velocity, and acceleration values. If acceleration is not logged, it is approximated as the numerical derivative of velocity ($\Delta v / \Delta t$). Gains and $R^2$ are saved as session annotations.
  2. **PID/Backlash Audit:** Computes tracking error ($e = r - y$) for matched setpoint/actual signal pairs and applies a Fast Fourier Transform (FFT) to isolate dominant frequencies indicating PID tuning hunting or mechanical backlash oscillations.
  3. **Stall & Thermal Check:** Calculates thermal load ($\int I_{\text{stator}}^2 \cdot R \cdot dt$) for drive motors (assuming $R = 0.05\,\Omega$). It also checks for high stator currents ($>40\,\text{A}$) paired with zero velocity ($<0.1\,\text{rps}$) lasting $\ge 500\,\text{ms}$ to flag mechanical stalls.
  4. **CAN Jitter Analysis:** Measures the standard deviation of frame-to-frame timestamp deltas for periodic messages. High standard deviation ($>8\,\text{ms}$) signals potential CAN bus loading or frame drops.

---

## 4. Local Simulation & Deployment Subsystem

### 4.1 Onboarding & Environment Guardrails
* **Workspace Setup:** If no cached configuration exists in the local preferences store (`java.util.prefs.Preferences` or a local JSON config file), the UI forces a blocking initialization overlay demanding explicit path selections for project workspaces and active tenant markers (`team_id`, `season_id`, `robot_id`).
* **Environment Verification:** The application fires an async coroutine executing a shell command validating a functional Java Development Kit configuration (`JAVA_HOME` validation). Access to execution dashboards remains strictly locked until environment verification returns a valid system exit code.

### 4.2 Process Management & Terminal Streaming
* **Compilation Branching:** Triggering project runs commands the application to fork non-blocking OS child processes via Kotlin `ProcessBuilder` wrapped in `Dispatchers.IO` coroutines, tailored to target configurations:
  * FRC Configuration: Executes the local workspace wrapper: `./gradlew simulateJava` (POSIX) or `gradlew.bat simulateJava` (Windows).
  * FTC Configuration: Compiles and targets physical device layouts: `./gradlew :TeamCode:installDebug`.
* **Output Streaming Terminal View:** Standard `stdout` and `stderr` handles are actively piped into Kotlin coroutine `Flow` streams and collected by a Compose Canvas-based terminal view component with ANSI color code parsing. The terminal panel drawer animates into view (`AnimatedVisibility`) if a child compilation process outputs a non-zero exit code or emits a known syntax error footprint string.
* **Keyboard Shortcuts:** `Ctrl+B` triggers build, `Ctrl+D` deploys to device, `Ctrl+K` kills running process, `Escape` dismisses the terminal drawer. Shortcuts are registered via Compose `onPreviewKeyEvent` modifiers.

### 4.3 Headless Simulation & Dashboard-Driven Control
* When the simulator is launched from the dashboard (via `gradlew.bat :simulator:run -PappArgs=--headless`), it starts in **teleop mode by default**.
* **Teleop Mode (default):** The dashboard's keyboard/joystick widget captures WASD/QE inputs and publishes them as NT4 topics (`ARES/Input/vx`, `ARES/Input/vy`, `ARES/Input/omega`). The simulator subscribes to these topics and feeds them into the OpMode's gamepad fields, driving the physics simulation in real time.
* **Auto Mode (on demand):** A **"Run Auto"** toggle button on the dashboard toolbar publishes `ARES/Input/isTeleopMode = false` via NT4. The simulator switches to autonomous path-following mode using a `HolonomicDriveController` with PID controllers. Toggling back publishes `true` to return to teleop.
* **Bidirectional NT4 Control Topics:**

| Dashboard → Simulator (Published) | Type | Purpose |
| :--- | :--- | :--- |
| `ARES/Input/vx` | double | Forward/back drive velocity |
| `ARES/Input/vy` | double | Strafe velocity |
| `ARES/Input/omega` | double | Rotational velocity |
| `ARES/Input/isTeleopMode` | boolean | Teleop/auto mode toggle |
| `ARES/Input/isFieldCentric` | boolean | Field-centric drive toggle |
| `ARES/Input/isIntaking` | boolean | Intake control |
| `ARES/Input/isFlywheelOn` | boolean | Flywheel control |
| `ARES/Input/isTransferring` | boolean | Transfer control |
| `ARES/Input/isRedAlliance` | boolean | Alliance color |
| `ARES/Input/heartbeat` | integer | Connection heartbeat |
| `ARES/Input/obstacles` | string | Dynamic obstacle JSON |

### 4.4 Code-Mutation Log Replay Execution
* Selecting a "Deterministic Log Replay" run instructs the application to pair parameters written via the Constants Tuning Subsystem with an explicit historical log file selection.
* The application kicks off the Gradle wrapper pipeline, injecting environment flags specifying lockstep replay ingestion parameters (e.g., routing target log paths as execution properties to your custom Kotlin simulation loops).
* Mutated output data arrays generated by the modified software parameters loop back via network sockets straight onto the dashboard layers to handle side-by-side behavioral modeling.

---

## 5. 2D Path Planning, Landmarks & Field Obstacle Designer

### 5.1 Spline Trajectory Computation & Serialization (v2025.0 Format)
* The Compose Desktop application builds an interactive, GPU-accelerated 2D Field Canvas using the Compose `Canvas` composable with Skia rendering, mapping relative Cartesian units over an adjustable coordinate space grid.
* Waypoint selections configure geometric anchors used to compute Cubic or Quintic Hermite Spline lines directly within the shared Kotlin math module — reusing the same spline algorithms available to the robot codebase.
* Drivetrain kinematic limits (maximum structural velocity, target angular acceleration thresholds) filter spline paths to construct localized time-series trajectory vectors.
* **Standardized PathPlanner Serialization:** Serializer modules import and export paths using the PathPlanner `.path` (JSON v2025.0) format schema containing anchor points, control vectors (bezier tangents), event markers, global constraints, ideal starting states, goal end states, rotation targets, constraints zones, and point-towards zones. Path files are saved into project asset directories matching active target structures using `kotlinx.serialization`:
  * FRC Targets: `/src/main/deploy/pathplanner/paths/[name].path`
  * FTC Targets: `/src/main/assets/pathplanner/paths/[name].path`
* **Bezier Tangent & Heading Reconciliation:** Waypoint orientation is stored in screen space and coordinate matrices as standard CCW-positive radians ($\theta$). When exporting to the PathPlanner format, control points are computed via:
  * $nextControl = anchor + (\cos(\theta), \sin(\theta)) \times 0.5$
  * $prevControl = anchor - (\cos(\theta), \sin(\theta)) \times 0.5$
  * Where the first waypoint has `prevControl = null` and the last waypoint has `nextControl = null`. Upon file ingestion, waypoint headings are reconstructed from these control point vectors using $\theta = \text{atan2}(dy, dx)$.

### 5.2 Interactive Waypoint & Event Marker Control Subsystem
* **Interactive Canvas Gestures:** The editor canvas locks the field background (disabling canvas panning) to ensure stable point manipulations. Waypoints can be dragged by their anchors or adjusted by clicking and dragging their direction handle dots to rotate their headings.
* **Numerical Sidebar Tuning:** High-fidelity numerical text fields in the editor sidebar allow real-time manual updates of coordinates (X, Y) and heading angles (degrees converted to/from radians dynamically). To prevent focus-loss or cursor jumping, local inputs use key-latching snapshot updates.
* **Spline-Relative Event Markers:** Users can define discrete path actions (e.g. starting intake, launching game pieces) by registering named event markers. The markers are positioned along the spline via a segment-relative fractional progress value ($waypointRelativePos$) where the integer part represents the segment index and the fractional part denotes the progress along that specific segment (e.g. $1.5$ is halfway between waypoint 1 and 2). Markers are rendered in real time on the canvas as purple nodes with visual cores and glowing outer rings.

### 5.3 Obstacle Designing & Real-Time Sensor Emulation
* The canvas workspace supports drawing custom polygon, circular, and rectangular vector zones indicating structural obstacles, alongside point vectors mapping specific AprilTag fiducial positioning coordinates relative to official yearly field blueprints loaded from `RobotFieldConfig`.
* **Simulation Raycasting Subsystem:** During local simulation execution, the application utilizes the active vector obstacle layout to compute rigid-body collision shapes while tracking line-of-sight intersection vectors targeting AprilTag centers.
* **Vision Noise Emulation:** The application generates simulated camera packet streams, injecting customizable Gaussian distribution tracking noise and synthetic network transport latencies before routing variables back into the running robot codebase via loopback sockets to model accurate vision processing stability.

### 5.4 Autonomous Trajectory Overlay
* During log replay or post-match analysis, the field canvas overlays the **planned autonomous path** (loaded from the project's serialized path files) against the **actual driven trajectory** (reconstructed from recorded odometry/EKF pose data).
* Deviation vectors are drawn at configurable intervals showing the perpendicular distance between planned and actual position, color-coded by magnitude (green < 2cm, yellow < 5cm, red > 5cm).
* A synchronized timeline cursor links the trajectory overlay to the replay scrubber, highlighting the robot's exact position at the selected timestamp on both paths.

### 5.5 Advanced Trajectory Tuning: Rotation Targets, Constraint & Aiming Zones
* **Holonomic Rotation Targets:** Rather than coupling heading to spline tangent, the system supports decoupled holonomic rotation targets. Each target defines a target heading at a spline-relative fractional progress position ($waypointRelativePos$). On the Field Canvas, these are rendered as oriented bounding boxes (e.g. 24.dp square) rotating in CCW-positive degrees with a cyan front-bumper indicator showing the robot's target facing direction.
* **Point-Towards (Aiming) Zones:** Defines path segments ($minWaypointRelativePos$ to $maxWaypointRelativePos$) where the robot must align its heading toward a stationary field coordinate target ($fieldPosition$). The canvas renders these aiming zones by drawing a cyan crosshair at the target coordinate and dashed cyan alignment rays from multiple sample points along the spline segment.
* **Localized Constraints Zones:** Spline segments with customized kinematic limits (max velocity and acceleration). The canvas highlights active constraint zones using thick, semi-transparent red dashed halos drawn directly over the planned path.
* **Kinematic Start/End States:** Starting and ending velocities (M/S) and headings (degrees) are configurable. A trapezoidal velocity profiler (`TrajectoryEstimator`) samples the spline, calculates curvature radius at each sample point, computes centripetal limits ($v_{\max} = \sqrt{a_{\max} \cdot R}$), integrates local constraint overrides, and performs forward-backward sweeps to estimate the total path execution duration.

---

## 6. Unified Dashboard: Live Telemetry & Instant Replay

The Dashboard is the single, unified interface for both **live telemetry** and **session replay**. There is no separate replay screen — the same widgets, same layout, and same field canvas operate in both modes.

### 6.1 Dashboard Modes
| Mode | Trigger | Data Source | Timeline Scrubber |
| :--- | :--- | :--- | :--- |
| **Live** | NT4 connection active, no session selected | `Nt4ClientService.telemetryFlow` (real-time NT4 WebSocket frames) | Visible (100% progress, green "LIVE" badge) |
| **Live Rewind** | User scrubs or pauses a live stream | `ReplayEngineService` loaded with `"live-telemetry"` database session | Visible (allows scrubbing captured live history, shows "Snap to Realtime" button) |
| **Replay** | User selects a recorded session from the Runs Index | `ReplayEngineService` → emits stored `TelemetryFrame` rows into the same `telemetryFlow` | Visible (allows full playback/seeking controls for historical logs) |

Switching between modes is seamless: selecting a session transitions to replay mode; closing/deselecting or clicking "Snap to Realtime" returns to live mode.

### 6.2 Live Telemetry Auto-Recording & Pruning
To support the rewinding capability during live sessions:
* **Background Recording:** All incoming live telemetry frames are automatically assigned the `"live-telemetry"` session ID and written to the SQLite database via a high-performance background spooling thread.
* **Cache Pruning:** To prevent local database bloat, a pruning task automatically deletes any frames older than 5 minutes (300,000 ms) from the `"live-telemetry"` session during periodic database flushes.
* **Session Lifecycle:** The `"live-telemetry"` session tables are fully cleared whenever a new host connection starts to ensure a clean state.

### 6.3 Replay Engine (`ReplayEngineService`)
* **Session Loading:** `loadSession(sessionId)` reads all `TelemetryFrame` rows for the session (historical log or `"live-telemetry"`) from SQLite, builds a sorted timestamp index, and sets the playhead to the start.
* **Frame Emission:** `updateFrameAtPlayhead()` aggregates all key/value pairs up to the current playhead timestamp into a `ReplayFrame(timestampMs, values: Map<String, Double>)`. Each key/value pair is then emitted as an individual `TelemetryFrame` into the same `telemetryFlow` that live telemetry uses — ensuring all dashboard widgets (FieldViewerCard, TelemetryChartPanel, MecanumVisualizer, etc.) work identically in both modes with zero widget-level changes.
* **Live Suspended State:** When the Replay Engine is active, the `Nt4ClientService` sets `isReplayActive = true`. This prevents live websocket incoming updates from emitting to `_telemetryFlow` (avoiding display flickering) while still spooling them to the database in the background.
* **Playback Controls:** Play, Pause, Stop, Step Forward, Step Backward, Speed Scaling (`0.5x`, `1x`, `2x`, `4x`), and percentage-based Scrub.
* **Timing:** The playback loop runs at 50fps (`delay(20)`), advancing the playhead by `deltaRealTime * speedMultiplier` each tick.
* **Binary Search Seeking:** `scrubTo(percentage)` computes the target timestamp and uses `binarySearch` over the timestamp index for O(log n) seeking.

### 6.4 Timeline Scrubber Bar
The timeline scrubber bar is visible at the bottom of the Dashboard whenever a connection is active or a session is loaded:
* **Scrub Slider:** Draggable progress bar (0% → 100% of session duration)
* **Play / Pause Button:** Toggles real-time playback
* **Stop / Go Live Button:** In live mode, acts as a quick "Snap to Realtime" action. In historical replay mode, stops playback.
* **Step Buttons:** Frame-by-frame forward/backward navigation (disabled during active live stream)
* **Speed Selector:** `0.5×`, `1×`, `2×`, `4×` playback speed
* **LIVE Badge:** A green pulsating indicator showing that the dashboard is displaying the live real-time edge.
* **Snap to Realtime Button:** A cyan button with a lightning bolt icon that appears when the user is currently rewinding a live stream, allowing them to instantly return to real-time telemetry.

### 6.5 Local Loopback Emulation
* During active playback, the `ReplayEngineService` re-broadcasts decoded telemetry frames over a local UDP loopback port (`127.0.0.1:5802`) using Kotlin `DatagramSocket`. This allows auxiliary desktop debugging toolsets (e.g., AdvantageScope) to hook directly into the replay stream and display log animations simultaneously.

### 6.5 Dashboard Widgets in Both Modes
All dashboard widgets consume from the same `telemetryFlow` and operate identically in both live and replay modes:
  1. **Field 2D Viewer (`FieldViewerCard`):** Renders the robot's position on the field canvas. In replay mode, the full driven path is overlaid.
  2. **Telemetry Chart (`TelemetryChartPanel`):** Plots user-selected channels over time. In replay mode, the time window follows the playhead.
  3. **Mecanum Visualizer:** Shows wheel velocities/powers.
  4. **Swerve Module Visualizer:** Displays steering angles and velocity vectors for swerve drivetrains.
  5. **Joystick Visualizer:** Shows analog stick deflections, trigger levels, and button states.
  6. **Mechanism Visualizer:** Draws arm angles and slide extensions.
  7. **Console Viewer:** Displays log/console messages.

---

## 7. Post-Match System Identification & Control Loop Diagnostics Pipeline

### 7.1 Multivariable Ordinary Least Squares (OLS) Characterization Engine
During edge processing passes, the application executes an isolated multivariable OLS regression routine using EJML (Efficient Java Matrix Library) across filtered velocity records to passively extract physical system matrices. The calculator matches target telemetry arrays to the standard DC motor characterization profile:

Voltage = kS * sgn(v) + kV * v + kA * a

To ensure calculations represent clean structural variables, the data analyzer isolates variables via a strict Data Filter Matrix:

| Target Extraction Parameter | Kinematic Constraint Gate | Filter Operations |
| :--- | :--- | :--- |
| **kS (Static Friction Voltage)** | Velocity Transition Window | Isolate frames where velocity steps away from zero (v -> 0) under low torque inputs to calculate the breakdown voltage. |
| **kV (Velocity Constant)** | Acceleration Deadband | Isolate continuous log blocks where acceleration variance approaches zero (a = 0) to map steady-state voltage scaling. |
| **kA (Acceleration Constant)** | High Acceleration Step | Isolate data rows exhibiting high-rate transient voltage step changes to map inertial mass scaling values. |

> **Data Cleansing Invariant:** The OLS engine drops all rows containing directional joystick changes within a +/- 50ms window to eliminate electrical signal noise from manual operator adjustments.

### 7.2 Closed-Loop Transient Response Evaluation
The application evaluates feedback loop tracking arrays by contrasting commanded tracking targets against localized encoder registers over the running timeline: Error(t) = Setpoint(t) - Actual(t).
* **Transient Profile Classification:** System channels analyze loop damping behavior, assigning performance flags within the summary dataset:
  * **Underdamped:** Identified by tracking continuous peak overshoots and evaluating settling decay metrics across cycles.
  * **Overdamped:** Flagged when rise times fail to intersect target tracking boundaries within default loop latency thresholds.
  * **Critically Damped:** Mapped when rise duration is systematically compressed without crossing overshoot boundaries.
* **Spectral Oscillation Analysis:** The application applies a Fast Fourier Transform (FFT) via Apache Commons Math over the error signal during closed-loop steady-state windows. If power spectral density peaks emerge above threshold frequencies, the app logs a targeted mechanical anomaly tracking flag to identify backlashed gears, loose belts, or over-tuned feedback loop gains.

### 7.3 Driver Input Performance Optimization
* The application intercepts high-frequency driver input channels (x, y, omega) recorded during match operations.
* The system executes a high-resolution FFT sweep across the controller inputs to isolate high-amplitude user command oscillations inside the 8Hz to 12Hz physiological boundary (identifying tracking over-correction or manual joystick panic jitter).
* The application automatically computes custom compensation profiles (such as optimized exponential deadband curves or input slew-rate constraints) designed to damp out the driver's specific jitter signature. These adjustments are exported as named **driver profiles**, allowing multiple drivers to maintain separate compensation configurations.
* Driver profiles are exposed to the Constants Tuning Subsystem for immediate optimization injection.

---

## 8. Automated Vision Extrinsic Calibration Optimization

### 8.1 Rotation Pass Configuration Rig
* To reconcile physical camera placement alignments, the robot codebase exposes a standardized execution function (`VisionExtrinsicCalibrationCommand`) handled directly inside `ARESLib`.
* When deployed, the command zeros the localized tracking orientation baseline and commands a smooth, controlled 360-degree rotation sweep. The rotation must remain entirely isolated from spatial Cartesian translation vectors (vx = 0, vy = 0).
* **Active Anti-Drift Subsystem:** To prevent physical translation drift caused by structural weight asymmetries or traction variances across mecanum rollers or swerve modules, `ARESLib` continuously intercepts data from the goBILDA Pinpoint Odometry Computer at 1,500Hz. Any measured linear velocity component deviating from zero feeds straight into a tight translation correction loop, injecting immediate counter-strafe adjustments to keep the center of rotation locked to a single point.
* The robot logs high-frequency tracking parameters over network keys: the raw gyroscopic heading, observed AprilTag target index mappings, and the relative camera-to-tag translation transformation matrix.

### 8.2 Multi-Camera Non-Linear Optimization Orbit Solver
1. Upon log archival processing, the application isolates calibration runs and pulls the associated field anchor coordinates out of the official yearly AprilTag Field Map layout template (`RobotFieldConfig.apriltags`) matching the active `season_id`.
2. **Per-Camera Extrinsic Solving:** The solver handles multiple cameras independently. Each camera is identified by its `cameraIndex` field in the calibration telemetry. Robots running 2–4 cameras (multiple Limelights, webcams) produce separate extrinsic solutions per camera, stored as distinct `Pose3d` constants.
3. For every logged tracking instance per camera, the application builds a global transformation loop predicting spatial target coordinate placement vectors based on nominal settings:
   
   T_world_to_tag_calc = T_world_to_robot(theta_gyro) * T_robot_to_camera_nominal[i] * Delta_T_calibration[i] * T_camera_to_tag

4. The application feeds the transformation vectors into a Levenberg-Marquardt non-linear least-squares optimization block implemented via EJML. The solver treats the six-degree-of-freedom physical camera displacement metrics (dx, dy, dz, d_roll, d_pitch, d_yaw) inside Delta_T_calibration as open parameters, converging on the exact matrix tweaks that minimize tracking errors:
   
   minimize Sum(|| p_field_map - p_calc_i(Delta_T_calibration) ||^2)

5. The resolved translation and angular orientation errors are exported as production-ready configuration profiles, directly serializable into ARESLib-compatible `Pose3d` constants via `kotlinx.serialization`.

---

## 9. Hardware Topology & Intelligent Diagnostics

### 9.1 Robot-Sourced Hardware Topology
The hardware topology is extracted **directly from the robot codebase** at runtime, not manually defined in the dashboard. This leverages ARESLib's existing self-registering `HardwareRegistry` pattern.

#### 9.1.1 ARESLib Extensions (Robot Side)
The following additions are made to the `com.areslib.hardware` package:

* **`TopologyNode` data class** — Serializable descriptor for a single hardware component:
  ```kotlin
  @Serializable
  data class TopologyNode(
      val id: String,               // e.g., "frontLeftMotor"
      val type: TopologyNodeType,   // CONTROL_HUB, MOTOR, SERVO, SENSOR, CAMERA, ODOMETRY
      val displayName: String,      // "Front Left Motor"
      val parentId: String?,        // What hub/bus this device is connected to
      val port: Int?,               // FTC: physical port number on parent hub
      val canId: Int?,              // FRC: CAN device ID (e.g., 1, 2, 10)
      val canBus: String?,          // FRC: CAN bus name ("rio", "canivore", etc.)
      val busPosition: Int?,        // FRC: physical position on the CAN daisy chain (MANUALLY SET)
      val connectionType: String?,  // "pwm", "i2c", "usb", "analog", "digital", "can", "ethernet"
      val metadata: Map<String, String> = emptyMap()
  )
  ```

* **`TopologyNodeType` enum** — `CONTROL_HUB`, `EXPANSION_HUB`, `SRS_HUB`, `ROBORIO`, `CANIVORE`, `CAN_MOTOR_CONTROLLER`, `MOTOR`, `SERVO`, `CAMERA`, `ODOMETRY_COMPUTER`, `IMU`, `COLOR_SENSOR`, `DISTANCE_SENSOR`, `BEAM_BREAK`, `ANALOG_SENSOR`, `CAN_CODER`, `PIGEON_IMU`, `POWER_DISTRIBUTION`

* **`HardwareTopology` data class** — The full serializable topology graph:
  ```kotlin
  @Serializable
  data class HardwareTopology(
      val robotId: String,
      val nodes: List<TopologyNode>
  )
  ```

* **`HardwareRegistry` extensions** — Overloaded registration methods accept optional topology metadata:
  ```kotlin
  // FTC: hub + port model
  fun registerMotor(name: String, motor: MotorIO, parentHub: String, port: Int)
  fun registerServo(name: String, servo: ServoIO, parentHub: String, port: Int)

  // FRC: CAN bus model — busPosition is the physical daisy-chain order (manually set by the team)
  fun registerMotor(name: String, motor: MotorIO, canBus: String, canId: Int, busPosition: Int? = null)
  fun registerDevice(name: String, device: LoggableDevice, canBus: String, canId: Int, busPosition: Int? = null)

  // Generic
  fun registerDevice(name: String, device: LoggableDevice, topology: TopologyNode)
  fun buildTopology(robotId: String): HardwareTopology
  ```

* **CAN Bus Ordering Convention:** CAN is a broadcast protocol — physical wiring order is invisible to software. Teams annotate `busPosition` in robot code based on actual wiring:
  ```kotlin
  // Physical daisy chain: RoboRIO → TalonFX(1) → TalonFX(2) → CANCoder(3) → TalonFX(4)
  HardwareRegistry.registerMotor("frontLeft", fl, canBus = "canivore", canId = 1, busPosition = 1)
  HardwareRegistry.registerMotor("frontRight", fr, canBus = "canivore", canId = 2, busPosition = 2)
  HardwareRegistry.registerDevice("flEncoder", enc, canBus = "canivore", canId = 3, busPosition = 3)
  HardwareRegistry.registerMotor("backLeft", bl, canBus = "canivore", canId = 4, busPosition = 4)
  ```
  If `busPosition` is omitted, devices are shown grouped by CAN bus without ordering and a dashboard warning prompts annotation.

* **NT4 broadcast** — On robot initialization (once, not per-loop), `ARESNetworkStatePublisher` calls `HardwareRegistry.buildTopology()` and publishes the serialized JSON to `Topology/HardwareMap`. The dashboard receives this automatically alongside regular telemetry.

#### 9.1.2 Dashboard Rendering (Desktop Side)
* The Compose application listens for the `Topology/HardwareMap` NT4 key on connection.
* The topology graph is rendered as an interactive node diagram via Compose `Canvas` with two rendering modes:
  * **FTC (Hub-Port Tree):** Hub nodes (Control Hub, Expansion Hub, SRS Hub) rendered as large rectangles with port indicators. Device nodes rendered as smaller shapes connected by directional lines to their parent hub/port. Connection type labels on edges (PWM, I2C, USB, analog).
  * **FRC (CAN Bus Chain):** CAN bus rendered as a horizontal linear chain ordered by `busPosition`. Each device node shows CAN ID and device type. When the AI forensics engine reports a `failed_node_id`, the dashboard highlights that node **and all devices downstream** on the chain as "potentially disconnected" — because a CAN wire break at position N drops everything after it.
* The topology is cached locally in SQLite per `robot_id` so it persists across sessions even without a live robot connection.

### 9.2 Post-Run Forensics Pipeline
1. When a run closes, the application assembles a condensed chronological diagnostic packet: local alert histories, pre-computed time-series summary metrics, derived SysId drift profiles, and the cached hardware topology graph.
2. The dataset is marshaled into an authenticated context payload using `kotlinx.serialization` and dispatched to the Ktor Cloud Run gateway via the Ktor HTTP client.
3. The Ktor microservice filters the data tokens, organizes the chronological failure breadcrumbs, and builds a contextual prompt for Google Cloud Vertex AI (Gemini API) using the official `google-cloud-vertexai` Java SDK, enforcing strict JSON output schemas.
4. The API evaluates cascading timelines and returns structured data fields matching specific parameters:
   ```json
   {
     "probable_root_cause": "String detailing mechanical, electrical, or loop tuning fault locus",
     "confidence_score": "Float percentage mapping evaluation certainty metrics",
     "cascading_nodes_affected": ["Array of structural node identifiers isolated below failure point"],
     "hardware_fault_locus": {
       "failed_node_id": "Target component identifier matching TopologyNode.id",
       "interrupted_link_id": "Target structural wiring connection path key"
     },
     "recommended_actions": ["Chronological list of physical mitigation steps"]
   }
   ```

### 9.3 Interactive Visual Topology Mapping & Triage Panels
* **Visual Fault Highlights:** The Compose application leverages the structured `hardware_fault_locus` schema token to update its topology node graph. The matching `TopologyNode.id` and directional communication lines are instantly highlighted in red or amber via `animateColorAsState`, visually pinpointing damaged wiring segments, bad crimps, or disconnected links. Because the topology comes directly from the robot code, `failed_node_id` values map 1:1 to registered hardware names.
* **Automated Pit Triage System:** The application processes the AI recommendation array alongside local high-priority alert histories to automatically construct a time-bounded Pit Triage Checklist panel.
* **Roster Accountability Tracking:** Checklist interactions, physical repair confirmations, and clearance flags prompt user verification inputs, appending localized timestamps and tracking identities onto the document file layout to synchronize maintenance records with the cloud database.

---

## 10. Identity Brokerage & Roster Governance

### 10.1 Federated Identity and Local Loopback Handshake
1. To comply with modern security guidelines restricting OAuth credential authorization loops inside embedded application windows, the dashboard delegates profile sign-ins straight to external browser windows.
2. Initializing a login session commands the application to instantiate an ephemeral local loopback background listener using an embedded Ktor server on `http://localhost:[ephemeral_port]/callback`.
3. The system commands the desktop operating system shell to launch the default native browser application (via `java.awt.Desktop.browse()`), loading Google's secure OAuth portal prepopulated with your client access keys and routing the loopback endpoint as the target redirect URI.
4. Upon confirmation, the native browser grabs the authorized access token and sends it back to the listening loopback server. The embedded Ktor server traps the token, shuts down the background socket, and updates the application's `MutableStateFlow<AuthState>` to initialize the Firebase User context.

### 10.2 GitHub Organization Roster Management
1. Inside the profile workspace, users connect their verified identities to their active GitHub profile. This action triggers a secure token handshake requesting authorized read-only scopes mapping organization membership records (`read:org`).
2. The Compose application routes this temporary GitHub OAuth token up to the secure Ktor Cloud Run microservice.
3. The Ktor microservice dispatches a validation query to the official GitHub API endpoint: `GET https://api.github.com/user/orgs` using the Ktor HTTP client.
4. **Automated Provisioning Logic:** The gateway runs loop validation evaluations matching the user's returned organizations against the team's official target handles (e.g., `"MARS2614"`, `"ARES23247"`).
5. If organization membership matches, the user's Firestore profile document is automatically stamped with the corresponding verified `team_id`, immediately granting access to the team's workspace, telemetry analytics, and dashboards without requiring manual administrative roster management.
6. If organization sub-teams (e.g., `mentors`, `software-leads`) are resolved, administrative access tiers are updated automatically to unlock permission-critical features like the Constants Tuning Subsystem.

---

## 11. Constants Tuning Subsystem

### 11.1 Configuration File Parsing
* The application targets designated configuration or variable storage files inside the workspace root (e.g., `Constants.kt` or `RobotConfig.java`).
* The core module exposes a `loadTunableConstants()` function that scans the text profile of the target file using Kotlin `Regex`, extracting variable identifiers, scalar assignments, and associated tuning metadata tags formatted as source comments.
* Extracted variables are passed to the Compose UI layer to be rendered as explicit, type-safe control elements (such as range sliders for gain limits or numeric input fields for mechanical boundaries).

### 11.2 Mutation Enforcement Invariants
* Modifying a UI control triggers a `saveConstant()` function call directly within the same JVM process — no IPC serialization overhead.
* The core module opens the file, applies targeted regular expression routines or inline string replacements to rewrite *only* the specific variable assignment in place, and instantly flushes the data buffer back to the local disk.
* Modifying a constant flags the dashboard compilation state via `MutableStateFlow<CompilationStatus>`, prompting the user to execute a new simulation run to foster a fast, iterative development cycle.

---

## 12. Match Schedule & Competition Integration

### 12.1 Event API Integration
* **FTC:** The application queries The Orange Alliance (TOA) API (`https://theorangealliance.org/api/`) to fetch event schedules, match results, and team rankings for the active event.
* **FRC:** The application queries The Blue Alliance (TBA) API (`https://www.thebluealliance.com/api/v3/`) for equivalent FRC event data.
* API keys are stored in the local encrypted preferences store, configured during onboarding.

### 12.2 Automatic Session Tagging
* When connected to an event, imported sessions are automatically tagged with match number, alliance color, and opponent team numbers based on the schedule data.
* The session list view displays match context alongside telemetry summaries, enabling rapid cross-referencing of performance against specific opponents.

---

## 13. Cross-Session Analytics & Battery Health

### 13.1 Battery Health Trending
* The dashboard provides a dedicated battery health view plotting `min_voltage` from `session_summaries` across the last N sessions.
* Degradation trend lines (linear regression over voltage minima) flag batteries whose minimum voltage is declining faster than expected, recommending replacement before elimination rounds.
* Battery labels (e.g., "Battery A", "Battery B") are tracked via session annotations.

### 13.2 Cross-Session Performance Trends
* All trend charts query pre-aggregated `session_summaries` data from local SQLite — never raw telemetry rows — to maintain instantaneous render performance per §12.3.
* Key trend metrics: min battery voltage, max EKF drift, average loop time, p95 loop time, motor current averages, vision measurement acceptance rate.

---

## 14. Dashboard Widget System

### 14.1 Customizable Widget Layout
* The dashboard supports a configurable grid of widgets. Users can add, remove, reorder, and resize widgets within the grid.
* Available widget types: Telemetry Chart, Motor Health Card, Vision Quality Card, Battery Gauge, Alert Panel, Match Info, AI Coach, Topology Map, Session Notes.
* Widget layouts are saved per user profile and per role (driver coach, programmer, pit crew) using the local preferences store.

### 14.2 Role-Based Default Layouts
* **Driver Coach:** Large telemetry chart, match timer, alert panel, battery gauge.
* **Programmer:** Motor health, SysId results, EKF diagnostics, terminal drawer.
* **Pit Crew:** Hardware topology, triage checklist, battery health, session notes.

---

## 15. Key Security & Operational Invariants

### 15.1 Architectural Layer Isolation Invariant
> **Strict Operational Invariant:** The Compose UI layer must function exclusively as a presentation and state-observation layer. All filesystem operations, process instantiations, socket connections, high-precision playback clock timing, AI payload compilation, and raw security token constructions must execute within dedicated service classes accessed through constructor-injected interfaces. Gradle module visibility rules (`internal` visibility, API/implementation dependency scoping) enforce that UI composables never directly import I/O, network, or process management classes. This preserves testability and prevents accidental coupling between rendering logic and system operations.

### 15.2 Credential Isolation Invariant
> **Strict Security Invariant:** Distributed application binaries must never package, contain, or access static Google Cloud JSON service account credentials. All cloud interactions involving storage buckets, semantic engines, or analytical streaming layers must be negotiated via temporary Firebase token security contexts or secure pre-signed vectors generated on-demand by the Ktor Cloud Run gateway.

### 15.3 Analytical Cost Control Invariant
> **Strict Cost Control Invariant:** BigQuery destinations must maintain intentional table partitions clustered by log timestamps and unique multi-tenant identification metadata strings (`team_id`, `season_id`, `robot_id`, `session_id`). Long-term macroscopic dashboards must query pre-aggregated Firestore summary layers instead of executing raw, on-the-fly table sweeps across millions of rows to control cloud database computing expenses.

### 15.4 Unified Language Invariant
> **Strict Maintainability Invariant:** All application source code — desktop client, cloud gateway, and shared libraries — must be written in Kotlin. This ensures that any team member capable of writing FTC robot code can read, debug, and extend the analytics platform without learning additional programming languages. Third-party dependencies may use JVM-compatible languages, but all first-party code must remain Kotlin.
