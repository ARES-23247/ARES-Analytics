# Robot build tools

ARES Robotics Studio itself includes a private desktop runtime. Students can create projects, use the GUI
builders, import logs, and analyze runs without installing Java, Kotlin, Gradle, Git, or GitHub
Desktop.

Robot compilation and simulation use additional league tools:

| Tool | Why it is used | How ARES handles it |
| --- | --- | --- |
| JDK 21 | Runs Gradle robot builds and local simulation | On Windows x64, **Install private JDK 21** downloads a verified Eclipse Temurin archive into the current user's ARES data directory. It does not change system-wide `JAVA_HOME`. |
| FTC Android SDK/NDK | Builds an FTC APK and communicates with Android devices | Install the reviewed Android Studio/command-line components shown by the readiness card. ARES detects common SDK locations and passes them only to child builds. |
| FRC WPILib 2026 | Builds, simulates, and deploys a RoboRIO project | Install the official WPILib release. ARES detects the standard WPILib location. |
| Git | Stores project history | Not required. Project Backup uses embedded JGit. |
| Kotlin/Gradle | Compiles generated and starter source | The robot starters include Gradle Wrapper configuration; dependencies are resolved by the build. |

## Install the private JDK

During first launch or in **Profile & Settings → Robot build tools**:

1. Select **Install private JDK 21**.
2. ARES obtains package metadata from Eclipse Adoptium, permits only reviewed HTTPS hosts, bounds
   the archive size, verifies the advertised SHA-256, rejects path traversal, and validates the
   extracted `java`/`javac` pair.
3. ARES records the selected private JDK under `~/.ares-analytics/toolchains` and supplies
   `JAVA_HOME`/`PATH` only to robot build, generation, simulation, and deployment child processes.

If the download fails, no system installation is changed. Choose **Recheck tools** after fixing the
network or installing a compatible JDK manually.

## Why FTC/FRC vendor tools are not silently installed

Android and WPILib distributions are large, season-sensitive, and have their own license and
platform workflows. ARES reports the exact missing component and links to setup guidance instead
of silently accepting licenses, guessing a season, or leaving a partial toolchain behind.

Local authoring and analysis remain available when any optional build tool is missing.
