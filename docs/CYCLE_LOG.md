# ARES Product Improvement Cycle Log

This log records evidence for simulator-first product cycles. It is not a claim that desktop or
simulator verification proves physical robot safety.

## Cycle 1 — Robot Academy and Robot Studio truthfulness

### Objective

Give a novice a discoverable first mission and one project-wide place to understand what is ready,
what is missing, what is optional, and what requires code or supervised physical validation.

### User-visible outcome

- Robot Academy now has six role paths: New student, Driver/operator, Robot builder, Autonomous
  developer, Data analyst, and Mentor.
- Lessons have prerequisites, recommended next work, resumable active state, durable versioned
  checkpoints, and persistent contextual coaching.
- The First Mission records only observable app facts for Local Sim selection, simulator process
  state, and local NT4 connectivity. Interpretation and safety decisions remain explicit human
  checkpoints.
- Robot Studio presents the twelve project stages in one guided view, names the exact canonical
  evidence it found, routes to the existing specialist screen, and shows status with words and
  icons in addition to color.
- FTC generated subsystems are installed in the season runtime instead of merely compiling.
- Drivebase choices are filtered by league. Differential and custom templates are clearly marked
  `CODE REQUIRED` and cannot be saved as complete no-code configurations.
- Generated recovery and calibration capabilities use one-shot Redux requests, fail-closed health
  gates, successful neutral recovery, and a neutral hold until a later explicit target command.
- Terminal output uses an explicit semantic foreground, including readable dark-theme handling for
  ANSI black.

### Repositories and ownership

- **ARESLib-Kotlin:** shared generated subsystem actions, safety sequencing, startup rollback, and
  cross-platform drivebase validation.
- **ARES-FTC:** generated subsystem lifecycle installation and simulator parity tests.
- **ARES-Analytics:** Academy, Robot Studio/readiness evidence, league-aware drivebase UX, nested
  local validation-repository propagation, terminal contrast, tests, and teaching documentation.
- **ARES-FRC:** no source change in this cycle; full consumer validation was still required and run.

User-owned subsystem Kotlin remains user-owned. The generator does not scan arbitrary Kotlin,
overwrite unknown source, or silently replace generated starters. Existing specialist builders
remain the source of editing behavior; Robot Studio is an evidence-and-routing layer.

### Verification evidence

All consumer builds used:

```text
-ParesRepository=file:///C:/Users/david/dev/robotics/ares/ARESLib-Kotlin/build/release-repository
```

No `mavenLocal()` fallback was used.

- ARESLib focused generator/capability/registry tests: passed.
- ARESLib full test suite: passed.
- ARESLib `apiCheck`: passed after intentional API baseline review.
- ARESLib `publishReleaseValidation`: passed; 5.0.0 artifacts were present in the isolated
  `org.aresfirst.ares` repository.
- FTC generation and generated-project verification: passed.
- FTC TeamCode and simulator: 101 tests, 0 failures; simulator compile and Android debug assembly
  passed.
- FRC generation, verification, tests, coverage, and build: 95 tests, 0 failures.
- Analytics focused Academy/Studio/drivebase/process tests: 57 tests, 0 failures.
- Analytics full repository tests: app 409, gateway 15, shared 13; 0 failures, 2 intentional app
  skips.
- Dashboard smoke: 12,000 expected and persisted frames, 0 drop, successful Parquet round-trip,
  and no performance violations.
- Trimmed packaged-runtime project loading: passed for canonical routine and subsystem documents.
- Visual walkthrough: normal theme, colorblind + high contrast + large text together, Robot Studio,
  Academy, Local Sim launch, NT4 connection, and automatic 1/5 → 3/5 First Mission progress.
- Release MSI bytecode shrinking was disabled because the desktop application intentionally uses
  reflective/platform-specific DuckDB, Ktor, JNA, and LWJGL entry points. The executable jlink
  runtime remains validated. MSI construction then stopped at the expected protected production
  OAuth client/broker configuration gate; no placeholder credential was embedded.

### Delivery

Protected pull requests were opened in dependency order, passed their required checks, and were
merged to `master`:

- ARESLib-Kotlin: [PR #25](https://github.com/ARES-23247/ARESLib-Kotlin/pull/25)
- ARES-FTC: [PR #25](https://github.com/ARES-23247/ARES-FTC/pull/25)
- ARES-Analytics: [PR #38](https://github.com/ARES-23247/ARES-Analytics/pull/38)

### Remaining limitations after Cycle 1

- Production MSI packaging requires the protected Google Desktop OAuth client ID and HTTPS broker
  URL in the release workflow. This local shell intentionally did not have them.
- Robot Studio reports canonical evidence and routes work, but project scaffolding and several
  specialist builder flows still require further novice-focused refinement.
- AI remains proposal-only. Builder form assistance must continue through structured validation,
  diff, undo, and explicit review before it can be called complete.
- No physical robot was available. Hardware-in-the-loop validation remains required for generated
  IO, neutral recovery, calibration/homing, current behavior, and real actuator direction.

## Cycle 2 — Guided run evidence review

### Objective

Give a novice one read-only route from selecting an imported run to understanding what was
measured, what remains a hypothesis, and what safe tool to open next—without starting from SQL or
a comparison spreadsheet.

### User-visible outcome

- **Analysis → Guided Run Review** is now the default Analysis destination; advanced Run History
  remains available through progressive disclosure.
- Runs are listed and analyzed only when team, season, and robot identity exactly match the active
  workspace.
- The review shows source provenance, decoder and SHA-256 when available, persisted timestamp
  range, units, historical freshness, and an explicit qualitative confidence explanation.
- Measured threshold evidence is visually and structurally separate from possible causes and safe
  verification steps. Finding timestamps are shown in seconds.
- Recent-run baselines are restricted to the same team, season, and robot; cross-workspace data can
  neither appear in the picker nor become a comparison baseline.
- Suggested actions only navigate to existing replay, import, tuning, Academy, or advanced-history
  tools. The review never writes tuning, source, or hardware output.
- Evidence export uses the existing checked atomic-file path and preserves the original session.
- Robot Studio and the Academy comparison lesson route to the guided review.

### Safety and ownership decisions

- Existing deterministic diagnostic, advanced-analytics, driver-review, import-archive, replay,
  and tuning services remain the owning implementations. Guided Run Review composes them rather
  than inventing a second analyzer.
- Source identity is reported as incomplete or ambiguous instead of selecting a file silently.
- Confidence is bounded at **Moderate evidence**. Historical desktop evidence is never described
  as causal proof, live freshness, or physical safety evidence.
- Raw tables and SQL remain in the existing advanced tools; they are not removed.

### Verification evidence

All Gradle validation used:

```text
-ParesRepository=file:///C:/Users/david/dev/robotics/ares/ARESLib-Kotlin/build/release-repository
```

- Focused analytics, workspace-race, navigation, and Robot Studio tests: 17 tests, 0 failures.
- Full Analytics repository tests: app 415, gateway 15, shared 13; 0 failures, 2 intentional app
  skips.
- Dashboard smoke and performance baseline: passed with 12,000 expected/persisted frames, zero
  drop, 12 ms query p95, exact Parquet restore, and no budget violations.
- Trimmed distributable project loading: passed for one routine and one subsystem document.
- The interactive app launched and reached the real selected workspace. The automated screenshot
  session was stopped at the Windows PIN screen; no PIN was entered, so a post-unlock visual pass
  remains outstanding. Static review confirmed responsive `LazyColumn`/`FlowRow` layout, semantic
  headings, text-labelled actions, semantic accent foregrounds, and no color-only status.
- No physical robot was used or required for this desktop, persistence, and analysis slice.

### Delivery

Implementation is on `codex/guided-run-analysis-v2`. A protected pull request and hosted checks
are the remaining delivery steps for this cycle.

### Remaining limitations and next cycle

- Perform the normal/colorblind/high-contrast/large-text visual walkthrough after the desktop is
  unlocked.
- Guided review currently links to existing builder/tuning destinations at the tool level; future
  work can add validated deep links to a specific parameter or mechanism when the finding carries
  a stable canonical ID.
- No physical validation is claimed. Any later physical recommendation still needs a supervised
  team procedure.

## Cycle 3 — Homing, freshness, and safe-recovery teaching lab

### Objective

Give a robot-builder student a hardware-free way to understand why homing and fault recovery need
valid cached evidence, freshness, bounded dwell, and a confirmed neutral write.

### User-visible outcome

- Robot Academy now includes a fifth interactive lab, **Homing & safe recovery**.
- Students can compare digital-sensor, current-stall, velocity-stall, and combined-stall evidence.
- The model exposes configuration health, cached-feedback age, measurement validity, evidence
  dwell, home-reference state, a latched output fault, and successful or failed neutral recovery.
- The decision is stated as **MOTION PERMITTED** or **MOTION BLOCKED** with explanatory text and an
  icon; color is supplemental.
- The Robot builder path includes the lab after safe subsystem design.
- The mentor guide includes objectives, an activity sequence, misconceptions, and explicit model
  and physical-safety limits.

### Safety and ownership decisions

- The lab is a pure immutable teaching model. It owns no NT4 publisher, service, project file,
  simulator command, or hardware reference.
- Stale or invalid required evidence fails closed and resets the modeled dwell.
- A latched output fault clears only after the modeled neutral write succeeds.
- Establishing a home reference does not bypass later configuration, freshness, validity, or fault
  checks.

### Verification evidence

- Focused model and catalog tests: 13 tests, 0 failures.
- Full Analytics app suite: 427 tests, 0 failures, 0 errors, 2 intentional skips.
- Dashboard smoke: passed.
- Trimmed distributable project loading: passed for one routine and one subsystem document.
- `git diff --check`: passed; only line-ending notices were emitted.
- No ARESLib contract changed in this slice, so no local validation-repository publication or
  consumer dependency substitution was required.

### Delivery and limitations

- Implementation is on `codex/academy-homing-safety-lab-v5`, based on the still-open safe-build
  Analytics change so its current Academy wording is preserved. It must be rebased or retargeted
  after that dependency merges before opening its protected PR.
- A post-unlock visual walkthrough in normal, colorblind, high-contrast, large-text, keyboard, and
  narrow-window modes remains required. The available desktop capture is still blocked by the
  Windows PIN screen; no credential was entered or bypassed.
- No physical robot was used. Real thresholds, directions, neutral behavior, and homing mechanics
  still require supervised hardware-in-the-loop validation.
