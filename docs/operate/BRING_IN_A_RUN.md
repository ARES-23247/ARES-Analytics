# Bring in a run

A **run** is a completed robot or simulator log imported into the local Analytics database. The current application does not require a Dashboard start/stop recording button.

Live telemetry and durable runs are different:

- **Live**: changing NT4 values, held in the reserved `live-telemetry` session for live viewing/rewind.
- **Run**: a completed log file imported as a persistent session for later replay and comparison.

## Before you start

- Select the correct robot workspace. Its team, season, robot ID, project path, and league label the imported evidence.
- Finish or stop the robot OpMode/routine or simulator so its logger closes the file.
- Keep the original match log until you have confirmed the import.

## Choose one source

### Local file or simulator log

Copy the log into one of the watched folders under the selected robot project:

```text
<robot-project>/logs/
<robot-project>/ftc-app/logs/
```

Do not place a new source file directly in `logs/imported/`; that folder is the archive destination. The scanner checks about every five seconds and waits for a file to remain stable before importing it. Allow roughly 10–15 seconds for a newly copied file.

Supported automatic-import suffixes include `.jsonl`, `.csv`, `.wpilog`, `.wpilogxz`, `.parquet`, `.hoot`, `.dslog`, `.rlog`, `.revlog`, and `.log`.

### FTC Control Hub

1. Select an FTC workspace.
2. Connect the laptop with ADB and confirm the ADB indicator is connected.
3. Stop the OpMode/logger so the log is no longer being written.
4. Leave Analytics open while it checks the configured Control Hub log folders.

The importer checks locations including `/sdcard/FIRST/telemetry_logs/`, `/sdcard/ctre-logs/`, and `/sdcard/FIRST/ctre-logs/`.

### FRC RoboRIO

1. Select an FRC workspace and verify its live-robot host.
2. Confirm the laptop can reach the RoboRIO and that its SSH host key has already been verified.
3. Stop the logger/run and leave Analytics open while it checks `/home/lvuser/logs/` and `/media/sda1/logs/`.

Analytics refuses unknown or changed SSH host keys. Ask a mentor to verify the fingerprint; do not bypass that protection.

## Confirm the import

1. Open **Data → Log Imports**.
2. Select **Refresh import reports** if the screen has not updated.
3. Find the file under **Successful imports**.
4. Check that its decoder, record count, and topic count are plausible. A green row with zero or surprisingly few records still deserves investigation.
5. If it is under **Quarantine**, read the error, preserve the source, correct the cause, and use **Retry** only after the decoder/tool problem is fixed.

Successful automatic imports are archived under `<robot-project>/logs/imported/` with an import report. Failed evidence is kept under `<robot-project>/logs/quarantine/`. Content fingerprints prevent the same unchanged file from becoming duplicate sessions.

## Open the run as a replay

1. Return to **Dashboard**.
2. In **Recorded Sessions**, select the imported **Practice Run** or match row. This widget is included in the default dashboard; if a custom layout removed it, enter layout editing and add **Recorded Sessions** from **Replay & review**.
3. Wait for the dashboard profile to change to **Replay** and for the timeline to appear.
4. Use **Play**, **Pause**, **Step Back**, **Step Forward**, or drag the timeline.
5. Confirm that the field, chart, or console follows the replay time.

Use **Analysis → Guided Run Review** for the student-first evidence path from source identity through a safe next action. Use **Run History** for advanced across-run calculated metrics and trends. Use the Dashboard **Recorded Sessions** widget and timeline for exact time-based replay. See [Guided run review](GUIDED_RUN_REVIEW.md).

## Success check

You are done when all of these are true:

- the file appears under **Successful imports**;
- a persistent session appears in **Recorded Sessions** or **Run History**;
- replay controls change the displayed historical data; and
- you can identify the source as **imported run**, not live robot or simulator data.

## Safety and recovery

- Import and replay cannot enable robot hardware.
- Do not delete the source or robot copy until the archive and replay are verified.
- A cloud warning does not mean local import failed. Confirm local success first; retry cloud sync later.
- Never raise parser size limits just to accept a damaged file. Preserve it and diagnose the producing logger/format.
- Do not edit manifests or move archived/quarantined files while Analytics is open.

## Mentor / advanced detail

The scanner requires two matching size/modified-time observations so it will not ingest a file still being written. It copies through a `.partial` staging file, fingerprints content, imports to DuckDB, writes a sidecar report, and only then removes a local watched-folder source. Treat the watched `logs/` folder as an inbox, not as the sole archive.

Robot data remains offline-first. FTC automatic collection uses ADB and FRC automatic collection uses SSH/SCP. The robot never sends a run to cloud storage; optional synchronization is performed by the desktop after local persistence.

For decoder-specific failures, see [Operations: import troubleshooting](../OPERATIONS.md#7-import-troubleshooting).
