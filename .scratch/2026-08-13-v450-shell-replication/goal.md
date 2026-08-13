# 目标定义 — 系统桌面（v450 壳层）复刻

> 演练样例：本文件是 `/to-goal` 在 fancy-ai-apk 仓库的 dry-run 产物，切片清单为示例（真实运行时以 `.scratch/<feature>/issues/` 中 `/to-tickets` 的实际切片为准）。本段可直接复制为 `/goal <本段>` 的目标，或粘贴给 `/create-workflow` 作为意图输入。

## 背景

项目是 AI Livesaver——本地优先的 AI 角色世界 Android 应用，本轮特性是**系统桌面**：以 Root 卡片、独立 App 图标与底部 Dock 构成的手机桌面壳层，视觉对齐参考 APP fancy-ai（仓库内 `v4.47-github-release.apk` / `v4.50-github-release.apk`），词表见 `CONTEXT.md`。

## 验收（全部满足才算完成）

1. 桌面壳层从启动直接可进入，可进入 Messenger、Ustagram、Rebbit、Y、Phone、Games 等入口。
2. 每个复刻切片完成后，同一操作路径下同时打开参考 APP 与当前 APP 对照走查（对照验收）；未通过双 APP 对照的切片不得视为完成。
3. 交互走查优先用 mobile-mcp（安装、截图、录屏、页面流程）；安装 APK、logcat、dumpsys 等系统级操作用 adb。
4. 现有增强能力（世界设定、关系状态、对话回顾、Provider 能力测试等）保留在其对应位置，不因对齐壳层而删除。

## 边界（本轮不做）

- 不改世界层、推理配置、数据层与既有社交空间的业务逻辑。
- 不新增第五套主导航容纳增强项；增强项放入 System Settings、角色资料、Messenger 等对应位置。
- 黑底荧光黄绿不作为主风格；对齐深蓝渐变、衬线标题、金色强调与卡片化模拟系统。
- 本轮承诺落在下方切片范围内，切片之外的不改动。

## 执行（按阻塞边顺序；每个切片以 issues/NN 文件为事实来源）

1. **01 — 桌面壳层骨架与视觉基线**：无阻塞。启动进入 Root 卡片 + App 图标 + Dock 的桌面，主色彩与字体对齐参考 APP。
2. **02 — 桌面入口连通**：阻塞于 01。Messenger、Ustagram、Rebbit、Y、Phone、Games 入口可进入。
3. **03 — 双 APP 对照走查**：阻塞于 02。对 01–02 按同一操作路径对照参考 APP 走查并留截图，修复走查发现的偏差。
4. 切片即原子单位：部分完成不算完成。实现细节以切片文件为准，目标文本只载阻塞顺序。

## 验证与收口

- 每个切片结束：构建通过；界面改动用 mobile-mcp 走查截图，真机/模拟器对照验收。
- 全部切片完成后：跑一遍完整测试套件；`/code-review`（Standards + Spec 两轴）；提交当前分支；把各 `issues/NN-*.md` 的 Status 改为 resolved 并追加 `## Answer`；更新开发进度文档。

## 缺口（未在切片中留痕、需要用户补齐的执行细节）

- 执行模型与 agent 预算
- 是否需要 herdr 顶层分 pane 编排
- 验收走查的设备与参考版本（v4.47 或 v4.50）
