# Crisp rendering in Filter Off and Sharp

Filter Off previously selected LibretroDroid 0.14.0's Default shader, which still
used GL_LINEAR. That blended neighboring pixels for all five systems, even when
Picture size was Sharp. Sharp also only preserved an aspect ratio; it did not
keep source pixels at whole-number sizes.

The local rendering module now uses GL_NEAREST for the Default shader's input
texture and pass. Sharp uses the largest integer multiple of the actual frame
size that fits. It follows video frame-size changes rather than assuming a fixed
Genesis or NES screen size. In Sharp, the renderer uses the source frame's square
pixel shape, so core aspect corrections do not add a second stretch or letterbox.
Large and Fill retain their sizing behavior; CRT retains its effect and sampling.
If even 1x cannot fit, Sharp falls back to aspect fit.

Typical sizes on a 480x640 game area:

| System | Source size | Sharp picture |
| --- | --- | --- |
| GB/GBC | 160x144 | 480x432 (3x) |
| GBA | 240x160 | 480x320 (2x) |
| NES | 256x240 | 256x240 (1x) |
| Genesis | 320x224 | 320x224 (1x) |

NES and Genesis games may use other frame sizes. Use Large when you prefer a
bigger picture over equal pixel blocks.

## Build

Initialize the pinned source dependencies before the first build:

```sh
git submodule update --init --recursive
cd app
# Use JDK 21 or another JDK supported by Gradle 9.4.1.
# Set sdk.dir in local.properties to your Android SDK path.
sh ./gradlew :app:testDebugUnitTest :app:assembleDebug
```

The renderer pins NDK 27.2.12479018 and CMake 3.22.1. See DEVELOPING.md.

## Checks

- All 48 app unit tests passed, covering all systems, both orientations, odd-sized
  windows, small windows and changed source frame sizes.
- Debug APK built successfully.
- An original GBA test ROM ran in an Android API 37 emulator with a 480x640 display.
  Native frame size was 240x160 and the game surface was 480x320. PixelCopy captured
  only the two source colors; every pixel was an exact 2x2 block. Switching
  Sharp -> Large -> Fill -> Sharp worked.
- The earlier GB/GBC patch was reported working on the user's phone. The expanded
  all-system patch still needs checks on a real phone, especially Genesis modes.
- Full lint still reports 11 errors and 29 warnings in unchanged app code, including
  an API-33-only incoming-share call and restricted dispatchKeyEvent calls.

The bundled emulator core binaries and their hashes are unchanged. This is a
source fix and debug build; it does not use the upstream public-release signing key.
