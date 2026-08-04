# Final AI acceptance test summary

- Date: 2026-08-04
- Working tree: current uncommitted finishing changes (see `git status --short`)
- Current APK: `app/build/outputs/apk/debug/app-debug.apk`
- Current APK SHA-256: `89e217208fa1792a4c65b474966a125480d9edcefd815d8fe5c2745e060e4c71`
- Reference APK SHA-256: `fbc341267b1e09a72ca975d00316ba9827b7731f752eabab35b54d690e8ee097`
- Devices: API28 `emulator-5554`; API36 `emulator-5556` / mobile-mcp `ai_livesaver_api36`
- Provider: `.env` LLM configuration, safely staged through stdin into an app-private file; no credential value is stored in this report or evidence.

## Binder multiple-candidate correction

Production `BinderOrchestrator.generate` now:

1. asks for 2–6 distinct candidates;
2. validates the typed candidate list;
3. rejects any response with fewer than two candidates before advancing the draft;
4. persists step 5 only after the multiple-candidate boundary passes.

`RealProviderInstrumentationTest.verifyBinder` calls this production orchestrator and asserts `generation.candidates.size >= 2` before confirmation.

Deterministic shipped-path coverage:

- `shippedGenerationRejectsSingleCandidateWithoutAdvancingDraft`
  - Provider returns exactly one typed candidate;
  - expected result: failure;
  - draft remains absent and cannot be confirmed.
- `shippedGenerationRetriesMalformedEnvelopeAndPersistsTwoCandidates`
  - first HTTP 200 response violates the strict outer envelope;
  - second response contains two valid candidates;
  - expected result: exactly two requests, two candidates, persisted step 5.
- `shippedGenerationRetriesTruncatedCandidateBodyAndPersistsTwoCandidates`
  - first response has a valid outer envelope but truncated candidate JSON in `body`;
  - production Binder retries that candidate-schema failure once;
  - second response contains two valid candidates and persists step 5.

All three tests pass on API28 and API36. Evidence: `../evidence/binder-validation-api28.log`, `../evidence/binder-validation-api36.log`.

Real `.env` Provider Binder + social path passes on both devices and includes the production `>= 2` assertion. Evidence: `../evidence/provider-binder-api28.log`, `../evidence/provider-binder-api36.log`.

## Gradle gates

Using Android Studio JBR `/Applications/Android Studio.app/Contents/jbr/Contents/Home`, the current worktree was forced through one post-change command with `--rerun-tasks`:

- `testDebugUnitTest`: PASS
- `lintDebug`: PASS
- `assembleDebug`: PASS
- `assembleDebugAndroidTest`: PASS
- `connectedDebugAndroidTest`: PASS on API28 and API36
- Gradle result: `BUILD SUCCESSFUL`, 88/88 actionable tasks executed

The captured log records the current source mtimes and SHA-256 values before running, including `ProviderConfig.kt`, `ProviderProtocolTest.kt`, `BinderOrchestrator.kt`, its validation test, and `MessengerRetryHttpSmokeTest.kt`. Evidence: `../evidence/provider-final-gates-current.log`.

## Authoritative staged instrumentation

Each device was reset to a clean app sandbox, installed with the same final APK/test APK, given one app-private `.env` Provider staging payload, and ran the complete AndroidJUnitRunner suite.

| Device | Total | PASS | SKIP | FAIL | Real Provider PASS/SKIP/FAIL |
|---|---:|---:|---:|---:|---:|
| API28 | 73 | 69 | 4 | 0 | 2 / 0 / 0 |
| API36 | 73 | 69 | 4 | 0 | 2 / 0 / 0 |

The four skips on each device are three Aura/Fancy-runtime tests and one external Local Dream host-mock success test. They are outside this LLM-only `.env` acceptance scope. Provider, Messenger, Binder, Ustagram, Rebbit, Y, blank-output, and Binder validation tests are not skipped.

Authoritative raw logs:

- `../evidence/instrumentation-api28.log`
- `../evidence/instrumentation-api36.log`

JUnit XML generated directly from those status events:

- `../evidence/instrumentation-api28.xml`
- `../evidence/instrumentation-api36.xml`

Automated consistency assertions passed on both devices:

- raw log starts = `73`;
- completion code `0` = `69`;
- assumption code `-4` = `4`;
- failure code `-2` = `0`;
- XML totals = `tests=73`, `skipped=4`, `failures=0`, `errors=0`;
- both named `RealProviderInstrumentationTest` methods exist in XML and are not skipped;
- raw log contains `OK (73 tests)` and no `FAILURES!!!`.

## Real Provider functions exercised

The staged real test class drives shipped production paths for:

- Provider capability qualification and persisted task configuration;
- Messenger stream cancellation and same-ID retry;
- production `MessengerGroupOrchestrator` with two member-attributed replies;
- production `BinderOrchestrator` with at least two real candidates and idempotent confirmation;
- Ustagram real post and provenance;
- Rebbit real post and provenance;
- Y real post and provenance.

No mock/template output is counted as real Provider success.

## Mobile-MCP

API28 and API36 each completed two explicit terminate→launch cycles. Saved screenshots cover:

- Provider
- Messenger
- Binder
- Ustagram
- Rebbit
- Y

All screenshots are under `../evidence/mobile-api*.png`.

## Credential audit

- Exact `.env` API-key byte scan over shipping source, final logs, XML, reports, and screenshots: `0` hits.
- Provider staging uses stdin; key is not placed in argv.
- Staged app-private JSON is deleted after first read and removed again by the host runner.
- `.env` remains untracked.
