# Branding, upgrades, and repair

The desktop product is named **ARES Robotics Studio** with the description **Design • Simulate •
Operate • Analyze**. It was previously distributed as **ARES Analytics**. The new name reflects the
complete student workflow: create a robot, learn the concepts, generate and verify code, simulate,
operate, tune, and analyze evidence.

## What the rename changes

- Window, installer, shortcut, onboarding, help, and documentation names
- Windows, macOS, Linux, and taskbar icons
- Public descriptions and generated project-history author text

## What deliberately stays compatible

- Existing `.ares` project documents and robot repositories
- `~/.ares-analytics` settings, local database, layouts, learning progress, and secure token files
- Google OAuth and GitHub App identities until their administrators update public display branding
- `com.ares.analytics` packages, NT4 client identity, update repository, and diagnostic prefixes
- Windows installer upgrade UUID

Keeping those technical identifiers stable prevents a cosmetic rename from losing local history,
disconnecting cloud accounts, breaking telemetry filters, or installing a second copy of the app.

## Upgrade

Download and run the newest installer normally. Windows recognizes the former ARES Analytics
installation through the same upgrade UUID and upgrades it in place. Projects are ordinary folders
and are never removed by an application upgrade.

## Repair or reinstall the same version on Windows

Run the downloaded `.msi` again. Windows Installer opens **Change, repair, or remove installation**;
choose **Repair**. Repair restores missing or corrupt installed program files and shortcuts without
deleting projects, `.ares` documents, the local telemetry database, settings, or user-bound secure
credentials.

You can also use Windows' standard repair command from an administrator terminal:

```powershell
msiexec.exe /fa "ARES Robotics Studio-<version>.msi"
```

The release build inspects every generated MSI and fails if the stable upgrade identity,
maintenance dialog, or Repair button disappears.

## Clean removal

Uninstalling removes the installed application, not robot projects or the local workspace data under
`~/.ares-analytics`. Export important runs and projects before intentionally deleting those folders.
