# MAXTER PLAYER — Android Native Source & Build Guide
**Tagline:** *Your Videos. Your Control.*

## 1. Architecture & Technical Overview
MAXTER PLAYER is an original, modern Android video player built with:
- **Language & UI:** Kotlin 1.9.24 + Jetpack Compose (Material 3 Dark AMOLED `#000000`, Charcoal `#121214`, & Orange `#FF6700` theme)
- **Playback Engine:** AndroidX Media3 ExoPlayer (`1.3.1`) with external `ACTION_VIEW` video intent handling and Picture-in-Picture (PiP) support
- **Decoder Pipeline:** `HW` (native `MediaCodec`), `HW+` and `SW`. Automatic fallback HW → HW+ → SW happens on decoder errors only. No FFmpeg extension is bundled, so HW+ / SW currently use the same MediaCodec path (with codec fallback enabled)
- **Smart Enhance GPU Pipeline (`SmartEnhanceGlShader.kt`):** Real-time OpenGL ES 3.0 GLSL shader integrated into `ExoPlayer.setVideoEffects()` providing:
  - Contrast-Adaptive Sharpening (CAS / Unsharp Mask kernel)
  - Spatial edge-preserving bilateral luma denoising
  - Brightness, Contrast, Saturation, and Night Mode amber blue-light filter
  - Interactive live Split-Screen Before/After comparison divider
- **Audio Engine:** Android `Equalizer`, `BassBoost`, `Virtualizer`, and `LoudnessEnhancer` (up to 200% volume boost) bound directly to ExoPlayer's `audioSessionId`
- **Database & Repository:** AndroidX Room (`MaxterDatabase.kt`) + `MediaScannerRepository.kt` persisting playback resume positions, recently played, continue watching, favorites, bookmarks, and playlists without overwriting user history during media scans
- **Dependency Injection:** Dagger Hilt (`AppModule.kt` & `MaxterViewModel.kt`) providing singleton database, DAO, media scanner, and player controller instances

---

## 2. Updated Project Structure
```text
Easy-player/
├── .github/
│   └── workflows/
│       └── build_apk.yml
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── README.md
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── assets/
        │   ├── index.html
        │   └── images/
        │       └── maxter-player.png
        ├── java/com/maxter/player/
        │   ├── MaxterApplication.kt
        │   ├── MainActivity.kt
        │   ├── di/
        │   │   └── AppModule.kt
        │   ├── data/
        │   │   ├── local/MaxterDatabase.kt
        │   │   ├── model/VideoModels.kt
        │   │   └── repository/MediaScannerRepository.kt
        │   ├── player/
        │   │   ├── MaxterPlayerController.kt
        │   │   └── SmartEnhanceGlShader.kt
        │   └── ui/
        │       ├── MaxterViewModel.kt
        │       ├── MaxterAppRoot.kt
        │       └── theme/Theme.kt
        └── res/
            ├── drawable/
            │   └── maxter_logo.png
            ├── drawable-nodpi/
            │   └── ic_launcher_foreground.png
            ├── mipmap-anydpi-v26/ (ic_launcher.xml, ic_launcher_round.xml)
            ├── mipmap-mdpi/ (ic_launcher.png, ic_launcher_round.png)
            ├── mipmap-hdpi/ (ic_launcher.png, ic_launcher_round.png)
            ├── mipmap-xhdpi/ (ic_launcher.png, ic_launcher_round.png)
            ├── mipmap-xxhdpi/ (ic_launcher.png, ic_launcher_round.png)
            ├── mipmap-xxxhdpi/ (ic_launcher.png, ic_launcher_round.png)
            └── values/
                ├── colors.xml
                ├── strings.xml
                └── themes.xml
```

---

## 3. Build & APK Generation Instructions

### Option A: Cloud Build via GitHub Actions (Recommended for Low-RAM Laptops)
1. Upload or push the contents of this repository to `https://github.com/balansakthi887-create/Easy-player`.
2. If an older upload is already in the repo, delete the old `app/src/main/java/com/master/` and `app/src/main/assets/Images/` folders so only `app/src/main/java/com/maxter/player/` and `app/src/main/assets/images/` remain.
3. Open the **Actions** tab on GitHub and run **Build MAXTER PLAYER Android APK** (`.github/workflows/build_apk.yml`).
4. The workflow automatically installs JDK 17 and Gradle 8.7 via `gradle/actions/setup-gradle@v3` and runs `gradle assembleDebug --no-daemon --stacktrace`.
5. Download `MAXTER-PLAYER-v2.0-AI-debug-apk` from the workflow run's **Artifacts** section.

### Option B: Local Build via Android Studio or Command Line
```bash
# Clean and build Debug APK using Gradle 8.7+ and JDK 17:
gradle clean assembleDebug --no-daemon

# Output APK location:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 4. Changelog (v2.0.1 fix)

**Build blockers fixed**
- Added the missing `app/src/main/res/` folder (strings, colors, theme, logo, launcher icons). Without it the manifest (`@string/app_name`, `@style/Theme.MaxterPlayer`, `@mipmap/ic_launcher`) and `R.drawable.maxter_logo` could not resolve, so the APK build failed.
- Moved sources from `java/com/master/player` to `java/com/maxter/player` to match the `com.maxter.player` package/namespace.
- Renamed `assets/Images` to `assets/images` (Android assets are case-sensitive; `index.html` already used `images/`).

**Bugs fixed**
- Skipping to next/previous video saved the new position under the previous video's id (stale closure).
- Right-edge swipe never changed the volume (per-event movement was below the step threshold).
- 16:9 and 4:3 aspect modes did nothing.
- Volume boost / bass boost were never attached (audio session id was missed), and one unsupported audio effect disabled all the others.
- Smart Enhance / mirror / night settings, boost and bass were lost whenever the decoder was switched or fell back.
- Decoder fallback was triggered by network/file errors; now only by decoder errors.
- GL effect pipeline ran even when every effect was off; it is now disabled in that case.
- Resume position was written to the database every 500 ms; now about every 5 s.
- Deleted videos stayed in the library forever; they are now pruned after a successful scan.
- Launch intent was re-handled after activity recreation, restarting the video.
- Removed the unused `PlaybackService` (it was never started and held a separate ExoPlayer) together with its permissions.
