# Final acceptance test summary

- Date: 2026-08-04
- HEAD: `17ce24d69aba9d4086d4476756d597f5462d78e4`
- Final APK: `app/build/outputs/apk/debug/app-debug.apk` was not present/locatable at acceptance time; SHA-256: **BLOCKED**. Rebuild was attempted but local Java runtime is unavailable.
- Devices: `emulator-5554` Android API 28 / Android 9; `emulator-5556` Android API 36. Both were connected.
- Installed current package observed: `io.github.lzyuuu.ailivesaver`; reference package: `com.mrj.fancyai.github` (from prior report).
- Seed: prior acceptance used `ProviderEnvSeedTest#seedDesktopShellForLiveVerification`; no destructive clear-data was performed in this run. Current app state is therefore **seeded prior state**, not a fresh unseeded install.
- UI tooling: mobile-mcp schema was not invoked in this worker; evidence uses existing precise scripts, UI dumps and screenshots. This is explicitly a tooling limitation.
- Stability: API28/API36 logcat excerpts contain no observed `FATAL EXCEPTION` or `ANR in` matching the app during the captured window: PASS (limited observation).
- External probes were unauthenticated and did not transmit secrets. HF returned HTTP 200; Forge probe returned HTTP 200; OpenRouter result was unavailable; LocalDream/Aura runtime/model execution was not proven. Provider availability: BLOCKED.

## Evidence
See `../evidence/` and the retained prior evidence under `../2026-08-04/`.

## Overall
The requested final dual-app, dual-API rerun is not fully reproducible because the reference APK and final APK were not available as files in the workspace and Java is absent. Results are conservatively marked per-slice in the matrix; no UI difficulty is reported as PASS.
