# Blockers and scope

## Cleared in this LLM Provider run

1. **Real chat Provider** — CLEARED on API28 and API36.
2. **Messenger group orchestration** — CLEARED through the production `MessengerGroupOrchestrator` with two attributed replies.
3. **Binder structured generation** — CLEARED through the production `BinderOrchestrator`; at least two candidates are now mandatory and verified with both real Provider and deterministic one-candidate rejection tests.
4. **Ustagram, Rebbit, and Y LLM generation** — CLEARED with persisted Provider/model provenance on API28 and API36.
5. **Blank or ellipsis social output** — CLEARED: rejected without post creation, provenance, or budget consumption.

## Outside this `.env` LLM-only acceptance scope

1. **Hugging Face models** — no HF credential/model set was provided by `.env` for this run.
2. **Real Forge / Local Dream image backend** — no live image service was provided.
3. **Aura MNN happy path** — the verified model files were not staged.

These external non-LLM conditions account for the four honest instrumentation skips per device (three Aura/Fancy-runtime tests and one Local Dream host-mock success test). They are not reported as PASS.

## Current conclusion

The requested `.env` Provider AI scope is complete: API28 and API36 each record 73 total tests, 69 PASS, 4 excluded conditional SKIP, 0 FAIL, including both real Provider tests with 0 skips. Broader Issue #3 closure still depends on whether the separately excluded HF/image/Aura scope is required by that issue's final policy.
