# Create a robot project without writing code

ARES can create a complete, buildable FTC or FRC simulation project during first-run setup. This is
the recommended starting point for a student who does not already have an ARES repository.

> **Simulation-only safety boundary:** the reviewed starters retain Team 23247's season hardware
> examples, including hand-authored composition that is not fully represented by GUI-owned
> descriptors. Builds and simulation are available immediately, but ARES blocks physical
> deployment. A student cannot bypass that boundary by acknowledging a warning.

## What ARES downloads

The setup screen names the exact **ARES FTC** or **ARES FRC** starter and its ARES version. The app
does not follow a mutable `master` branch. It downloads one reviewed commit, verifies the archive's
SHA-256, and only then unpacks it.

The verified archive is cached under the local ARES Analytics application data. After one successful
download, the same starter can be used again without internet access.

## Create the project

1. Open workspace setup and choose **Create a new robot**.
2. Choose **FTC** or **FRC**.
3. Read the displayed starter name and version.
4. Choose an existing parent folder such as `Documents\Robots`.
5. Enter a new folder name. ARES never merges into or replaces an existing file or folder.
6. Continue and enter the team, season, stable robot ID, and friendly name.
7. Review the setup and select **Create workspace**.

ARES unpacks into a private sibling staging folder, writes the robot identity, validates the source
layout, and publishes the finished directory in one move. If download, verification, extraction, or
personalization fails, the incomplete staging folder is removed and the requested destination is
left absent.

## What is personalized

- `.ares-robot.json`: team, season, robot ID, friendly name, and league.
- `.ares/project.json`: stable project ID while retaining the reviewed league coordinate convention
  and starter geometry until the student measures and reviews the real robot dimensions.
- `.ares/drivetrains/*.aresdrivetrain` and `.ares/tuning/*.arestuning`: robot-, drivebase-, and
  profile-level UIDs are rebound to this team, league, season, and robot. Parameter/component IDs
  stay stable because the reviewed runtime consumes them.
- `.ares/template-provenance.json`: starter ID, exact revision, SHA-256, and ARES version.
- FTC `local.properties`: when an installed Android SDK is found, ARES records its machine-local
  path so the new project can build without copying settings from another repository.

No Kotlin source file is rewritten during personalization. Canonical documents are decoded,
rewritten through their typed codecs, and validated as one identity graph. Generated mechanisms
still use Robot Studio's normal preview, ownership headers, confirmation tokens, tests, and
generated-source boundaries.

## Next steps

1. Open **Robot Studio → Project identity** and replace the starter footprint with measured values.
2. Choose the supported drivebase for the league.
3. Add mechanisms with Subsystem Builder.
4. Use the reviewed season driving controls as a baseline, or add a controller profile and control
   scheme together when you want GUI bindings for named mechanism actions.
5. Run **Verify & build**. It regenerates the project bridge, verifies ownership, runs tests, and
   packages the project without deploying.
6. Choose **Local Sim**, then start the now-verified simulator.

Creation does not deploy or enable a physical robot. Downloaded starters remain blocked from the
deploy service even after a Hardware Setup review. A future generic composition must prove that
every physical actuator and sensor is GUI-owned before that policy can change.

Robot Studio's [Hardware Setup](HARDWARE_SETUP.md) screen can still aggregate the canonical
drivebase and subsystem addresses, detect cross-document conflicts, and record a hash-bound review.
That review is still useful for finding descriptor conflicts and teaching hardware mapping, but it
does not remove the season template's simulation-only block. It is not powered hardware validation,
calibration, inspection approval, or permission to test without adult supervision.
