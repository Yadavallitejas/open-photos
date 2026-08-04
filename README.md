# OpenPhotos — Telegram-Backed Photo Vault

> **Free, open-source Android app giving you Google-Photos-style backup and gallery browsing, backed entirely by your own Telegram account.**

---

> ⚠️ **IMPORTANT SECURITY & PRIVACY NOTICE (v1 Scope)**
> 
> **Client-side file encryption is NOT implemented in v1.**
> 
> All media files are uploaded at original quality as raw Telegram Documents into your personal **Saved Messages** chat. Your files are protected **solely by Telegram's transport security (MTProto) and your Telegram account access controls**.
> 
> Anyone with access to your Telegram account or Saved Messages chat will be able to view these files. Opt-in portable client-side passphrase encryption is explicitly planned for **v2** (see [`docs/DRD.md`](docs/DRD.md) §5 / §11).

---

## 🌟 Features (v1)

- **Direct Telegram Authentication**: Log in with your real Telegram account using your own `api_id` and `api_hash` from `my.telegram.org` (Phone → OTP → conditional 2FA cloud password).
- **Original Quality (Zero Compression)**: Every photo and video is uploaded as a Telegram Document (never as inline compressed photos/videos) to preserve 100% of original bytes and EXIF metadata.
- **Serverless Index Sync**: Maintains a compact JSON index (`openphotos_index_v1.json`) stored in your Saved Messages. Reinstalling or setting up a second device reconstructs the full gallery in seconds without re-downloading media files.
- **Sequential Queue & Flood-Wait Handling**: Paced single-file upload queue that gracefully handles Telegram rate limits (FLOOD_WAIT) with automatic exponential backoff and live countdown banners.
- **Checksum Duplicate Detection**: Computes SHA-256 hashes locally prior to upload to prevent uploading duplicate files to Saved Messages.
- **Background Auto-Backup**: Optional WorkManager job that periodically (every 15 min when connected) scans for new camera photos and feeds them into the backup queue.
- **"Glass-Card-On-Void" Design System**:
  - Chromeless 3-column thumbnail grid on deep Void `#060709` background.
  - Tiered Glass system (`RenderEffect` blur on Android 12+ API 31+, translucent fallback on API 26–30).
  - Animated **Sync Halo** around active uploads, contracting into an Aurora checkmark on completion.
  - Full-screen viewer with shared-element transitions (`SharedTransitionLayout`).

---

## 🚫 Deferred Scope (What v1 Deliberately Does NOT Do)

Per [`docs/DRD.md`](docs/DRD.md) §5, the following features are intentionally out of scope for v1:
- **Client-side encryption** (v2 feature).
- **iOS and Desktop apps** (Android v1 first; Tauri desktop planned for v2).
- **Social sharing, shared albums, collaborative libraries**.
- **AI features** (face grouping, auto-tagging, cloud search).

---

## 🏗️ Architecture & Tech Stack

Built following modern Android practices (Clean MVVM, strict 3-layer architecture):

```
UI Layer (Compose, Material 3, Custom Glass Design System)
       ↕ StateFlow / ViewModel
ViewModel Layer (Per-screen StateFlow adapters)
       ↕ Repository interfaces
Repository Layer (TelegramAuthRepository, UploadRepository, IndexRepository, MediaRepository)
       ↕ Data sources
Data Sources (TDLib JNI, Room DB cache, DataStore + Keystore, MediaStore)
```

- **Layering Rule**: Repositories are the **only** layer allowed to touch TDLib or Room directly. ViewModels and UI composables never touch TDLib or Room.
- **TDLib Integration**: Official Telegram Client Library (prebuilt JNI AAR via `com.github.tdlibx:td`).
- **Local Cache**: Room Database (`vault_db`) caches the backed-up items and mirrors the remote Telegram JSON index.
- **Secure Credentials**: `api_id`, `api_hash`, and TDLib database keys are encrypted using `DataStore` + Android Keystore (AES-256/GCM).

---

## 🛠️ Building & Setup Instructions

### Prerequisites
- **JDK 17** (or Android Studio Ladybug/JBR 17+).
- **Android SDK** (API 35 compileSdk, minSdk 26).
- **Telegram API Credentials**: Obtain an `api_id` and `api_hash` for free at [my.telegram.org](https://my.telegram.org).

### Build Commands

1. **Clone the repository**:
   ```bash
   git clone https://github.com/Yadavallitejas/open-photos.git
   cd open-photos
   ```

2. **Compile Debug APK**:
   ```bash
   # Linux / macOS
   ./gradlew :app:assembleDebug

   # Windows (PowerShell with Android Studio JBR)
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
   .\gradlew :app:assembleDebug --no-daemon
   ```

3. **Install to Connected Device / Emulator**:
   ```bash
   ./gradlew :app:installDebug
   ```

---

## 📁 Repository Structure & Documentation

```
openphotos/
├── app/                      # Main Android application module
│   └── src/main/java/com/qaxlabs/openphotos/
│       ├── data/             # Repositories, Room DB, WorkManager & TDLib wrapper
│       ├── ui/               # Compose screens (Auth, Gallery, Viewer, Upload, Settings)
│       └── OpenPhotosApp.kt  # Application entry point & Hilt WorkerFactory provider
├── docs/                     # Source-of-truth project documentation
│   ├── DRD.md                # Detailed Requirements Document
│   ├── tech_stack.md         # Technology stack & architectural guidelines
│   ├── design_system.md      # Glass-Card-on-Void visual specs & tokens
│   └── roadmap.md            # Version roadmap & Index JSON schema
├── build.gradle.kts          # Root build script
├── gradle.properties         # JVM heap & build settings (3 GB heap + ParallelGC)
└── README.md                 # Product overview & setup guide
```

---

## 📄 License

Distributed under the **Apache License 2.0**. See `LICENSE` for details.
