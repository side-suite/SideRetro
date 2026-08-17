# Releasing SideRetro

This is the release procedure for public SideSuite / App Pack builds. Do not publish a
debug-key-signed artifact.

## Release gates

Before signing, confirm all of these are true:

1. The complete physical-tile test pass has been performed on the SP-01.
2. The Lit frame and CRT filter defaults have been approved on-device. The release menu exposes
   only Off and CRT; legacy Crisp, Sharp and LCD preferences migrate to CRT.
3. The accepted Classic/T9 limitation has been checked: they are keycode-indistinguishable, so
   Classic intentionally uses the T9 legend and its extra d-pad, Enter and soft-key controls are
   not shown there.
4. The public source tree contains the finished tile art, launcher icon, and wordmark.
5. `CORE_SHA256SUMS` matches the bundled core binaries.
6. The exact sources locked in `THIRD_PARTY_SOURCES.md` have been archived immutably,
   including every recursive ClownMDEmu submodule and LibretroDroid 0.14.0, alongside
   complete licence texts and the available build information. The v1 archive is assembled
   locally; publishing it with the GitHub Release remains open.
7. `THIRD_PARTY_NOTICES.md` is complete and reviewed.
8. A fresh public source repository has an intentional commit and signed/tagged source
   release matching the APK source.

## Create and protect the signing key

From `app/`, copy the example and make a release key once:

```bash
cp keystore.properties.example keystore.properties
keytool -genkeypair -v -keystore sideretro-release.jks -alias sideretro -keyalg RSA -keysize 4096 -validity 10000
```

Fill in `keystore.properties`. Store the keystore and its passwords outside the source
tree in at least one recoverable secure backup. Losing it prevents future updates to
the package ID. Never commit either file.

Record the public certificate fingerprint after the first signed build:

```bash
keytool -list -v -keystore sideretro-release.jks -alias sideretro
```

## Build and verify

From `app/` with the release properties in place:

```bash
export JAVA_HOME=/usr/local/opt/openjdk@21
./gradlew clean :app:testDebugUnitTest :app:assembleRelease
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
aapt dump badging app/build/outputs/apk/release/app-release.apk
```

Verify the certificate is the backed-up release certificate (not Android Debug), that
the package is `fi.palonkorpi.sideretro`, version code is `1`, version name is `1.0`,
and only `arm64-v8a` native libraries are present. Recheck bundled-core bytes:

The v1 release certificate SHA-256 fingerprint is
`140bfb3a7cbd71b6f54f301f5fa3309e0663d1fdd79a9fe1760d6d925a4d13d5`
(`CN=Oliver Palonkorpi`, RSA 4096). Future updates must preserve this signing identity.

```bash
cd ..
shasum -a 256 -c CORE_SHA256SUMS
```

Install the final APK on the SP-01 and run the device release checklist. Test updating
from the previous signed public version before publishing the APK.

## Publish

1. Tag the exact source commit (for example `v1.0.0`) and publish it with the release.
2. Create a GitHub Release from that tag and attach the signed
   `app-release.apk` without renaming it.
3. Publish the matching SideRetro source and the immutable third-party source archive
   specified by `THIRD_PARTY_SOURCES.md` for every copyleft component.
4. Run the established release workflow manually in `side-suite/fdroid-repo` after the
   GitHub Release is public.
5. After the App Pack rebuilds, verify the signed index lists SideRetro and install it
   through the SP-01 Library. Confirm its next signed update is accepted.
