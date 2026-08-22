# Project Backup

ARES can save named versions of a robot project and optionally synchronize them to a private GitHub
repository. This feature is independent of Google Drive session/log synchronization.

## What students need

- No separate Git installation. ARES includes JGit for project history and synchronization.
- A name and email for the project history record.
- A GitHub account only if the team wants an off-computer backup.

Open **Profile & Settings → Project Backup**.

1. Choose **Start local history**. This creates a standard `.git` directory inside the selected
   robot project and does not upload anything.
2. Review the exact changed-file list, describe the change, then choose **Save this version**.
   A content-bound confirmation token prevents a file changed after preview from being committed.
3. Optionally choose **Sign in with GitHub**, approve the short device code in the browser, and
   create a private repository. GitHub backup is allowed only from a clean saved version.

## Safety and privacy

- GitHub device authorization uses a public application client ID and never a client secret.
- On Windows the GitHub token is protected with DPAPI for the current user. Other platforms use
  the existing owner-only ARES credential-file policy until a native keychain backend is added.
- Tokens are never embedded in remote URLs, robot project files, terminal arguments, or logs.
- Known secret-bearing paths such as `credentials.json`, keystores, `.env`, and `.ares/secrets/`
  block a save if they are not already ignored.
- Push is non-destructive. A non-fast-forward or permission conflict fails visibly instead of
  rewriting remote history.
- **Disconnect GitHub** deletes the saved app credential. It does not delete local versions or the
  private repository.

## Administrator setup

The official installer must receive `ARES_GITHUB_OAUTH_CLIENT_ID` from a protected repository
variable at build time. The corresponding GitHub OAuth App must have Device Flow enabled. The
client ID is public application identity; never configure or bundle a GitHub client secret.

The requested `repo` scope is used because the app creates and pushes private repositories. Teams
whose organization policy blocks OAuth Apps should keep local history enabled and use their
approved backup process.
