LibretroDroid 0.14.0, commit 8835c3098514390a271e36983957f7bb5f40abf1.
Source: https://github.com/Swordfish90/LibretroDroid/tree/0.14.0
Oboe: b15f5e39c01a7ada306d959e5129620b145fb8b4
libretro-common: b0c348ea5543c4d7fb0bc479258aa6988b20c0c9
Both pinned submodules and their licenses are included.
Changes: default shader chain/pass linear flags changed to false (GL_NEAREST).
Module build moved to AGP 9 built-in Kotlin; API 36, arm64, NDK 27.2.12479018.
CRT and other shader definitions are unchanged. Integer sizing for all five systems is in the app.
Added explicit <functional> includes to rumble.h and utils/javautils.h for NDK 27.
Frame size is read from native video callbacks on the emulation thread and exposed
as a volatile immutable value to the UI. Sharp follows changes in actual game size.
Added a square-pixel mode so core aspect corrections cannot letterbox Sharp output
(e.g. Genesis H32 or interlace). Large/Fill retain the core aspect behavior.
