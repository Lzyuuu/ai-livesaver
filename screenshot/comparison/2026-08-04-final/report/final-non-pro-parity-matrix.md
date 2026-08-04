# Final non-Pro parity matrix — 2026-08-04

## 验收口径

最终验证实现提交：`34866d3`，报告整合后的当前 HEAD 见 `test-summary.md`。参考 APK 为 `v4.47-github-release.apk`；当前 APK 为 `app/build/outputs/apk/debug/app-debug.apk`。两者均已通过 mobile-mcp 安装到 API 28（`emulator-5554`）和 API 36（`emulator-5556` / mobile-mcp `ai_livesaver_api36`）。Pro 锁、完整游戏、真实电话与完整 STT/TTS 按 ADR-0061 排除。

| 切片 | API28 | API36 | 判定 | 本轮证据 |
|---|---|---|---|---|
| Home runtime / Root CTA | PASS | PASS | PASS | 四态纯函数测试；当前 APK 双 API 启动；mobile-mcp 截图 |
| Settings 9 roots / search | PASS | PASS | PASS | `HomeSettingsAcceptanceTest` 双 API；真实九根 IA、typed leaf、搜索与返回 |
| Messenger prompt / backup / groups | PASS | PASS | PASS | `MessengerSmokeTest` + 本地 HTTP `MessengerRetryHttpSmokeTest` 双 API；群成员、group prompt、stop/clear/retry/persist |
| Ustagram / Rebbit / Y | PASS | PASS | PASS | 三 kind SQLite 隔离、CRUD/互动/重启；失败不写模板；Y audience、Rebbit sort；既有三 App UI smoke |
| Binder / Characters / Phone | PASS | PASS | PASS | 五步草稿、严格 candidate parser、幂等确认；Binder/Phone/三格式导入 targeted tests |
| Imaging / Advanced / Clear | PASS | PASS | PASS | Advanced 走真实设置持久化；Clear 仅清 studio 状态；Gradle/现有 Imaging tests |
| Gallery unified assets | PASS | PASS | PASS | 统一 CreativeAsset、筛选/详情/删除；`GalleryFlowSmokeTest` 双 API |
| Aura picker / Gallery linkage | CONDITIONAL | CONDITIONAL | PASS（条件） | system picker、缩略图、交换/清空、Aura output lineage 已落地；模型缺失错误路径可验证；真实 MNN 仍受四模型条件阻断 |
| Games | PASS | PASS | PASS | 六入口、明确占位、无 Pro、返回稳定；`MainActivitySmokeTest` 双 API |
| Voice & Calls | PASS | PASS | PASS（ADR 范围） | 设置骨架与持久开关、明确无真实通话；Settings smoke 双 API |
| v23→v24 migration | PASS | PASS | PASS | `WorldStoreContractsTest` fresh/v23 upgrade、数据保留、事务/lineage |
| Crash / ANR | PASS | PASS | PASS（验收窗口） | 双 API instrumentation 无 crash/ANR；mobile-mcp API36 无 crash；API28仅有历史旧 crash，无本轮新记录 |
| 外部 Provider | BLOCKED | BLOCKED | BLOCKED | 本地 HTTP protocol/retry 已通过，但无可用真实密钥，不能验证远端真实推理 |
| HF / Local Dream / Forge / Aura models | BLOCKED | BLOCKED | BLOCKED | SHA 与协议测试通过；本轮缺模型/服务/凭据，不能验证真实下载、出图与 MNN happy path |

## 最终判定

所有不依赖外部服务/模型的非 Pro 产品切片已实现并通过 API28/API36 自动化门禁。Issue #3 的最终关闭门禁仍为 **BLOCKED**：批准计划明确要求真实 Provider、HF/模型、至少一个真实出图 backend 和 Aura 在本轮成功，当前环境未提供这些依赖。不得关闭 #3，也不得把条件通过写成全部通过。
