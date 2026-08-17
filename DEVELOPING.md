# Developing SideRetro

## Prerequisites

- **JDK 21.** The project uses Gradle 9.4.1 / Android Gradle Plugin 9.2.1. On this
  development machine the required setting is:

  ```bash
  export JAVA_HOME=/usr/local/opt/openjdk@21
  ```

- Android SDK API 36. Put its path in `app/local.properties` (this file is ignored):

  ```properties
  sdk.dir=/Users/you/Library/Android/sdk
  ```

- `adb` and a Sidephone SP-01 for device qualification.

## Toolchain

| | |
|---|---|
| Android Gradle Plugin | 9.2.1 |
| Gradle | 9.4.1 |
| JDK / Java target | 21 / 17 |
| compileSdk / targetSdk | 36 |
| minSdk | 31 (Android 12) |
| Application ID | `fi.palonkorpi.sideretro` |
| Supported release ABI | `arm64-v8a` |

## Build and test

Run these from `app/`:

```bash
export JAVA_HOME=/usr/local/opt/openjdk@21
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

Until `keystore.properties` exists, `assembleRelease` is intentionally signed with
the Android debug key. That makes it useful for testing the release variant, but it
is not an artifact that may be published or installed over the final public build.

Install and start a debug build:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p fi.palonkorpi.sideretro 1
```

`./play.sh` builds, installs, launches, and tails logs. Pass `--seed` to include its
local seed step. It also requires the JDK 21 setting above.

## Tests

The unit suite covers the pure behaviour most likely to regress: game-system
classification, scaling arithmetic, Keytile inference, and physical-key-to-RetroPad
mapping. It needs neither a device nor ROM files. Changes to any of those areas should
keep `:app:testDebugUnitTest` green before device testing.

## Native cores

The app packages these prebuilt `arm64-v8a` libretro cores:

- mGBA — Game Boy, Game Boy Color, Game Boy Advance
- FCEUmm — NES
- ClownMDEmu — Mega Drive / Genesis

They are located in `app/app/src/main/jniLibs/arm64-v8a/`; the exact bundled bytes are
listed in [`CORE_SHA256SUMS`](CORE_SHA256SUMS). Do not replace a core binary without
updating that manifest and its source-provenance record. See `THIRD_PARTY_NOTICES.md`
and the release gate in [`RELEASE.md`](RELEASE.md).

## Release signing

Copy `app/keystore.properties.example` to `app/keystore.properties`, create the key
once, and replace its placeholders. The properties file and keystores are ignored by
Git. Keep the release key backed up securely: Android requires every update to retain
the signing lineage of the first public build. The complete release procedure is in
[`RELEASE.md`](RELEASE.md).
