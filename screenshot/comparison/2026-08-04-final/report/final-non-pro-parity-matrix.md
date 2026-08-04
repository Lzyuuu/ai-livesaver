# Final non-Pro parity matrix — 2026-08-04

## 判定口径
基于 HEAD `17ce24d69aba9d4086d4476756d597f5462d78e4`、既有双模拟器精确脚本/UI dump/截图证据和本次稳定性检查。参考 APK 文件未在当前工作树可定位，且本机无 Java，无法重新安装/构建；因此不能把未重跑项伪称 PASS。Pro 锁定差异按 ADR-0062/项目策略排除。

| 切片 | API28 | API36 | 判定 | 证据/说明 |
|---|---|---|---|---|
| Home/Desktop | PASS | PASS | PASS | 双 API current/reference home 精选截图；桌面入口可达 |
| Settings 9 roots/search | FAIL | FAIL | FAIL | 当前 Settings IA 与参考 Chat brain/Developer & About/System Settings/Help/Update 不同；既有 dumps |
| Messenger 控件 | PASS | PASS | PASS | Recent/New Character/Search/Root 入口证据 |
| Messenger Groups | BLOCKED | BLOCKED | BLOCKED | 入口存在，但完整群聊创建/成员管理深度未重新证明 |
| Ustagram | PASS | PASS | PASS | 可进入、Generate 控件存在；空态为已知差异 |
| Rebbit | PASS | PASS | PASS | 可进入；内容深度未达参考 |
| Y | PASS | PASS | PASS | 可进入、Generate 控件存在 |
| Binder | FAIL | FAIL | FAIL | 当前为偏好表单 Build first match，非参考策展流 |
| Characters | PASS | PASS | PASS | 角色入口/导入/Search/Root 可达 |
| Phone | PASS | PASS | PASS | 联系人列表并可跳 Messenger；功能深度较薄 |
| Imaging | PASS | PASS | PASS | On-device/Forge/Local Dream 入口证据 |
| Gallery | FAIL | FAIL | FAIL | 当前空资产，参考有资产 |
| Aura Swap | BLOCKED | BLOCKED | BLOCKED | 表单入口存在，真实素材联动未证明 |
| Games | PASS | PASS | PASS | Hub 六入口可见；玩法闭环未证明 |
| Voice & Calls | BLOCKED | BLOCKED | BLOCKED | 当前为骨架页，不可宣称与参考能力等价 |
| 外部 Provider/HF/LocalDream/Forge/Aura | BLOCKED | BLOCKED | BLOCKED | 无凭据探测：HF 200、Forge endpoint 200；OpenRouter 无输出/不可判定；LocalDream/Aura 需设备运行时/模型 |
| Crash/ANR | PASS | PASS | PASS | 本次 logcat 未发现可归因 FATAL/ANR；不等价于长时稳定性证明 |

## 总结
非-Pro 入口地图大体可用，但 Settings、Binder、Gallery 和若干深流程仍 FAIL/BLOCKED。不能关闭 issue #3。
