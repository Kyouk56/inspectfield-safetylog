# Privacy Policy for InspectField – Equipment SafetyLog

**Effective Date:** October 22, 2026  
**Last Updated:** October 22, 2026  
**Application ID:** `com.safetylog.inspection.app`  
**Publisher:** InspectField Systems / SafetyLog Compliance Group  
**Contact:** privacy@safetylog.internal / support@safetylog.internal

---

## 1. Overview & Local-First Philosophy
InspectField SafetyLog ("the Application") is an offline-capable, local-first commercial life safety compliance utility built for fire safety inspectors, facility managers, and safety compliance officers.

We believe that your facility's safety records belong to you. By default, **all inspection logs, equipment registries, technician digital signatures, defect tags, and audit histories are stored directly and exclusively on your local device** using an encrypted Room database and hardware-isolated Android Keystore memory. We do not sell, rent, or monetize your personal or inspection data.

---

## 2. Information We Collect and How We Use It

### A. Information Stored Locally on Your Device
- **Equipment Asset Data:** Tag IDs, serial numbers, equipment types, physical location descriptions, and inspection frequency protocols (NFPA 10, NFPA 25, NFPA 72, OSHA 1910.151).
- **Inspection Checklists & Records:** Passed/Deficient verification responses, pressure gauge readings, tamper seal observations, defect descriptions, and timestamps.
- **Digital Signatures:** Touch-drawn digital inspector signatures collected on-device, bound to certified PDF compliance reports.
- **Inspector Profile Metadata:** Inspector name, company name, certification license numbers (e.g. NICET, State Fire Marshal), and contact email/phone for display on generated PDF compliance audit certificates.

### B. Device Permissions & Hardware Access
- **Camera (`android.permission.CAMERA`):**  
  Used exclusively when you choose to take a photo of an equipment asset or a specific defect during an inspection. Photos are stored locally on your device or in designated application directories. The camera is never accessed in the background, and photos are never uploaded to advertising networks.
- **Notifications (`android.permission.POST_NOTIFICATIONS`):**  
  Used solely to display high-importance scheduled inspection due-date reminders that you configure. We do not send marketing, promotional, or remote push tracking notifications.
- **Exact Alarms (`android.permission.SCHEDULE_EXACT_ALARM`):**  
  Used to trigger calendar-precise reminder alarms for recurring NFPA/OSHA inspection schedules (monthly, quarterly, semi-annual, annual). The alarm manager schedules are calculated on-device.
- **Device Reboot (`android.permission.RECEIVE_BOOT_COMPLETED`):**  
  Used solely to restore your scheduled inspection alarms in the system `AlarmManager` after your device restarts so compliance due dates are not missed.
- **Device Calendar (Zero-Permission Intent):**  
  When you tap "Add to Device Calendar", the application launches your device's native Calendar app via Android's standard `ACTION_INSERT` contract with pre-populated event details and recurrence rules (`RRULE`). The Application does **not** read your calendar or request calendar permissions.

---

## 3. Cryptographic Security & Hardware Keystore
- **Android Keystore MasterKey:**  
  Authentication tokens, session hashes, and sensitive secrets are encrypted using AES-256-GCM cipher keys backed by hardware Trusted Execution Environments (TEE) or StrongBox Hardware Security Modules (HSM) where available on the device.
- **Crash Log Vault:**  
  Application crash logs and diagnostic traces are encrypted and stored in a private local vault (`CrashLogVault`). Cloud telemetry dispatch is strictly **opt-in** and disabled by default. You can inspect or clear crash traces at any time in Settings.

---

## 4. Third-Party Services & Sub-Processors

### A. Google Mobile Ads (AdMob)
- **Free-Tier Users:** The standard free tier includes non-intrusive banner advertisements served via the Google Mobile Ads SDK. Google AdMob may collect and process device identifiers (such as the Google Advertising ID), IP addresses, and diagnostic signals in accordance with [Google's Privacy Policy](https://policies.google.com/privacy).
- **Ad-Free & Paid Subscribers:** Upgrading to the Ad-Free Pass ($2.99), Individual Pro ($0.99/mo), or Enterprise ($0.50/seat/mo) completely and permanently disables all AdMob banner instances and network requests.

### B. Google Play In-App Billing
- Subscription and in-app purchase transactions are processed securely through Google Play In-App Billing. InspectField SafetyLog does not collect, receive, or store your credit card or financial account numbers. All financial transactions are subject to the [Google Play Terms of Service](https://play.google.com/intl/en_us/about/play-terms/).

### C. Optional Cloud Drive & Webhook Backups
- If you configure Company OAuth, Team Sync, or Google Drive folder exports, data transmission occurs exclusively over HTTPS/TLS to the endpoints or folders that you or your organization explicitly authorize.

---

## 5. Data Retention, Portability & Deletion Rights
- **Data Export:** You can export all inspection history records and certified compliance audit certificates into standard US Letter PDF format at any time.
- **Data Deletion:** Decommissioning an asset or clearing data within the application permanently removes the corresponding records from your device's SQLite/Room storage. Uninstalling the application deletes all locally stored data.

---

## 6. Children's Privacy
InspectField SafetyLog is designed for commercial facility safety managers, technicians, and compliance officers (ages 18 and older). We do not knowingly collect personal information from children under 13.

---

## 7. Changes to This Privacy Policy
We may periodically update this Privacy Policy to reflect application updates or regulatory requirements. Any modifications will be posted within the Application settings and on the hosted policy URL.

---

## 8. Contact & Compliance Inquiries
If you have questions regarding this Privacy Policy or your data protections, please contact our Compliance Officer:
- **Email:** `privacy@safetylog.internal`
- **Application:** InspectField SafetyLog – Settings > Regulatory & Privacy
