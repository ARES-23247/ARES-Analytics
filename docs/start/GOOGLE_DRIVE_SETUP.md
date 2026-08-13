# Set up Google Drive sync

Google Drive sync is optional. Robot connections, simulation, project authoring, log import, and
local analysis work without it.

ARES does not embed a shared Google credential. A team administrator creates a Google OAuth client
owned by the team:

1. Open a Google Cloud project and enable the **Google Drive API**.
2. Configure the Google Auth consent screen. While the app is in testing, add each team member as a
   test user.
3. Create an OAuth client with application type **Desktop app**. ARES uses PKCE and a loopback
   callback at `http://localhost:5805/callback`; Desktop clients support the loopback flow without
   storing a client secret in the application.
4. Copy the client ID ending in `.apps.googleusercontent.com`.
5. In ARES, open **Profile → Google Drive → Google OAuth setup**, paste the client ID, save the
   profile, and select **Google Sign-In**.

If Google reports `deleted_client`, the configured client no longer exists. Create a replacement and
update ARES; retrying or changing Google accounts cannot repair a deleted client.

References: [Google Drive Java quickstart](https://developers.google.com/drive/api/quickstart/java)
and [OAuth for desktop apps](https://developers.google.com/identity/protocols/oauth2/native-app).

