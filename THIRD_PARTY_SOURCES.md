# Bundled core source lock

SideRetro v1 preserves the three native cores already qualified on the SP-01. Each
stripped binary contains a version string that resolves to an exact upstream Git
revision. `CORE_SHA256SUMS` is the binary identity; this file is the matching source lock.

The APK also embeds LibretroDroid 0.14.0. Its release tag resolves to
[`8835c3098514390a271e36983957f7bb5f40abf1`](https://github.com/Swordfish90/LibretroDroid/commit/8835c3098514390a271e36983957f7bb5f40abf1),
and its complete GPL-3.0-or-later source is included in SideRetro's corresponding-source archive.

| Core | Embedded identity | Exact upstream revision | ELF build ID |
|---|---|---|---|
| mGBA | `0.11-219-e31759b` | [`e31759b24e7a4e3899285ff720d7b573ac328ae7`](https://github.com/libretro/mgba/commit/e31759b24e7a4e3899285ff720d7b573ac328ae7) | `a744a8aa49a45015cb7dc6b5198f5c0030089786` |
| FCEUmm | `(SVN) b5e3566` | [`b5e3566515c27dc66c9c20572171673126532e06`](https://github.com/libretro/libretro-fceumm/commit/b5e3566515c27dc66c9c20572171673126532e06) | `602b0f73bd303b3d231eb16317c8bebdb2f6a912` |
| ClownMDEmu | `v1.6.11 935d6fc` | [`935d6fc060eb82172dac29e880fe9b877fbdb640`](https://github.com/Clownacy/clownmdemu-libretro/commit/935d6fc060eb82172dac29e880fe9b877fbdb640) | `08c954c1b4fb8d9ca673971bc3baaf7b71bda951` |

ClownMDEmu must be archived recursively with its locked gitlinks:

- `common`: `586878bca67c956b4130d5c69fc7d926b8b87744`
- `libretro-common`: `e9a4ccc4a6b05f136ef5358218a5a94ebeccb7cb`

## Evidence and limitations

The identities above were read directly with `strings -a` from the checked-in `.so`
files and the short revisions were resolved against their upstream repositories. ELF
build IDs were read from the same files. The revisions and dates align with the recorded
2026-08-09 libretro buildbot fetch.

This establishes the source revisions corresponding to the shipped cores. It cannot
prove that the historical buildbot applied no unrecorded patch, nor recover every
compiler flag from stripped binaries. Preserve the working binaries for v1; do not
replace them merely to claim reproducibility without repeating hardware qualification.

## Public-release source bundle

Before publishing the APK:

1. Create an immutable archive containing LibretroDroid 0.14.0 and all three core repositories
   at the revisions above, with ClownMDEmu's submodules initialized recursively.
2. Include the complete MPL-2.0, GPL-2.0-or-later, and AGPL-3.0 licence texts and notices.
3. Include this lock, `CORE_SHA256SUMS`, the recorded 2026-08-09 fetch date, and all known
   build inputs/instructions in that archive.
4. Publish the archive next to the APK and link it from the GitHub release and App Pack metadata.

Future replacements should be built in a pinned Android NDK container, record the full
toolchain and command lines, and be requalified on the SP-01 before their hashes replace
the v1 binaries.
