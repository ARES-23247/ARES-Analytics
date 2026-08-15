# First launch

This guide gets one local robot workspace into ARES Analytics. It does not connect to or enable a physical robot.

## Before you start

Have these ready:

- JDK 17 installed.
- Either a local robot project or a parent folder where ARES can create one.
- Your team number, season, and robot name or ID.

Choose the robot project itself, not the four-project `ares` workspace and not the `ARES-Analytics` folder. A project with `.ares-robot.json` is easiest because Analytics can fill in its identity.

If you are developing from source, launch from `ARES-Analytics`:

```powershell
.\gradlew.bat :app:run
```

If a mentor installed the desktop application, open **ARES Analytics** normally instead.

## Set up the workspace

The first-run **ARES setup** has four short stages.

### 1. Choose or create your robot project

For a new student:

1. Choose **Create a simulation-first robot**.
2. Select **FTC** or **FRC** and read the exact verified starter version.
3. Choose a parent folder, enter a new project folder name, then select **Continue**.

For an existing project:

1. Choose **Open an existing project**.
2. At **Robot project folder**, select **Choose project**.
3. Choose the repository root and read the green detection message, then select **Continue**.

An existing selected folder usually contains `settings.gradle` or `settings.gradle.kts`. New-project
download, SHA-256 verification, personalization, and publication occur only after the final review.
See [Create a robot project](CREATE_ROBOT_PROJECT.md) for the safety and recovery contract.

The current downloaded starters retain Team 23247 season hardware examples. They are fully usable
for Robot Studio, tests, and local simulation, but ARES blocks physical deployment from them.

### 2. Check the robot details

1. Confirm **Competition** is **FTC** or **FRC** as expected.
2. Fill in any values that were not detected:
   - **FIRST team number**: digits only.
   - **Season**: the season used by this project.
   - **Robot ID**: a stable short identifier for this robot.
   - **Friendly name (optional)**: a recognizable display name.
3. Select **Continue**.

### 3. Optional connections

1. Expand **Cloud sync (optional)** only if you want to set up Drive now. Choose **Sign in with
   Google** to continue, or **Use ARES without Google** to close it and keep working locally.
   Google sign-in is not required for local telemetry, authoring, simulator work, imports, or replay.
2. After a successful sign-in, choose the personal, team, shared-folder, or Shared Drive destination
   for this workspace. You can also add it later from **Profile & Settings → Google Drive**.
3. Leave **Connection settings (advanced, optional)** collapsed for normal setup.
4. If a mentor asks you to expand it:
   - **Robot NetworkTables address** is the saved **Live Robot** host. **Local Sim** uses `127.0.0.1` automatically later.
   - **Simulator command (optional)** can remain blank when the project's league default works.
5. Select **Review setup**.

### 4. Ready to finish

1. Read the **Workspace summary**. Use **Back** if the project, robot, team/season, competition, or connection is wrong.
2. Check the **JDK 17** result. Select **Check** again after fixing Java if necessary.
3. Select **Create workspace**.

After Dashboard opens, choose **Help & Learn → First mission**. This is the recommended
hardware-free handoff from setup into the app. The lesson coach can keep the next checkpoint
visible while you select Local Sim, open Dashboard, and stop the simulator.

Workspace setup records which repository you selected, but it does not guess a physical robot
footprint or silently create canonical project metadata. Before using the no-code builders, open
**Robot Studio**, choose **Set up project identity**, enter measured dimensions, and review the
structured `.ares/project.json` diff. See [Project Identity](../learn/PROJECT_IDENTITY.md).

## Success check

Setup is complete when:

- the main **Dashboard** opens;
- the workspace selector shows the robot you chose;
- the execution toolbar offers **Live Robot** and **Local Sim** targets; and
- no required-field or JDK error remains.

Setup does not mark a Robot Academy lesson complete. Academy records only observable simulator
facts automatically; students still identify the data source and explain their evidence themselves.

The sidebar shows labeled **NT4 on/off** and, for FTC, **ADB on/off** status. These are connectivity indicators, not setup scores. They can say `off` until a robot or simulator is running.

## If setup does not finish

| What you see | What to do |
| --- | --- |
| **Create workspace** shows **JDK 17 required** | Confirm `java -version` reports JDK 17, then select **Check** again. |
| “Choose a folder that contains your robot project” | Browse to `ARES-FTC` or `ARES-FRC`, not their parent folder. |
| The wrong competition was detected | Select the correct **Competition** (**FTC** or **FRC**) before creating the workspace, and tell a mentor if the project lacks or misstates `.ares-robot.json`. |
| A team, season, or robot field is rejected | Use short, non-empty identifiers. Do not substitute a robot's IP address for its ID. |
| Google sign-in fails | Collapse/skip **Cloud sync (optional)** and continue. Cloud access is not required for local setup. |
| The wrong workspace opens later | Use the workspace selector at the top of the main screen. Choose the intended robot profile before launching or importing anything. |

## Safety and recovery

- First launch only saves a desktop workspace profile. It does not deploy code or enable a robot.
- **Verify & build** is a separate, compile-only toolbar action. It runs verification, tests, and packaging for the selected project; it never installs code on a robot.
- Do not paste secrets into screenshots or team chat. Normal Google sign-in never asks a student
  for a client secret; custom OAuth and broker configuration belong to an administrator.
- If you made a profile for the wrong folder, create or select the correct workspace rather than moving project folders while Analytics is running.

## Mentor / advanced detail

The workspace identity (`teamId`, `seasonId`, `robotId`) is attached to imported sessions and cloud paths. The project path also controls local log scanning and simulator launch. Correcting the profile before collecting data prevents two robots' evidence from being mixed.

The configured NT4 host is used for the **Live Robot** target. Choosing **Local Sim** overrides the active connection host with loopback (`127.0.0.1`) without rewriting the saved robot address.

Next: [Robot Academy](../learn/ROBOT_ACADEMY.md), then [Connect the simulator](CONNECT_SIMULATOR.md).
