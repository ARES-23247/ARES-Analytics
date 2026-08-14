# Build one robot with Robot Studio

Robot Studio is the guided front door to ARES robot authoring. It does not replace the Drivebase Builder, Subsystem Builder, TeleOp Controls, Auto Builder, Tuning, simulator, or Run History. It reads their canonical project documents, explains the current evidence, and sends you to the correct specialized tool.

## Read the stage labels literally

Every stage includes a text label and icon. Color is supplemental.

| Status | Meaning |
| --- | --- |
| **Ready** | The required canonical document exists and passed the checks Robot Studio can perform locally. |
| **Needs action** | A required student step or explicit build/simulation action has not happened yet. |
| **Optional** | The robot can omit this stage for the current workflow. |
| **Blocked** | Fix an earlier required stage before continuing. |
| **Invalid** | A present document failed decoding, identity, reference, platform, or safety validation. |
| **Code required** | The descriptor is understandable, but this season project has no matching no-code runtime adapter. |
| **Running now** | Analytics observes its managed build or simulator process running. This is not a success result. |

Robot Studio never marks a build ready merely because an old generated file exists. Run **Generate & build** and read the terminal result. A successful build or simulator run still is not physical-robot validation.

## Follow the workflow

1. **Workspace & robot identity** — select the intended repository and review `.ares/project.json`.
2. **League & platform** — make sure workspace, descriptor, generator, and season runtime all agree on FTC or FRC.
3. **Drivebase** — describe physical identity, inversion, geometry, localization, safety, and the supported runtime adapter.
4. **Mechanisms & subsystems** — add only the mechanisms the robot has; drive-only robots may leave this optional.
5. **Sensors & localization** — select one compatible primary pose source and optional vision fusion.
6. **Capabilities & actions** — review the named Redux actions available to controls and autonomous routines.
7. **Driver & operator controls** — create controller profiles and conflict-free bindings.
8. **Autonomous routines** — optional while learning TeleOp; start with a short simulator-first routine.
9. **Tuning & calibration** — keep structural identity separate from reviewed canonical values and local experiments.
10. **Generate & verify** — preview generated work, preserve USER-OWNED source, build, and read the result.
11. **Simulate** — run the actual robot project against desktop adapters and identify the telemetry source.
12. **Import & analyze** — preserve a simulator or robot run before making claims about behavior.

## Know what is stored where

Robot Studio shows the exact destination on each stage. The important boundaries are:

- `.ares/project.json` owns project identity and league.
- `.ares/drivetrains/*.aresdrivetrain` owns structural drivebase configuration.
- `.ares/subsystems/*.aressubsystem` owns subsystem contracts; editable starters remain explicit source files.
- `.ares/controllers/*.arescontroller` and `.ares/controls/*.arescontrols` own controller identity and bindings.
- `.ares/routines/*.aresroutine` and `.ares/autonomous-catalog.json` own autonomous behavior.
- `.ares/tuning/*.arestuning` owns reviewed canonical tuning; local experiments stay under `.ares/local/tuning`.
- mechanical generated plumbing belongs under Gradle `build/generated` directories.
- imported run evidence belongs in the local Analytics database and may optionally be synchronized to the workspace-selected Google Drive destination.

Changing a display name must not change a stable document or action ID. Generated starter replacement still requires a structured diff and explicit confirmation. USER-OWNED or unknown source is never an eligible replacement target.

## When Robot Studio blocks you

Read the stage issue before editing files manually:

- **Wrong platform:** open workspace settings or repair the canonical metadata; do not generate cross-league code.
- **Code required:** use a supported no-code drivebase for this season project, or ask a mentor/developer to implement and verify the missing runtime adapter.
- **Invalid catalog or binding:** open the linked builder and fix the referenced stable ID or conflict.
- **Missing controls:** create both a controller profile and a control scheme before building a driveable robot.
- **Build failure:** keep the terminal output visible; the generated file already on disk is not proof of freshness.

Use [Robot Academy](ROBOT_ACADEMY.md) when a concept is unfamiliar. Use [Drivebase Builder](../DRIVEBASE_BUILDER.md), [Subsystem Builder](../SUBSYSTEM_BUILDER.md), and [Routines and controls](../ROUTINES_AND_CONTROLS.md) for deeper task instructions.

## Physical validation boundary

Robot Studio reports documents, local validation, managed processes, simulator connections, and imported-run evidence. It does not inspect wiring, mechanical clearance, emergency-stop readiness, field setup, or human supervision. Complete the team’s restrained and supervised hardware checklist before enabling a physical mechanism.
