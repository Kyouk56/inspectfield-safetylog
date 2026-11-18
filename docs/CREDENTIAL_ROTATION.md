# Signing Credential Rotation (D-21)

**Date:** 2026-11-17

## What was exposed

`SAFETYLOG_KEYSTORE_PASSWORD` and `SAFETYLOG_KEY_PASSWORD` were committed in plaintext in
the initial release commit, and an older fallback (`safetylogPass2026`) was previously
hardcoded in `app/build.gradle.kts`. A global `~/.gradle/gradle.properties` also held the
old values.

## What was done

1. Generated new high-entropy passwords.
2. Rotated the **root** `keystore/safetylog-release.jks` store password with `keytool`
   (PKCS12, so the key password must equal the store password).
3. Rotated the duplicate `app/keystore/safetylog-release.jks` as well (it uses alias
   `safetylog-release-key` and had a different old password). The release build uses the
   root keystore via `SAFETYLOG_KEYSTORE_FILE=../keystore/...` from the app module.
4. Wrote the new passwords to the **gitignored** `signing-secrets.properties` and added a
   `signingSecret()` loader in `app/build.gradle.kts` that prefers Gradle properties /
   environment (for CI), then the local file.
5. Removed the old passwords from `~/.gradle/gradle.properties`.
6. Rewrote the local git history with `git filter-repo`, replacing every occurrence of the
   old secret values with `REDACTED`. Expired reflogs and pruned unreachable objects.
7. Verified `./gradlew clean assembleRelease` succeeds with the rotated credentials.

## Remote purge

The rewritten history was force-pushed on 2026-11-18 with
`git push --force-with-lease origin main`. `origin/main` now points at commit `5fba877`
(the rewritten v1.0.16 history). Old commits became unreachable on GitHub, which garbage
collects them automatically.

If collaborators still have the old history locally, they must reset to the new `origin/main`
rather than merging; otherwise the old commits (and the secrets in them) could be pushed back.

## Follow-up recommendations

- Move the signing keystore out of the repository entirely and store the release artifact
  signing in CI with secrets from your CI provider.
- Consider generating a brand-new signing key instead of reusing one whose password was
  public. Rotating the password does not undo the exposure of the key material's protection
  if anyone had access to the keystore file. Changing the signing key changes the app's
  signing certificate fingerprint and would require a new Play Store app listing / key
  upgrade request.
- Add a pre-commit secret scanner so credential-like strings are rejected before commit.
