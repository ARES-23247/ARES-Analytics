# ARES Analytics

ARES Analytics is the desktop mission-control, log-analysis, replay, and pit-diagnostics application for the ARES FTC and FRC robots. It receives live NetworkTables 4 (NT4) telemetry, imports robot logs into DuckDB, runs analysis locally, and optionally synchronizes artifacts through Google Drive.

The robot remains offline-first: robot code never sends data directly to a cloud service. The desktop app owns every cloud interaction.

The routine and controller editors are offline project tools as well: they operate on the selected
repository's `.ares` documents and never require a connected robot. See the [student routines and
controller guide](docs/ROUTINES_AND_CONTROLS.md).

## Repository layout

| Module | Responsibility |
| --- | --- |
| `app` | Compose Desktop UI, NT4 client, DuckDB persistence, log import, replay, analytics, simulation controls, and Google Drive synchronization |
| `shared` | Serializable models and unit-conversion helpers shared by the desktop app and gateway |
| `gateway` | Small authenticated Ktor service exposing the Vertex AI pit-forensics endpoint |

The application also consumes `com.areslib:core:1.0-SNAPSHOT`. When `../ARESLib-Kotlin` exists, Gradle uses that sibling checkout as a composite build; otherwise it resolves the artifact from `mavenLocal()`.

## Requirements

- JDK 17
- PowerShell on Windows, or a POSIX shell on macOS/Linux
- A sibling `ARESLib-Kotlin` checkout for normal workspace development
- Optional tools by workflow:
  - Android SDK platform tools (`adb`) for FTC log pulling and deployment
  - SSH/SCP for RoboRIO log pulling
  - CTRE `owlet` for `.hoot` conversion
  - Google application-default credentials for running the cloud gateway

## Quick start

From the workspace root, publish the shared library after changing it:

```powershell
cd ARESLib-Kotlin
.\gradlew.bat publishToMavenLocal
```

Then build and run the desktop application:

```powershell
cd ..\ARES-Analytics
.\gradlew.bat :app:run
```

To run the desktop application and local gateway together:

```powershell
.\gradlew.bat run
```

The root `run` task writes subprocess output under `build/run-logs/`. The gateway listens on port `8080` unless `PORT` overrides it.

## Verification

Run all module tests:

```powershell
.\gradlew.bat :shared:test :gateway:test :app:test
```

Useful narrower checks:

```powershell
.\gradlew.bat :app:test --tests com.ares.analytics.service.Nt4ClientServiceTest
.\gradlew.bat :app:test --tests com.ares.analytics.service.ReplayEngineServiceTest
.\gradlew.bat :app:test --tests com.ares.analytics.service.log.WpiLogDecoderTest
.\gradlew.bat :app:test --tests com.ares.analytics.service.ParquetExporterServiceTest
```

Run the automated dashboard smoke or 30-minute-equivalent soak profile:

```powershell
.\gradlew.bat :app:dashboardSmoke
.\gradlew.bat :app:dashboardSoak
```

Both tasks enforce performance budgets and write JSON plus Markdown reports under `app/build/reports/dashboard-validation/`. See [Automated dashboard validation](docs/VALIDATION.md) for workload settings, budget overrides, CI behavior, and hardware-test boundaries.

## Runtime data flow

```text
robot or simulator
    |  NT4 WebSocket :5810
    v
Nt4ClientService
    |-- current values and bounded live history -> Compose UI
    |-- ordered frame batches -> DuckDB
    `-- dashboard input publications -> simulator/robot input topics

robot log files
    |-- LogManagerServer HTTP :5002, ADB, SCP, or local files
    v
log decoder -> FrameBatcher -> DuckDB -> summaries / SysId / replay

desktop app -> Google Drive or authenticated gateway
```

Important invariants:

- Topic names are stored without leading `/`; the wire client accepts either form.
- Internal angles are radians and counter-clockwise positive.
- Live telemetry uses the reserved session ID `live-telemetry`; recordings use persistent session IDs.
- A replay seek must restore the last value at or before the seek time, not only values inside the visible window.
- Numeric and string telemetry are both first-class data. Do not coerce strings to numeric zero.
- Log import is streaming and bounded. A malformed or truncated input must fail visibly rather than silently produce a partial “successful” session.

## Connecting to a target

Common NT4 targets are:

| Target | Default address |
| --- | --- |
| FTC Control Hub | `192.168.43.1:5810` |
| FRC RoboRIO | `10.TE.AM.2:5810` |
| Desktop simulator | `127.0.0.1:5810` |

`Nt4ClientService` constructs a unique `/nt/ARES-Analytics-{timestamp}` client path; normally enter only the host in the UI. Switching targets clears live state and pending frames so values from two robots cannot be mixed.

## Log formats

The importer supports ARES JSONL/CSV, WPILib `.wpilog`, CTRE `.hoot`, DS logs/events, REV logs, Road Runner logs, RLOG, and Parquet. Format-specific decoders live under `app/src/main/kotlin/com/ares/analytics/service/log/`.

Parquet export is performed through the narrow `DatabaseService.exportSessionToParquet` API. General raw-SQL execution is deliberately read-only and must not be repurposed for export.

## Gateway configuration

The gateway exposes:

- `GET /healthz`
- authenticated pit-forensics routes under the diagnostics router

Relevant environment variables:

| Variable | Meaning | Default |
| --- | --- | --- |
| `PORT` | HTTP listen port | `8080` |
| `GOOGLE_OIDC_CLIENT_ID` | accepted Google ID-token audience | configured production audience |
| `GOOGLE_CLOUD_PROJECT` | Vertex AI project | `ares-analytics` |
| `GOOGLE_CLOUD_LOCATION` | Vertex AI region | `us-central1` |
| `CORS_ALLOWED_HOSTS` | comma-separated HTTPS browser origins | none |

The Compose client is not subject to browser CORS. Browser access must be explicitly allowlisted. Requests are limited to 1 MiB and forensics requests are rate-limited per authenticated subject.

## Documentation

- [Automated dashboard validation](docs/VALIDATION.md) - smoke/soak profiles, performance budgets, reports, and CI
- [Student routines and controller bindings](docs/ROUTINES_AND_CONTROLS.md) - offline authoring, visual controls, generation, selection, and troubleshooting
- [Architecture](ARCHITECTURE.md) — modules, service lifecycles, persistence, replay, and extension points
- [Telemetry contract](docs/TELEMETRY_CONTRACT.md) — canonical topics, types, coordinate conventions, and NT4 behavior
- [Operations guide](docs/OPERATIONS.md) — setup, connections, import/replay workflows, and troubleshooting
- [Security audit](AUDIT.md) — dated audit evidence; verify status against current code before treating an item as open

## Where to start in the code

- `app/src/main/kotlin/com/ares/analytics/di/ServiceRegistry.kt` — service ownership and shutdown order
- `app/src/main/kotlin/com/ares/analytics/service/Nt4ClientService.kt` — live telemetry boundary
- `app/src/main/kotlin/com/ares/analytics/service/DatabaseService.kt` — database facade
- `app/src/main/kotlin/com/ares/analytics/service/log/LogParserService.kt` — import routing
- `app/src/main/kotlin/com/ares/analytics/service/ReplayEngineService.kt` — replay state reconstruction
- `gateway/src/main/kotlin/com/ares/analytics/gateway/Application.kt` — gateway security and routing

## Contribution rules

1. Keep composables declarative; side effects belong in view models or services.
2. Give every long-lived service one owned coroutine scope and an explicit shutdown method.
3. Hold the database mutex only around the JDBC operation itself.
4. Bound untrusted payload lengths before allocating memory.
5. Use parameterized SQL where DuckDB permits it; otherwise isolate and escape the smallest possible internal API.
6. Add a regression test for protocol, parser, replay, or mathematical changes.
7. When changing an NT4 topic, update the producer, consumer, and `docs/TELEMETRY_CONTRACT.md` together.
