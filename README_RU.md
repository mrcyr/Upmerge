# UpMerge — native Android / libGDX

This branch contains the first native libGDX port scaffold, based on the Alpha 5 working archive.

## Project layout
- `app/` — Android application and libGDX Android backend.
- `core/` — platform-independent Java game and deterministic board engine.
- `core/src/test/` — JUnit regression tests for the native deterministic board engine. The original 4.11 JavaScript suite remains in the reference archive and has not yet been fully mirrored into this repository.

## Current gameplay shell
- 5 columns × 7 rows.
- UP/DOWN lane direction.
- Tap a column to launch a cube.
- Orthogonally connected equal-value cubes merge.
- 460 ms merge anticipation delay.
- Lane compaction after merges.

## Build environment
- Android Gradle Plugin 8.6.1
- Gradle 8.7
- libGDX 1.13.1
- JDK 17
- compileSdk 35 / minSdk 23 / targetSdk 35

Run `./gradlew :app:assembleDebug` (Windows: `gradlew.bat :app:assembleDebug`). The checked-in `gradlew` and `gradlew.bat` bootstrap scripts download Gradle 8.7 on first run. They are lightweight launchers, not the standard Gradle Wrapper JAR.

## Important: build status
This import is source scaffolding, not a verified APK. The previous Alpha 3 crash was caused by missing `libgdx.so`. Alpha 4 then failed on duplicate native library paths. Alpha 6 uses separate ABI destination folders in the native extraction task and includes JUnit engine tests, but neither the extraction task nor the full Android build has yet been verified by a successful CI run. Do not treat this branch as device-tested or release-ready.

Next step: validate native dependency extraction and APK contents for `arm64-v8a/libgdx.so`, then build and launch on a real Android device. Only after that should the remaining 4.11 menus, progression, shop, achievements, saves, tools, and visual effects be ported.
