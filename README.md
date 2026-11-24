# FrameCut

**FrameCut** is a native **Android (Java 11 + XML Views + Groovy Gradle)** screen recorder and universal photo/video cropper arranged at the repository root for direct **Android Studio** import and automated **GitHub Actions** debug APK builds.

## Android Studio Project Structure (Root)

```text
.
├── .github/workflows/build-apk.yml   # Automated GitHub Actions Debug APK build
├── build.gradle                      # Android Gradle Plugin 8.7.3
├── settings.gradle                   # Root project & :app module
├── gradle.properties
├── gradlew / gradlew.bat
├── gradle/wrapper/gradle-wrapper.properties  # Gradle 8.11.1
└── app/
    ├── build.gradle                  # compileSdk 35, minSdk 26, Java 11, ViewBinding
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/aistudio/framecut/
        │   │   ├── audio/            # AudioCaptureManager (Mic + API 29+ internal playback AAC)
        │   │   ├── capture/          # ScreenCaptureManager (MediaProjection + VirtualDisplay)
        │   │   ├── crop/             # PhotoCropEngine, VideoCropEngine, MediaCropInspector
        │   │   ├── encoder/          # VideoEncoder (HEVC/H.264) + GlRegionCropper (EGL14/OES)
        │   │   ├── model/            # Enums, NormalizedRegion, PixelRegion, SettingsStore
        │   │   ├── muxer/            # MediaMuxerManager + MediaStore export
        │   │   ├── recordings/       # RecordingsRepository (MediaStore query/delete)
        │   │   ├── region/           # RegionSelectionManager, RegionBorderView, RegionOverlayManager
        │   │   ├── service/          # RecordingService + RecordingSessionBus
        │   │   └── ui/               # MainActivity, SettingsActivity, RecordingsActivity, CropToolActivity, Views
        │   └── res/
        │       ├── drawable/         # Adaptive launcher icon + vector icons + shape drawables
        │       ├── font/             # space_grotesk.ttf, plus_jakarta_sans.ttf, jetbrains_mono.ttf
        │       ├── layout/           # XML layouts for all screens & dialogs
        │       ├── mipmap-anydpi-v26/# Adaptive ic_launcher & ic_launcher_round
        │       ├── values/           # colors, dimens, strings, themes
        │       ├── values-night/     # dark theme colors
        │       └── xml/              # file_paths.xml
        ├── test/java/com/aistudio/framecut/
        │   └── RegionAndCropMapperTest.java
        └── androidTest/java/com/aistudio/framecut/
            └── AppPackageInstrumentedTest.java
```

## Importing into Android Studio

1. Open **Android Studio** (Ladybug or newer with JDK 17).
2. Choose **File → Open…** and select the **repository root directory**.
3. Allow Gradle Sync to complete (`Gradle 8.11.1`, `AGP 8.7.3`).
4. Run on a device/emulator or choose **Build → Build Bundle(s) / APK(s) → Build APK(s)**.

## Automated GitHub Actions Debug APK Build

Every push, pull request, or manual run triggers `/.github/workflows/build-apk.yml`:
1. Sets up **JDK 17**, **Android SDK**, **Gradle 8.11.1**, and a temporary debug keystore.
2. Runs `gradle :app:testDebugUnitTest --no-daemon --stacktrace`.
3. Builds the APK with `gradle :app:assembleDebug --no-daemon --stacktrace`.
4. Uploads **`FrameCut-debug-apk`** (`app/build/outputs/apk/debug/*.apk`) under the **Actions** tab.
