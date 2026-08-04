# Tech Stack Document
## Telegram-Backed Photo Vault — Android v1

---

## 1. Platform & Language
- **Language**: Kotlin (100% — no Java).
- **minSdk**: 26 (Android 8.0) — covers the near-totality of active devices
  while still supporting scoped storage APIs cleanly.
- **targetSdk**: latest stable at build time.
- **Build system**: Gradle with Kotlin DSL (`build.gradle.kts`).

## 2. UI Layer
- **Jetpack Compose** with **Material 3** as the component foundation, then
  themed to a custom design system (see the design document) rather than
  left at Material defaults.
- **Navigation**: `androidx.navigation:navigation-compose`.
- Reasoning: Compose is the modern default, pairs naturally with the
  glass/blur treatment via `RenderEffect` and `Modifier.graphicsLayer`, and
  matches the LifeForge codebase's general direction.

## 3. Architecture Pattern
Clean-ish MVVM, three layers:

```
UI (Compose screens)
   ↕ observes state from
ViewModel (per screen, exposes StateFlow)
   ↕ calls into
Repository (single source of truth per domain: Auth, Media, Vault/Index)
   ↕ backed by
Data sources: TDLib client, Room DB, MediaStore
```

- Repositories are the only layer allowed to touch TDLib or Room directly.
- ViewModels never talk to TDLib or Room directly — only through a
  repository interface, which keeps the TDLib dependency swappable/testable.

## 4. Local Persistence
- **Room** for the local index cache (mirrors the LifeForge pattern) —
  tables for vault items and sync metadata.
- Room is a **cache of the Telegram-stored index**, not the source of truth
  — see the DRD's FR-INDEX requirements.

## 5. Concurrency & Background Work
- **Kotlin Coroutines + Flow** throughout — no callbacks, no RxJava.
- **WorkManager** reserved for v2's background auto-backup; v1's manual
  upload queue runs as a coroutine-based in-app queue tied to app lifecycle.

## 6. Telegram / MTProto Integration
- **TDLib** (official Telegram client library) via JNI.
- Prefer a **prebuilt AAR** distribution (e.g. a JitPack-hosted precompiled
  TDLib bundle) over building TDLib from source, to avoid a multi-hour
  NDK/CMake cross-compile detour. Fall back to building from source only if
  a prebuilt proves unreliable.
- Optionally layer a **Kotlin Coroutines/Flow wrapper** over TDLib's raw
  callback-based Java bindings, so the rest of the app never touches raw
  TDLib callbacks directly.
- TDLib requires a local database encryption key at init time for its own
  internal cache — generate and store this silently; it is unrelated to the
  app's own file-encryption decision (deferred to v2).

## 7. Image Loading
- **Coil** (Compose-first, Kotlin-coroutines-native) for thumbnail loading
  and caching — the standard choice for Compose image loading, minimal
  boilerplate, disk+memory caching built in.

## 8. Secure Local Storage
- `androidx.security.crypto` (Jetpack Security) — `EncryptedSharedPreferences`
  backed by the Android Keystore, for `api_id`, `api_hash`, and TDLib
  session state.
- This is deliberately scoped to **credentials only** — it is separate from,
  and does not conflict with, the v1 decision to skip encrypting the actual
  photo/video files.

## 9. Dependency Injection
- **Hilt** — standard for modern Android, minimal boilerplate over Dagger,
  integrates cleanly with ViewModel and WorkManager (needed once v2's
  background backup lands).

## 10. Testing
- **JUnit5** + **MockK** for repository/ViewModel unit tests.
- **Compose UI testing** (`androidx.compose.ui:ui-test-junit4`) for critical
  flows: login, selection, upload queue states.
- Given solo/small-team development, prioritize tests around FR-INDEX
  reconciliation logic and the upload queue's backoff behavior — these are
  the two places silent bugs would be most damaging (data loss / account
  flood-limiting).

## 11. CI/CD
- **GitHub Actions** (free for public repositories) — build + unit tests on
  every PR; optionally a release workflow that builds and attaches a signed
  APK to GitHub Releases on tag push.

## 12. Distribution
- **GitHub Releases** for the APK from day one.
- **F-Droid** as a natural fit once stable — it's the standard distribution
  channel for privacy-oriented open-source Android apps and doesn't require
  a Play Store developer fee.
- Google Play listed as optional/later — note that Play's policies around
  broad storage/media permissions may require additional justification
  during review.

## 13. Deliberately Not Used (v1)
- **No Firebase Crashlytics / Analytics by default** — conflicts with the
  app's own "your data stays in your account" positioning (NFR-PRIVACY-1 in
  the DRD). If crash reporting is added later, it should be opt-in and use
  an open alternative (e.g. ACRA) rather than a Google-owned pipeline.
- **No Bot API** anywhere in the stack — see the DRD's risk section; MTProto
  via TDLib is the only integration path used.

## 14. Consolidated Dependency Reference
```kotlin
dependencies {
    // Compose + Material 3
    implementation("androidx.compose.material3:material3:<latest>")
    implementation("androidx.navigation:navigation-compose:<latest>")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:<latest>")

    // Room
    implementation("androidx.room:room-runtime:<latest>")
    implementation("androidx.room:room-ktx:<latest>")
    ksp("androidx.room:room-compiler:<latest>")

    // TDLib (prebuilt AAR — verify current publisher/coordinates before use)
    implementation("com.github.tdlibx:td:<latest>")

    // Image loading
    implementation("io.coil-kt:coil-compose:<latest>")

    // Secure storage
    implementation("androidx.security:security-crypto:<latest>")

    // DI
    implementation("com.google.dagger:hilt-android:<latest>")
    ksp("com.google.dagger:hilt-compiler:<latest>")

    // Testing
    testImplementation("io.mockk:mockk:<latest>")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:<latest>")
}
```
*(Pin exact versions when the agent scaffolds the project — check each
library's current release rather than trusting versions from training data.)*
