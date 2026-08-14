# 目标定义 — Fancy Store 商店形态改造（v4.50 基座）

本段是 grill → spec → tickets 共识编译出的执行定义；所有结论已在 GitHub tracker 留痕（map #32、spec #38、切片 #39–#44），请按此执行，不新做设计决策。可直接作为 `/goal <本段>` 的目标，或粘贴给 `/create-workflow` 作为意图输入。

仓库内没有 `.scratch/2026-08-13-v450-fancy-store/issues/`；切片事实来源是 `Lzyuuu/ai-livesaver` 的 #39–#44。实现细节以对应 issue 正文为准。

如果 `/goal` 需要更具体的内容，可在 `/goal` 后追加 `--budget <tokens>`，或要求主 agent 先读本文件再执行。

## 背景

项目是 AI Livesaver——本地优先的 AI 角色世界 Android 应用（Kotlin / Jetpack Compose）。本轮特性是 **Fancy Store 商店**（CONTEXT.md 尚无此词条，定义一次：系统桌面内置的应用商店，作为独立 App 与 Pack 的唯一分发入口；用户从商店安装能力，未完成功能在商店中可见但暂不开放下载）。

本轮要解决的验收问题：把当前硬编码在 Hub 里的 8 个独立 App，改成商店驱动形态——保留现有**系统桌面**（时钟 + Root 卡 + 快速入口 + 手机 Dock），商店作为 Dock 第 5 槽常驻入口；目录 17 个产品；安装状态持久化；入口经 `openDesktopApp` 门控。

词表来自 `CONTEXT.md`（系统桌面、对照验收、开放入口、增强能力、迎宾引导、世界事件、Binder、Games Hub、Phone、Creative Suite）。权威决策见 wayfinder map #32（5 个决策 ticket 已全部解决）。规格见 #38。参考 APP 为仓库内 `v4.50-github-release.apk`。对照验收遵循 ADR-0062。

## 验收（全部满足才算完成）

1. 系统桌面形态不变：时钟、Root 卡、快速入口（4 Hub）、手机 Dock（Messenger、Imaging、Gallery、Settings）仍在；Dock 固定第 5 槽为商店；点商店图标进入商店页。
2. 商店页有「Store / 你的库」分段、搜索、三区列表（可获取 6：Y、Ustagram、Rebbit、Binder、Storage、Aura Swap / 即将开放 2：Games、Phone / 即将推出 9：Groups、Root Creator、Aura、Root Producer、Benchmark、Lorebook、高清放大包、人脸增强包、语义记忆包）、底部弹层详情。
3. 用户可完成安装全链路：获取 → 模拟下载进度（约 1.5 秒）→ 已安装/打开 → ＋添加到首页 → 你的库管理 → 卸载回可获取；安装状态与 `on_home` 跨重启持久化（`app_install` 表，DB v24→v25 升级无损，默认不预装任何商店 App）。
4. 未完成能力诚实呈现：Games、Phone 标记「即将开放」且按钮禁用；9 个即将推出产品按钮禁用。商店中无付费墙（**开放入口**贯穿商店；`requirements` 只展示）。
5. 入口门控生效：未安装的 App 从 Hub、Dock、深链 `OPEN_DESKTOP_APP_EXTRA`、通知/世界事件都不可直接打开，只引导到商店；COMING_SOON / UPCOMING 不可达；已安装的 App 各入口可达，Screen composable 零改动，功能与迁移前一致。
6. 已「添加到首页」的 App 追加进 Dock（商店槽固定不挤占）；桌面与商店状态双向一致。
7. 世界备份包含 `app_install` 并可恢复；世界数据擦除同时清除安装状态。
8. 界面文案简体中文，专有名保留英文（Y、Ustagram、Rebbit、Binder、Messenger、Phone、Games）。视觉延续深蓝/金色/衬线 + AppStore 式布局（原型 C）。**迎宾引导**保留。
9. 现有**增强能力**（世界设定、关系状态、对话回顾、Provider 能力测试等）保留，不因商店化而删除。
10. **对照验收**（跨切片共享，ADR-0062）：每个切片完成后，同一操作路径下同时打开参考 APP v4.50 与当前 APP 走查；未通过双 APP 对照的切片不得视为完成。
11. 交互走查优先用 mobile-mcp（安装、截图、页面流程）；安装 APK、logcat、dumpsys 等系统级操作用 adb。

## 边界（本轮不做）

- 真实 LLM 推理 / Local Dream 出图 / Aura 四模型 / HF 下载 / 真实通话 STT-TTS 的完整实现（后续期以商店 Pack/App 开放）。
- 3 个 Pack（高清放大、人脸增强、语义记忆）的真实下载管线（本轮全部「即将推出」，不启用）。
- Pro 付费墙（明确不引入）。
- v4.50 的 Root 全屏 + 4 Tab 形态（已修订为保留现有系统桌面）。
- Games 玩法与 Phone 真实通话（COMING_SOON，后续期开放）。
- 商店目录远程更新（本轮只用 assets 内置 `fancy_store.json`）。
- 本轮承诺落在下列切片范围内（#39–#44），切片之外不改动。

## 执行（按阻塞边顺序；每个切片以对应 GitHub issue 为事实来源）

切片即原子单位：部分完成不算完成。实现细节按文件确认对应 issue，目标文本只载阻塞顺序与可观察结果。

1. **T1 — 商店目录与安装状态基座**（#39）：无阻塞。`fancy_store.json` 17 产品 + 归一化 + 三档可用性推导 + `app_install` 表（DB v24→v25，含迁移测试，默认不预装）+ 安装状态机纯逻辑 + JVM 单测。按 #39 验收标准逐条确认。
2. **T2 — 商店只读骨架 UI**（#40）：阻塞于 #39。Store / 你的库 + 搜索 + 三区列表 + 弹层详情 + 空库引导；无安装动作；androidTest 覆盖呈现。按 #40 验收标准逐条确认。
3. **T3 — 安装状态交互**（#41）：阻塞于 #40。获取 → 下载 → 打开 → 添加首页 → 卸载全链路 + 你的库管理 + 首访引导一次性 + 跨重启持久化；androidTest 覆盖。按 #41 验收标准逐条确认。
4. **T4 — 入口门控与深链降级**（#42）：阻塞于 #41。`openDesktopApp` 统一安装态校验；Hub 未安装不可直达；深链/通知/世界事件降级商店引导；存量测试改种子安装态后保持绿色。按 #42 验收标准逐条确认。
5. **T5 — 壳层 Dock 接入**（#43）：阻塞于 #42。Dock 固定第 5 槽商店；添加首页进 Dock；桌面与商店状态双向一致；androidTest 覆盖。按 #43 验收标准逐条确认。
6. **T6 — 端到端回归与对照验收**（#44）：阻塞于 #43。全量 instrumentation（API 28 + API 36）无 crash/ANR；备份/擦除含 `app_install`；v24→v25 旧库实测迁移；对照 v4.50 同路径走查并归档 `screenshot/comparison/`；走查问题回录 issue（不阻塞 T6 关闭，但必须显式列出）。按 #44 验收标准逐条确认。

## 验证与收口

- **T1 结束**：JVM 单测覆盖目录解析、归一化、三档推导、状态机全转移；`WorldStoreContractsTest` 风格补 v24→v25 升级用例（旧库数据保留 + 新表 + 种子不预装）。
- **T2 结束**：构建通过；androidTest 覆盖三区呈现、搜索、详情弹层、disabled 按钮、空库引导；mobile-mcp 对照原型 C 与 v4.50 商店浏览路径截图。
- **T3 结束**：androidTest 覆盖获取→下载→打开→添加首页→移除→卸载 + 重启持久化 + 引导一次性；对照走查安装交互。
- **T4 结束**：新增测试覆盖未安装不可达、已安装可达、coming_soon 不可达、深链降级不崩溃；存量经 Hub 打开的测试改为种子安装态后全绿；对照走查门控。
- **T5 结束**：androidTest 覆盖 Dock 5 槽、商店进出、添加首页后 Dock 更新、移除后消失、未安装不可从桌面直达；对照走查桌面 Dock。
- **T6 结束**：API 28 + API 36 全量 instrumentation 绿；备份/擦除/迁移测试绿；对照截图归档。

全部切片完成后的质量门与收口：

- 跑完整测试套件（T6 已含双 API instrumentation）。
- `/code-review`（Standards + Spec 两轴）。
- 提交当前分支 `prototype/v450-shell`。
- 把各切片 issue（#39–#44）Status 改为 resolved，并在其下追加 `## Answer`；按 `docs/agents/issue-tracker.md` 用 `gh` 操作 `Lzyuuu/ai-livesaver`。
- 更新 `docs/development-progress-and-roadmap.md`。
- 不在用户要求之外关闭 map #32 / spec #38，也不另开 code-review 以外的流程。

## 产物冲突（不自行解决，执行时按切片正文）

- `CONTEXT.md` 的 **Games Hub** / **Phone** 写第一期桌面入口可进入；spec #38 与 T2–T5 将其标为 coming_soon，未安装不可达。切片已拍板，与 CONTEXT 旧表述冲突。
- `CONTEXT.md` 的 **Creative Suite** 写第一期 HF 模型下载工作流须可用；spec #38 Out of Scope 将 HF 下载 / Aura 四模型整段推迟。切片已拍板。

## 缺口（未在切片中留痕的执行细节）

已由用户在 `/to-goal` 审阅时选定「全部用默认」：

- 执行：主 agent 按 T1→T6 串行；不指定模型与 budget；不用 herdr（本文件不加 herdr 小节）。
- Dock 超出 5 槽后的收纳规则、商店弹层选型（ModalBottomSheet vs sheet 路由）：仍不在本文件拍板，留给实现期按最小可用做（map #32 / spec #38 原文即「实现期细化」）。
- 对照走查设备：按 T6 正文用 API 28 + API 36 模拟器；不额外指定真机。
