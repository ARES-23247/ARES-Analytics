# ARES Analytics operations guide

This guide covers local development, pit use, target connections, log handling, replay, and recovery. It assumes the four ARES repositories are sibling directories.

Task-focused guides:

- [First launch](start/FIRST_LAUNCH.md) — create and verify a robot workspace.
- [Connect the simulator](start/CONNECT_SIMULATOR.md) — novice-safe live telemetry and recovery.
- [Bring in a run](operate/BRING_IN_A_RUN.md) — completed-log collection, import evidence, and replay.
- [Google Drive setup](start/GOOGLE_DRIVE_SETUP.md) — optional one-click sign-in, destination selection, and recovery.
- [OAuth and Drive architecture](GOOGLE_DRIVE_ARCHITECTURE.md) — multi-team isolation and administrator policy.

## 1. Build environment

Use JDK 17 for the Analytics Gradle build.

```powershell
java -version
.\gradlew.bat --version
```

Normal builds consume the pinned ARESLib release from Maven Central. To validate an unpublished library change:

```powershell
cd ..\ARESLib-Kotlin
.\gradlew.bat apiCheck publishReleaseValidation
cd ..\ARES-Analytics
.\gradlew.bat :shared:test :app:test -ParesRepository="..\ARESLib-Kotlin\build\release-repository"
```

The sibling directory is not substituted automatically. Use `-ParesUseSiblingLib=true` only for focused library development; binary validation should use the isolated repository above.

## 2. Run modes

Desktop application only:

```powershell
.\gradlew.bat :app:run
```

Gateway only:

```powershell
.\gradlew.bat :gateway:run
```

Desktop and gateway together:

```powershell
.\gradlew.bat run
```

The combined task records child-process output in `build/run-logs/app.log` and `build/run-logs/gateway.log`.

To prevent the Gradle task from stopping an earlier Analytics JVM during investigation, add `-PskipKill=true`.

## 3. Pre-pit checklist

- [ ] Launch the application once before leaving an internet connection so Gradle/native dependencies are cached.
- [ ] Confirm the selected team, league, season, and robot workspace.
- [ ] Confirm the laptop can reach the target NT4 port `5810`.
- [ ] Confirm the laptop can reach the robot log server on `5002` if using HTTP pull.
- [ ] For FTC, verify `adb devices` sees the Control Hub if using ADB import/deploy.
- [ ] For FRC, verify SSH/SCP access if using RoboRIO file pull.
- [ ] Finish a short robot/simulator log, confirm its automatic import, and replay it before the match.
- [ ] Verify battery, loop-time, pose, and key mechanism topics are updating.
- [ ] Verify the target alliance and field-centric settings before enabling simulator control.

## 4. Connection diagnostics

### Simulator

Expected target: `127.0.0.1:5810`.

If the UI connects but pose or controls do not move:

1. Confirm the simulator and Analytics did not each start a conflicting server on port `5810`.
2. Check that `ARES/EstimatedPose` and `Drive/Pose_X` appear in active topics.
3. Verify dashboard inputs appear under `ARES/Input/*`.
4. Check that all simulator inputs are read from the custom ARESLib NT4 server.
5. If alliance changes do not take effect, inspect the atomic v2 `ARES/Input/driveFrame` array. Its
   flags field is element 7; red alliance is bit 5 (`1 << 5`, value `32`). Alliance is not sent on
   a separate scalar topic.

### FTC Control Hub

Common address: `192.168.43.1:5810` while connected to the Control Hub network.

```powershell
Test-NetConnection 192.168.43.1 -Port 5810
adb devices
```

If ADB is missing, configure `ANDROID_HOME` or `ANDROID_SDK_ROOT`, or put platform-tools on `PATH`.

### FRC RoboRIO

The mDNS or team address depends on team configuration. The conventional team address is `10.TE.AM.2`.

```powershell
Test-NetConnection 10.TE.AM.2 -Port 5810
ssh lvuser@10.TE.AM.2 true
```

Automatic SCP import requires the RoboRIO host key in the user's normal `known_hosts` file. Verify
the fingerprint on the first interactive SSH connection; Analytics deliberately refuses unknown or
changed host keys instead of bypassing SSH identity checks.

Do not diagnose a disconnected robot from stale dashboard values. Target changes clear topic metadata, latest values, live history, and pending database frames by design.

## 5. Live data and persistent runs

Live telemetry is stored under the reserved session ID `live-telemetry` in the in-memory database. It supports the live dashboard and live rewind, but it is not automatically a durable practice/match run.

The current UI does not expose a general start/stop recording control. A persistent run is created when Analytics imports a completed robot or simulator log. The producing logger owns the start/stop boundary; Analytics waits for the file to stop changing, archives it, imports it into DuckDB, and writes an import report. Follow [Bring in a run](operate/BRING_IN_A_RUN.md) for the student workflow.

If live charts update but no run appears:

1. Treat that as expected until the robot/simulator logger has closed a file.
2. Stop or finish the OpMode/routine cleanly so the source log is no longer being written.
3. Verify the selected Analytics workspace matches the producer's team, season, robot, league, and project path.
4. Check **Data → Log Imports** for a successful or quarantined report.
5. Verify the target timestamps increase and inspect application/import errors if no stable file is discovered.
6. Preserve the source until the imported session appears in **Recorded Sessions** and replays successfully.

## 6. Log collection

For a task-level walkthrough and success criteria, see [Bring in a run](operate/BRING_IN_A_RUN.md).

### HTTP pull

ARESLib's `LogManagerServer` listens on port `5002` and exposes:

```text
GET  /api/logs
GET  /api/download?file={name}
POST /api/delete
```

The desktop downloads files first. Deletion is a separate explicit operation after successful local handling. Robot code never uploads to cloud storage.

### FTC ADB locations

The automatic importer checks configured locations such as:

```text
/sdcard/FIRST/telemetry_logs/
/sdcard/ctre-logs/
/sdcard/FIRST/ctre-logs/
```

### FRC locations

The automatic importer checks configured RoboRIO/USB locations such as:

```text
/home/lvuser/logs/
/media/sda1/logs/
```

Imported local files are archived under the workspace's `logs/imported/` directory. A file is identified durably; leaving it on the robot must not create duplicate sessions every scan.

## 7. Import troubleshooting

| Symptom | Likely cause | Action |
| --- | --- | --- |
| Import never completes and CPU is high | malformed parser loop or enormous declaration | capture the file; run the format-specific decoder test; verify every decoder loop consumes input |
| Import succeeds but data is missing | truncated/corrupt input was tolerated | inspect logs; decoders should now throw on structural truncation |
| CSV has `_ExtraFieldsJson` only | older importer or malformed JSON extras | use the current CSV decoder, which expands each late key |
| `.wpilog` ends early | invalid record widths/length or damaged file | run `WpiLogDecoderTest`; verify exact-length reads and record header widths |
| `.hoot` conversion times out | `owlet` unavailable or blocked | verify `owlet` is on `PATH`; inspect conversion exit status |
| `.revlog` conversion fails | converter missing or returned nonzero | inspect the reported converter failure; commands are launched as direct argv, not through a shell |
| Same remote log appears repeatedly | import identity database/config was reset | preserve the application data directory; verify source identity metadata |

Untrusted log lengths are bounded before memory allocation. Do not raise limits merely to make a corrupt file import; first validate the format and expected maximum.

## 8. Replay troubleshooting

Replay is stateful. A topic last changed at `t=0` should still be present after seeking to `t=30s`.

If a seek loses values:

1. Verify the session contains the topic before the seek time.
2. Confirm `getLatestTelemetryBefore` returns one baseline per key.
3. Confirm loading a new session cleared old cache bounds and indices.
4. Confirm string telemetry is read from `string_value`.

If replay contaminates live UI:

1. Check `isReplayActive` ownership in `ReplayEngineService`/`Nt4ClientService`.
2. Stop replay and confirm no extra first-frame emission occurs.
3. Confirm the replay socket is not reopened by `stop()`.

## 9. DuckDB and export recovery

The default persistent database is under:

```text
~/.ares-analytics/telemetry.duckdb
```

Before manual repair, close every Analytics process and copy the database file.

Do not run ad hoc write SQL through `executeQueryRaw`; it is intentionally read-only. Use repository methods or a dedicated migration/export API.

Parquet round-trip verification:

```powershell
.\gradlew.bat :app:test --tests com.ares.analytics.service.ParquetExporterServiceTest
```

The test covers numeric/string restoration and paths/session IDs containing apostrophes.

## 10. Gateway operations

Minimum local environment:

```powershell
$env:GOOGLE_CLOUD_PROJECT = "ares-analytics"
$env:GOOGLE_CLOUD_LOCATION = "us-central1"
$env:GOOGLE_OIDC_CLIENT_ID = "your-client-id.apps.googleusercontent.com"
$env:ARES_GOOGLE_OAUTH_CLIENT_ID = "your-desktop-client-id.apps.googleusercontent.com"
# Inject ARES_GOOGLE_OAUTH_CLIENT_SECRET from a local secret manager or protected shell.
.\gradlew.bat :gateway:run
```

`GOOGLE_OIDC_CLIENT_ID` is the accepted audience for authenticated diagnostics. The two
`ARES_GOOGLE_OAUTH_*` values configure the separate desktop token broker. Never commit the secret,
pass it as a Gradle property, include it in a command example, or enable body logging while testing.
Production Cloud Run must inject it from the protected secret manager.

Health check:

```powershell
Invoke-WebRequest http://localhost:8080/healthz
```

Browser CORS is disabled unless `CORS_ALLOWED_HOSTS` contains comma-separated HTTPS hosts. The desktop client does not require CORS.

The diagnostics endpoint requires a Google ID token with the configured audience. Rate limits are
per authenticated subject. Payloads larger than 1 MiB or beyond configured alert/topology limits
are rejected.

The broker accepts `POST /api/oauth/google/token` and `POST /api/oauth/google/refresh`. It is
separately rate-limited and rejects an unexpected redirect URI, malformed PKCE verifier, missing
configuration, and excessive input before calling Google. A `broker_unavailable` error means the
gateway's protected OAuth configuration is incomplete; do not ask a student for a secret. The
broker is not a Drive proxy and must never receive workspace data.

For a local desktop build that deliberately targets a broker, set the non-secret values before the
build:

```powershell
$env:ARES_GOOGLE_OAUTH_CLIENT_ID = "your-desktop-client-id.apps.googleusercontent.com"
$env:ARES_GOOGLE_OAUTH_BROKER_URL = "https://your-broker.example.org"
.\gradlew.bat :app:run
```

Clear the variables after the run. Custom clients entered in the app require the same pair: public
Desktop client ID plus an administrator-operated HTTPS broker URL. A client secret never belongs in
the app configuration.

## 11. Shutdown and recovery

Normal shutdown should:

1. stop scanners and update checks;
2. cancel and join NT4 work;
3. stop replay and close UDP resources;
4. close cloud and event clients;
5. checkpoint and close DuckDB;
6. release gamepad/native resources.

Avoid terminating with `System.exit`, because it bypasses structured cleanup and can abandon file locks or pending work.

If a previous Analytics JVM is still running, the root Gradle task checks Java process identity using `jps`. It does not kill arbitrary processes merely because they listen on a common robotics port.

## 12. Release checklist

- [ ] Pin a published ARESLib version, or validate the matching isolated release repository.
- [ ] Run `:shared:test :gateway:test :app:test`.
- [ ] Test a live custom ARESLib NT4 server.
- [ ] Test a standards-compliant WPILib NT4 server.
- [ ] Import at least one JSONL/CSV and one binary log.
- [ ] Seek replay beyond a sparse topic's last update.
- [ ] Export/import a Parquet session containing string telemetry.
- [ ] Verify target switching clears old robot state.
- [ ] Verify gateway health, authentication, request limits, and rate limiting.
- [ ] Confirm the protected repository variables `ARES_GOOGLE_OAUTH_CLIENT_ID` and `ARES_GOOGLE_OAUTH_BROKER_URL` point to the active production Desktop client and stable HTTPS broker.
- [ ] Confirm the broker receives the matching `ARES_GOOGLE_OAUTH_CLIENT_ID` and `ARES_GOOGLE_OAUTH_CLIENT_SECRET` from protected Cloud Run/secret-manager configuration, and that neither secret nor token bodies appear in logs.
- [ ] Verify focused code-exchange, refresh, invalid-input, missing-configuration, error-redaction, and rate-limit tests.
- [ ] Verify one-click PKCE sign-in, destination creation/selection, a small upload/download, sign-out, refresh, and reconnect against the active production client and broker on a clean installed profile. Do not publish an installer before this passes.
- [ ] Verify a second workspace cannot list or synchronize the first workspace's root, and that removed folder permissions fail visibly.
- [ ] Exercise personal folders, created team folders, existing shared folders, and Shared Drive folders when the test account supports them; verify account switching, revoked refresh tokens, deleted clients, offline startup, and concurrent sync.
- [ ] Build the same native package used by the release workflow. Set `ARES_GOOGLE_OAUTH_CLIENT_ID` and `ARES_GOOGLE_OAUTH_BROKER_URL` from protected repository variables in the build environment (do not paste them into command arguments), then run `.\gradlew.bat :app:packageMsi "-ParesAnalyticsVersion=1.2.3" --no-daemon` and clear the environment values afterward.
- [ ] Confirm `:app:verifyDistributableProjectLoading` passes. Native package tasks depend on this guard, which loads metadata, routines, subsystems, capabilities, and autonomous choices through the trimmed jlink runtime rather than the development JDK.

The installer is not approved for publication until the production OAuth and Drive round trip above
has actually passed. Successful automated tests, CodeQL, dashboard validation, and MSI packaging do
not substitute for that external end-to-end verification.
