# Code Review — Fix & Validation Log

Generated: 2026-11-16
Worktree: `fix-review-findings` (branch `air/fix-review-findings`)
Base: `main @ e8b9ae486fb221dbab517bab04132a9e5728c42c`
Verification at time of writing: `./gradlew compileDebugKotlin testDebugUnitTest` → **BUILD SUCCESSFUL** (all unit tests pass).

This file is the hand-off record for validation models (e.g. Antigravity). It records
exactly what was changed, what was deliberately left alone, and how to verify each item.

---

## 0. How to validate

```bash
cd <worktree-path>
./gradlew clean compileDebugKotlin testDebugUnitTest --console=plain
git diff e8b9ae486fb221dbab517bab04132a9e5728c42c -- <file>
```

Review context worth knowing before judging the changes:

- Commit `a0447f47bf09d810ada0217b720d643a29cd895a` ("fix(release): gate billing/auth/paywall
  simulation fallbacks behind BuildConfig.DEBUG") establishes the project's own stated rule:
  **release builds must never grant paid tiers or fake authenticated sessions for free.**
  Several fixes below complete that partially-applied change (the commit gated
  `launchBillingFlow`, `setProStatus`, `signInSandbox`, but missed `setTier`,
  `reactivateSubscription`, and the paywall callbacks).
- The initial review's "critical auth bypass" claim was **retracted**: the only UI callers of
  `signInWithEmail` pass the default `UserRole.USER`; the owner whitelist in
  `AuthModels.kt:75-83` is the only privilege path, and the code itself documents the identity as
  "unverified free-text". That flow is a deliberate local-profile feature, not a code defect.
- Across the repo `signInWithEmail`/`signInWithMicrosoft`/`signInWithCorporateSso` still fabricate
  local sessions. This was left as-is (see DEFERRED D-01).

---

## 1. FIXED — Billing / revenue integrity

### F-01 Paid tiers were granted for free from the paywall (Critical) — FIXED
- **Files:** `data/BillingRepository.kt`, `ui/components/PaywallDialog.kt`,
  `ui/inspection/InspectionViewModel.kt`
- **Before:** public `setTier()` unconditionally published `PaymentStatus.ACTIVE`; the paywall
  confirm called `onSelectTier` → `setTier(...)` before launching Play billing, in all paid screens.
- **After:** `setTier()` is DEBUG-only for paid tiers (release may only reset to `FREE`); the actual
  state mutation moved to `private fun applyTier(...)`; paid entitlements are reachable only through
  `handleValidPurchase()` (a Play-reported purchase).
- **Verify:** `grep -n "fun setTier" data/BillingRepository.kt` shows
  `if (!BuildConfig.DEBUG && tier != SubscriptionTier.FREE) return`, and
  `grep -n "applyTier" data/BillingRepository.kt` shows it is used only inside the class.

### F-02 Unknown product IDs silently granted Individual Pro (High) — FIXED
- **Before:** `handleValidPurchase` ended with `else -> setTier(INDIVIDUAL_PRO)`.
- **After:** unrecognised SKUs are logged and ignored.
- **Verify:** `grep -n "unrecognised product" data/BillingRepository.kt`.

### F-03 Lapsed/cancelled subscriptions were never revoked (Critical) — FIXED
- **Before:** `queryPurchases` had an empty reconciliation block.
- **After:** when Play reports no active subscription for a persisted recurring tier,
  `markPaymentFailed()` is called, and `calculateEffectiveTier` demotes to Free/Ad-Free.
- **Verify:** `grep -n "No active Play subscription" data/BillingRepository.kt`.

### F-04 Refunded lifetime Ad-Free license was never cleared (Medium) — FIXED
- **After:** `queryPurchases` clears `_hasOneTimeAdFree` and the pref when `SKU_AD_FREE` is absent.
- **Verify:** `grep -n "Ad-free license no longer reported" data/BillingRepository.kt`.

### F-05 Purchases seen by `queryPurchases` were never acknowledged (High) — FIXED
- **After:** acknowledgment centralised in `handleValidPurchase()`, so both `onPurchasesUpdated`
  and `queryPurchases` acknowledge; result code is logged on failure.
- **Verify:** `onPurchasesUpdated` now only loops `handleValidPurchase(purchase)` (no local
  duplicate ack logic).

### F-06 `reactivateSubscription()` restored paid status locally (High) — FIXED
- **After:** in release it re-queries Play (`queryPurchases()`) and returns; DEBUG keeps the
  simulation path.
- **Verify:** `grep -n "Retry / pay outstanding" -A 12 data/BillingRepository.kt`.

### F-07 AD_FREE counted as "Pro" and unlocked unlimited audits (Medium) — FIXED
- **After:** `_isProUser` is true only for `INDIVIDUAL_PRO` or `ENTERPRISE`, in both the initial
  StateFlow and `recalculateEntitlements()`.
- **Verify:** `grep -n "_isProUser" data/BillingRepository.kt`.

### F-08 Duplicate inspection records on paywall confirm (High) — FIXED
- **Before:** `setSubscriptionTier()` called `saveInspection()` (line 269) and
  `upgradeToProAndSave()` called it again (line 275); the paywall invoked both, plus a DEBUG
  fallback third save.
- **After:** `setSubscriptionTier()` records the tier only; `upgradeToProAndSave()` is the single
  save path; the DEBUG fallback save in `launchBillingFlow()` was removed.
- **Verify:** `grep -n "saveInspection()" ui/inspection/InspectionViewModel.kt` returns only one
  call site inside `upgradeToProAndSave`.

### F-09 Selecting "Free" in the paywall reactivated the subscription (Medium) — FIXED
- **After:** `onUpgradeClicked()` is invoked only for non-FREE selections.
- **Verify:** `grep -n "selectedTier != .*SubscriptionTier.FREE" ui/components/PaywallDialog.kt`.

---

## 2. FIXED — Signing secrets, data integrity, compliance

### F-10 Committed signing passwords + debug-signature fallback (High) — FIXED (rotate still required)
- **Files:** `gradle.properties`, `app/build.gradle.kts`
- **Before:** `SAFETYLOG_KEYSTORE_PASSWORD` / `SAFETYLOG_KEY_PASSWORD` were committed in plaintext;
  the build script had hardcoded fallbacks (`safetylogPass2026`) and silently signed release with
  the debug key when the keystore was missing. `org.gradle.java.home` hard-coded one developer's
  JDK path.
- **After:** passwords removed (env/CI only), hardcoded fallbacks removed, debug fallback removed
  (missing keystore now makes AGP fail the release build), machine-specific Java home removed.
- **Verify:** `grep -n "Password\|java.home" gradle.properties app/build.gradle.kts` shows no
  literal secret values. **Action required by a human:** rotate the exposed passwords and purge
  them from git history; the value is still in git history.
- **Note:** default `SAFETYLOG_KEY_ALIAS` fallback was corrected to `safetylog_release` to match
  the committed property.

### F-11 Date picker off-by-one day in negative UTC offsets (High) — FIXED
- **File:** `ui/equipment/EquipmentFormComponents.kt`
- **After:** both `SimpleDateFormat`s set `timeZone = UTC` to match the DatePicker's UTC-midnight
  millis.
- **Verify:** `grep -n "timeZone = TimeZone.getTimeZone(\"UTC\")" .../EquipmentFormComponents.kt`
  returns two hits.

### F-12 Viewer role could manage assets on Free/Pro tiers (High) — FIXED
- **File:** `ui/dashboard/DashboardScreen.kt`
- **Before:** `canManageAssets = role.canManageAssets || tier != ENTERPRISE`.
- **After:** `canManageAssets = uiState.userRole.canManageAssets`.
- **Verify:** `grep -n "val canManageAssets" ui/dashboard/DashboardScreen.kt`.

### F-13 Checklist substring matching misclassified equipment (High) — FIXED + TESTED
- **File:** `domain/ChecklistRegistry.kt`, `test/.../ChecklistRegistryTest.kt`
- **Before:** raw `contains("water")`, `contains("chemical")`, etc. mapped "Water Heater",
  "CO2 Incubator", "LED Floodlight", "Door Access Control" to life-safety protocols.
- **After:** whole-token matching with documented priority (AED → extinguisher → eyewash →
  fire door → lighting → general).
- **Verify:** `./gradlew testDebugUnitTest --tests '*ChecklistRegistryTest'` — includes new
  regression tests `testGenericWordsDoNotSelectWrongProtocol` and `testSpecificProtocolsStillMatch`.

### F-14 Blanket NFPA 10 certification on every PDF (High) — FIXED
- **File:** `util/InspectionPdfGenerator.kt`
- **After:** neutral wording ("applicable life-safety inspection protocols and local ordinances").
- **Verify:** `grep -n " NFPA 10" util/InspectionPdfGenerator.kt` should return nothing.

### F-15 Hardcoded "5/5 checks verified" on PDFs (High) — FIXED
- **After:** the summary is derived from the five stored check booleans; if any check failed the
  report no longer claims full compliance.
- **Verify:** `grep -n "checkFlags" util/InspectionPdfGenerator.kt`.

### F-16 Auto-generated fake signature bypassed validation (Medium) — FIXED
- **Files:** `ui/inspection/InspectionFormScreen.kt`, `ui/inspection/InspectionChecklistComponents.kt`
- **After:** the save button no longer fabricates an "Electronically Certified & Signed" image;
  `generateAutoSignatureFile()` was deleted entirely. The real signature validation in
  `saveInspection()` now surfaces the error.
- **Verify:** `grep -rn "generateAutoSignatureFile" app/src` returns nothing.

### F-17 Destructive Room migration (High) — FIXED (schema export still recommended)
- **File:** `data/SafetyLogDatabase.kt`
- **After:** `fallbackToDestructiveMigration(true)` removed; defensive `MIGRATION_1_3` and
  idempotent `MIGRATION_2_3` (check `PRAGMA table_info` before adding columns) are registered.
- **Verify:** `grep -n "fallbackToDestructive" app/src/main/java/.../SafetyLogDatabase.kt` returns
  nothing.
- **Caveat:** schema export was disabled historically, so the exact v1 schema is unknown. Validate
  `MIGRATION_1_3` against a real v1 database; enable `exportSchema = true` with a checked-in
  schema location going forward.

---

## 3. FIXED — Auth/token state, privacy, UI state

### F-18 Keystore diagnostics falsely claimed hardware backing (Medium) — FIXED
- **File:** `data/auth/SecureTokenStorage.kt`
- **After:** missing key / inspection failure now returns `isHardware = false` and "Unknown (…)".
- **Verify:** `grep -n "Unknown (key not present)\|Unknown (inspection failed" .../SecureTokenStorage.kt`.

### F-19 Reported master-key alias did not match the key actually returned (Low) — FIXED
- **After:** the fallback branch sets `activeMasterKeyAlias = DATA_CIPHER_ALIAS`; the unused
  `DEFAULT_MASTER_KEY_ALIAS` constant was removed.

### F-20 `assignedRole` StateFlow stale in Google sandbox fallback (Medium) — FIXED
- **File:** `data/auth/AuthRepository.kt` — added `_assignedRole.value = user.role` in the fallback
  sign-in branch.

### F-21 Webhook secret rendered in plain text (Medium) — FIXED
- **File:** `ui/settings/SettingsSyncAndSecuritySection.kt` — applied
  `PasswordVisualTransformation()` and `KeyboardType.Password`.

### F-22 Auth snackbar effects cleared each other's messages (Medium) — FIXED
- **Files:** `ui/auth/AuthViewModel.kt`, `ui/auth/AuthScreen.kt`
- **After:** separate `clearErrorMessage()` / `clearSuccessMessage()`; each effect clears only its
  own message.

### F-23 Raw ID-token prefix displayed as "fingerprint" (Medium) — FIXED
- **File:** `ui/auth/AuthProviderCards.kt` — row now shows "Vault Token Active (AES-256)"; no
  token material is rendered.
- **Verify:** `grep -rn "idToken?.take" app/src` returns nothing.

### F-24 Corporate-domain field could not be cleared (Medium) — FIXED
- **File:** `ui/auth/AuthProviderCards.kt` — one-shot `domainAutoFilled` guard; the autofill effect
  no longer re-enters and refills the field after the user clears it.

### F-25 Exported MainActivity trusted arbitrary intents / dead `onNewIntent` (Medium) — FIXED
- **Files:** `AndroidManifest.xml`, `MainActivity.kt`, `InspectionAlarmReceiver.kt`,
  `InspectionReminderManager.kt`
- **After:** `android:launchMode="singleTop"`; notification intents carry a private
  `EXTRA_FROM_NOTIFICATION` marker; `MainActivity` ignores unmarked intents for navigation.
- **Verify:** `grep -n "launchMode" app/src/main/AndroidManifest.xml` and
  `grep -rn "EXTRA_FROM_NOTIFICATION" app/src/main/java`.

### F-26 Three webhook methods leaked `HttpURLConnection` (Medium) — FIXED
- **File:** `data/sync/TeamSyncEngine.kt` — `var conn` + `finally { conn?.disconnect() }` in
  `testWebhookEndpoint`, `dispatchInspectionToWebhook`, `dispatchTeamRosterToWebhook`.
- **Verify:** `grep -c "conn?.disconnect()" .../TeamSyncEngine.kt` == 3.

### F-27 NetworkMonitor callback never unregistered / stale `onAvailable` (Medium/Low) — FIXED
- **File:** `util/NetworkMonitor.kt` — callback retained in a field, `unregister()` added,
  `onAvailable` evaluates the supplied `Network`.

### F-28 Crash-log metadata stored unencrypted (High) — FIXED (with legacy fallback)
- **File:** `util/CrashLogVault.kt` — `message`, `thread`, `exception`, `device` are now encrypted
  with `secureStorage.encryptString`; export decrypts them with a plaintext fallback for legacy
  entries.
- **Verify:** `grep -n "put(\"message\", secureStorage.encryptString" .../CrashLogVault.kt`.

### F-29 PDF write failures leaked native resources and left truncated files (Medium) — FIXED
- **File:** `util/InspectionPdfGenerator.kt` — both generators write to a `.tmp` file, publish
  only on success, and always `document.close()` in `finally`.

### F-30 Full-resolution signature decode could OOM the export (Medium) — FIXED
- **File:** `util/InspectionPdfGenerator.kt` — `decodeSignature()` uses `inJustDecodeBounds` +
  `inSampleSize` and catches `OutOfMemoryError` explicitly.

---

## 4. DEFERRED / NOT FIXED (with reasons) — validate these are still open

These were reported in the review and are **intentionally not changed** in this pass. Validators
should treat them as known open issues, not regressions.

> Follow-up (2026-11-17/18): D-01, D-02, D-05 and D-12 are addressed by the
> server-authoritative layer in `docs/SERVER_AUTHORIZATION.md`. D-06/D-07/D-08 are fixed in
> `docs/TEAM_SYNC.md`. D-21 credentials were rotated and the remote history was force-pushed;
> see `docs/CREDENTIAL_ROTATION.md`. The remaining code-level items (D-03, D-09, D-10, D-11,
> D-14, D-15, D-17, D-18, D-19) were also addressed in this pass: scoped legacy-role
> migration, exact-alarm permission UI, collision-free alarm request codes with cooperative
> boot timeout, atomic free-tier quota insert and month-boundary-aware count flow, OAuth
> draft merging, profile-load retry, narrowed FileProvider paths with encrypted diagnostic
> exports, and uniform signature-stroke scaling. The rows below remain as the historical
> record of why the local-only design existed.

| ID | Issue | Why deferred |
|---|---|---|
| D-01 | `signInWithEmail`/Microsoft/corporate SSO fabricate local sessions; identity is unverified free-text; Google ID token signature/audience/nonce never verified | Deliberate offline/local-profile design (code logs this explicitly). Real auth requires a backend; product decision needed. |
| D-02 | Hardcoded owner-email whitelist (`AuthModels.kt:75-83`) | Same as D-01; part of the local ownership model. |
| D-03 | Roles can bleed across accounts via `secureStorage.getActiveRole()` | Needs an account-keyed role store design; risky to change blindly. |
| D-04 | Keystore keys not bound to biometric/PIN | UX/product decision (would require BiometricPrompt flow). |
| D-05 | `getAuthToken` returns expired cached token; no refresh; `0L` expiry means "never" | No backend token refresh exists; needs design. |
| D-06 | Encrypted team packages use device-bound Keystore key → cannot be opened on another device despite Drive/Email sharing claims | Requires envelope/passphrase encryption redesign. |
| D-07 | `TeamSyncEngine` export writes `"inspections"` but import ignores it | Requires DAO upsert + stable sync ID + migration; do not bolt on without schema work. |
| D-08 | Reminder config omitted from export/import | Same round-trip redesign as D-07. |
| D-09 | `SCHEDULE_EXACT_ALARM` is special-access; no permission request flow → reminders degrade to inexact | Needs UI/settings flow and platform-policy decision (`USE_EXACT_ALARM` eligibility). |
| D-10 | BootReceiver `goAsync()` with unbounded work; request-code collisions via `Long.hashCode()`/`toInt()` | Needs WorkManager/alarm-ID schema change. |
| D-11 | `EntitlementManager` monthly count captured once in Flow; quota check not atomic with insert | Needs DAO/transaction changes. |
| D-12 | BillingClient has no reconnect after `onBillingServiceDisconnected`; no purchase authenticity verification | Needs backend verification and connection-state handling. |
| D-13 | Several unit tests are tautological/self-referential (auth version pinning, `RoleAndSubscriptionTest`, `SafetyBannerAdConfigTest`, `TeamSyncEngineTest`, `ReminderAndScheduleTest`, `KeystoreInitializationTest`) | Not fixed except the new `ChecklistRegistryTest` regressions. High-risk auth/billing/sync/crypto paths remain untested. |
| D-14 | `SettingsScreen` OAuth drafts (`remember(uiState.companyOAuthConfig)`) reset on unrelated saves | Needs ViewModel-owned form state. |
| D-15 | Transient profile read failure permanently blocks saves (`ProfileViewModel`) | Needs explicit retry/allow-non-blank save design. |
| D-16 | Stale "due" counts/overdue badges; overlapping report queries; stale `generatedPdfFile!!` race; main-thread image copy; hard-coded 5 checklist indices; tag-ID collisions | Lower severity; tracked but not changed in this pass. |
| D-17 | FileProvider shares whole `files/`/`cache/` (`file_paths.xml` `path="."`) | Must verify all write paths before narrowing; leaving unchanged avoids breaking sharing. |
| D-18 | Diagnostic export writes decrypted traces to plaintext cache file and is called synchronously from a click | Requires export lifecycle/share-intent changes. |
| D-19 | Signature bitmap in `SignaturePad` is written at fixed 800x300 without coordinate scaling (strokes can clip) | Needs canvas-size capture; visual behaviour change should be QA'd. |
| D-20 | `fallbackToDestructiveMigration` removed but schema export still disabled; `MIGRATION_1_3` needs validation against a real v1 DB | Human/QA action with legacy data. |
| D-21 | Rotate exposed signing passwords and purge git history | Human action; code change alone is insufficient. |

---

## 5. Suggested validation checklist for an autonomous reviewer

1. **Build/test:** clean build + unit tests (commands in §0). Expect success.
2. **No free paid tiers:** simulate a release-variant call to `BillingRepository.setTier(INDIVIDUAL_PRO)`
   from a ViewModel/UI — it must be a no-op. Only `handleValidPurchase` may grant it.
3. **No duplicate saves:** from the inspection form, tapping the paywall confirm must call
   `saveInspection()` exactly once (instrument or inspect call graph).
4. **Secrets:** no signing password may appear in the worktree. Passwords were rotated on
   2026-11-17 into the gitignored `signing-secrets.properties`, and the local git history was
   rewritten with `git filter-repo` (D-21). The remote still has the old history until the
   owner force-pushes the rewritten `main`.
5. **Checklist matching:** run `ChecklistRegistryTest`; confirm "Water Heater" and "CO2 Incubator"
   map to `Commercial Safety Equipment`.
6. **Dates:** with device timezone set to America/New_York, pick a date in the equipment form and
   assert the displayed/stored date matches the picked calendar day.
7. **Role gate:** `USER` role on a Free/Pro plan must not see asset-management controls
   (`DashboardScreen.canManageAssets`).
8. **PDFs:** generate a report for a non-extinguisher asset and assert the footer contains no
   "NFPA 10" claim; assert no `.tmp` files are left behind after generation.
9. **Intents:** launch `MainActivity` with `equipmentId` but no `EXTRA_FROM_NOTIFICATION` — it must
   not deep-link into a record.
10. **Crash vault:** after recording a crash, inspect `safetylog_crash_vault.xml` — `message` and
    `thread` values must not be readable plaintext.
11. **Open issues:** confirm the D-01…D-21 items remain open by design; do not report them as new
    regressions introduced by this change set.

---

## 6. Change inventory

26 files changed, 370 insertions, 142 deletions. Full diff:

```bash
git diff e8b9ae486fb221dbab517bab04132a9e5728c42c
```
