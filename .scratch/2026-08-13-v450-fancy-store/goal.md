# 目标定义 — Fancy Store 商店形态改造（v4.50 基座）

> 本段可直接复制为 `/goal <本段>` 的目标，或粘贴给 `/create-workflow` 作为意图输入。
> 说明：此文本是编译好的执行定义，所有结论已在 GitHub tracker 留痕（spec #38、切片 #39–#44），请直接按此执行。
> 如果 `/goal` 需要更具体的内容，可在 `/goal` 后追加 `--budget <tokens>` 或要求主 agent 先读目标文件再执行。

## 背景

项目是 AI Livesaver——本地优先的 AI 角色世界 Android 应用（Android Compose）。本轮特性是 **Fancy Store 商店**（领域术语，CONTEXT.md 尚无此词条，定义如下：系统桌面内置的应用商店，作为独立 App 与 Pack 的唯一分发入口，用户从商店安装能力、未完成功能可见但暂不开放下载）。参考 APP 为 fancy-ai `v4.50-github-release.apk`，词表见 `CONTEXT.md`，权威决策见 GitHub 上的 wayfinder map（Lzyuuu/ai-livesaver#32，5 个决策已全部解决）。

## 验收（全部满足才算完成）

1. 商店作为 Dock 第 5 槽常驻入口，从系统桌面可进入；商店页有「Store / 你的库」分段、搜索、三区列表（可获取 / 即将开放 / 即将推出）、底部弹层详情。
2. 用户可完成安装全链路：获取 → 模拟下载进度 → 已安装/打开 → ＋添加到首页 → 卸载；安装状态跨重启持久化（`app_install` 表，DB v24→v25 升级无损）。
3. 未完成能力诚实呈现：Games、Phone 标记「即将开放」且按钮禁用；9 个未上架产品（Groups、Root Creator、Aura、Root Producer、Benchmark、Lorebook、高清放大包、人脸增强包、语义记忆包）标记「即将推出」且按钮禁用。
4. 入口门控生效：未安装的 App 从任何入口（Hub、Dock、深链、通知/世界事件）都不可直接打开，只引导到商店；已安装的 App 各入口可达，功能与迁移前完全一致。
5. 每个切片完成后，同一操作路径下同时打开参考 APP 与当前 APP 对照走查（对照验收，ADR-0062）；未通过双 APP 对照的切片不得视为完成。
6. 交互走查优先用 mobile-mcp（安装、截图、页面流程）；安装 APK、logcat、dumpsys 等系统级操作用 adb。
7. 现有增强能力（世界设定、关系状态、对话回顾、Provider 能力测试等）保留，不因商店化而删除。

## 边界（本轮不做）

- 真实 LLM 推理 / Local Dream 出图 / Aura 四模型 / HF 下载 / 真实通话 STT-TTS 的完整实现（后续期以商店 Pack/App 开放）。
- 3 个 Pack（高清放大、人脸增强、语义记忆）的真实下载管线（本轮全部「即将推出」，不启用）。
- Pro 付费墙（明确不引入，开放入口原则贯穿商店）。
- v4.50 的 Root 全屏 + 4 Tab 形态（已修订为保留现有系统桌面）。
- Games 玩法与 Phone 真实通话（COMING_SOON 状态，后续期开放）。
- 商店目录远程更新（本轮只用 assets 内置 fancy_store.json）。
- 本轮承诺落在下方切片范围内（#39–#44），切片之外不改动。

## 执行（按阻塞边顺序；每个切片以对应 GitHub issue 为事实来源）

1. **T1 — 商店目录与安装状态基座**（#39）：无阻塞。fancy_store.json 17 产品资产 + app_install 表（DB v24→v25 含迁移测试）+ 目录解析/归一化/三档可用性推导 + 安装状态机纯逻辑 + JVM 单测。
2. **T2 — 商店只读骨架 UI**（#40）：阻塞于 T1。Store/你的库分段 + 搜索 + 三区列表 + 弹层详情 + 空库引导，androidTest 覆盖呈现。
3. **T3 — 安装状态交互**（#41）：阻塞于 T2。获取→下载→打开→添加首页→卸载全链路 + 持久化 + 引导一次性，androidTest 覆盖。
4. **T4 — 入口门控与深链降级**（#42）：阻塞于 T3。openDesktopApp 统一安装态校验，未安装降级商店引导，存量测试改造保持绿色。
5. **T5 — 壳层 Dock 接入**（#43）：阻塞于 T4。Dock 固定第 5 槽商店 + 添加首页进 Dock + 桌面与商店状态双向一致，androidTest 覆盖。
6. **T6 — 端到端回归与对照验收**（#44）：阻塞于 T5。全量回归（双 API 无 crash/ANR）+ 备份/擦除含 app_install + 对照 v4.50 同路径走查 + 截图归档。
7. 切片即原子单位：部分完成不算完成。实现细节以对应 issue 文件为准，目标文本只载阻塞顺序与结果定义。

## 验证与收口

- 每个切片结束：构建通过；按切片验收标准逐条确认；界面改动用 mobile-mcp 走查截图。
- 全部切片完成后：跑完整测试套件；`/code-review`（Standards + Spec 两轴）；提交分支；把各切片 issue 的 Status 改为 resolved 并追加 `## Answer`；更新开发进度文档。
- 走查发现的问题回录 issue（不阻塞 T6 关闭，但必须显式列出）。

## 缺口（未在切片中留痕、需要用户补齐的执行细节）

- 执行模型与 agent 预算
- 是否需要 herdr 顶层分 pane 编排
- 对照验收的参考版本与设备（v4.50 APK 已装 emulator-5554；双 API 指 API 28 + API 36）
