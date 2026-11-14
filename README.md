# MAXTER PLAYER — Android Native Source & Build Guide
**Tagline:** *Your Videos. Your Control.*

## 1. Architecture & Technical Overview
MAXTER PLAYER is an original, modern Android video player built with:
- **Language & UI:** Kotlin 1.9.24 + Jetpack Compose (Material 3 Dark AMOLED `#000000`, Charcoal `#121214`, & Orange `#FF6700` theme)
- **Playback Engine:** AndroidX Media3 ExoPlayer (`1.3.1`) with `MediaSessionService` background audio, external `ACTION_VIEW` video intent handling, and Picture-in-Picture (PiP) support
- **Decoder Pipeline:** `HW` (native `MediaCodec`), `HW+` (MediaCodec + extension renderers with automatic fallback), and `SW` (software preferred fallback)
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
        │   │   ├── PlaybackService.kt
        │   │   └── SmartEnhanceGlShader.kt
        │   └── ui/
        │       ├── MaxterViewModel.kt
        │       ├── MaxterAppRoot.kt
        │       └── theme/Theme.kt
        └── res/
            ├── drawable/
            │   └── maxter_logo.png
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
2. Make sure the old `app/src/main/java/com/master/` and `app/src/main/assets/Images/` folders are deleted from GitHub so only `app/src/main/java/com/maxter/player/` and `app/src/main/assets/images/` exist.
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
