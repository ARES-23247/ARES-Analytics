# Subsystem authoring

The Subsystem Builder is an offline, project-backed editor under **Robot → Subsystem Builder**. It
creates the same canonical `.ares/subsystems/*.aressubsystem` documents consumed by Gradle and the
hand-authored DSL. A robot connection and cloud account are never involved.

The builder deliberately preserves separate domain, control, hardware, simulation, lifecycle, and
verification responsibilities. File count is not a design goal. The artifact plan groups the output
into **Domain**, **Control**, **Hardware**, **Simulation**, **Generated Plumbing**, and
**Verification**, and explains both the owner and destination of every file.

## Ownership labels

- **USER-OWNED** is normal source code. Generation never replaces it.
- **GENERATED STARTER** is an initial, documented customization point. If a starter already exists
  and differs, the builder shows a line-oriented diff and requires explicit confirmation before it
  can be replaced.
- **GENERATED — DO NOT EDIT** is deterministic registration, lifecycle, or DSL plumbing written to a
  Gradle generated-source directory. Change the canonical subsystem document or generator instead.

Generated plumbing is collapsed by default in the UI. This keeps attention on the files a robot
team is expected to understand and customize without hiding the runtime wiring.

## Runtime contract

Every generated or hand-authored subsystem follows one flow:

```text
Input → Redux action/reducer → immutable state → controller → IO contract → FTC or simulated adapter
```

Reducers are pure. Controllers decide outputs from immutable state and a cached input snapshot.
The platform adapter performs all hardware reads once during its refresh/read phase, stores the
results in the supplied snapshot, and writes only the already-decided outputs. The mock adapter must
implement the same contract and fault behavior as the FTC adapter.

Every target state becomes a typed capability action automatically (for example,
`subsystem.elevator.set.targetMeters`). The controls editor and routine builder discover these
derived actions; do not duplicate them in `action-catalog.json` or add handwritten glue methods.

## Capability templates

Templates select behavior and safety capabilities; they are not “fewer files” profiles.

| Template | Start here when… | Safety emphasis |
|---|---|---|
| Simple actuator | A motor or servo only needs bounded open-loop output. | Neutral output, output-write faults, current validity. |
| Position-controlled mechanism | A mechanism tracks a position measurement. | Soft limits, stale feedback, bounded position control. |
| Velocity-controlled mechanism | A flywheel or conveyor tracks speed. | Stale feedback, current monitoring, safe spin-down. |
| Sensor-only subsystem | The subsystem observes without commanding an actuator. | Cached snapshots, signal validity, close behavior. |
| Homed mechanism | Motion is unsafe until a home reference is established. | Homing gate, calibration status, soft limits, fault recovery. |
| Composite mechanism | Multiple coordinated devices form one mechanism. | Atomic snapshots, coordinated neutral, partial-failure handling. |
| Advanced/custom | The standard templates do not express the mechanism. | Every safety choice must be completed explicitly. |

Applicable templates declare motors, servos, sensors, cached inputs, supported control modes,
homing/calibration, soft limits, current monitoring and validity, safe neutral output, configuration
health, fault latching with explicit neutral recovery, telemetry, and autonomous actions/resources.
The builder reports missing safety decisions before generation rather than inventing permissive
defaults.

## Builder workflow

1. Select a capability template and create a new subsystem draft.
2. Map hardware and define the cached input snapshot.
3. Define immutable target, measurement, status, and configuration state.
4. Connect control behavior and complete every applicable safety setting.
5. Review the grouped artifact plan, module destinations, ownership labels, and generated Kotlin.
6. Save the canonical document revision.
7. Review any starter replacement diff. Confirm only when discarding the existing customization is
   intentional.
8. Generate, then run the generated contract tests and the project test suite.

Saving creates immutable history under `.ares/history/subsystems`. **Save & Generate** invokes the
selected repository's Gradle wrapper. Generated output is deterministic: unchanged input produces
byte-for-byte identical output, user-owned files are protected, and starter replacement is never
silent.

## Writing a subsystem by hand

Hand authoring uses the same boundaries as the generator. A good implementation contains explicit
customization points for domain state/reducer behavior, controller policy, the IO contract, the FTC
adapter, and the simulated adapter. Mechanical registration and lifecycle integration should remain
generated when possible.

### Domain

- Define immutable state with safe defaults. Disabled or not-yet-configured state must imply neutral
  output.
- Define typed actions and a pure reducer. Do not access hardware, time, or mutable global state from
  the reducer.
- Use stable action keys and typed arguments so autonomous routines and controls can validate them.

### Controller

- Consume immutable state plus one cached input snapshot.
- Gate closed-loop output on configuration health, fresh/valid feedback, required homing, and the
  absence of a latched fault.
- Clamp commands to soft limits and declared output bounds.
- A failed output write must latch a fault. Recovery requires an explicit neutral command followed
  by the documented reset action; a non-neutral command must never clear the latch.
- Keep the periodic path allocation-free. Preallocate buffers and avoid collections, iterators,
  reflection, temporary arrays, and freshly constructed geometry values in the loop.

### IO contract and adapters

- Expose a mutable, reusable input snapshot owned by the subsystem. `refresh(inputs)` updates every
  field once per loop; getters must not trigger hardware reads.
- Expose explicit neutral and close/resource-cleanup behavior.
- Return or record write success so the controller can latch failed writes.
- Distinguish “zero amps” from “current reading unavailable.” Treat validity as data.
- The simulated adapter must match FTC behavior for clamping, invalid/stale feedback, faults,
  homing, neutral recovery, and close semantics—not merely nominal motion.
- Use `RobotClock` for freshness and timeouts; never call system wall/monotonic clocks directly in
  reusable robot code.

### Verification checklist

At minimum, cover:

- safe startup and neutral default output;
- disabled and stop behavior;
- invalid and stale feedback;
- failed output writes and fault latching;
- homing/calibration requirements;
- explicit neutral recovery;
- current-reading validity and monitoring;
- FTC/mock behavioral parity;
- idempotent close/resource cleanup; and
- zero-allocation periodic paths where applicable.

Prefer one contract-test suite that is run against both the FTC test adapter and mock adapter. Add
mechanism-specific tests beside it instead of weakening or replacing the shared safety contract.

## Build integration

Canonical documents are the source of truth. Gradle owns generated-source directories and verifies
that generated output is current before compilation. When ARESLib changes, publish it to local Maven
first, then generate/build FTC and the simulator in dependency order.

See `ARESLib-Kotlin/docs/subsystem-dsl.md` for the shared DSL and code examples. The generated source
also contains ownership headers, KDoc on customization points, safety invariants, and links back to
the canonical document so it can serve as an executable example for hand-authored subsystems.
