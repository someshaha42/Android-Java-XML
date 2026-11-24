# FrameCut — Traditional Native Android (Java + XML)

This directory contains the complete standalone **Java 11 + XML Views + Groovy Gradle** Android implementation of FrameCut (no Kotlin, no Jetpack Compose).

## Architecture Overview

- **Gradle (Groovy DSL)**: `settings.gradle`, `build.gradle`, `app/build.gradle` (`minSdk 26`, `targetSdk 35`, `compileSdk 35`, `viewBinding true`, AppCompat, Material Components, ConstraintLayout, RecyclerView, ExifInterface).
- **UI Activities & Views (`com.aistudio.framecut.ui`)**:
  - `MainActivity`: Recording console, live timer, Full Screen / Region mode, interactive region adjustment, Orientation, Audio Source (Internal / Microphone), Video Bitrate presets + custom input, animated border toggle, theme switcher (`AppCompatDelegate`), and permission/MediaProjection consent.
  - `SettingsActivity`: Video bitrate mode (`VBR` / `CQ`), 8-bit SDR / 10-bit HEVC, FPS presets + custom input, GOP interval presets + custom input, BT.709 color range, audio format + custom kHz/kbps, and live lock banner during active sessions.
  - `RecordingsActivity` & `RecordingsAdapter`: MediaStore query (`FrameCut_%`), async thumbnails, in-app `VideoView` playback dialog, `Intent.ACTION_SEND` sharing, and delete confirmation.
  - `CropToolActivity`: SAF document pickers, `MediaCropInspector` (EXIF + `MediaMetadataRetriever` + `MediaExtractor`), interactive `CropEditorView` (8 drag handles + rule-of-thirds), aspect presets, 1:1 lossless copy vs fallback encoding (`PhotoCropEngine` & GPU `VideoCropEngine`), progress & cancellation.
- **Capture & Encoding Pipeline**:
  - `RecordingService` + `RecordingSessionBus`: Foreground service (`mediaProjection|microphone`), notification Pause/Resume/Stop actions, `RegionOverlayManager`.
  - `ScreenCaptureManager` + `GlRegionCropper`: `MediaProjection` + `VirtualDisplay` + OpenGL ES 2.0 / `EGL14` hardware cropper.
  - `VideoEncoder`: `MediaCodec` H.265/HEVC (with H.264/AVC fallback).
  - `AudioCaptureManager`: `AudioRecord` (`MIC` with external mic priority, or `AudioPlaybackCaptureConfiguration` on API 29+) + AAC `MediaCodec`.
  - `MediaMuxerManager`: Thread-safe MP4 muxing and `MediaStore` publication (`Movies/FrameCut`, `Pictures/FrameCut`).

## Building in Android Studio

Open the `/android` directory in Android Studio (or run `./gradlew assembleDebug testDebugUnitTest` in a JDK 17 + Android SDK environment).
