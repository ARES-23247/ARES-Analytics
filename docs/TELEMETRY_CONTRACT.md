# ARES telemetry contract

This is the integration contract between ARESLib, the FTC/FRC season repositories, the simulator, and ARES Analytics. Update it in the same change as any topic rename or type change.

## Wire protocol

- Transport: NetworkTables 4 over WebSocket, normally port `5810`.
- Subprotocol: `v4.1.networktables.first.wpi.edu`.
- Control messages: JSON arrays containing `publish`, `unpublish`, `subscribe`, `unsubscribe`, `announce`, or `unannounce` messages.
- Value updates: a MessagePack stream of four-element arrays. Each array is `[topic-or-publisher-id, timestamp, type-id, value]`; multiple arrays are concatenated in one WebSocket frame without an outer batch array.
- Publisher IDs and subscriber IDs are scoped to one WebSocket connection.
- Topic names are normalized without leading `/` for storage and matching.
- An existing topic's declared wire type is immutable for its lifetime.

The dashboard subscribes with prefix matching to:

```text
ARES             Drive              Robot             Hardware
Topology         Tuning             Profiling         Diagnostics
Vision           Path               Gamepad1          Gamepad2
Superstructure   Calibration        SysId             Swerve
Mechanism        LoopTimeMs         TimestampMs
```

## Units and coordinates

| Quantity | Contract |
| --- | --- |
| field position | meters |
| linear velocity | meters per second |
| heading | radians, counter-clockwise positive |
| angular velocity | radians per second |
| loop time | milliseconds where key ends in `Ms`; otherwise document explicitly |
| voltage/current | volts/amperes |

Field heading `0` points along field `+X`; `π/2` points along `+Y`.

The dashboard canvas uses `canvasX = -fieldY` and `canvasY = -fieldX`. The robot icon requires a `-90°` image offset because the artwork points right at zero image rotation.

## Canonical pose topics

| Topic | NT4 type | Meaning |
| --- | --- | --- |
| `Drive/Pose_X` | `double` | fused/EKF field X |
| `Drive/Pose_Y` | `double` | fused/EKF field Y |
| `Drive/Pose_Heading` | `double` | canonical fused heading |
| `ARES/EstimatedPose` | `double[]` | `[x, y, heading]` fused or simulator pose |
| `ARES/EstimatedPose/0` | `double` | pose X compatibility scalar |
| `ARES/EstimatedPose/1` | `double` | pose Y compatibility scalar |
| `ARES/EstimatedPose/2` | `double` | pose heading compatibility scalar |
| `Drive/Odom_X` | `double` | raw odometry X |
| `Drive/Odom_Y` | `double` | raw odometry Y |
| `Drive/Odom_Heading` | `double` | raw odometry heading |

The simulator must publish `ARES/EstimatedPose` from physics ground truth (`currentPose`), not from a disconnected default Redux state.

## Drive and estimator diagnostics

| Topic | Type | Meaning |
| --- | --- | --- |
| `Drive/Velocity_X` | `double` | measured field-relative X velocity |
| `Drive/Velocity_Y` | `double` | measured field-relative Y velocity |
| `Drive/Velocity_Omega` | `double` | measured angular velocity |
| `Drive/EKF_Drift_X` | `double` | estimator X drift/error signal |
| `Drive/EKF_Drift_Y` | `double` | estimator Y drift/error signal |
| `Drive/Innovation_Theta` | `double` | latest heading innovation |
| `Robot/Odometry/Covariance` | `double[]` | `[Pxx, Pyy, Pθθ]` covariance diagonal |
| `Robot/Pose3d` | structured/raw | AdvantageScope-compatible pose |

Do not derive “drift” summary metrics from every key containing `EKF`; pose coordinates are not errors.

## Robot health

| Topic | Type | Meaning |
| --- | --- | --- |
| `Robot/BatteryVoltage` | `double` | robot supply voltage |
| `Robot/BrownoutPowerScale` | `double` | allowed output fraction `[0,1]` |
| `Robot/BrownoutState` | `string` | brownout guard state |
| `Robot/StateOfCharge` | `double` | estimated remaining battery fraction/percent as configured by producer |
| `Robot/LoopTimeMs` | `double` | main loop duration in milliseconds |
| `Profiling/LoopTime_ms` | `double` | loop-time compatibility topic |
| `Profiling/Hz` | `double` | loop frequency |
| `Diagnostics/Power/BrownoutCount` | `double` | cumulative trip count |

Summary code treats only explicit battery-voltage topics as battery voltage. Per-motor voltage is not a battery minimum.

## Hardware and topology

Per-device hardware metrics follow:

```text
Hardware/Motors/{device}/Power
Hardware/Motors/{device}/Velocity
Hardware/Motors/{device}/CurrentAmps
Hardware/Motors/{device}/Temperature
```

FTC drivetrain devices are normally `fl`, `fr`, `rl`, and `rr`. Dashboard widgets also accept `bl`/`br` compatibility aliases for rear motors.

`Topology/HardwareMap` is a string containing serialized `HardwareTopology`. It is generally published once at initialization and cached by robot identity.

## Vision and calibration

| Topic | Type | Meaning |
| --- | --- | --- |
| `Vision/HasTarget` | `boolean` | a usable target/measurement exists |
| `Vision/Target_X`, `Vision/Target_Y` | `double` | producer-defined target-space coordinates |
| `Vision/MeasurementCount` | `double` | number of current measurements |
| `Vision/Pose_X`, `Vision/Pose_Y` | `double` | primary vision field pose |
| `Vision/Pose_Heading` | `double` | primary vision heading |
| `Vision/Primary_TagId` | `double` | tag ID, `-1` when absent |
| `Vision/Primary_Ambiguity` | `double` | measurement ambiguity |
| `Calibration/IsActive` | `boolean` | calibration capture is active |
| `Calibration/GyroHeading` | `double` | robot gyro heading in radians |
| `Calibration/TagIndex` | `double` | selected tag index/ID |
| `Calibration/CameraIndex` | `double` | selected camera index |
| `Calibration/CameraToTag` | `double[]` | measured camera-to-tag transform parameters |
| `Calibration/TagField` | `double[]` | known tag field position/pose parameters |

Limelight target-space yaw is `-robotPoseTargetSpace.rotation.y`. Rotation Z is tilt/roll in that boundary and must not be treated as robot heading.

## Paths and superstructure

| Topic | Type | Meaning |
| --- | --- | --- |
| `Path/Active` | `boolean` | an active path exists |
| `Path/DistanceMeters` | `double` | current path progress |
| `Path/IsChained` | `boolean` | path is part of a chain |
| `Path/DetourActive` | `boolean` | dynamic detour is active |
| `Path/Error_CrossTrack` | `double` | cross-track error in meters |
| `Path/Error_AlongTrack` | `double` | along-track error in meters |
| `Path/Error_Heading` | `double` | heading error in radians |
| `Path/Points` | `double[]` | flattened `[x, y, heading, ...]` |
| `Superstructure/PackedState` | `double[]` | season-defined packed mechanism state |
| `Superstructure/IndicatorLight/{name}` | `double` | named light output/state |

Consumers of `PackedState` must be versioned alongside the season producer; the array has no self-describing field names.

## Dashboard-to-target inputs

The Analytics client reserves stable publisher IDs for common inputs, but receivers must depend on names and types rather than the numeric IDs.

| Topic | Type | Meaning/default |
| --- | --- | --- |
| `ARES/Input/vx` | `double` | requested X velocity, default `0` |
| `ARES/Input/vy` | `double` | requested Y velocity, default `0` |
| `ARES/Input/omega` | `double` | requested angular velocity, default `0` |
| `ARES/Input/isIntaking` | `boolean` | intake request, default `false` |
| `ARES/Input/isFlywheelOn` | `boolean` | flywheel request, default `false` |
| `ARES/Input/isTransferring` | `boolean` | transfer request, default `false` |
| `ARES/Input/isTeleopMode` | `boolean` | teleop mode request |
| `ARES/Input/isFieldCentric` | `boolean` | field-centric drive request |
| `ARES/Input/isRedAlliance` | `boolean` | alliance; simulator startup default is `true` |
| `ARES/Input/heartbeat` | `int` | dashboard liveness counter |
| `ARES/Input/isButtonAPressed` | `boolean` | virtual A button |
| `ARES/Input/isButtonBPressed` | `boolean` | virtual B button |
| `ARES/Input/isButtonXPressed` | `boolean` | virtual X button |
| `ARES/Input/isPoseReset` | `boolean` | pose-reset edge/request |
| `ARES/Input/obstacles` | `string` | serialized simulator obstacle update |
| `ARES/DriverStation/Command` | `string` | driver-station command |
| `ARES/DriverStation/SelectedOpMode` | `string` | selected OpMode |
| `ARES/DriverStation/MatchTime` | `double` | current match time |
| `ARES/DriverStation/MatchState` | `string` | match state |
| `SysId/Command` | `string` | characterization command |

Simulator inputs must all be read from the same custom `NT4Server` instance used by Analytics. Mixing WPILib's process-local `NetworkTableInstance` subscribers with the custom server leaves values stuck at defaults.

## String and console telemetry

String frames set `TelemetryFrame.stringValue`; their numeric field is not meaningful. Replay, Parquet, CSV extras, and summaries must preserve the string.

Only a closed set of explicit console topic names may be classified as console messages. Do not use substring checks such as `contains("log")`, which misclassify ordinary topics like `Path/Logging/Position`.

## Compatibility checklist

When adding or changing a topic:

- [ ] Producer and consumer agree on exact NT4 type.
- [ ] Topic is under a subscribed prefix.
- [ ] Leading slash is removed for stored identity.
- [ ] Units and coordinate frame are documented.
- [ ] Simulator and real-robot sources use the same convention.
- [ ] Numeric/string replay behavior is tested.
- [ ] Dashboard input has a safe default.
- [ ] A standards-compliant NT4 peer can publish/subscribe successfully.
