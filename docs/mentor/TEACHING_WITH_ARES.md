# Teaching with ARES Analytics

This guide helps a mentor run a simulator-first lesson in which students learn to identify evidence before they control anything.

## Recommended learning sequence

1. **Simulator:** observe current data without physical hardware risk.
2. **Imported replay:** make claims from repeatable historical evidence.
3. **Live robot:** apply the same observations under the team's normal enable/disable and field safety rules.
4. **Cloud Sync:** discuss collaboration/backup only after students understand that local data is authoritative.

Do not start a new student with a live robot merely because its connection is convenient. The simulator teaches the same target/topic/mode distinctions with better reset and recovery.

## A 45-minute first lesson

### Learning outcomes

By the end, a student should be able to:

- choose the correct robot workspace;
- identify **Live Robot**, **Local Sim**, and **Replay** without guessing;
- name one telemetry topic/value and its unit;
- stop the Analytics-managed simulator;
- explain why a live stream is not yet a persistent run; and
- find successful or quarantined import evidence.

### Roles for a group

- **Navigator:** reads the task guide and states the next step.
- **Operator:** uses the mouse/keyboard only after repeating the requested action.
- **Observer:** watches target, mode, connection, and one chosen value.
- **Data steward:** records the workspace, source, time, units, and result.
- **Safety lead:** controls the physical safety checklist. In a simulator-only lesson, this student verifies that **Local Sim** remains selected.

Rotate roles rather than letting the most experienced student perform every action.

### Activity

1. Use [First launch](../start/FIRST_LAUNCH.md) to create/select one workspace.
2. Open **Help & Learn → First mission** and assign one student to read the lesson coach aloud.
3. Before launching anything, ask each student to predict which indicators will change.
4. Follow [Connect the simulator](../start/CONNECT_SIMULATOR.md).
5. Choose one value, such as X pose, heading, battery, or mechanism state. Record:
   - source: simulator;
   - topic/widget;
   - unit;
   - expected behavior;
   - observed behavior.
6. Pause and ask: “Would this same number mean the same thing in replay?” The unit and topic can be the same, but the time/source is historical.
7. Stop the simulator cleanly.
8. If the activity created a completed log, follow [Bring in a run](../operate/BRING_IN_A_RUN.md), then replay the same evidence.
9. End with a one-minute student handoff: they must name the workspace, source mode, success signal, and recovery action.

Robot Academy deliberately separates **Observed by ARES** checkpoints from **Your reflection**.
A process or connection fact may be recorded automatically; source interpretation, learning, code
quality, and physical safety must never be inferred from it. Practice marks are local reminders, not
grades or certification.

## The evidence loop

Use this loop for every lab, fault, or tuning discussion:

1. **Question:** What behavior are we trying to understand?
2. **Prediction:** Which topic should change, in which direction, and in what unit?
3. **Source check:** Live robot, simulator, or replay?
4. **Observe:** Capture the value, time, and operating state.
5. **Compare:** Did the evidence match the prediction?
6. **Change one thing:** Code, parameter, condition, or test—not several at once.
7. **Repeat or recover:** Stop safely, preserve evidence, and reset.

This keeps “the graph looks strange” from becoming an untraceable sequence of changes.

## Physical robot gate

Move from simulator/replay to **Live Robot** only when all applicable items are true:

- [ ] A designated adult/lead student owns enable/disable and emergency response.
- [ ] The correct workspace, league, robot, and live host were read aloud.
- [ ] The robot is on blocks or inside the approved test area for the planned motion.
- [ ] People, tools, cables, and game pieces are outside the mechanism/drivetrain envelope.
- [ ] Battery, radio/network, and driver-station state meet team standards.
- [ ] The student knows which Analytics actions are observational and which publish commands.
- [ ] Autonomous selection, remote drive, driver-station controls, and tuning pushes are mentor-approved.
- [ ] The team has a stop plan independent of the Analytics toolbar.

The Analytics **Stop** button ends Analytics-managed desktop build/simulator processes. It is not a robot emergency stop.

## Recovery script for students

Teach this short response before introducing failure:

1. **Hands off controls.** Release gamepad/keyboard inputs.
2. **Name the mode.** Live robot, Local Sim, or Replay.
3. **Make safe.** Use the proper driver-station/robot disable process for hardware; use Analytics **Stop** for its simulator.
4. **Preserve evidence.** Keep terminal text and log files; do not delete quarantine.
5. **Change nothing else.** Call the mentor and report the workspace, target, last action, and symptom.

## Assessment prompts

Use concrete prompts instead of “Do you understand?”

- “Point to the evidence that this is simulator data.”
- “If Wi-Fi disappears, which parts still work?”
- “Why doesn't a green NT4 indicator mean the robot is safe to approach?”
- “Where will a completed local log go after import?”
- “What is the difference between the `live-telemetry` session and an imported run?”
- “Which axis and sign convention does heading use?”
- “What should you preserve before retrying a quarantined log?”

## Layering advanced detail

Once students can complete the task without prompts, add one layer at a time:

- **Protocol layer:** topic names, types, NT4, leading-slash normalization.
- **Geometry layer:** field axes, radians, CCW-positive heading, field-to-canvas transform.
- **State layer:** Redux action → reducer → immutable state → IO output.
- **Estimation layer:** odometry versus EKF versus simulator ground truth.
- **Persistence layer:** stable-file detection, fingerprints, DuckDB sessions, replay baselines.
- **Operations layer:** ADB versus SSH/SCP, ports `5810` and `5002`, quarantine, cloud sync.

Have students cite the [Telemetry contract](../TELEMETRY_CONTRACT.md) or [Glossary](../learn/GLOSSARY.md) rather than memorizing unexplained acronyms.

The [Robot Academy guide](../learn/ROBOT_ACADEMY.md) describes all paths, checkpoint meanings,
lab boundaries, progress migration, and recovery behavior.

## Lesson preparation checklist

- [ ] Launch Analytics and the selected simulator once before class so dependencies are cached.
- [ ] Verify the workspace points to the intended robot project.
- [ ] Verify **Local Sim** reaches NT4 port `5810` and produces at least one obvious changing value.
- [ ] Prepare one known-good completed log and one safe example of an import failure if teaching quarantine.
- [ ] Keep cloud sign-in out of the critical path; local lessons must work offline.
- [ ] Decide which students may operate and which controls are out of scope.
- [ ] Leave time for every student to perform the stop/recovery script.
