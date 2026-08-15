# Superstructure Studio

Use **Robot → Superstructure Studio** when multiple generated mechanisms must move as one
coordinated behavior. Examples include raising an arm before extending an intake, or selecting a
complete scoring posture that sets elevator height, wrist angle, and roller state together.

Do not use a superstructure for one mechanism. Its feedback control, homing, limits, neutral
recovery, and hardware IO belong in [Subsystem Builder](SUBSYSTEM_BUILDER.md).

## Runtime flow

```text
named action or fresh cached sensor evidence
  → generated Redux superstructure coordinator
  → one complete target preset
  → generated subsystem target tasks
  → subsystem controller and safety contract
  → FTC/FRC or simulated IO
```

The coordinator never reads hardware and never writes a motor or servo. It references immutable
fields from generated subsystem descriptors. Hand-authored Kotlin is not guessed; it needs a typed
adapter before it can participate.

## Build one coordinator

1. Save the generated subsystems that need to move together.
2. Create a coordinator with a stable lowercase file ID.
3. Add each generated target field. ARES adds it to **every** posture with its declared neutral
   default.
4. Create named postures. Change values only after checking units, bounds, and mechanism clearance.
5. Select one startup posture and one fault-neutral posture. Fault values remain locked to each
   subsystem's declared neutral.
6. Add transitions:
   - **Driver/autonomous action request** uses a parameterless action from the project catalog.
   - **Fresh sensor condition** uses cached generated state and supports debounce.
   - **Time elapsed** waits in one posture for a bounded duration.
7. Add interlocks only when one numeric measurement must clamp another mechanism's numeric target.
8. Add a lookup table when a numeric target depends on numeric evidence. The on-screen table is an
   educational authoring preview, not a robot physics simulation.
9. Resolve every project error, then review the exact hash-bound save.

The canonical file is stored at:

```text
.ares/superstructures/<stable-id>.aressuperstructure
```

Immutable recovery snapshots are stored under:

```text
.ares/history/superstructures/<stable-id>/<content-hash>.aressuperstructure
```

Normal robot builds regenerate disposable Kotlin under the robot module's `build/generated/ares`
directory. Never hand-edit that generated plumbing.

## Failure behavior

Before changing posture, runtime code verifies that every generated subsystem target task exists
and that every dynamic value is finite and type-correct. Only then does it dispatch the complete
preset. Missing tasks or failed application enter the explicit fault posture and attempt its full
neutral target set. At most one transition is consumed per robot loop.

These rules prevent a half-applied posture in software. They do not prove physical clearance,
motor direction, sensor polarity, wiring, or safe current. Verify first in simulation, then use the
team's supervised restrained-hardware process when a robot is available.

## What is and is not no-code

Generated subsystem descriptors plus this Studio produce a buildable generated coordination
runtime without hand-written Kotlin. Existing hand-authored season mechanisms remain user-owned and
require explicit adapters; ARES does not scan source or invent constructors. Competition-specific
strategy may still require code when it cannot be expressed as bounded named actions, presets,
guards, interlocks, lookup tables, and routines.
