# Guided run review

Use **Analysis → Guided Run Review** when you have a completed simulator, practice, or match run and want to understand it without starting from a table or SQL query. The review is read-only: it does not change the run, publish tuning, edit robot source, or command hardware.

## Follow the evidence path

1. Confirm the selected workspace names the expected team, season, and robot.
2. Choose a run. ARES lists only sessions with that exact identity.
3. Read **Data source**, **Freshness**, and **Interpretation confidence** before interpreting a graph.
4. Inspect timestamps, units, and persisted alerts.
5. Compare only against compatible runs from the same team, season, and robot.
6. Keep **Observed evidence** separate from **Possible causes to verify**.
7. Open the exact timeline before changing a builder or tuning value.
8. Export the Markdown evidence report alongside the original log.

## Understand the confidence language

- **Moderate evidence** means the source and timeline support threshold screening and same-robot comparison. It does not prove causation or physical safety.
- **Limited evidence** means the run is usable, but source identity, summary coverage, or a screening service is incomplete.
- **Insufficient evidence** means the timeline or telemetry topic set is missing. Missing data is not a normal measurement.

Every review is historical. Its freshness line names the persisted timestamp range and explicitly says it is not a live reading. A green status or a quiet alert list never proves that a robot is safe to approach.

## Preserve source identity

Import through **Data → Log Imports** whenever possible. ARES then keeps the source filename, decoder, accepted and rejected record counts, warnings, and SHA-256 digest. A workspace Drive object can also provide a stable object identity and digest. If neither record exists, Guided Run Review labels provenance incomplete rather than guessing.

## Use the next action safely

Suggested actions only open an existing ARES tool:

- **Dashboard replay** shows the selected session timeline.
- **Tuning** lets you review current, requested, and canonical values; opening it does not apply a proposal.
- **Robot Academy** explains evidence and missing-data limits.
- **Log Imports** preserves a better source record for the next capture.
- **Advanced Run History** retains tables and developer analysis through progressive disclosure.

For a physical test, stop at the recommendation and use the team's supervised procedure. Simulator or recorded evidence does not establish mechanism clearance, wiring correctness, actuator direction, current limits, or emergency-stop readiness.
