# Team Sync Package Fixes (D-06 / D-07 / D-08)

## D-06 — Portable encrypted packages

Previously, `TeamSyncEngine` encrypted packages with `SecureTokenStorage.encryptData()`,
which uses a non-exportable Android Keystore AES key. A package could only be decrypted on
the device that created it, despite the "email/Drive sharing" claims.

`PackageCrypto.kt` now supports a portable envelope:

- AES-256-GCM ciphertext
- key derived from a user-supplied passphrase with PBKDF2-HmacSHA256 (150,000 iterations,
  16-byte random salt, 12-byte random IV)
- self-describing JSON envelope: `format`, `version`, `kdf`, `iv`, `ciphertext`

Enter a passphrase in **Settings → Team Synchronization** before exporting to create a
portable package, and enter the same passphrase before importing it on another device.
Packages exported without a passphrase keep the legacy device-bound format (`cryptoMode:
"device"`) and remain readable only locally. Import accepts both formats and reports a
clear error if a passphrase-protected package is opened without one.

> Passphrase strength is the user's responsibility. Use a long, random phrase and share it
> out of band — not in the same email as the package.

## D-07 — Inspections round-trip

Two changes make inspection history portable and idempotent:

1. `InspectionRecord` gained a stable `syncId` (UUID, unique index). Room migration
   `MIGRATION_3_4` adds the column to existing databases and backfills it with
   `lower(hex(randomblob(16)))`. The old local auto-increment `id` is not used across devices.
2. Export writes `syncId` with each inspection. Import resolves the equipment by `tagId`,
   looks up an existing inspection by `syncId`, and updates it or inserts a new one.
   Re-importing the same package therefore does not duplicate audit records.

`photoUri` and `signaturePath` are deliberately not transferred: they point to files on
the originating device. On an update import the existing local paths are preserved.

## D-08 — Reminder settings round-trip

Export now includes `reminderEnabled` and `reminderLeadDays` for each equipment item, and
import reads them (falling back to the model defaults for older v2 packages). The due date
and review time were already included.

## Import UI

Import was previously unreachable — `importEncryptedTeamPackage()` had no caller. Settings
now has an **Import Encrypted Team Package** button backed by
`ActivityResultContracts.GetContent()`, plus a passphrase field shared with export.

## Tests

- `PackageCryptoTest` — real encrypt/decrypt round-trip, wrong passphrase and tamper
  rejection, envelope/legacy detection, blank passphrase.
- `TeamSyncEngineTest` — `buildPackageJson()` now produces the real exported shape and is
  tested for reminder fields, `syncId`, `equipmentTag` and version/crypto mode instead of
  asserting on a self-constructed JSON object.

## Remaining caveat

Cross-device import relies on matching `tagId` values between devices. If two sites use the
same tag for different assets, the import will merge them. A future pass should add an
organization/asset namespace to the package and validate it before import.
