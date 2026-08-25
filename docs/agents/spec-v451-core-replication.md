# Spec: 4.51 核心功能复刻与可用 APK 交付

## Problem Statement

当前项目（AI LifeSaver / Fancy OS）虽然已完成桌面系统（Fancy OS）、开放商店（Fancy Store）以及基础社交子应用的架构重构，但在实际体验和对齐 4.51 参考版本时，仍存在影响真实可用性与交互深度的核心断点：
1. **Messenger 会话控制与实时记忆交互割裂**：用户在与 AI 角色对话时，无法像 4.51 版本那样通过抽屉便捷管理该角色的多平台发帖授权、自动配图开关、即时查看/修改长期记忆或触发记忆整理，且在生成过程中误触返回缺少防中断确认保护。
2. **角色外貌与视觉身份设定不完整**：角色创建与编辑目前为单层表单，缺少 4.51 风格/性别等预设标签和从描述自动生成外观提示词的能力，导致生图与图文发帖时缺乏统一的视觉一致性。
3. **推理与生成参数缺乏精细化调节**：系统设置中缺少对 LLM 生成温度（Temperature）、最大 Token 数、采样策略预设（精准/均衡/创意）和角色扮演指令模板（Roleplay/Direct 等）的调节入口，无法灵活适配不同模型特质。
4. **缺少端到端闭环并输出真机安装的独立 APK**：各功能分散在代码各层，尚未完成云端真实 Provider 连通下的端到端完整链路联调与验证，用户无法直接获取到功能完整、真正开箱即用的 APK 产物。

## Solution

在保留现有 Fancy OS 系统桌面与 Fancy Store 开放生态架构的前提下（免去桌面与商店的像素级微调），聚焦 4.51 版本的核心能力复刻与端到端可用性交付：
1. **Messenger 会话抽屉与防中断保护**：在单聊会话中新增 4.51 同款底部滑出抽屉，提供“对话控制”（网络检索/自动配图/Y、Ustagram、Rebbit 发帖授权）与“记忆管理”（长期记忆列表/手动添加/立即整理记忆）双 Tab，并引入生成中返回防中断确认弹窗。
2. **Characters 双 Tab 角色与外貌视觉身份编辑器**：将角色创建与编辑页面划分为“角色设定”与“外貌设定”双 Tab，提供艺术风格（写实、胶片、动漫、油画等）、性别等快捷预设标签与负向提示词字段，支持从角色描述智能提取视觉身份，并保持 Character Card V2 PNG/JSON 导入导出的双向兼容。
3. **Settings 生成参数、采样预设与指令模板**：在系统设置 AI & Models 模块中新增生成参数调节项（Temperature 0.0~2.0、Max Output Tokens、Top_P、采样预设快速切换：精准/均衡/创意、指令模板切换：Roleplay/Direct/Straight answers），参数直接注入云端及本地推理请求 Payload。
4. **端到端全链路联调与 Release APK 构建**：完成云端 BYOK（OpenAI/DeepSeek/OpenRouter）真实流式对话、自动配图、记忆整理的全流程联调，通过单元测试与回归验收，最终构建生成稳定可运行的 `app-release.apk`。

## User Stories

1. As a user, I want to swipe up or open a bottom drawer menu in Messenger chat, so that I can conveniently configure character permissions and memories in one place without leaving the conversation.
2. As a user, I want to toggle whether an AI character can autonomously post to Y, Ustagram, and Rebbit from the chat drawer, so that I can control each character's social sphere activity.
3. As a user, I want to enable or disable automatic image generation after each assistant reply from the chat drawer, so that I can control visual accompaniment based on my bandwidth and preferences.
4. As a user, I want to toggle DuckDuckGo web search augmentation for a character from the chat drawer, so that the character can access real-time information when needed.
5. As a user, I want to view all long-term memories associated with the current character in the chat drawer's memory tab, so that I know what the character remembers about me.
6. As a user, I want to manually type and add a new memory item directly inside the chat drawer, so that I can explicitly teach the character important facts without waiting for automated extraction.
7. As a user, I want to trigger an immediate memory consolidation action from the chat drawer, so that recent conversation highlights are condensed into durable long-term memories on demand.
8. As a user, I want to receive an exit confirmation dialog when attempting to leave a chat where a reply is actively streaming, so that I don't accidentally abort or lose an in-progress response.
9. As a user, I want the character creation and editing screen to be organized into "Character" and "Appearance" tabs, so that I can configure personality and visual identity distinctly.
10. As a user, I want to select visual style presets (Photoreal, Film Photo, Anime, Painted) and gender presets when configuring a character's appearance, so that setting up visual prompts is quick and intuitive.
11. As a user, I want to click a "Fill from description" button in the appearance tab, so that the app automatically derives visual prompt keywords from the character's personality and bio.
12. As a user, I want character visual identity fields (positive prompt, negative prompt, style tags) to be properly preserved when importing and exporting Character Card V2 PNG and JSON files, so that I can share rich cards across platforms.
13. As a user, I want character visual identity tags to be automatically supplied to Imaging Studio and in-chat automatic image generation, so that generated portraits and scene illustrations stay visually consistent.
14. As a user, I want to adjust inference temperature and max output tokens in System Settings, so that I can fine-tune the creativity and length of model generations.
15. As a user, I want to choose among sampling presets (Precise 0.2, Balanced 0.7, Creative 1.1) in System Settings, so that I can quickly optimize generation parameters without manual number tuning.
16. As a user, I want to select system instruction templates (Roleplay, Direct, Straight answers) in System Settings, so that the model's fundamental behavior matches my desired interaction mode.
17. As a user, I want all configured generation parameters and instruction templates to be seamlessly transmitted in API calls to OpenAI-compatible, DeepSeek, and OpenRouter endpoints, so that changes take effect immediately.
18. As a user, I want to download and install a fully functioning `app-release.apk` on my Android device, so that I can experience the entire 4.51 feature set locally without development tools.

## Implementation Decisions

### 1. Messenger 会话抽屉与控制系统 (Chat Drawer & Controls)
- **UI 结构**：在 `ChatsScreen.kt` 的 `ConversationScreen` 中集成 `ModalBottomSheet`，包含双 Tab：`对话控制 (Conversation)` 与 `记忆管理 (Memories)`。
- **对话控制 Tab 状态**：
  - 自动配图开关：`auto_image_generation`（布尔值，持久化于角色上下文或会话配置）。
  - 社交发帖授权开关：`allow_post_y`、`allow_post_ustagram`、`allow_post_rebbit`（布尔值，持久化于 `ResidentCharacter` 的扩展设定或 `character_cognition`）。
  - 网络搜索开关：`web_search_enabled`（布尔值，持久化与提示词注入）。
- **记忆管理 Tab 状态**：
  - 长期记忆列表：绑定 `store.memories(character.id)`，支持单项置顶/删除。
  - 手动添加：提供输入框与“添加记忆”按钮，调用 `store.rememberIfCurrent` / `store.addManualMemory`。
  - 立即整理：触发 `MemoryExtractor` 对最近未整理消息进行总结沉淀。
- **防中断保护**：在 `BackHandler` 逻辑中检测 `sending || ActiveChatReplies.contains(character.id)`，若为真则弹出 `AlertDialog` 二次确认，用户确认退出时调用 `stopGeneration()` 并退出，点击取消则保留会话。
- **开关持久化（2026-08-25 访谈定）**：5 个开关按角色级持久化到 `member_world_context` 的 per-member KV（不动数据库 schema；语义为「成员在此世界中的授权状态」，角色离场后随世界历史保留、恢复活动时仍在）。不放入 `card_json` 的 `data.extensions`——角色卡是可移植资产，授权状态不应随导入导出外流。
- **P0 修复前置纳入验收（2026-08-25 走查发现）**：`ChatsScreen.kt` 的 `rememberSaveable { mutableStateOf(ChatControls()) }` 导致打开 1:1 聊天必崩溃（`ChatControls` 无 Saver）。本 ticket 必须先以最小修消除崩溃（自定义 `listSaver`），再谈接通；回归测试覆盖 Saver 往返。
- **DuckDuckGo 检索（完整实现，不做占位）**：请求 DDG lite/html 端点、解析结果摘要、注入 system prompt；带超时，失败静默降级为不检索。全库当前无任何检索基础设施（2026-08-25 走查证实）。
- **回复后自动配图**：复用世界事件既有 LocalDream 管线（`LocalDreamQueue` + 「配图意图」机制），Local Dream 未安装时静默入队等待，不阻塞聊天回复。
- **发帖授权消费点**：世界引擎 tick 选行动前过滤 + 发布执行前兜底双检查；tick 需新增 Y 发帖分支（复用 `YGeneration`，当前 tick 从不发 Y）。授权开关**默认开**，维持现有世界活性不回归。

### 2. 角色外观与视觉身份双 Tab 编辑器 (Characters Appearance)
- **UI 结构**：`CharacterCreateEditScreen` 重构为顶部两 Tab：
  - `角色 (Character)`：Name, Handle, Description, Personality, Scenario, First Message, Relationship.
  - `外貌 (Appearance)`：
    - 风格选择器单选 Chip 组：Photoreal (写实), Film Photo (胶片), Anime (动漫), Painted (油画插画), Custom (自定义).
    - 性别选择器单选 Chip 组：Woman, Man, Non-binary, Unspecified.
    - 视觉特征固定描述（Prompt）：发色、瞳色、服装、体态特征。
    - 负向提示词（Negative Prompt）：默认过滤畸变与低质量标签。
    - “从描述填充 (Fill from description)”快捷按钮：从已填写的 Description/Personality 中智能抽取关键词填充到 Prompt。
- **协议兼容**：`CharacterCardV2` 数据类扩展支持 `visual_identity` 扩展字典字段，在打包 PNG `tEXt` 块及 JSON 导出时完整编码，导入时自动解析还原。
- **从描述填充实现（2026-08-25 访谈定）**：已配置 Provider 时调 LLM 从 Description/Personality 抽取视觉关键词；未配置时降级为本地规则/关键词映射，并提示「配置 Provider 后效果更好」。

### 3. 生成参数与指令模板系统 (Generation Settings)
- **数据结构与存储**：在 `ProviderStore` / `InferenceConfig` 中新增生成调优参数：
  - `temperature`: Float (0.0f - 2.0f，默认 0.7f)
  - `max_tokens`: Int (128 - 8192，默认 2048)
  - `top_p`: Float (0.0f - 1.0f，默认 0.95f)
  - `generation_preset`: Enum (`Precise`, `Balanced`, `Creative`, `Custom`)
  - `instruction_template`: Enum (`Roleplay`, `Direct`, `StraightAnswers`)
- **UI 展示**：在 `SystemSettings` 的 `AI & Models` 中增加 `Generation Parameters` 与 `Instruction Templates` 分区卡片，提供预设单选器与精细 Slider。
- **推理下发**：在 `ProviderChatClient` 与 `ProviderProtocol` 构建 OpenAI 兼容 Payload 时，将 `temperature`, `max_tokens`, `top_p` 填入 JSON 请求体，并根据 `instruction_template` 包装系统提示词头部。
- **继承链（2026-08-25 访谈定，见 ADR-0063）**：生成参数为「全局默认 + 推理配置可覆盖，未覆盖继承全局默认」。存储：全局默认存设置层，推理配置 record 增可空覆盖字段（空 = 跟随默认）；Payload 拼装按「覆盖优先、缺省继承全局」解析。注：`GenerationSettings` 已全线接入请求（`ProviderConfig.kt:644-655`，默认 temp 0.7 / max 2048 / top_p 0.95 / Balanced / Roleplay），本 ticket 主体是 UI 与继承链存储。

### 4. 端到端闭环与构建发布
- 保持 Fancy OS 桌面与 Fancy Store 解耦形态（ADR-0061）。
- 所有测试用例在 JVM Unit Test 与 API 28/36 架构下通过。
- 执行 `./gradlew assembleRelease` 生成最终 `app-release.apk`。
- **联调环境（2026-08-25 访谈定）**：真实云端联调使用 `.env` 已更新的 DeepSeek key（`deepseek-v4-flash`，2026-08-25 实测 `/models` 与 `/chat/completions` 均 200）。注意该模型带 `reasoning_content` 字段，流式解析必须兼容。Release 构建未配置 `AI_LIVESAVER_KEYSTORE_PATH` 时按 `app/build.gradle.kts` 既有逻辑复用 debug 证书，产物可安装。

## Testing Decisions

- **Good Test Criteria**：针对外部可观察行为（UI 状态变更、数据库读写一致性、API 请求 Payload 拼装正确性、导入导出协议对称性）编写测试，不测试内部私有实现细节。
- **测试模块与覆盖**：
  1. `MessengerDrawerTest`：验证会话抽屉中的发帖权限开关变更、记忆添加与置顶、记忆整理触发、生成中返回拦截与确认逻辑。
  2. `CharacterAppearanceCardTest`：验证双 Tab 角色编辑器外观字段保存、预设应用、从描述抽取提示词、Character Card V2 PNG/JSON 导入导出字段完整性。
  3. `GenerationSettingsTest`：验证系统设置参数调整后，`ProviderChatClient` 构建的 HTTP 请求包含正确的 `temperature`、`max_tokens` 和预设模板 system prompt。
  4. `EndToEndFlowTest`：验证从角色创建（含外观）→ 进入 Messenger 聊天流式回复 → 抽屉修改发帖权限与记忆 → 退出返回桌面的端到端无崩溃闭环。
- **Prior Art**：
  - `CharacterCardV2Test.kt`
  - `ProviderProtocolTest.kt`
  - `MemoryExtractorTest.kt`
  - `ActiveChatRepliesTest.kt`

## Out of Scope

- 桌面系统（Fancy OS Desktop）与商店系统（Fancy Store）的像素级 UI 微调（继续保持当前设计）。
- 手机端离线编译专用 llama.cpp NPU/GPU 驱动与本地模型权重（优先交付云端 BYOK 与标准端点兼容）。
- 真实手机网络运营商级别的 Voice & Calls 硬件通话（仅保留应用内拨号与界面入口）。
- Pro 付费订阅墙与商业门禁。

## Further Notes

- 本 Spec 为 4.51 版本功能复刻的规范基准，由地图 issue #50 派生，拆分为 #51、#52、#53、#54 四个执行 ticket 推进。
