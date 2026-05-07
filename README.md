# C128

A Commodore 128 emulator for Android. I wanted a "good enough", very basic emulator that would tickle my nostalgia. This has both 128 and 64 mode (GO 64 / GO 128) and boots into 64 mode if you hold down a volume key while launching. Maybe CP/M mode will be added sometime...

## What works

- 40×25 PETSCII text grid with blinking cursor and authentic boot banner.
- BASIC interpreter: `PRINT`, `LIST`, `NEW`, `RUN`, `END`, `REM`, `HELP`,
  `GOTO`, `IF`/`THEN`, `FOR`/`TO`/`STEP`, `NEXT`, `LET`, `INPUT`, `GOSUB`/
  `RETURN`, `LOAD`, `GO 64` / `GO 128`.
- Functions: `ABS`, `INT`, `RND`, `LEN`, `CHR$`, `ASC`, `LEFT$`, `RIGHT$`,
  `MID$`, `STR$`, `VAL`.
- Full operator set including string `+` concat, comparisons, `AND`/`OR`/`NOT`.
- Numeric (`A`) and string (`A$`) variables; `FOR` loop frames; `GOSUB` stack.
- Authentic error messages (`?SYNTAX ERROR`, `?DIVISION BY ZERO ERROR`,
  `?ILLEGAL QUANTITY ERROR`, `?OUT OF MEMORY ERROR`, …) with line numbers.
- CPU pacing: runs at ~500 statements/sec to feel like a real C128.
- Volume-down acts as RUN/STOP to break out of runaway programs.

## Build

Termux on aarch64 is the supported build host. One-time setup:

    pkg install openjdk-21 gradle aapt2 apksigner d8

Then install the Android SDK cmdline-tools to `~/android-sdk` and via
`sdkmanager` install `platforms;android-34`, `build-tools;34.0.0`,
`platform-tools`.

Termux ships its own aarch64 `aapt2`; AGP's bundled binary is x86_64-only and
will not run. Point AGP at the Termux binary by adding the following line to
`~/.gradle/gradle.properties` (per-user, not committed):

    android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2

On non-Termux build hosts (CI, desktop Linux, macOS), leave this unset and AGP
will use its bundled aapt2.

Build:

    ./gradlew :app:assembleDebug

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

Run the test suite:

    ./gradlew :app:test

## Install

    pm install -r app/build/outputs/apk/debug/app-debug.apk

Or via adb from another machine:

    adb install -r app/build/outputs/apk/debug/app-debug.apk

## Project layout

    app/src/main/kotlin/com/lcdcode/c128/
      MainActivity.kt          Compose entry point, input handling
      basic/                   Tokenizer, Interpreter, Builtins, Errors
      terminal/                TextBuffer (40×25 cell grid)
      ui/                      Canvas screen, palette, glyph atlas

## License

MIT. See `LICENSE`.
