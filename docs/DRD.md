# Detailed Requirements Document (DRD)
## Telegram-Backed Photo Vault — Android v1

---

## 1. Product Vision
A free, open-source Android app that gives people Google-Photos-style backup
and browsing for their photos and videos, without a company-owned server in
the middle. Storage lives entirely inside the user's own Telegram account.
Nobody — including the app's own developer — holds a copy of the user's
library on infrastructure they control.

## 2. Problem Statement
Free-tier cloud photo backup (Google Photos, iCloud) is shrinking or paywalled.
Existing Telegram-backed alternatives are mostly single-purpose, one-way
backup utilities on one platform — none combine real two-device sync, a
polished gallery experience, and full quality preservation in one open-source
app.

## 3. Target Users
- Privacy-conscious users who already have a Telegram account and want an
  alternative to paid cloud storage.
- Users in regions/plans where mobile data is expensive — value that backup
  can be paced and doesn't require a subscription.
- Open-source contributors who want to extend or self-audit the app, since
  it touches their personal media.

## 4. Goals — v1
- G1: Log in with a real Telegram account (phone + OTP + optional 2FA).
- G2: Browse the device's photo/video library and manually select items to
  back up.
- G3: Upload selected items to the user's own Telegram account at original
  quality (no compression).
- G4: Maintain an index of what's backed up, stored inside Telegram itself,
  so a second device on the same account can reconstruct the library with no
  external server.
- G5: Browse the backed-up library in a dedicated in-app gallery, independent
  of Telegram's own chat UI.
- G6: Handle Telegram's rate limits gracefully during large first-time
  backups.

## 5. Non-Goals — v1 (explicitly deferred)
- NG1: Client-side file encryption. Deferred to v2 as an opt-in, portable
  (non-device-bound) passphrase scheme.
- NG2: iOS or desktop apps. Android only for v1; desktop is planned next,
  reusing the same Telegram-stored index format.
- NG3: Automatic background backup. v1 is manual/on-demand only; background
  auto-backup is a v2 feature once the manual pipeline is proven stable.
- NG4: Sharing, albums, collaborative libraries, or social features.
- NG5: AI features (auto-tagging, face grouping, search-by-content).

## 6. Functional Requirements

### FR-AUTH — Authentication
- FR-AUTH-1: First-run screen collects the user's own Telegram `api_id` and
  `api_hash`, with inline instructions and a link to my.telegram.org.
- FR-AUTH-2: Standard MTProto login: phone number → OTP code → conditional
  2FA cloud-password field (shown only if the account has 2FA enabled).
- FR-AUTH-3: On success, persist credentials and session state locally,
  scoped to this device only.
- FR-AUTH-4: Provide a "Log out" action that clears the local session without
  touching any data already stored in the user's Telegram account.
- FR-AUTH-5: Detect and surface an invalid/expired session (e.g., user
  revoked the session from Telegram's own device-management screen) and
  route back to login without data loss.

### FR-MEDIA — Device Media Access
- FR-MEDIA-1: Request only the minimum media permissions needed (scoped
  photo/video access on Android 13+, legacy storage permission below that).
- FR-MEDIA-2: List device photos and videos via MediaStore, newest first,
  with folder/album filtering.
- FR-MEDIA-3: Multi-select with a running count and total size of the current
  selection before upload.

### FR-UPLOAD — Upload Pipeline
- FR-UPLOAD-1: Every file uploads as a Telegram Document, never as inline
  Photo/Video, to avoid Telegram's automatic compression.
- FR-UPLOAD-2: Uploads process from a sequential queue, not in parallel
  bursts, with exponential backoff on any flood-wait response from Telegram.
- FR-UPLOAD-3: Each queued item shows a per-file progress state (queued,
  uploading, done, failed).
- FR-UPLOAD-4: A failed upload retries automatically up to N times before
  surfacing as "failed — tap to retry" to the user.
- FR-UPLOAD-5: Files over Telegram's per-account size ceiling (2GB free /
  4GB Premium) are flagged before upload starts, not after failing partway.

### FR-INDEX — Serverless Index Sync
- FR-INDEX-0: On login, the app searches the user's Telegram chat list for
  an existing private vault channel titled exactly `OpenPhotos Vault` with
  About text exactly `vault-marker:openphotos-v1`. If found, it's reused;
  if not, the app creates a new private channel with that exact title and
  About text, with the user as sole member/owner. All uploads and the
  index live in this channel, never in Saved Messages — keeps the vault
  isolated from the user's own unrelated Telegram activity.
- FR-INDEX-1: After every successful upload or deletion, the app updates a
  JSON index and writes it back to a fixed, pinned message inside the
  private vault channel (from FR-INDEX-0).
- FR-INDEX-2: On login (including on a second device), the app pulls the
  latest index from Telegram and reconciles it against the local cache.
- FR-INDEX-3: The index includes enough metadata (filename, size, checksum,
  original taken-date, Telegram message reference) to fully reconstruct the
  gallery without re-downloading files.
- FR-INDEX-4: If the local cache and the remote index disagree (e.g., a
  second device made changes since the last sync), the remote index wins,
  and any local-only pending changes are replayed on top of it.

### FR-GALLERY — Library Browsing
- FR-GALLERY-1: Grid view of everything backed up, sorted by taken-date.
- FR-GALLERY-2: Thumbnails are cached locally; full-resolution files are
  fetched on demand when the user opens an item, not pre-downloaded in bulk.
- FR-GALLERY-3: Tapping an item opens a full-screen viewer with swipe
  navigation between adjacent items.
- FR-GALLERY-4: An item can be deleted from the vault (removes the Telegram
  message and updates the index) independently of deleting it from the
  device.

### FR-BACKUP-STATUS — Progress & State
- FR-BACKUP-STATUS-1: A persistent, dismissible status surface shows overall
  backup progress ("124 of 340 backed up") during an active queue.
- FR-BACKUP-STATUS-2: The app clearly communicates when uploads are paused
  due to a Telegram rate limit, including an estimated resume time if
  Telegram provides one.

### FR-SETTINGS — Settings
- FR-SETTINGS-1: View and edit stored `api_id`/`api_hash`.
- FR-SETTINGS-2: Log out.
- FR-SETTINGS-3: View total items backed up and total size.
- FR-SETTINGS-4: Link to the open-source repository and license.

## 7. Non-Functional Requirements
- NFR-PERF-1: Gallery grid scrolls smoothly (target 60fps) with a locally
  cached thumbnail set of at least 5,000 items.
- NFR-PERF-2: Cold start to a usable gallery view under 2 seconds on a
  mid-range device, once the local cache is warm.
- NFR-REL-1: An interrupted upload (app killed, network drop) resumes or
  cleanly retries on next launch — no orphaned partial uploads.
- NFR-REL-2: The app never permanently loses the index — the Telegram-stored
  copy is always the recoverable source of truth.
- NFR-COMPAT-1: minSdk 26 (Android 8.0), targetSdk latest stable.
- NFR-COMPAT-2: Functions correctly on the four standard ABIs
  (arm64-v8a, armeabi-v7a, x86_64, x86).
- NFR-PRIVACY-1: No third-party analytics or crash reporting enabled by
  default; if added, it must be opt-in and disclosed in Settings.
- NFR-PRIVACY-2: No data leaves the device except to the user's own Telegram
  account — no app-owned backend server exists in v1.
- NFR-OSS-1: Codebase and license are public from the first commit; no
  telemetry, no hidden network calls beyond Telegram's own API.

## 8. Key User Flows

**First run → first backup**
1. Open app → first-run screen → enter api_id/api_hash → phone number → OTP
   → (2FA if applicable) → logged in.
2. Gallery permission prompt → device media grid appears.
3. User multi-selects items → taps "Back up" → queue starts, paced uploads,
   progress surface visible.
4. On completion, items appear in the in-app vault gallery.

**Second device, same account**
1. Install app on second device → same login flow.
2. App locates the existing private vault channel (FR-INDEX-0) and pulls the
   index from it → local gallery populates without re-uploading anything.

**Viewing and deleting an item**
1. Tap a thumbnail → full-screen viewer, swipe to adjacent items.
2. Delete from vault → confirmation → Telegram message removed → index
   updated → change propagates to other devices on next sync.

## 9. Edge Cases & Error Handling
- Login with an account that has no 2FA vs. one that does.
- User revokes the app's session from within Telegram itself — app must
  detect this and re-prompt login, not silently fail.
- Duplicate upload of an already-backed-up file (match by checksum, skip or
  warn rather than re-uploading).
- Upload interrupted mid-transfer by lost connectivity.
- Telegram flood-wait triggered mid-queue — queue pauses and resumes
  automatically rather than dropping remaining items.
- Local cache and remote index diverge after an offline period.
- File exceeds the account's size ceiling (2GB/4GB).
- User deletes the pinned index message, or the whole vault channel, manually
  from Telegram itself — app should detect a missing index and offer to
  rebuild it from the device's local cache, or from scanning the channel's
  message history as a fallback.
- User leaves or accidentally deletes the vault channel entirely — since
  they're the sole owner, this is destructive and unrecoverable; consider
  a confirmation step in Settings if the app ever offers a "leave/delete
  channel" action (it shouldn't by default).

## 10. Constraints & Assumptions
- Fully dependent on Telegram's continued API availability, size limits, and
  ToS — the entire product model breaks if Telegram changes any of these.
- No control over Telegram's flood-wait thresholds; they're undocumented and
  can vary by account age/reputation.
- Users are responsible for obtaining and safeguarding their own
  `api_id`/`api_hash`.

## 11. Risks
- **Policy risk**: Telegram could change ToS or rate-limit MTProto-based
  storage clients at any time — no SLA exists for this use case.
- **Account risk**: Aggressive/misconfigured upload pacing could get an
  individual user's account temporarily flood-limited.
- **Native integration risk**: TDLib's native (JNI) layer is the most likely
  source of hard-to-debug build issues.
- **No-encryption risk (accepted for v1)**: Files are protected only by
  Telegram's own transport security and account access control, not by the
  app — must be stated clearly in the README, not just implied.

## 12. Acceptance Criteria — v1 "Done"
- A new user can go from install to first successfully backed-up photo in
  one sitting, without needing to read documentation beyond the in-app
  first-run instructions.
- A second device logging into the same account sees the full library
  without any manual restore step.
- A 500+ photo bulk backup completes without the app crashing or the account
  receiving anything beyond a normal, self-resolving flood-wait pause.
- No file is ever visibly degraded in quality compared to the original.

## 13. Future Scope (v2+)
- Opt-in client-side encryption with a portable passphrase.
- Background automatic backup (WorkManager-driven).
- Desktop app (Tauri) sharing the same Telegram-stored index format.
- iOS app.
- Duplicate/near-duplicate detection, storage stats, album organization.
