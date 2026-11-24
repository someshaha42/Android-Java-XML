# FrameCut — Android Studio Project (Java + XML)

This `/android` directory is a standalone, standard **Android Studio** project written in **Java 11 + Android XML Views + Groovy Gradle** (no Kotlin, no Jetpack Compose).

## Folder Structure

```text
android/
├── build.gradle
├── settings.gradle
├── gradle.properties
├── gradlew / gradlew.bat
├── local.properties.example
├── gradle/
│   └── wrapper/
│       └── gradle-wrapper.properties      # Gradle 8.11.1
└── app/
    ├── build.gradle                       # AGP 8.7.3, compileSdk 35, minSdk 26, Java 11, ViewBinding
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/aistudio/framecut/
        │   │   ├── audio/                 # AudioCaptureManager (Mic + API 29+ internal playback AAC)
        │   │   ├── capture/               # ScreenCaptureManager (MediaProjection + VirtualDisplay)
        │   │   ├── crop/                  # Photo & GPU Video crop engines + MediaCropInspector
        │   │   ├── encoder/               # VideoEncoder (HEVC/H.264) + GlRegionCropper (EGL14/OES)
        │   │   ├── model/                 # Enums, NormalizedRegion, PixelRegion, SettingsStore
        │   │   ├── muxer/                 # MediaMuxerManager + MediaStore export
        │   │   ├── recordings/            # RecordingsRepository (MediaStore query/delete)
        │   │   ├── region/                # RegionSelectionManager, RegionBorderView, RegionOverlayManager
        │   │   ├── service/               # RecordingService + RecordingSessionBus
        │   │   └── ui/                    # MainActivity, SettingsActivity, RecordingsActivity, CropToolActivity, Views
        │   └── res/
        │       ├── drawable/              # Adaptive launcher icon + vector icons + shape backgrounds
        │       ├── font/                  # space_grotesk.ttf, plus_jakarta_sans.ttf, jetbrains_mono.ttf
        │       ├── layout/                # All XML layouts
        │       ├── mipmap-anydpi-v26/     # Adaptive ic_launcher & ic_launcher_round
        │       ├── values/                # colors, dimens, strings, themes
        │       ├── values-night/          # dark palette colors
        │       └── xml/                   # file_paths.xml
        ├── test/java/com/aistudio/framecut/
        │   └── RegionAndCropMapperTest.java
        └── androidTest/java/com/aistudio/framecut/
            └── AppPackageInstrumentedTest.java
```

## Importing into Android Studio

1. Open **Android Studio** (Ladybug or newer recommended, JDK 17).
2. Choose **File -> Open…** and select the **`android/`** directory inside this repository.
3. Wait for Gradle Sync (Gradle 8.11.1 + AGP 8.7.3).
4. Select the `app` run configuration and click **Run** or **Build -> Build Bundle(s) / APK(s) -> Build APK(s)**.

## Automated GitHub Actions Debug APK Build

The workflow at `/.github/workflows/build-apk.yml` automatically runs on every push, pull request, or manual `workflow_dispatch`:
1. Provisions **JDK 17**, **Android SDK**, and **Gradle 8.11.1**.
2. Executes `gradle testDebugUnitTest` in `android/`.
3. Executes `gradle assembleDebug` in `android/`.
4. Uploads **`FrameCut-debug-apk`** (`FrameCut-debug.apk`) as a downloadable workflow artifact in the repository's **Actions** tab.
