# Third-party notices

## Iconoir

The small subset of menu icons in `app/app/src/main/res/drawable/ic_retro_*.xml` is derived from
[Iconoir](https://github.com/iconoir-icons/iconoir), Copyright (c) 2021 Luca Burgio, under the MIT
License.

> MIT License
>
> Copyright (c) 2021 Luca Burgio
>
> Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
> associated documentation files (the "Software"), to deal in the Software without restriction,
> including without limitation the rights to use, copy, modify, merge, publish, distribute,
> sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
> furnished to do so, subject to the following conditions:
>
> The above copyright notice and this permission notice shall be included in all copies or
> substantial portions of the Software.
>
> THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
> BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
> NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
> DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
> OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

## LibretroDroid 0.14.0

SideRetro depends on [LibretroDroid](https://github.com/Swordfish90/LibretroDroid)
(`com.github.Swordfish90:LibretroDroid:0.14.0`), the Kotlin/JNI libretro host wrapper.
It is licensed GPL-3.0-or-later. Its source and licence are available from the
[upstream repository](https://github.com/Swordfish90/LibretroDroid) and
[upstream licence file](https://github.com/Swordfish90/LibretroDroid/blob/master/LICENSE).

## Bundled libretro cores

SideRetro packages three `arm64-v8a` native core binaries. Their exact file hashes
and paths are in [`CORE_SHA256SUMS`](CORE_SHA256SUMS). Embedded version strings resolve
to the exact upstream revisions recorded in [`THIRD_PARTY_SOURCES.md`](THIRD_PARTY_SOURCES.md):

| Bundled file | Intended upstream project | Licence |
|---|---|---|
| `libmgba_libretro_android.so` | [libretro/mgba](https://github.com/libretro/mgba) | MPL-2.0 |
| `libfceumm_libretro_android.so` | [FCEUmm](https://github.com/libretro/libretro-fceumm) | GPL-2.0-or-later |
| `libclownmdemu_libretro_android.so` | [ClownMDEmu libretro](https://github.com/Clownacy/clownmdemu-libretro) | AGPL-3.0 |

**Public-release requirement:** publish immutable copies of those exact sources,
including recursive submodules, and include their complete licence texts and available
build information with the release. The old rolling buildbot output is not expected to
be bit-for-bit reproducible from the stripped binaries alone.
