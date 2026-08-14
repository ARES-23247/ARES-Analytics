# Learn with Robot Academy

Robot Academy is the in-app route from “I have never used ARES” to creating and reviewing robot behavior with evidence. It combines real application tasks with simplified interactive models. It works offline, and its first mission requires no physical robot.

## Start with First mission

Open **Help & Learn → First mission**. The lesson coach keeps the current checkpoint visible while you move between Academy, Dashboard, and the execution toolbar.

The first mission asks you to:

1. select **Local Sim**;
2. start one Analytics-managed simulator;
3. wait for the local simulator and NT4 connection to report ready;
4. open Dashboard, identify one changing value, and name its source; and
5. stop the simulator.

ARES can record process and connection facts such as “Local Sim selected” or “simulator process running.” It cannot tell whether you understood a graph, followed a team safety procedure, or proved hardware safe. Those checkpoints remain explicit student reflections.

## Choose a path

| Path | Use it to | Physical robot needed? |
| --- | --- | --- |
| **New student · First mission** | Start Local Sim, identify the data source, preserve a run, and compare evidence | No |
| **Driver & operator** | Identify the active data source, review a safe mechanism action, map a control, and compare practice evidence | No for the simulator-first lessons |
| **Robot builder** | Describe a drivebase and subsystem, explore mechanism sizing and control, and review tuning evidence | No for authoring and simulation; hardware verification comes later |
| **Autonomous developer** | Study localization and motion limits, then preview a bounded routine | No for authoring and simulation |
| **Data analyst** | Import and compare runs, distinguish measurement from inference, and prepare reversible proposals | No for imported or simulated evidence |
| **Mentor** | Lead simulator-first activities and keep physical-validation boundaries explicit | No for the Academy material |

Prerequisites are recommendations, not hidden locks. You may preview a later lesson with a mentor. The status text says **Not started**, **In progress**, **Practiced**, or **Recommended later** so progress never depends on color alone.

If the first lesson in a role path depends on an earlier foundation, Academy recommends that prerequisite even when it belongs to another path. After you practice it, return to the role path and continue.

## Use learning checkpoints

Each lesson includes stable checkpoints. Two types are deliberately separate:

- **Observed by ARES** means the desktop observed a narrow fact such as a managed process or local NT4 connection.
- **Your reflection** means you recorded an interpretation in your own words.

Neither means graded, certified, code-reviewed, deployed, or physically safe. **Mark lesson practiced** is a private reminder stored on this computer.

## Use interactive labs well

Every lab starts with an outcome, model boundary, short experiment, reflection questions, and success description.

Use the same evidence loop each time:

1. Ask one question.
2. Predict what will change and name its unit.
3. Change one input.
4. Observe the result.
5. Compare it with the prediction.
6. State what the model cannot prove.
7. Reset before the next experiment.

The control-response, sensor-fusion, motion-profile, and mechanism-sizing labs are teaching models. They do not run the production robot algorithms, command hardware, save project constants, validate a mechanism, or prove field clearance.

## Resume and recover

Academy stores local progress in `.ares-analytics/learning-progress.json` under the current operating-system user. Older version-1 practiced marks are preserved when the richer checkpoint format is loaded. Migration never invents checkpoint evidence.

If the progress file is unreadable, Academy starts with empty progress instead of blocking the app. This does not affect robot projects, imported runs, or cloud data.

If a simulator lesson gets stuck:

1. release controls;
2. confirm the selected target says **Local Sim**;
3. use Analytics **Stop** for its managed process;
4. preserve the terminal text; and
5. ask a mentor before changing project or network settings.

The Analytics Stop action is not a physical robot emergency stop.

## For mentors

Ask for observable answers instead of “Do you understand?” Good prompts include:

- “Show me the text proving this is Local Sim.”
- “Name the value, unit, source, and expected direction.”
- “Which checkpoint did ARES observe, and which one did you decide?”
- “What can this model not prove?”
- “What would you preserve before changing one thing?”

Continue with [Teaching with ARES](../mentor/TEACHING_WITH_ARES.md) for a group lesson plan and physical-robot gate.
