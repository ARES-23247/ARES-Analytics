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

Protected pull requests were opened in dependency order. Required checks were running when this
cycle log was last updated:

- ARESLib-Kotlin: [PR #25](https://github.com/ARES-23247/ARESLib-Kotlin/pull/25)
- ARES-FTC: [PR #25](https://github.com/ARES-23247/ARES-FTC/pull/25)
- ARES-Analytics: [PR #38](https://github.com/ARES-23247/ARES-Analytics/pull/38)

### Remaining limitations and next cycle

- Production MSI packaging requires the protected Google Desktop OAuth client ID and HTTPS broker
  URL in the release workflow. This local shell intentionally did not have them.
- Robot Studio reports canonical evidence and routes work, but project scaffolding and several
  specialist builder flows still require further novice-focused refinement.
- The guided Analyzer workflow remains the highest-priority incomplete product slice: it must lead
  from run selection through evidence, inference, safe next action, and builder/lesson links.
- AI remains proposal-only. Builder form assistance must continue through structured validation,
  diff, undo, and explicit review before it can be called complete.
- No physical robot was available. Hardware-in-the-loop validation remains required for generated
  IO, neutral recovery, calibration/homing, current behavior, and real actuator direction.
