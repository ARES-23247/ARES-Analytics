# Automated Dashboard Validation

ARES Robotics Studio includes repeatable smoke and soak profiles for the complete dashboard data path. Validation combines the in-process NT4 server/client test with deterministic telemetry ingestion, indexed DuckDB queries, CSV and Parquet export, lossless Parquet restore, replay load and scrubbing, alert regression checks, memory measurement, and performance budgets.

## Local commands

Run the PR-sized smoke profile:

```powershell
.\gradlew.bat :app:dashboardSmoke
```

Run smoke plus the checked-in performance-regression baseline (the CI gate):

```powershell
.\gradlew.bat :app:dashboardPerformanceBaseline
```

Run the 30-minute-equivalent soak profile:

```powershell
.\gradlew.bat :app:dashboardSoak
```

Validate a physical robot or separately running simulator:

```powershell
.\gradlew.bat :app:dashboardHardware `
  "-Pvalidation.hardwareHost=192.168.43.1" `
  "-Pvalidation.hardwarePort=5810" `
  "-Pvalidation.hardwareRequiredKeys=Robot/BatteryVoltage,Drive/Pose_X"
```

On macOS or Linux, replace `.\gradlew.bat` with `./gradlew`.

Neither profile waits for the simulated session duration in wall-clock time. Smoke generates 10 seconds at 100 Hz. Soak generates the sample volume of a 30-minute session at 20 Hz, then exercises queries, round-trip persistence, and replay against that dataset.

Reports are written to:

```text
app/build/reports/dashboard-validation/dashboard-validation-<profile>.md
app/build/reports/dashboard-validation/dashboard-validation-<profile>.json
```

The JSON report is suitable for trend ingestion. The Markdown report summarizes configuration, measured results, and budget violations.
CI retains both formats for 90 days and publishes the Markdown report in the GitHub Actions job summary. The baseline gate reads `config/dashboard-performance-baseline.json`; update it only after reviewing an intentional performance change on comparable hardware.

## What is validated

| Area | Automated check |
|---|---|
| Live transport | In-process NT4 server and `Nt4ClientService` connection, topic flow, pose state, and motor telemetry |
| Persistence | Batched multi-topic ingestion, exact frame counts, microsecond ordering, and zero dropped samples |
| Analytics queries | Exact-key, key-pattern, distinct-key, and bounded-range DuckDB queries |
| Export | CSV table generation and full-session Parquet generation |
| Restore | Parquet import restores the exact frame count |
| Replay | Session load, current-frame creation, and repeated scrub latency |
| Alerts | Threshold and composite alert regression suites in the smoke task |
| Resources | Ingestion throughput, p95 query latency, replay timing, Parquet timing, and heap growth |

Compose rendering is intentionally not launched in headless CI. Declarative widget behavior is covered through the same database and telemetry services used by the UI. Actual GPU rendering and physical Wi-Fi behavior remain part of the optional pit/hardware check.

## Performance budgets

Defaults are deliberately stable across developer machines and GitHub runners:

| Budget | Smoke default | Soak default |
|---|---:|---:|
| Minimum ingestion | 1,000 frames/s | 1,000 frames/s |
| Query p95 | 1,000 ms | 2,000 ms |
| Replay load | 5,000 ms | 15,000 ms |
| Replay scrub p95 | 2,000 ms | 2,000 ms |
| Parquet import/export | 15,000 ms | 30,000 ms |
| Heap growth | 256 MiB | 512 MiB |
| Drop rate | 0 | 0 |

Override workload or budget values with Gradle properties. For example:

```powershell
.\gradlew.bat :app:dashboardSoak `
  "-Pvalidation.simulatedSeconds=3600" `
  "-Pvalidation.sampleRateHz=50" `
  "-Pvalidation.maxQueryP95Ms=2500"
```

Supported properties are:

- `validation.simulatedSeconds`
- `validation.sampleRateHz`
- `validation.topicCount`
- `validation.batchSize`
- `validation.queryIterations`
- `validation.minIngestionFramesPerSecond`
- `validation.maxQueryP95Ms`
- `validation.maxReplayLoadMs`
- `validation.maxReplayScrubP95Ms`
- `validation.maxParquetOperationMs`
- `validation.maxHeapGrowthMb`
- `validation.maxDropRate`
- `validation.hardwareHost`
- `validation.hardwarePort`
- `validation.hardwareObservationSeconds`
- `validation.hardwareConnectTimeoutSeconds`
- `validation.hardwareMinFrames`
- `validation.hardwareMinTopics`
- `validation.hardwareRequiredKeys`

## GitHub Actions

`.github/workflows/dashboard-validation.yml` runs:

- `dashboardPerformanceBaseline` (which runs `dashboardSmoke` first) for relevant pull requests and pushes to `master`.
- `dashboardSoak` nightly and when manually selected through `workflow_dispatch`.
- Report and JUnit artifact upload even when a budget fails.

The workflow checks out `ARESLib-Kotlin` beside `ARES-Analytics`, matching the composite-build layout used by local development.

`.github/workflows/build-distributions.yml` gates every installer build on the official-template
acceptance test. It creates fresh FTC and FRC projects from the same hash-pinned archives used by
onboarding, personalizes their canonical ARES identities, and then generates, verifies, tests, and
packages both projects through their normal immutable dependency repositories. The FTC project also
runs the headless drivetrain verifier, which must demonstrate translation, field-centric control,
and rotation before an installer can be produced. This is simulator evidence, not physical-hardware
validation.

On Windows, the package job then selects the newest earlier stable GitHub release, installs its MSI
on the clean runner, upgrades it with the candidate MSI, verifies that no side-by-side older product
remains, runs an explicit repair transaction, and uninstalls the test product. The job rejects a
version that already has a GitHub Release so different package bytes cannot reuse one public version.

Fresh-project acceptance and native Windows/macOS packaging run concurrently to shorten release
latency. The publication job still depends on both lanes, so no package can be released unless the
zero-code consumer matrix and every native package check have completed successfully.

## Optional physical hardware check

The hosted pipeline cannot reproduce radio congestion, Control Hub storage pressure, RoboRIO CPU contention, or field-network policies. Run `dashboardHardware` manually from the driver-station laptop while it is connected to the robot network. The task observes live NT4 traffic for 30 seconds by default, checks frame/topic minimums and required keys, persists the received data, and writes its report locally. No self-hosted GitHub runner is required.
