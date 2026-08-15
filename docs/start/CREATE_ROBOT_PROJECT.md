# Create a robot project without writing code

ARES can create a complete, buildable FTC or FRC simulation project during first-run setup. This is
the recommended starting point for a student who does not already have an ARES repository.

> **Simulation-only safety boundary:** the current reviewed starters retain Team 23247's season
> hardware examples. ARES blocks physical deployment from a downloaded starter. A future generic,
> hardware-neutral runtime template must pass the same release checks before that gate can open.

## What ARES downloads

The setup screen names the exact **ARES FTC** or **ARES FRC** starter and its ARES version. The app
does not follow a mutable `master` branch. It downloads one reviewed commit, verifies the archive's
SHA-256, and only then unpacks it.

The verified archive is cached under the local ARES Analytics application data. After one successful
download, the same starter can be used again without internet access.

## Create the project

1. Open workspace setup and choose **Create a simulation-first robot**.
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
- `.ares/template-provenance.json`: starter ID, exact revision, SHA-256, and ARES version.

No source file is rewritten during personalization. Generated mechanisms still use Robot Studio's
normal preview, ownership headers, confirmation tokens, tests, and generated-source boundaries.

## Next steps

1. Open **Robot Studio → Project identity** and replace the starter footprint with measured values.
2. Choose the supported drivebase for the league.
3. Add mechanisms with Subsystem Builder.
4. Configure controller bindings and routines.
5. Choose **Local Sim**, then run **Verify & build** before starting the simulator.

Creation does not deploy or enable a physical robot. Downloaded reference projects are rejected by
the deploy service even after confirmation; removing that boundary requires a reviewed generic
runtime template, not a student clicking through a warning.

Robot Studio's [Hardware Setup](HARDWARE_SETUP.md) screen can still aggregate the canonical
drivebase and subsystem addresses, detect cross-document conflicts, and record a hash-bound review.
That prepares a project for a future hardware-neutral runtime, but it does not remove the current
season reference template's simulation-only deployment block.
