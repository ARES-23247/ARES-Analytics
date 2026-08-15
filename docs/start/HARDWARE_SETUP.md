# Hardware Setup

Hardware Setup is the bridge between a simulated robot project and a reviewed physical wiring plan.
It does not replace the Drivebase Builder or Subsystem Builder, and it does not scan Kotlin source.

## Where hardware values belong

- Drive motors, localization sensors, CAN buses, geometry, and drivetrain inversion stay in
  `.ares/drivetrains/*.aresdrivetrain`.
- Mechanism motors, servos, sensors, channels, safe outputs, current limits, and follower
  relationships stay in `.ares/subsystems/*.aressubsystem`.
- Hardware Setup reads both sources and reports one combined inventory. Use its links to edit the
  owning builder instead of creating a second mapping.

## What the review proves

Beside the disabled robot, compare every listed device with the robot configuration and wiring
diagram. Confirm all five checks:

1. The device exists and the wiring diagram matches.
2. Hardware-map names, CAN IDs and buses, PWM/DIO/analog channels match the controller.
3. Device inversion and leader/follower direction match the mechanism.
4. Every actuator has a reviewed safe neutral and disabled/stop behavior.
5. Current, soft, motion, homing, and feedback limits are reviewed where applicable.

ARES writes `.ares/hardware-review.json` with the exact descriptor inventory hash and the reviewer's
name. Any drivetrain or subsystem edit changes that hash and marks the review **stale**. Re-review the
new mapping; do not edit the review JSON by hand.

## What the review does not prove

A current review is not a powered hardware test, calibration result, inspection approval, or proof
that a mechanism is safe to move. Follow your team's supervised bring-up procedure, test one device
at a time at low output, and preserve logs.

Downloaded Team 23247 season starters remain **simulation/reference only** even after a review.
Their hand-authored composition is not yet fully represented by GUI-owned descriptors, so the
inventory cannot prove that every physical device was reviewed. A future generic composition may
use the review-required policy only after that completeness is verified. ARES will still block it
whenever the review is missing, stale, invalid, or has address conflicts.
