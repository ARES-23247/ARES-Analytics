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

The builder uses seven guided stages. You can move backward at any time; advanced settings remain
collapsed until you need them or a validation problem points to them.

1. **Purpose** — choose a capability template, name the subsystem, and explain what it should do.
2. **Hardware** — add motors, servos, and sensors using the exact Robot Controller configuration
   names. Each declared measurement is cached once per robot loop. Adding hardware also adds its
   normal explicit state: motor position/velocity/current, servo command/position, or the sensor's
   typed reading. Add extra mechanism state only when it has meaning beyond those signals.
3. **State & behavior** — distinguish observed status from requested targets, then connect bounded
   controller rules to actuators.
4. **Safety** — complete feedback, homing, current, configuration-health, neutral-output, and fault
   recovery requirements. The summary shows the protections currently enabled.
5. **Capabilities** — review the typed driver/autonomous actions that the subsystem exposes.
6. **Simulation & testing** — choose mock support and generated contract verification so the design
   can be exercised without a physical robot.
7. **Review** — resolve warnings, inspect ownership and module destinations, then save or generate.

Save creates the canonical document revision. Review any starter replacement diff and confirm only
when discarding the existing customization is intentional. Generate, then run the generated
contract tests and the project test suite.

Saving creates immutable history under `.ares/history/subsystems`. **Save & Generate** invokes the
selected repository's Gradle wrapper. Generated output is deterministic: unchanged input produces
byte-for-byte identical output, user-owned files are protected, and starter replacement is never
silent.

Every major editor card has a keyboard-focusable help button and hover explanation. Longer concepts
link to this guide. The homing and feedforward sections include small interactive labs; those labs
only explain the configured math and never connect to or command robot hardware.

## Homing

Homing establishes where a mechanism is physically located before normal motion is allowed. ARES
supports several explicit evidence sources:

- **Digital sensor** — a limit switch, beam break, or other Boolean home signal.
- **Current stall** — current remains above a threshold while moving with a small bounded output.
- **Velocity stall** — measured speed remains near zero while a bounded homing output is applied.
- **Current and velocity stall** — recommended sensorless method: require both high current and low
  velocity, so ordinary drag or an encoder glitch is less likely to be mistaken for the hard stop.
- **Custom measurement** — an advanced combination of cached typed signals.

Sensorless homing is not “drive until something happens.” The generated controller requires an
explicit homing request, fresh and valid cached measurements, a limited search output, continuous
evidence for the configured dwell, and a hard attempt timeout. It neutralizes before assigning the
home position. Timeout, reset, or output-write failure latches a fault; a successful neutral cancel
is required before retrying. Teams must choose a homing voltage low enough not to damage the
mechanism and validate it on the real robot when hardware becomes available.

## Feedforward

Feedback and feedforward solve different problems:

- **PID feedback** observes target error and corrects it.
- **Feedforward** predicts the output required for the requested motion before error develops.

The editor offers **simple motor** (`kS`, `kV`, `kA`), **elevator** (motor terms plus constant `kG`),
and **arm** (motor terms plus `kG × cos(angle)`) models. The interactive preview shows the predicted
voltage for velocity, acceleration, and angle; PID correction is added afterward. Units matter:
`kV` and `kA` must match the units of the selected desired-velocity and acceleration fields, while
arm angle is radians. Start with SysId data when possible and validate all gains in simulation before
careful hardware testing.

## Leader and follower actuators

Use **Command source → Follow …** when two motors or servos should always receive one command. This
is also called master/slave control in older documentation. A follower cannot own a second controller
rule, preventing two policies from fighting the same mechanism.

- Motors and continuous servos may follow in the same or inverted direction.
- Positional servos may follow the same position or mirror it around the 0–1 range.
- Physical FTC/FRC adapters and mock IO use the same transform.
- Neutral output, output-fault latching, cleanup, and verification cover the full group. A failed
  follower write safes the group rather than allowing asymmetric continued motion.
- **Reverse hardware direction** is a separate per-device setting for reversed physical mounting.
  The follower transform is applied first and mounting reversal second; using both deliberately
  reverses twice.

## AI-assisted form filling

The **Help me design this** card sends the current subsystem form and a student's plain-language
request to the Gemini provider configured in Profile. It does not send Kotlin source, robot logs,
network telemetry, or credentials. Gemini returns a complete form proposal—not repository writes.

A useful request describes the physical parts and the safe behavior, for example:

> Add a second motor that follows the lift motor in the opposite direction. Home downward using
> fresh current above 7 A and low velocity for 250 ms, stop the attempt after 3 seconds, and use
> elevator feedforward with position feedback.

You do not need to know the descriptor field names. The assistant should translate the physical
description into the form; hover or press the help icon beside any proposed field to learn what it
means. If important information is unknown—such as a safe current threshold—leave it unresolved
and ask a mentor rather than accepting a guess.
Students see plain-language reasoning, local validation results, and a structured before/after diff
before choosing **Apply to form** or **Discard proposal**. Applying creates one normal Undo step;
Save and Generate remain separate explicit actions. Protected platform, revision, source ownership,
hand-authored class metadata, and catalog action keys are restored locally even if an untrusted model
tries to change them. Accepted changes must still pass deterministic local validation, safety review,
ownership checks, and starter replacement confirmation. AI will never
silence safety warnings, invent a successful hardware test, generate around invalid data, or
overwrite USER-OWNED Kotlin. This proposal boundary also allows the form to remain usable offline
when Gemini is not configured.

Before applying, check the proposal in this order:

1. **Hardware:** device types, wiring names/IDs, physical reversal, and follower relationships.
2. **Safety:** neutral output, feedback freshness, current validity, limits, homing dwell, and timeout.
3. **Control:** measurement/target units, output bounds, feedback gains, and feedforward terms.
4. **Simulation:** mock support and failure cases that can be tested without a robot.

Gemini is a teaching and form-filling aid, not evidence that a mechanism is safe on hardware.

## Registering a subsystem that is already written by hand

ARES does not scan Kotlin and guess which classes form a subsystem. Imports, factories, aliases,
and conditional hardware construction make that unreliable. Instead, create a hand-authored
`.aressubsystem` descriptor and explicitly identify:

- the owning Gradle module and USER-OWNED source files;
- subsystem, IO-contract, hardware-adapter, and optional simulator class names;
- simulation support and teaching level;
- the existing action-catalog keys that drivers and autonomous routines may invoke; and
- the same hardware, state, and safety responsibilities documented by generated subsystems.

Hand-authored registration never emits or replaces Kotlin starters. Generated plumbing includes a
registration reminder while the season composition root remains responsible for constructing the
implementation. Catalog validation fails when a declared action key is missing, so the GUI cannot
silently advertise a behavior the robot does not implement.

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

See [Hand-authored subsystem prototype](SUBSYSTEM_HAND_AUTHORED_PROTOTYPE.md) for the measured
Indicator/Prism comparison and the evidence gate used before considering Intake or Flywheel.
