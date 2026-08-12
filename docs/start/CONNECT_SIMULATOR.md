# Connect the simulator

Use this task for the safest first live-data experience. The simulator produces telemetry now, like a robot, but it does not operate physical hardware.

## Before you start

- Complete [First launch](FIRST_LAUNCH.md).
- Select the workspace for the robot project you want to simulate.
- Close any older simulator using NT4 port `5810`.
- Save project work before launching; simulator output opens in the Analytics terminal drawer.

## Launch and connect

1. Open **Dashboard**.
2. In the execution toolbar, open the target selector and choose **Local Sim**.
3. Select the monitor-shaped **Launch Simulator** control. Its tooltip is **Launch Desktop Simulator (Ctrl+D)**.
4. Keep the terminal drawer open while the project builds and starts.
5. Wait for both signs of success:
   - the **Local Sim** dot in the toolbar turns green; and
   - the application's connection indicator reports connected.
6. Watch a live widget. Good first choices are the field viewer, robot pose, system health, or a telemetry chart.
7. If the simulation routine drives the model, confirm that pose or another value changes over time.

Analytics switches its NT4 connection to `127.0.0.1` for **Local Sim**. You do not need to replace the saved live-robot address.

## Success check

You are connected when the simulator is still running, the target is **Local Sim**, and at least one expected telemetry value updates. A simulator process that merely printed “build successful” is not enough; verify data on the Dashboard.

Say which mode you are in:

> “This is live simulator data. It is not a recorded replay, and it cannot move the physical robot.”

## Stop cleanly

1. Select the square **Stop** control in the execution toolbar. Its tooltip is **Kill Active Process (Ctrl+Shift+K)**.
2. Wait for the simulator-running indicator to clear.
3. Keep the last terminal lines if the process ended with an error.

The toolbar **Stop** action stops Analytics-managed build and simulator processes. It does not disable a separately running physical robot.

## If it does not connect

Work from the top of this table; do not repeatedly launch more simulator processes.

| Symptom | Recovery |
| --- | --- |
| Terminal says the project folder is missing or has no Gradle wrapper | Switch to the correct robot workspace. The profile must point to `ARES-FTC` or `ARES-FRC`. |
| The simulator task is unknown | Ask a mentor for the project's command and save it as **Simulator Command (Optional)** in the workspace profile. Defaults are league-specific. |
| Port `5810` is already in use | Select **Stop**, close the older simulator/NT4 server, then launch once. |
| The process runs but the dot stays gray | Confirm **Local Sim** is selected and look for an NT4 server startup line. Check that firewall rules are not blocking loopback. |
| Connected, but the field pose stays at zero | Check whether the chosen OpMode/routine is initialized or started. Try another known changing topic before assuming the connection failed. |
| Alliance or starting side looks wrong | Stop the routine, set the intended alliance before INIT, then restart. Simulator teleport/alliance initialization occurs at INIT. |
| Values look like an old match | Stop historical replay and select the live/simulator view. Replay and live values share dashboard widgets, so confirm the mode label. |

## Safety

- **Local Sim** is the safe default. Keep **Live Robot** unselected during this exercise.
- Do not use **Build & Deploy**; launching a simulator does not require deploying to a Control Hub or RoboRIO.
- Keyboard/driver controls may command the simulated model. Use them only when the instructor has explained the deadman/enable behavior.
- A green simulator dot does not prove a nearby robot is disabled. Continue to follow the team's physical robot rules.

## Mentor / advanced detail

The toolbar launches the configured simulator command in the active robot project. With no override, Analytics uses the league default (FTC `:TeamCode:runSim`, FRC `simulateJava`) or a packaged simulator JAR when present. It then connects the same `Nt4ClientService` used for robots to loopback.

Simulator ground-truth pose normally arrives under `ARES/EstimatedPose/*`; robot estimator pose normally arrives under `Drive/Pose_*`. Both are CCW-positive radians internally. If a heading visualization disagrees, verify the producer rather than adding a dashboard sign flip.

Next: [Bring in a run](../operate/BRING_IN_A_RUN.md).
