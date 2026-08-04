# Final acceptance test summary

- Date: 2026-08-04
- Current implementation baseline: `17ce24d69aba9d4086d4476756d597f5462d78e4` plus final evidence/report commits
- Current APK: `app/build/outputs/apk/debug/app-debug.apk`
- Current APK SHA-256: `50fe2376b6820787fca97c782a682e8d60efed3dc98c3f6a0b8e5d1e78b208d9`
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

## Instrumentation

- Core non-native suite: 15/15 PASS on API28 and 15/15 PASS on API36. It covers WorldStore contracts/migration, Home/Settings, Messenger + real local HTTP retry, Social isolation, Binder, Phone and Gallery.
- System/Games/Voice suite: 8/8 PASS on API28 and 8/8 PASS on API36.
- Aura native happy path remains conditional and is not called PASS without staged verified models.

Durable logs: private scratch `instrumentation-dual-api.log` and `polish-dual-api.log`.

## Mobile-MCP

- mobile-mcp discovered both online devices.
- Final and reference APKs were successfully installed on both devices.
- Both packages were launched; paired final screenshots are under `../evidence/`.
- API36 crash list is empty. API28 crash list contains historical reports from July 30, including one old app crash, but no new final-run crash entry.

## Overall

Local/product scope gates pass. External Provider/HF/image backend/Aura model success paths are still genuinely BLOCKED, so Issue #3 remains OPEN under the approved final gate.
