# Blockers

## External success paths

1. **Real chat Provider** — local OpenAI-compatible HTTP request/stream/structured/retry behavior passes, but no production endpoint/key is available for a real inference success run.
2. **Hugging Face models** — trusted SHA-256 enforcement and resolver tests pass, but the full model set was not downloaded and validated on-device in this run.
3. **Real image backend** — Forge/Local Dream protocol and UI are implemented, but no live configured service produced an image in this run.
4. **Aura MNN happy path** — picker, app-owned copies, Gallery linkage, failure states and output lineage are implemented; four verified model files are not staged, so native face swap cannot honestly be marked successful.

These are environmental/credential/model blockers, not substituted with mocks or historical screenshots. Per the approved plan, they block Issue #3 closure even though local product slices, build gates and dual-API core tests pass.

## Issue #3

**Keep OPEN.** Close only after all four external paths above are reproduced in the same acceptance run and the independent verifier returns PASS.
