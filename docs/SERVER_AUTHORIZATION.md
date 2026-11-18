# Server-Authoritative Authorization

This document describes the server-side authorization layer added to SafetyLog. It closes
the locally-decided privilege problem: before this change, roles, subscription tier and
the OWNER allowlist all lived on the device, so any user could grant themselves privileges
by editing local storage.

## Architecture

```
Google Credential Manager
        │  verified Google ID token (signature/aud/iss/exp/nonce checked by server)
        ▼
Cloudflare Worker  (server/)
  ├─ verifies the Google ID token against Google JWKS
  ├─ reads role / tier / roster from the KV registry (source of truth)
  └─ issues a short-lived RS256-signed snapshot (default 15 min)
        │  signed JWT
        ▼
Android app
  ├─ verifies the signature with the embedded PUBLIC key (cannot forge)
  ├─ DashboardUiState.role / tier / roster come ONLY from the snapshot
  └─ privileged mutations require connectivity + an unexpired snapshot (fail-closed)
```

The private signing key exists only as a Worker secret. The app embeds only the public
key, so tampering with local preferences can no longer elevate a role or buy a tier.

## What changed in the app

| Concern | Before | After |
|---|---|---|
| Role source | local prefs + hardcoded allowlist + test override | signed snapshot when configured |
| Tier / entitlement source | local `BillingRepository` prefs | signed snapshot when configured |
| Team roster source | local encrypted prefs | signed snapshot when configured |
| Owner identity | email string comparison on device | verified Google ID token email vs `OWNER_EMAILS` on server |
| Privileged mutations | UI / ViewModel checks on local role | additionally require online + valid signed snapshot |
| Play purchases | trusted locally | purchase token verified via Play Developer API by server |
| Offline field use | fully local | inspections still fully offline; only privileged changes need a connection |

If the server is not configured, debug/test builds keep the legacy local behavior
(`TestControls.isEnabled`). Public release builds with no `AUTH_API_BASE_URL` fail closed:
the app is usable for inspections but no privileged action is possible.

## Deploy the server

See `server/README.md` for the full walkthrough. Short version:

```bash
cd server
npm install
cp wrangler.example.toml wrangler.toml
# create the KV namespace and put its id in wrangler.toml
npm run keys                                   # prints the two base64 key values
npx wrangler secret put SIGNING_KEY_PKCS8_BASE64
npx wrangler secret put SIGNING_KEY_SPKI_BASE64
npx wrangler secret put GOOGLE_SERVICE_ACCOUNT_JSON
npm run deploy
```

Then add to `gradle.properties` (or environment variables) of every build that should use
server authority:

```properties
AUTH_API_BASE_URL=https://safetylog-auth.<your-subdomain>.workers.dev
AUTH_PUBLIC_KEY_SPKI_BASE64=<the public key value from npm run keys>
```

Both are compiled into `BuildConfig` and consumed by
`app/src/main/java/com/safetylog/app/util/ServerAuthorization.kt`.

## Bootstrap an OWNER

Owners are defined by the Worker variable `OWNER_EMAILS` (comma-separated verified Google
account emails) — the hardcoded client allowlist is no longer the authority. After an OWNER
signs in and receives a snapshot, use the admin endpoints to seed roles/entitlements/roster:

```bash
TOKEN="<snapshot JWT returned by /v1/auth/exchange>"

curl -X POST https://<worker>/v1/admin/role \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"email":"manager@company.com","role":"MANAGER"}'

curl -X POST https://<worker>/v1/admin/entitlement \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"email":"manager@company.com","tier":"ENTERPRISE","paymentStatus":"ACTIVE","seats":10}'
```

`OWNER` itself cannot be assigned through the API — it comes only from `OWNER_EMAILS`.

## API contract

| Method | Path | Auth | Purpose |
|---|---|---|---|
| `GET` | `/health` | none | liveness |
| `POST` | `/v1/auth/exchange` | `{idToken, nonce?}` | verify identity, return signed snapshot |
| `POST` | `/v1/auth/refresh` | snapshot bearer | re-read registry, issue fresh snapshot |
| `GET` | `/v1/me` | snapshot bearer | decoded authoritative claims |
| `POST` | `/v1/admin/role` | OWNER snapshot | assign a non-owner role |
| `POST` | `/v1/admin/entitlement` | OWNER/ACCOUNT_ADMIN snapshot | set tier, status, seats |
| `PUT` | `/v1/admin/team` | OWNER/ACCOUNT_ADMIN/MANAGER snapshot | replace roster |
| `POST` | `/v1/purchases/verify` | snapshot bearer | verify Play token, reissue entitlement snapshot |

Snapshot claims (v1): `sub`, `email`, `role`, `tier`, `effectiveTier`, `paymentStatus`,
`seats`, `team[]`, `iat`, `exp`, `jti`. Keep this in sync with
`AuthorizationSnapshot.kt` and `server/src/types.ts`.

## Offline behavior

- **Read-only UI** uses the cached signed snapshot even offline, until `exp`.
- **Privileged mutations** (tier/billing/seats/OAuth settings/team CRUD) are denied when
  offline or when no valid snapshot exists — `AuthorizationPolicy.privilegedActionAllowed`.
- **Inspections and equipment logging** remain fully offline; the policy is not applied there.
- On sign-in the app exchanges the Google ID token for a snapshot; if the server is
  unreachable the user is signed in as USER locally (offline inspections still work).
- On dashboard creation the app attempts a refresh; the snapshot can also be refreshed by
  any successful privileged-action flow via `AuthorizationRepository.refresh()`.

## Verification

```bash
# server (Node 20+)
cd server && npm install && npm run typecheck && npm test

# app
./gradlew testDebugUnitTest
```

New tests cover JWT signature verification and tamper rejection, snapshot expiry, API
error mapping with a fake transport, registry owner/role invariants, and the fail-closed
policy matrix.

## Follow-up fixes (2026-11-17)

- **Inspector email sign-in is now server-verified.** `POST /v1/auth/email` checks a
  PBKDF2-SHA256 password hash stored in the Worker KV. The app's email form now has a
  password field. A self-declared name/email can no longer grant a privileged role.
  Seed accounts with `POST /v1/admin/credential` (OWNER only); `OWNER` cannot be assigned.
- **Team roster CRUD is routed through the server.** Add/update/remove push the new roster
  to `PUT /v1/admin/team`, reissue a signed snapshot, and roll the local change back if the
  server rejects it. Server-side checks also reject ACCOUNT_ADMIN assignments by managers.
- **Snapshots auto-refresh** every 5 minutes while the app is alive, so a long-running
  session keeps its authoritative role/tier without a fresh sign-in.
- **Play purchase verification** still needs a Google Cloud service account with the
  `androidpublisher` scope and access to the app in Play Console. Without
  `GOOGLE_SERVICE_ACCOUNT_JSON` the endpoint fails closed. See the manual setup steps below.
- **D-06 / D-07 / D-08** are fixed in `docs/TEAM_SYNC.md`, and **D-21** in
  `docs/CREDENTIAL_ROTATION.md`.

## Deployed instance (2026-11-18)

The Worker is live at:

```
https://safetylog-auth.safetylog-auth.workers.dev
```

- Account: `Jdesrosiers88@gmail.com's Account` (`c9f27dfec359f8d53bc00808986ba4a3`)
- Worker: `safetylog-auth`, KV namespace `AUTH_REGISTRY` = `e3f48c67176248e29c7404a69f5a989c`
- Secrets set: `SIGNING_KEY_PKCS8_BASE64`, `SIGNING_KEY_SPKI_BASE64`, `GOOGLE_SERVICE_ACCOUNT_JSON`
- The matching URL and public key are in the project `gradle.properties`, so app builds
  now enforce server-issued decisions.

The Google service account `safetylog-play-verifier@safety-app-512814.iam.gserviceaccount.com`
still needs Play Console access before paid purchases can be verified end-to-end.
**Blocker found 2026-11-18:** the signed-in Google account has no Play Console developer
account yet (Play Console only offers "Create a new developer account"). A developer
account — and the app registered in it — is required before the service account can be
invited under **Users and permissions** with **View app information** access. Purchase
verification fails closed until then; all other authorization flows work.

## Play service account setup (manual, remaining step)

Completed: the service account and JSON key were created and the key is stored as the
Worker secret `GOOGLE_SERVICE_ACCOUNT_JSON`. One step remains in your browser:

1. Open <https://play.google.com/console> → **Users and permissions**.
2. Invite `safetylog-play-verifier@safety-app-512814.iam.gserviceaccount.com` with
   **View app information** access to the SafetyLog app, and accept the invite if needed.
3. Never commit the JSON key. If it leaks, rotate it in Google Cloud Console → **Keys**.
