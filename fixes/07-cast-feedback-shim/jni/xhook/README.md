# xhook/ (vendored at build time)

This folder is populated by CI from **iqiyi/xHook v1.2.0** (MIT; `queue.h` and
`tree.h` are BSD-2-Clause). `.github/workflows/build.yml` clones the pinned tag
and copies `libxhook/jni/*.{c,h}` and its `LICENSE` here before `ndk-build`.

The sources themselves are not committed (only this note); the build fetches
them so the pinned upstream stays the single source of truth.
