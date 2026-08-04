# Final acceptance test summary

- Date: 2026-08-04
- Verified implementation HEAD: `31f736dce993c4b196499c6c83aa759eb2ba4d31` (evidence-only commit follows)
- Current APK: `app/build/outputs/apk/debug/app-debug.apk`
- Current APK SHA-256 at final verification: `d0554e0058f5e327940e76fed3fd24fc4f32c63cd7b9cb87d3c60b2fd4bfcd81`
- Reference APK: `v4.47-github-release.apk`
- Reference APK SHA-256: `fbc341267b1e09a72ca975d00316ba9827b7731f752eabab35b54d690e8ee097`
- Devices: API28 `emulator-5554`; API36 `emulator-5556` / mobile-mcp `ai_livesaver_api36`
- Packages: current `io.github.lzyuuu.ailivesaver`; reference `com.mrj.fancyai.github`

## Gradle gates

Using Android Studio JBR `/Applications/Android Studio.app/Contents/jbr/Contents/Home`:

- `testDebugUnitTest`: PASS
- `lintDebug`: PASS
- `assembleDebug`: PASS
- `assembleDebugAndroidTest`: PASS

Durable log: private scratch `gradle-gates.log`.

## Complete instrumentation

Command: `./gradlew connectedDebugAndroidTest --no-daemon`

- API28: finished 70 tests, 0 failed, 4 conditional skips.
- API36: finished 70 tests, 0 failed, 4 conditional skips.
- Conditional skips are honest environment gates: Aura verified model/runtime tests and Local Dream live host success test. Their local protocol/failure behavior remains covered by other shipped tests.
- The complete rerun includes all in-repo instrumentation, not only selected core classes.

Durable evidence: `../evidence/instrumentation-api28.xml`, `../evidence/instrumentation-api36.xml`; full console log is in private scratch `full-instrumentation-after-fix.log`.

## Mobile-MCP

- mobile-mcp discovered both online devices.
- Final and reference APKs were installed successfully on both devices.
- Both packages were launched; paired screenshots are under `../evidence/`.
- API36 crash list is empty. API28 lists historical reports, but no new final-run app crash was observed.

## Overall

All local/product build and API28/API36 instrumentation gates pass. External Provider/HF/image backend/Aura model success paths remain genuinely BLOCKED, so Issue #3 stays OPEN under the approved gate.
