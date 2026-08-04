# Telegram-Backed Photo Vault — Android PRD & Build Roadmap
*(working title — rename freely)*

## 1. What we're building
An open-source Android app that works like Google Photos, but stores every file
in the user's own Telegram account instead of a company server. The user logs
in with their real Telegram account (phone number + OTP, not a bot), browses
their device's photos/videos, selects what to back up, and the app uploads
them to Telegram as full-quality documents. The index of what's been uploaded
lives inside Telegram itself, so a second device (or the future desktop app)
can log into the same account and see the same library with no separate
backend server.

## 2. Core architecture decisions (locked in)
- **No backend server.** The index of uploaded files is a JSON blob kept in
  the user's own Saved Messages, updated after every change. Any device that
  logs into the same Telegram account can pull it down. This is what keeps
  the "your data lives only in your Telegram account" promise true.
- **Files upload as Documents, never as Photo/Video.** Telegram compresses
  anything sent inline; Document mode skips that and preserves full quality,
  up to the account's real ceiling (2GB free / 4GB Premium per file).
- **No client-side file encryption in v1.** Deliberate scope cut to avoid the
  device-bound-key/data-loss risk. Revisit later as an opt-in, portable
  passphrase-based scheme — does not block anything below.
- **User-supplied `api_id` / `api_hash`.** Entered once on first run (from
  my.telegram.org), stored on-device. Isolates risk per user instead of one
  shared credential that could get every install flagged at once.
- **MTProto via TDLib**, not the Bot API. The Bot API caps uploads at 50MB and
  Telegram's own bot developer terms explicitly exclude "cloud storage site"
  use cases. TDLib is the official, most mature MTProto client library and is
  what the more serious open-source Telegram-storage projects use.
- **Session credentials get OS-level protection**, separate from the "no file
  encryption" decision above. `api_id`/`api_hash`/session token go in Android's
  `EncryptedSharedPreferences` (Keystore-backed). This doesn't reintroduce the
  cross-device key problem — each device already gets its own independent
  MTProto session by design, so there's nothing to keep in sync here.

## 3. Local storage
- Room DB (mirrors your LifeForge pattern) as a fast local **cache** of the
  index — not the source of truth.
- Source of truth = the JSON blob in Saved Messages. On login: pull it down,
  hydrate Room. After every upload/delete: update Room, then push the updated
  blob back up.

## 4. Auth flow
1. First-run screen: short explainer + link to my.telegram.org + two fields
   (`api_id`, `api_hash`).
2. Phone number entry.
3. OTP code entry.
4. 2FA cloud password field, shown only if the account has one enabled.
5. On success: store credentials + session in `EncryptedSharedPreferences`.

## 5. Upload pipeline
1. MediaStore query → grid of device photos/videos, multi-select.
2. Selected files sent via TDLib as Document to Saved Messages (or a
   dedicated "vault" channel — decide once v0.2 is running).
3. On success: append `{id, messageId, filename, sizeBytes, checksum,
   takenAt, uploadedAt}` to the index, update Room, push the updated blob.
4. **Sequential upload queue with backoff**, honoring TDLib's flood-wait
   errors — not optional. A first-time backup of an existing multi-thousand
   photo library will trip Telegram's flood protection if uploads fire in
   parallel bursts. Pace it, and surface "backing up, this will take a
   while" in the UI rather than promising instant sync.

## 6. Gallery UI
- Grid view, thumbnails cached locally.
- Full-resolution file fetched on demand when tapped — don't hoard full files
  on-device by default; that defeats the point of offloading storage.

## 7. Index JSON shape (draft)
```json
{
  "version": 1,
  "files": [
    {
      "id": "abc123",
      "messageId": 48291,
      "filename": "IMG_20260731.jpg",
      "sizeBytes": 4213112,
      "checksum": "sha256:...",
      "takenAt": "2026-07-31T10:22:00Z",
      "uploadedAt": "2026-07-31T10:23:11Z"
    }
  ]
}
```

## 8. Phased build roadmap
Each version should compile and run before moving to the next — don't let the
agent skip ahead.

- **v0.1 — Auth only.** TDLib integrated, first-run api_id/api_hash screen,
  phone/OTP/2FA login, session stored encrypted. No UI beyond "logged in as
  +91XXXXXXXXXX."
- **v0.2 — Manual upload, local only.** MediaStore picker, multi-select,
  upload as Document, record locally in Room. No Telegram-index-sync yet.
- **v0.3 — Serverless index sync.** JSON blob read/write to Saved Messages;
  this is the piece that makes multi-device possible later. Test by wiping
  local Room and re-hydrating from the Telegram blob.
- **v0.4 — Gallery UI.** Grid, thumbnails, lazy full-res fetch on tap —
  the actual Google-Photos-like experience.
- **v0.5 — Background auto-backup.** WorkManager-driven, watches for new
  camera roll files, feeds the paced upload queue with flood-wait handling.
- **v0.6 — Hardening.** Error states, retry UI, duplicate detection via
  checksum, settings screen, README + open-source packaging.

## 9. v0.1 starter prompt
Paste this into Antigravity (with the Android CLI bundle installed) or
Android Studio's agent to kick off v0.1:

> Create a new native Android app in Kotlin using Jetpack Compose, minSdk 26.
> Integrate TDLib for Android (try the prebuilt AAR via JitPack —
> `com.github.tdlibx:td` — before attempting to build TDLib from source).
> Build a first-run screen that collects a Telegram `api_id` and `api_hash`
> from the user, with a short explainer and a link to my.telegram.org.
> Follow that with a standard MTProto login flow: phone number entry, OTP
> code entry, and a conditional 2FA cloud-password field shown only if the
> account requires it. On successful login, store `api_id`, `api_hash`, and
> the resulting TDLib session state in Android's `EncryptedSharedPreferences`
> (Keystore-backed). No other screens yet — the only success criteria is
> reaching a screen that shows "Logged in as +<phone number>" after a real
> login.

## 10. Tooling notes
- **TDLib on Android:** try a prebuilt AAR first (JitPack `tdlibx/td`, or a
  Kotlin Coroutines/Flow wrapper on Maven) — building TDLib from source via
  NDK/CMake is a multi-hour, failure-prone detour most people don't need to
  take for a first build.
- TDLib asks for a local database encryption key on init — that's for its own
  on-device cache only, unrelated to the "no photo encryption" decision.
  Generate and store it silently.
- **IDE:** drive the build with Antigravity (install the Android CLI bundle:
  Settings → Customizations → Build With Google Plugins) since it matches
  your existing workflow. Keep Android Studio installed alongside it — the
  moment you hit a Gradle/NDK linking error the agent can't resolve (likely
  at least once, given TDLib's native component), that's where you'll debug
  it. Skip AI Studio for this project — it's aimed at lightweight prototypes,
  not native-library-integrated production apps.
