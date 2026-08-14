# Set up project identity

Project Identity creates or reviews `.ares/project.json`, the small Git-tracked document that tells every ARES builder which robot and field frame belong to the selected repository.

Open **Robot Studio**, then choose **Set up project identity** when the first stage needs action. You can also open **Robot > Project Identity** directly.

## What each value means

- **Stable project ID** connects drivebase, subsystem, controls, autonomous, and tuning documents. It is not a display name. Choose it once; after creation the editor locks it to prevent accidental broken references.
- **League** comes from the selected workspace. FTC and FRC projects use different generated adapters, so Project Identity will not silently change the platform of an existing project.
- **Coordinate convention** is derived from the league. FTC uses a center origin with counter-clockwise-positive heading. FRC uses the blue-corner origin with counter-clockwise-positive heading.
- **Robot length and width** are measured bumper-to-bumper dimensions in meters. Do not enter wheelbase or track width here.
- **Field length and width** define autonomous bounds in meters. ARES pre-fills its current league preset, but a student or mentor must verify it for the selected season.

Project Identity does not store CAN IDs, motor names, tuning gains, credentials, or evidence that hardware was physically tested. Those responsibilities stay in their dedicated documents and workflows.

## Safe creation and editing

1. Confirm the selected project path shown at the top of the screen.
2. Measure the robot footprint; do not guess values to make validation pass.
3. Verify the field preset against the current game manual or team field model.
4. Select **Review structured diff**. No file is written yet.
5. Read every before/after value and the destination.
6. Select **Create reviewed identity** or **Save reviewed changes**.

When updating a valid file, ARES preserves the previous canonical content under `.ares/history/project/<content-hash>.json` before replacing it atomically. If the file changes after preview, the save is rejected and you must reload and review again.

An unreadable existing `.ares/project.json` is protected. ARES will not overwrite it. Preserve the file, repair it or restore a known-good revision, then reload. A workspace/canonical league mismatch is also protected rather than rewritten.

## Success check

Return to Robot Studio. **Workspace & robot identity** and **League & platform** should report **Ready** only when the canonical file exists, validates, and agrees with the selected workspace. This is document evidence, not a build, simulation, deployment, or physical-robot safety result.

Next: [Build one robot with Robot Studio](ROBOT_STUDIO.md).
