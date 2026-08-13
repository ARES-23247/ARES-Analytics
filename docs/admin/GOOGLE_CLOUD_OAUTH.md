# Google Cloud OAuth administration

This page is for ARES release administrators and schools using their own Google Cloud project.
Students should use the one-click flow in [Google Drive setup](../start/GOOGLE_DRIVE_SETUP.md).

## Official ARES client

Official installers use the active **ARES-Analytics Desktop Client** in the `aresfirst-portal`
project. The Google Auth Platform configuration must remain:

- App name: **ARES Analytics**
- Homepage: <https://aresfirst.org>
- Privacy policy: <https://aresfirst.org/privacy>
- Terms: <https://aresfirst.org/terms>
- Authorized domain: `aresfirst.org`
- Audience: External, published for users outside Team 23247
- APIs: Google Drive API enabled
- Declared scopes: OpenID, email, profile, and `drive.file`

The Desktop client ID is public application identity, not a password. Store it as the protected
GitHub repository variable `ARES_GOOGLE_OAUTH_CLIENT_ID` so official package jobs inject it into the
installer without echoing it in command arguments. Never create, store, or package a client secret
for the desktop flow.

Google Picker is used for existing team folders and folders inside Shared Drives. Picker performs a
second PKCE authorization with only `drive.file`, then returns the selected folder ID. Do not replace
this with `drives.list` or broad Drive search unless a reviewed product requirement and Google scope
verification justify the expanded access.

## Bring your own OAuth client

A school may create a Google OAuth **Desktop app** client in its own project, enable Drive API, and
configure the same branding, audience, and scopes. In ARES, open **Advanced administrator settings**,
enable the custom-client option, and enter the public client ID ending in
`.apps.googleusercontent.com`. Do not enter a secret.

The custom client changes consent branding, quota, publishing policy, and application identity. It
does not change who owns Drive data or bypass folder and Shared Drive permissions. If Google reports
`deleted_client`, disable the custom option to return to managed sign-in or replace the client.

## Release verification

Before publishing an installer:

1. Confirm consent-screen branding verification and publishing status in Google Cloud.
2. Build the MSI through the protected workflow so the managed ID is present.
3. On a clean Windows user profile, complete one-click sign-in with the production client.
4. Create a personal/team destination and upload/download a small test session.
5. Select an existing shared folder through Picker and repeat the round trip.
6. If the account supports Shared Drives, select a folder inside one and repeat the round trip.
7. Revoke access, remove folder permission, switch accounts, and verify ARES fails visibly while
   local authoring and analysis remain available.
8. Sign out and verify the local DPAPI credential record is removed.

Do not publish merely because unit tests and packaging pass; production OAuth consent and Drive
round-trip verification are release gates.
