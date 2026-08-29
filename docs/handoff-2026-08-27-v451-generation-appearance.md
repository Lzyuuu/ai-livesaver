# Handoff: V4.51 生成/指令页与外貌分类（#65–#68）

Date: 2026-08-27
Next session: continue after #66/#67/#68 landed locally; do not re-grill or re-implement those tickets unless a new bug is opened first.

## Goal

Product work for aligning generation/instruction UI and appearance classifications with reference APP `com.mrj.fancyai.github` V4.51 is **implemented locally** (not pushed). Next agent should pick leftover acceptance (#66 dual-app walk), optional glossary nits, or a new ticket — not redo #51–#54 / #64 / #67 / #68.

## Repo

- Path: `/Users/laizhaoyu/Documents/知识工作区/30-个人/项目/fancy-ai-apk`
- Package: `io.github.lzyuuu.ailivesaver`
- Branch: `prototype/v450-shell`
- HEAD: `d61db2248e9cf20195fda649a4325c26836ee7ad`
- Tracker: `Lzyuuu/ai-livesaver` (`gh` with `--repo Lzyuuu/ai-livesaver`; local dir may have no git remote)

## Constraints (still bind)

- Do **not** `git push` unless the user asks.
- Do **not** edit `.env`.
- Do **not** commit unrelated dirty tree: `SocialScreens.kt` deletion, `SocialWidgets.kt`, `screenshot/`, `.scratch/` deletions, `research/`, drawable deletions, `.gitignore`, `.agents/`, `.claude/`, `.cursor/`, `walkthrough/`, `docs/releases/…`.
- New bugs: open an issue first, then change code.
- #51 #52 #53 #54 #64 CLOSED — do not redo.
- Empty-bubble: not a bug (Q3 A). Do not open one.
- Dual-app acceptance: ADR-0062.
- Cloud payload stays OpenAI subset: ADR-0013 / ADR-0014 / ADR-0064.

## Artifacts (do not recopy bodies)

### Spec / tickets

- Spec: https://github.com/Lzyuuu/ai-livesaver/issues/65 (`ready-for-agent`, parent #50)
- Tickets:
  - https://github.com/Lzyuuu/ai-livesaver/issues/66 — Instruction library + Instruction page — **OPEN** (implementation shipped; dual-app of save-as / delete / restore / next-reply not walked)
  - https://github.com/Lzyuuu/ai-livesaver/issues/67 — Appearance classifications — **CLOSED**
  - https://github.com/Lzyuuu/ai-livesaver/issues/68 — Generation expert sampling / presets / reset — **CLOSED** (blocked-by #66 was native GitHub dependency)
- Legacy question-shaped OPEN issues, **not** agent tickets: #62, #63. Do not `/triage` them. Do not implement from those bodies.
- Backlog: #56 on-device llama.cpp/LiteRT (where expert sampling becomes real).

### Docs / ADRs

- Glossary: `CONTEXT.md` — 视觉身份, 外观补充, 生成参数, 专家采样, 指令模板, 推理配置, 角色卡, 增强能力, 对照验收
- ADR-0063: `docs/adr/0063-inherit-generation-parameters-from-global-defaults.md`
- ADR-0064: `docs/adr/0064-follow-v451-generation-ui-keep-openai-payload.md`
- ADR-0065: `docs/adr/0065-visual-identity-as-composed-classifications.md`
- Tracker conventions: `docs/agents/issue-tracker.md`

### Commits on this branch (unpushed)

| SHA | Message |
|---|---|
| `5e826e4` | feat: 指令模板库与指令页，拆分设置入口 (#66) |
| `17759cd` | feat: 外貌 Tab 八组视觉身份分类与外观补充 (#67) |
| `26cdc07` | feat: 生成页专家采样、预设对齐与重置 (#68) |
| `d61db22` | docs: 记录 V4.51 生成/指令页与外貌分类决策 (#65) |

Diff vs pre-work: `git diff 5efa0e8...HEAD`

## What happened this conversation

1. `/to-spec` published #65. `/to-tickets` (user approved 3 slices) published #66/#67/#68; #68 blocked-by #66; all three are sub-issues of #65. Parent #65 body was not rewritten.
2. User asked `/skill:herdr` to start sequential omp panels running `/skill:implement`, then parent review. Panes: `impl-66` (`wC:p15`) → `impl-67` (`wC:p16`) → `impl-68` (`wC:p17`). Those panes were later closed; current tab is only `wC:p13`.
3. User asked to commit remaining docs. Only `CONTEXT.md` + ADR-0064/0065 went in as `d61db22`. Feature code was already in the three feat commits.
4. User asked to close finished sub-panels; done.

## Implementation map (pointers only)

- Generation / instruction library / expert sampling / payload: `app/src/main/java/io/github/lzyuuu/ailivesaver/ProviderConfig.kt`
- Settings IA: `SettingsDestination.INSTRUCTION` + `GENERATION` in `SettingsNavigation.kt`; UI `InstructionSettingsScreen` / `GenerationSettingsScreen` in `SystemSettingsScreens.kt`; routing in `MainActivity.kt`
- Appearance: `CharacterCardV2.kt` (`VisualIdentity.composeFixedFeature` order: style, gender, age, ethnicity, skin, eyes, hair, body, 外观补充); `CharactersScreens.kt`
- Tests (agreed seams, do not invent a third): `GenerationSettingsTest.kt`, `CharacterAppearanceCardTest.kt`, `CharacterCardV2Test.kt`, `EndToEndFlowTest.kt`, `SettingsNavigationTest.kt`

Seam XML evidence at review time (JUnit, 0 failures): GenerationSettings 28, CharacterAppearanceCard 10, CharacterCardV2 11, EndToEnd 2, SettingsNavigation 5.

Factory instruction bodies and Precise/Creative numbers were copied from reference V4.51 (jadx + on-device), not from old English one-liners. Balanced factory is 0.80 / 1024. Reset label has no llama.cpp.

## Review (parent `/code-review` on `5efa0e8...HEAD`)

### Standards

No blocking violations. Glossary nits in `strings.xml`:

- `character_fill_from_description_llm` still says 人设; button is 从描述填充
- `generation_repetition_penalty` says 重复惩罚; `generation_preset_info` / CONTEXT say 重复防护

Judgement smells (do not “fix” unless asked): classification field copies across types; `persistOwned` name; thin `CharacterCardV2` wrappers around `VisualIdentity`.

### Spec

Requirements of #65/#66/#67/#68 are present except ticket wording vs #53 contract:

- Tickets say prepend / 系统提示词**头部**
- Code: `applyInstructionTemplate` is `"$system\n\n${body}"` (suffix). Chat `chatRequest` uses a standalone system message with the library body. Tests `applyInstructionTemplateAppendsPromptOnce` and `selectedLibraryBodyIsInjectedAsSystemHeader` lock the suffix.
- Do **not** flip to prepend in-place. If product wants true prepend, open a new issue first.

#66 left OPEN on purpose: save-as-new / delete / restore-factory and live next-reply after switching templates were unit-tested, not dual-app walked.

## Suggested skills

Invoke in this order unless the user names a different task:

1. **`/ask-matt`** — if the next request is ambiguous (close #66 vs glossary nits vs prepend ticket vs push).
2. **`/grill-with-docs`** — only if a new product fork appears (true prepend; sending expert knobs; Top-K; closing #62/#63).
3. **Do not** `/implement` on #66 unless finishing dual-app and closing; **do not** `/implement` #67/#68 again.
4. **`/tdd`** at the existing generation-settings or visual-identity seam if a new bug is ticketed.
5. **`/code-review`** after any code change, fixed point = current HEAD before the change.
6. **`/diagnosing-bugs`** — empty-bubble or hard-to-repro UI only; not grilling.
7. **`/herdr`** — only if the user asks to spawn another omp panel.

Do **not** run `/to-spec` / `/to-tickets` / `/triage` on #62/#63/#65 unless the user reopens product design.

## Next moves (pick with user)

1. Dual-app walk remaining #66 paths on emulator `ai_livesaver_api36` vs `com.mrj.fancyai.github`, then close #66 or file gaps.
2. Optional small ticket: glossary strings (人设 / 重复惩罚).
3. Optional new ticket: instruction prepend vs current suffix — only if product wants to change #53.
4. Push `prototype/v450-shell` only if asked.
5. Do not start #56.

## Evidence dirs

- `walkthrough/2026-08-25-replica-audit/screenshots/ref/` (`ref-20`, `ref-23*`, `ref-24*`, `ref-43*`)
- `walkthrough/2026-08-25-ticket-acceptance/`
- Reference package: `com.mrj.fancyai.github` V4.51
