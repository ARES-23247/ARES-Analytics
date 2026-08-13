# Google OAuth and multi-team Drive architecture

## Application identity is not data ownership

The production `ARES-Analytics Desktop Client` in the `aresfirst-portal` Google Cloud project is a
public OAuth application identifier. It tells Google which desktop application is requesting
consent. It does not grant Team 23247 access to a user's Drive and it does not select where data is
stored. Every user authenticates directly with Google; Google remains authoritative for identity,
ownership, sharing, revocation, and quotas.

ARES uses Authorization Code + PKCE and a localhost loopback callback. It requests `openid`,
`userinfo.email`, `userinfo.profile`, and `drive.file`. No client secret is bundled, requested, or
required.

## Destination behavior

Each `WorkspaceConfig` may contain one `DriveDestinationConfig` with a stable root folder ID,
destination type, display name, signed-in Google subject/email, and optional Shared Drive ID.

| Destination | Google owner | How access is granted | ARES behavior |
|---|---|---|---|
| Personal folder | Signed-in user | User owns it | Creates and scopes all ARES files below it |
| Team folder | Creating user | Owner shares it | Same technical model as My Drive, visibly labeled team-shared |
| Existing shared folder | Existing owner | Owner grants Editor access, then user selects it in Google Picker | Picker grants per-folder `drive.file` access; ARES never searches Drive for it |
| Shared Drive | Google Workspace organization | User selects a folder in Picker and has Shared Drive membership | Stores both selected root and owning Shared Drive ID; uses `supportsAllDrives` |

## Isolation and permissions

The stored root ID is a security boundary. Sync no longer searches My Drive for an
`ARES-Analytics` folder. Before list/read/write/delete, the Drive service verifies that the target
is the root or a descendant. Manifest file IDs are checked the same way before bytes are read or
deleted. A workspace/account mismatch fails before a network file operation.

ARES does not call `drives.list`, because that endpoint requires a broader Drive scope. Google
Picker is a second PKCE authorization requesting only `drive.file`; it returns the one folder the
user selected. ARES verifies the picker account matches the signed-in account before storing the ID.

Google permissions are always authoritative. ARES may describe a person as a student or mentor for
teaching workflows, but that label does not grant cloud access. If an ARES role conflicts with the
Google role, the operation fails closed. HTTP 401 clears the unusable session; 403/404 reports lost
permission, removed sharing, or deletion rather than presenting an empty cloud.

## Advanced OAuth client ownership

Official builds receive the team-owned public client ID through the protected
`ARES_GOOGLE_OAUTH_CLIENT_ID` GitHub Actions variable. The normal UI has one **Sign in with Google**
action. Schools may explicitly enable a custom Desktop OAuth client in Advanced administrator
settings. Existing client IDs in old workspace files are ignored unless that switch is enabled,
which migrates users away from the deleted legacy client safely.

## Token storage and revocation

On Windows, new releases protect the serialized OAuth token record with current-user DPAPI and
migrate `~/.ares-analytics/auth.json` to `auth.dpapi` after the first successful read. macOS and
Linux currently retain the owner-only atomic token file; native Keychain/Secret Service storage is
a documented follow-up rather than an unverified shell integration. Client IDs are public, but ARES
does not print them in OAuth errors. Tokens and authorization response bodies are never logged.

Saved tokens record the OAuth client ID that issued them. A client mismatch, `deleted_client`,
`invalid_grant`, revoked access, or an expired refresh token clears unusable state and requires a
new sign-in.

## Migration, disconnect, and export

Changing or disconnecting a destination never deletes remote files. The recommended migration is:

1. Verify the old destination and download/import remote-only sessions.
2. Export the non-secret destination record for audit or handoff.
3. Select the new personal/team/shared destination.
4. Explicitly sync local sessions to the new destination.
5. Let the Google owner archive or delete the old folder after independent review.

Local DuckDB data and exported Parquet/CSV/WPILOG files remain independent of Google Drive, so a
team is never locked into cloud synchronization.
