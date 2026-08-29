---
status: complete
created: 2026-08-29
updated: 2026-08-29
attempts: 1
attempt_cap: 4
---

## Objective

在不需要真实设备（V2546A 真机 `10AG5J1G74002B8` 全程零操作）的前提下，
完成 2026-08-29 剩余工作审计中所有可在模拟器 `emulator-5556` + 宿主机上闭环的项：

- **批一（实现与修正）**：模型 SAF 导入、引擎内部参数页、Lorebook 条目结构、
  Games Hub 六游戏按参考深化、Fancy Store 真实下载与打开路由修正、
  Benchmark LiteRT 代码分支、Local Dream 远程主机配置（仅实现+测试）、
  checklist 坏引用修正、71 行口径统一、tracker #74/#75/#76 内容同步。
- **批二（视觉验收）**：在模拟器上重拍当前 APP 全部页面（含修复后的聊天详情/抽屉、
  阶段③新页、语音与通话 SE-05、语音模型管理区），重新生成 71 张双端合成图，
  并对每页留逐页审阅结论（PASS / 差异 / 已返工）。
- **明确排除（需真机或外部资产，不在本 goal）**：LiteRT CPU/GPU/NPU 真实运行、
  真机 Benchmark tokens/s、真机端到端本地聊天、Local Dream 真机互联复验。
  Benchmark LiteRT 分支只要求代码路径+单测，不要求真实执行。

## Success criteria

1. 文档与 tracker：`walkthrough/2026-08-29-phase3/coverage-checklist.md` 5 处坏引用
   全部修正；全仓库走查口径统一为基线 71 行；`gh issue view 74/75/76` 的正文与
   代码现状一致（#74 不再声称 Games/Lorebook/RootCreator/Benchmark 未实现，
   #75 附 LiteRT 真机 blocker 日志与解除条件）。
2. 功能实现（每项至少 1 个新增测试，且全绿）：
   - ModelEngineScreen/LocalModels 支持文件选择器导入 GGUF/.litertlm
     （登记、激活、删除、大小上限，不写入凭据）；
   - 引擎内部参数页（上下文长度、预填充/解码线程、批大小）可配置、持久化，
     并被 LlamaChat/LlamaNative/Benchmark 真实消费；
   - Lorebook 条目具备名称/关键词/启用开关/编辑，注入逻辑按关键词过滤；
   - BenchmarkScreen 对 litert 类型走 LiteRT 计时分支（可注入引擎 seam 供单测）；
   - Fancy Store package 类点击后真实下载资产并校验，打开路由进入正确页面；
   - Games Hub 六游戏规则按参考 APP 逐项深化（保留 GamesEngineTest 模式）；
   - LocalDreamClient 支持可配置远程主机（默认仍 127.0.0.1:8081，
     URL 校验拒绝非 http/https，mock server 测试覆盖 /info /models /generate）。
3. 视觉验收：`walkthrough/2026-08-29-phase3/comparisons/` 下 71 张双端合成图
   全部重新生成（文件 mtime 晚于本 goal 创建时间），其中当前侧一律为本轮模拟器
   新截图；SE-05 不再缺失；语音模型管理区有当前侧截图；
   `whisper-smoke.log` 用模拟器 instrumentation 重新生成并归档。
4. 逐页审阅：新增 `walkthrough/2026-08-29-phase3/page-review.md`，71 行每行有
   结论（PASS / 差异描述 / 已返工+复拍记录），无"未审"行。
5. `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
   ./gradlew :app:testDebugUnitTest` 退出 0。
6. 过程约束：本 goal 期间所有 adb 操作仅指向 `emulator-5556`；
   不安装/卸载/操作真机任何 APP；不复制参考 APK 二进制进项目。

## Verification

1. 脚本校验：python 遍历 08-25 基线 71 行 ID，逐一断言 comparisons/ 同名 png
   存在且 mtime ≥ 2026-08-29 本 goal 创建时刻；断言 checklist 内引用的每个
   相对路径文件存在。
2. `gh issue view 74 --repo Lzyuuu/ai-livesaver`（及 75/76）正文含预期更新内容。
3. `page-review.md` 行数与基线 71 行 ID 一一对应，grep 无"未审/TODO"残留。
4. `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
   ./gradlew :app:testDebugUnitTest` 退出 0。
5. 新增功能逐项 `grep`/读码确认真实消费（非仅配置 UI），测试文件存在且通过。

## Boundaries

- 允许：`app/src/**`、`app/src/test/**`、`app/src/androidTest/**`、
  `walkthrough/` 证据、tracker issue 正文更新（gh issue edit）、
  CONTEXT.md/ADR、assets 清单。
- 设备：仅模拟器 `emulator-5556`（复刻 APP + 参考 APP + Local Dream）；
  参考侧只读（安装/运行/截图对照）。
- 禁止：真机 `10AG5J1G74002B8` 的任何操作；编辑 `.env`；未经询问 `git push`；
  改动 `.mimosa/` 与钩子；提交无关脏工作区（SocialScreens.kt 删除等在研改动）；
  复制参考 APK 的 so/dex 进项目；把合成图或未执行代码冒充真实验收。
- LiteRT 真实运行保持 `litert-hardware-blocker.md` 记录的 BLOCKED 状态，
  本 goal 不尝试解除。

## Stop conditions

- 某项实现发现必须依赖真机或未公开资产才能验证 → 标 BLOCKED 移入排除清单，
  不阻塞其余项，汇报后继续。
- attempts ≥ 4。
- 参考交互/规则细节在模拟器参考 APP 与 APK 静态分析下仍无法确定且影响验收 →
  停下向用户求证。
- 高风险操作（覆盖在研改动、删除用户数据等）→ 停下确认。

## 完成审计（2026-08-29）

1. 文档/tracker ✓：checklist 引用修正（SO-02/SO-03/SO-04/IM-03/SE-06 路径 +
   SO-04 错标改 ref-82 + whisper-smoke.log 文件本轮重建）；基线口径勘误为
   71 行/DONE 64/BLOCKED 7；#74（勘误段+真实剩余项）、#75（LiteRT blocker+
   解除条件）、#76（对照图问题+处理说明）已 `gh issue edit` 更新。
2. 功能实现 ✓（新增测试 7 组 36 个用例，全绿）：
   - 导入：`LocalModelsImportTest`（5）；`LocalModels.importFromUri`（SHA-256/上限/类型校验）
   - 引擎内部：`EngineInternalsTest`（6）；`LlamaChat.ensureLoaded` 与
     `defaultLlamaBenchmark` 真实消费 context/线程/批大小（JNI 5 参签名）
   - Lorebook：`LorebookKeywordsTest`（5）；关键词解析/触发/注入过滤 +
     `buildChatSystemPrompt(conversationText=)` 真实接线 + DB v27 迁移
   - Benchmark LiteRT 分支：seam（`LlamaBenchmark`/`LiteRtBenchmark`）+
     `LiteRtLm.benchmark` 墙钟 s/次（不伪造 tokens/s）
   - 商店真实下载：`StoreDownloadsTest`（4）+ `StoreDownloads.downloadAll`
     （SHA-256 校验）+ 进度 UI + launchTarget 修正（imaging/settings）
   - Games Hub：`GamesRulesTest`（9）；HP 战斗/行程进度/题库/猜谎/逆位塔罗 +
     六游戏面板 UI（保持 instrumentation 合同）
   - LocalDream 远程主机：`LocalDreamEndpointTest`（4）+ `LocalDreamRemoteTest`
     （3，mock /info /models）+ /generate 由 `LocalDreamHttpSmokeTest` mock 覆盖
3. 视觉验收 ✓：`comparisons/` 71/71，missing 0、stale 0；当前侧全部为
   `fresh/`（2026-08-29 模拟器）；SE-05 已补；`whisper-smoke.log` 由
   `WhisperSmokeTest` 真机模拟器实测重建（"Hello fancy voice test."）。
4. 逐页审阅 ✓：`page-review.md` 71 行 = PASS 55 / DIFF 15 / BLOCKED 1（MS-06
   离开确认需生成中状态，模拟器无 Provider，不伪造）。
5. 单测 ✓：`:app:testDebugUnitTest` BUILD SUCCESSFUL（267 用例）。
6. 过程约束 ✓：adb 全程仅 `emulator-5556`；真机零操作；未复制参考 APK 二进制；
   LiteRT 保持 BLOCKED；未 push、未动 `.env`/`.mimosa`/在研脏改动。

排除项状态（未完成、未冒充）：LiteRT CPU/GPU/NPU 真实运行、真机 tokens/s、
真机端到端本地聊天、Local Dream 真机互联复验——按解除条件等待外部资产/用户触发。
