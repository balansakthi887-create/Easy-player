# MAXTER PLAYER — Android Native Source & Build Guide
**Tagline:** *Your Videos. Your Control.*

## 1. Architecture & Technical Overview
MAXTER PLAYER is an original, modern Android video player built with:
- **Language & UI:** Kotlin 1.9.24 + Jetpack Compose (Material 3 Dark AMOLED `#000000` & Orange `#FF6700` theme)
- **Playback Engine:** AndroidX Media3 ExoPlayer (`1.3.1`) with `MediaSessionService` background audio & PiP support
- **Decoder Pipeline:** `HW` (native `MediaCodec`), `HW+` (MediaCodec + extension renderers with automatic fallback), and `SW` (software preferred fallback)
- **Smart Enhance GPU Pipeline (`SmartEnhanceGlShader.kt`):** Real-time OpenGL ES 3.0 GLSL shader integrated into `ExoPlayer.setVideoEffects()` providing:
  - Contrast-Adaptive Sharpening (CAS / Unsharp Mask kernel)
  - Spatial edge-preserving bilateral luma denoising
  - Brightness, Contrast, Saturation, and Night Mode amber blue-light filter
  - Interactive live Split-Screen Before/After comparison divider
  - *Hardware/AI Transparency:* Real-time playback uses deterministic zero-latency GPU GLSL shaders so it runs smoothly even on 2GB–4GB RAM phones (like Infinix Note 50x / GT 20 Pro). Heavy neural super-resolution models (such as Real-ESRGAN / NCNN TFLite) require offline frame-by-frame export and are never faked as basic color filters.
- **Audio Engine:** Android `Equalizer`, `BassBoost`, `Virtualizer`, and `LoudnessEnhancer` (up to 200% volume boost) bound directly to ExoPlayer's `audioSessionId`
- **Database:** AndroidX Room (`MaxterDatabase.kt`) persisting playback resume positions, recently played, continue watching, favorites, bookmarks, playlists, and biometric/PIN private vault items
- **Low-RAM Optimization (`MaxterApplication.kt`):** Automatic `ActivityManager.isLowRamDevice` detection capping Coil video-frame thumbnail memory cache to 12% on low-RAM devices.

---

## 2. Project Structure
```text
maxter_player_android/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        └── java/com/maxter/player/
            ├── MaxterApplication.kt
            ├── MainActivity.kt
            ├── data/
            │   ├── local/MaxterDatabase.kt
            │   ├── model/VideoModels.kt
            │   └── repository/MediaScannerRepository.kt
            ├── player/
            │   ├── MaxterPlayerController.kt
            │   ├── PlaybackService.kt
            │   └── SmartEnhanceGlShader.kt
            └── ui/
                ├── MaxterAppRoot.kt
                └── theme/Theme.kt
```

---

## 3. Step-by-Step Build & APK Generation Instructions

### Option A: Build via Android Studio (Recommended)
1. Extract `maxter_player_android_source.zip` to a folder on your computer.
2. Open **Android Studio Ladybug (2024.2.1)** or newer.
3. Click **Open** and select the extracted `maxter_player_android` directory.
4. Wait for Gradle Sync to download Android SDK 34 and Media3 dependencies.
5. To test on your phone (e.g., Infinix GT 20 Pro or Infinix Note 50x):
   - Enable **Developer Options → USB Debugging** on your phone.
   - Connect via USB and click **Run ('app')** (`Shift + F10`).
6. To generate a standalone installable APK:
   - Go to **Build → Build Bundle(s) / APK(s) → Build APK(s)** for a debug APK, or **Build → Generate Signed Bundle / APK...** for a release APK.
   - Output path: `app/build/outputs/apk/debug/app-debug.apk` or `app/build/outputs/apk/release/app-release.apk`.

### Option B: Build via Command Line / GitHub Actions (Zero Local RAM Overhead)
If you are working on a lightweight laptop (such as an Intel Celeron N4020C with 4GB RAM where Android Studio is heavy), you can build the APK in the cloud for free using **GitHub Actions** or via CLI:

```bash
# 1. Using Gradle CLI locally (requires JDK 17 and Android SDK Platform 34):
chmod +x gradlew
./gradlew assembleDebug

# 2. Generating a signed Release APK via CLI:
keytool -genkey -v -keystore maxter-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias maxter
./gradlew assembleRelease
```

To build on **GitHub Actions** without using your laptop's RAM:
1. Push this project folder to a new GitHub repository.
2. Create `.github/workflows/android.yml` with `actions/setup-java@v4` (Java 17) and `gradle/actions/setup-gradle@v3` running `gradle assembleDebug`.
3. Download the compiled `app-debug.apk` directly from the GitHub Actions **Artifacts** tab onto your phone!
