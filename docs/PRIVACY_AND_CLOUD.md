# Privacy and cloud data

ARES Analytics is local-first. Live robot telemetry, imported logs, authoring files, simulation,
and analysis run on the laptop. Robots do not sign into Google and do not upload directly.

Google Drive synchronization is opt-in. Before consent, the app explains that it requests basic
Google identity and `drive.file`. This scope is limited to files ARES creates or the user explicitly
selects for ARES. ARES stores the selected workspace folder/Shared Drive ID and does not scan
unrelated Drive files.

OAuth access and refresh tokens are sensitive. Windows installations protect them with DPAPI for
the current Windows user. Existing plaintext token files are migrated after a successful secure
write. macOS/Linux currently use an atomically replaced owner-only file. Workspace configuration
may contain public OAuth client IDs and stable Drive folder/account identifiers, but never requires
a Google client secret.

Signing out removes the local token record. Disconnecting a workspace destination leaves both local
data and Drive files intact. Removing ARES access in the Google Account permissions page revokes
Google's grant; ARES detects the unusable session and asks the user to reconnect.
