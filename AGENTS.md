# AGENTS.md

除非用户另有要求，默认使用中文交流和输出。

## 参考 APP（复刻目标）

仓库根目录下的 `v4.47-github-release.apk` 是 **fancy-ai** 的安装包，它是本项目的参考 APP。开发时需要参考它的设计与整体 APP 功能进行复刻：UI 布局、交互流程、页面结构与核心功能都应以该 APK 的实际表现为准。可使用 `mobile-mcp` 将其安装到模拟器/真机上进行截图、录屏和页面流程走查，作为实现与验收的对照依据。

涉及 Android 模拟器/真机交互、UI 验证、截图、录屏、页面流程检查时，优先使用 `mobile-mcp`；只有在安装 APK、查看 `logcat`、`dumpsys`、权限/进程/Activity 控制等系统级调试场景，再使用 `adb`。

## Agent skills

### Issue tracker

Issues are tracked in GitHub Issues for `Lzyuuu/ai-livesaver` using the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

The default five-role triage vocabulary is used. See `docs/agents/triage-labels.md`.

### Domain docs

This repo uses a single-context domain-doc layout. See `docs/agents/domain.md`.
