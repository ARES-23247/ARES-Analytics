# Robot Academy classroom pilot

This guide runs a small, offline-first Robot Academy pilot before a team adopts the curriculum more broadly. It uses a verified ARES starter project, synthetic practice runs, written student reflections, and mentor review. It does not require Google Drive or a physical robot.

## Prepare once

1. Install JDK 17 and launch ARES Analytics.
2. Open **Help & Learn → Classroom & mentor toolkit**.
3. Select the intended workspace, or choose **Create practice workspace** and then **Create new** in first-run setup. ARES downloads a hash-pinned official FTC or FRC starter and caches the verified archive for later offline reuse.
4. Select **Install offline practice runs**. ARES asks for confirmation, then adds:
   - `.ares/academy/practice-runs/baseline-arm-run.csv`;
   - `.ares/academy/practice-runs/stalled-arm-run.csv`; and
   - a README identifying both files as synthetic teaching data.
5. Open **Imports** and import both CSV files when the run-analysis lesson calls for them.

The installer never replaces existing practice files. If a file with the same name has different bytes, installation stops and preserves that file.

## Run a student session

1. Enter a local student display name. Avoid email addresses or other unnecessary personal information. This pilot stores one active learner record per operating-system account.
2. Choose one learning path. The toolkit shows practiced lessons, recorded checkpoints, and the recommended next lesson.
3. Start or resume the recommended lesson. Automatic checkpoints record only narrow app facts. Student checkpoints require a written observation or explanation.
4. Ask the student to include:
   - what they predicted;
   - the named evidence and unit;
   - whether the source was a teaching model, simulator, imported run, or robot;
   - what the evidence supports; and
   - what it does not prove.
5. Add a mentor note only after discussing the evidence. Use the rubric prompts to record the current level; do not infer a rating from completion counts.
6. Export the selected path's Markdown learning record. The export contains local Academy progress, reflections, mentor notes, and rubric ratings. It does not contain telemetry rows, OAuth tokens, or robot credentials.

## Restart and reuse

- **Restart lesson** removes only that lesson's practiced mark, checkpoints, reflections, and mentor note, then starts it again.
- **Restart this path** removes those fields for every lesson in the path. If a lesson belongs to multiple paths, it restarts in all of them because lesson progress is shared.
- Rubric ratings remain after a path reset so a mentor can intentionally revise them after new evidence. Selecting **Not reviewed** removes a stored rating.
- Export before resetting if the team needs a record.
- Before another student uses the same OS account, export the current record and choose **Start new student record**. Confirming this removes all prior Academy progress, reflections, notes, and rubric ratings so evidence is not silently mixed.

## Suggested 60-minute pilot

| Time | Activity | Evidence boundary |
| --- | --- | --- |
| 0–10 min | First mission and source identification | Process/connection state is not understanding |
| 10–25 min | Input, state, and telemetry lab | Teaching trace is not generated runtime execution |
| 25–40 min | Import the two synthetic runs | Synthetic CSV is not simulator or robot evidence |
| 40–50 min | Guided comparison and written claim | A possible cause is not an observation |
| 50–60 min | Mentor rubric and export | Local coaching record is not certification |

## Pilot review questions

- Could a student resume without a mentor reconstructing their place?
- Did the student use written evidence instead of checking boxes reflexively?
- Did any label imply that simulation or generated code proved physical safety?
- Could every student identify the active project, data source, and unit?
- Was text readable without relying on color?
- Did the lesson fit the available screen size and input method?
- Which instruction required unexplained developer terminology?

Record those answers outside the rubric as curriculum feedback. The rubric describes a student's current practice; it is not a product-usability survey.
