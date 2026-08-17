#!/usr/bin/env bash
# Build, install, launch, tail. The everyday loop while working on SideRetro.
#
#   ./play.sh            build + install + launch the library
#   ./play.sh --seed     also copy sideretro/spike/roms into the app's library
#
# JAVA_HOME is set explicitly: this machine's default `java` is 11 and Gradle 9 needs 17+.
set -euo pipefail

cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-/usr/local/opt/openjdk@21}"
export PATH="$PATH:$HOME/Library/Android/sdk/platform-tools"

PKG=fi.palonkorpi.sideretro
APK=app/build/outputs/apk/debug/app-debug.apk

./gradlew :app:assembleDebug --console=plain
adb install -r "$APK"

if [[ "${1:-}" == "--seed" ]]; then
    # The library lives in internal storage, so seeding goes through run-as rather than a push.
    # This is a development shortcut only — the shipped paths are the file picker and the
    # "open a downloaded file with SideRetro" intent filter.
    adb shell run-as "$PKG" mkdir -p files/roms
    for rom in ../spike/roms/*; do
        name="$(basename "$rom")"
        adb push "$rom" "/data/local/tmp/$name" > /dev/null
        adb shell chmod 644 "/data/local/tmp/$name"
        adb shell run-as "$PKG" cp "/data/local/tmp/$name" "files/roms/$name"
        echo "seeded $name"
    done
fi

adb shell am force-stop "$PKG"
adb logcat -c
adb shell am start -n "$PKG/.ui.LibraryActivity" > /dev/null
echo "--- logcat (ctrl-c to stop) ---"
adb logcat -s SideRetro:* AndroidRuntime:E
