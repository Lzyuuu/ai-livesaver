# Final non-Pro parity matrix — 2026-08-04

## 验收口径

最终验收对象为当前未提交收尾工作树，详细构建与测试状态见 `test-summary.md`。参考 APK 为 `v4.47-github-release.apk`；当前 APK 为 `app/build/outputs/apk/debug/app-debug.apk`。两者均已通过 mobile-mcp 安装到 API 28（`emulator-5554`）和 API 36（`emulator-5556` / mobile-mcp `ai_livesaver_api36`）。Pro 锁、完整游戏、真实电话与完整 STT/TTS 按 ADR-0061 排除。

| 切片 | API28 | API36 | 判定 | 本轮证据 |
|---|---|---|---|---|
| Home runtime / Root CTA | PASS | PASS | PASS | 四态纯函数测试；当前 APK 双 API 启动；mobile-mcp 截图 |
| Settings 9 roots / search | PASS | PASS | PASS | `HomeSettingsAcceptanceTest` 双 API；真实九根 IA、typed leaf、搜索与返回 |
| Messenger prompt / backup / groups | PASS | PASS | PASS | `MessengerSmokeTest` + 本地 HTTP `MessengerRetryHttpSmokeTest` 双 API；群成员、group prompt、stop/clear/retry/persist |
| Ustagram / Rebbit / Y | PASS | PASS | PASS | 三 kind SQLite 隔离、CRUD/互动/重启；失败不写模板；Y audience、Rebbit sort；既有三 App UI smoke |
| Binder / Characters / Phone | PASS | PASS | PASS | 五步草稿、至少两个候选、严格 candidate parser、外层 envelope 与内层截断 JSON 各自有限重试、幂等确认；Binder 三项 shipped HTTP validation 与真实 Provider 双设备通过 |
| Imaging / Advanced / Clear | PASS | PASS | PASS | Advanced 走真实设置持久化；Clear 仅清 studio 状态；Gradle/现有 Imaging tests |
| Gallery unified assets | PASS | PASS | PASS | 统一 CreativeAsset、筛选/详情/删除；`GalleryFlowSmokeTest` 双 API |
| Aura picker / Gallery linkage | CONDITIONAL | CONDITIONAL | PASS（条件） | system picker、缩略图、交换/清空、Aura output lineage 已落地；模型缺失错误路径可验证；真实 MNN 仍受四模型条件阻断 |
| Games | PASS | PASS | PASS | 六入口、明确占位、无 Pro、返回稳定；`MainActivitySmokeTest` 双 API |
| Voice & Calls | PASS | PASS | PASS（ADR 范围） | 设置骨架与持久开关、明确无真实通话；Settings smoke 双 API |
| v23→v24 migration | PASS | PASS | PASS | `WorldStoreContractsTest` fresh/v23 upgrade、数据保留、事务/lineage |
| Crash / ANR | PASS | PASS | PASS（验收窗口） | 双 API instrumentation 无 crash/ANR；mobile-mcp API36 无 crash；API28仅有历史旧 crash，无本轮新记录 |
| 外部 LLM Provider | PASS | PASS | PASS | `.env` Provider 真实测试双设备均为 2 PASS / 0 SKIP / 0 FAIL；覆盖能力鉴定、Messenger、Binder（至少两个候选）、Ustagram、Rebbit、Y 与 provenance；mock/template 不计为真实成功 |
| HF / Local Dream / Forge / Aura models | BLOCKED | BLOCKED | BLOCKED | SHA 与协议测试通过；本轮 `.env` 仅提供 LLM，缺少 HF 凭据、真实出图服务与 Aura 四模型，不能验证真实下载、出图与 MNN happy path |

## 最终判定

所有不依赖未提供外部模型/图像服务的非 Pro 产品切片，以及本轮 `.env` 覆盖的真实 LLM Provider 切片，均已通过 API28/API36 自动化门禁。LLM 范围已清零；HF、Local Dream / Forge 与 Aura 真实 happy path 仍按外部依赖诚实记为 **BLOCKED**，不得把这些条件项写成 PASS。
