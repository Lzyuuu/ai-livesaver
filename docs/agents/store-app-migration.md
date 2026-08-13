# Fancy Store：8 个已实现 App 迁移盘点

> 关联 Issue：[#37 Task: 现有 8 App 迁移盘点与商店接入](https://github.com/Lzyuuu/ai-livesaver/issues/37)  
> 前置设计：`docs/agents/store-install-model.md`、`research/fancy-store-catalog.md`  
> 调研日期：2026-08-13（纯本地代码）

本文盘点当前 **8 个待迁入 Fancy Store 的桌面 App** 的现状，为「从 Hub 固定入口 → 商店安装 / 添加到首页」做准备。**商店壳层、`app_install` 表、`fancy_store.json` 尚未落地**；本文只描述现状与迁移改动点。

---

## 1. 总览

### 1.1 当前桌面编排（迁移前）

| 区域 | 内容 |
|------|------|
| **Dock（固定 4 项）** | Messenger、Imaging、Gallery、Settings — **不在本 Issue 8 App 内** |
| **快速入口（4 Hub 文件夹）** | Social Hub、Creative Suite、System Core、Entertainment |
| **8 App 分布** | Social Hub：Y / Ustagram / Rebbit / Binder / Phone；Creative Suite：Aura Swap；System Core：Storage；Entertainment：Games |

入口 **无安装态**：`DesktopHub.*.apps` 与 `DesktopHubSheet` 直接列出全部 `DesktopApp`，`DesktopApp.openEntry` 恒为 `true`，`DesktopNavigator.isPaywalled()` 恒为 `false`。

### 1.2 统一启动链路（现状）

```
用户点击 Hub/Dock/Intent
  → SystemDesktopScreen.onOpenApp / DesktopHubSheet.onOpenApp
  → MainActivity.openDesktopApp(DesktopApp)
       ├─ Characters → showCharacters = true（overlay，不改 desktopRouteKey）
       ├─ Settings   → desktopRouteKey = "settings"
       └─ 其余       → desktopRouteKey = app.route
  → activeDesktopApp = DesktopApp.fromRoute(desktopRouteKey)
  → when (activeDesktopApp) { … composable … }
```

**冷启动 / 深链**（商店安装后应复用）：

```
Intent extra OPEN_DESKTOP_APP_EXTRA = "open_desktop_app"（route 字符串）
  → MainActivity.openDesktopAppRoute
  → LaunchedEffect → DesktopApp.fromRoute(route) → openDesktopApp(app)
```

定义位置：`MainActivity.kt`（`OPEN_DESKTOP_APP_EXTRA`、`openDesktopApp`、`when (activeDesktopApp)`）。

### 1.3 商店安装后复用原则

按 `store-install-model.md`：

1. 目录 `fancy_store.json` 的 `launchTarget` → `DesktopApp.route`（`aura` → `imaging` 特例）。
2. `app_install` 记录 `installed` + 可选 `on_home`。
3. 商店「打开」→ `openDesktopApp(resolveDesktopApp())` 或 `Intent.putExtra(OPEN_DESKTOP_APP_EXTRA, route)`。
4. Hub 图标 / 首页磁贴改为 **仅展示已安装且（可选）已 pin 的 App**，不再硬编码 `DesktopHub.apps` 全量列表。

---

## 2. 商店接入映射表

| store id | launchTarget | DesktopApp | route | Screen / 实现文件 | 当前 Hub | 商店 kind | 迁移后默认策略 |
|----------|--------------|------------|-------|-------------------|----------|-----------|----------------|
| `y` | `y` | Y | `y` | `YScreen` — `YScreens.kt` + `generateYPost` — `YGeneration.kt` | Social Hub | app, builtIn | 升级种子 `installed`；可从 Hub 移除 |
| `ustagram` | `ustagram` | Ustagram | `ustagram` | `UstagramAppScreen` — `UstagramScreens.kt` | Social Hub | app, builtIn | 同上 |
| `rebbit` | `rebbit` | Rebbit | `rebbit` | `RebbitScreen` — `RebbitScreens.kt` | Social Hub | app, builtIn | 同上 |
| `binder` | `binder` | Binder | `binder` | `BinderScreen` — `BinderScreens.kt`；`BinderDomain.kt`；`BinderOrchestrator.kt` | Social Hub | app, builtIn | 同上 |
| `storage` | `storage` | Storage | `storage` | 桌面：`PlaceholderAppScreen`；详情：`StorageScreen` — `SystemSettingsScreens.kt` | System Core | app, builtIn | 同上；桌面应直接进 `StorageScreen` |
| `aura-swap` | `aura_swap` | AuraSwap | `aura_swap` | `AuraSwapScreen` — `AuraSwapScreens.kt` | Creative Suite | app + 模型字节 | `installed` + 模型就绪检查；内嵌 Model Store tab 可保留 |
| `games` | `games` | Games | `games` | `GamesHubScreen` / `GamePlaceholderScreen` — `SystemDesktopScreens.kt`；`GamesHubEntries` — `DesktopRoutes.kt` | Entertainment | app, Pro 文案 | **COMING_SOON**：目录可见、不可安装 |
| `phone` | `phone` | Phone | `phone` | `PhoneContactsScreen` — `SystemDesktopScreens.kt` | Social Hub | app, Pro 文案 | **COMING_SOON**：目录可见、不可安装 |

**不在 8 App 内但相关**：`aura` → `DesktopApp.Imaging` / `imaging` / `ImagingStudioScreen`（Dock 固定，商店侧 COMING_SOON）。

---

## 3. 逐项盘点

### 3.1 Y

| 维度 | 现状 |
|------|------|
| **入口** | Social Hub → `hub-app-y`；无 Dock；World Event 路由 `DesktopApp.Y.route` |
| **实现程度** | **高**：Feed（`Y_POST_KIND = "y"`）、发帖、嵌套回复、@mention 队列、`WorldEngine.respondToPost`；AI 生成帖 `generateYPost`（需 `ProviderTask.World` + Structured） |
| **依赖** | `WorldStore`：`posts` / `createPost` / 回复表；`ProviderStore` + `ProviderTextClient`；`WorldEngine` 社交响应队列；SharedPreferences `social_y`（生成 prompt） |
| **启动路径** | `openDesktopApp(Y)` → `desktopRouteKey = "y"` → `YScreen` |
| **测试** | androidTest：`YFeedFlowSmokeTest`（1）；`MainActivitySmokeTest`（间接 Hub）；`SocialDomainPersistenceSmokeTest` 等跨域。unit：`YFeedTest`（9） |
| **迁移改动** | ① Hub 列表改读 `app_install`；② `FancyStoreInstallDefaults` 种子 `y`；③ `openDesktopApp` 可选安装守卫；④ 商店 Open 走 `OPEN_DESKTOP_APP_EXTRA`；⑤ Hub 预览图标可保留在 `DesktopHub.Social` 定义中作静态资源，展示层过滤 |

**迁移改动量：中**（社交栈成熟，但测试多、World Event 深链要保留）

---

### 3.2 Ustagram

| 维度 | 现状 |
|------|------|
| **入口** | Social Hub；Settings `MeScreen` → `onOpenMoments`；World Event → `Destination.Moments` |
| **实现程度** | **高**：图文 Feed（`moment`）、发帖、双击点赞、评论/回复、AI 生成帖/图、`WorldEngine.generateMomentPost`、`LocalDreamQueue` |
| **依赖** | `WorldStore` moment 域；`ProviderStore`；`WorldEngine`；可选 Local Dream / Imaging |
| **启动路径** | `openDesktopApp(Ustagram)` → `ustagram` → `UstagramAppScreen` |
| **测试** | androidTest：`UstagramFlowSmokeTest`（1，覆盖 compose/like/reply/generate）；`MainActivitySmokeTest.opensSocialHubAppAndReturnsToDesktop`；`SocialPostVersionSmokeTest` 等。unit：`UstagramChromeTest`（8） |
| **迁移改动** | 同 Y；额外注意 Settings 快捷入口 `onOpenMoments` 应检查 `app_install` 或始终允许（系统设置内链） |

**迁移改动量：中**

---

### 3.3 Rebbit

| 维度 | 现状 |
|------|------|
| **入口** | Social Hub；Settings `onOpenCommons`；World Event → `Destination.Commons` |
| **实现程度** | **高**：论坛 Feed（`forum`）、发帖、投票、子版块管理、`rebbit_subreddits` 表、`WorldEngine.generateRebbitPost`、详情页回复 |
| **依赖** | `WorldStore`：`ensureDefaultRebbitSubreddits`、`rebbitSubreddits`、`posts("forum")`；Provider；Local Dream（配图） |
| **启动路径** | `openDesktopApp(Rebbit)` → `rebbit` → `RebbitScreen` |
| **测试** | androidTest：`RebbitFlowSmokeTest`（1，长流程）；`MainActivitySmokeTest`；`SocialDomainPersistenceSmokeTest`。unit：`RebbitSubredditTest`（9） |
| **迁移改动** | 同社交三件套；子版块默认种子与商店安装解耦（已 runtime seed） |

**迁移改动量：中**

---

### 3.4 Binder

| 维度 | 现状 |
|------|------|
| **入口** | Social Hub → `hub-app-binder` |
| **实现程度** | **中高**：5 步问卷、`binder_drafts` / `binder_candidates`、Provider 生成 2–6 候选、`confirmBinderCandidateIdempotently` 导入角色并开聊；**无**独立头像生成管线（persona 文本为主） |
| **依赖** | `WorldStore` binder 表；`BinderOrchestrator` → `ProviderTask.World` + `ProviderTextClient.completeStructured` |
| **启动路径** | `openDesktopApp(Binder)` → `binder` → `BinderScreen` |
| **测试** | androidTest：`BinderFlowSmokeTest`（1，仅前几步）；`BinderOrchestratorValidationTest`（3，Provider mock）。unit：`BinderDomainTest`（4） |
| **迁移改动** | 商店安装 + Hub 过滤；生成流程依赖 Provider，商店详情应展示 Requirements |

**迁移改动量：小–中**（单屏、无 Hub 外入口）

---

### 3.5 Storage

| 维度 | 现状 |
|------|------|
| **入口** | System Core Hub；Settings → `onOpenStorage` → `showStorage` overlay **`StorageScreen`**（真功能） |
| **实现程度** | **中**：`StorageScreen` 展示 world.db / media / filesDir / 可用空间；桌面路由为 **`PlaceholderAppScreen`** + 按钮跳转 Settings 内同一 `StorageScreen`（双层入口） |
| **依赖** | `WorldStore.mediaStorageBytes()`；`StatFs`；无 Provider / MNN |
| **启动路径** | Hub → `storage` → Placeholder → 可选 `showStorage`；Settings 直达 `StorageScreen` |
| **测试** | androidTest：`SystemSettingsSmokeTest`（1，经 Settings 进 Storage）；**无** Hub `storage` 占位屏测试 |
| **迁移改动** | ① 商店安装后 Hub 打开应 **直接 `StorageScreen`**，去掉 Placeholder；② `storage` 与 Settings 内链并存；③ 种子 `installed` |

**迁移改动量：小**（逻辑简单，主要是 UX 统一）

---

### 3.6 Aura Swap

| 维度 | 现状 |
|------|------|
| **入口** | Creative Suite Hub → `hub-app-aura_swap` |
| **实现程度** | **中高**：双图选择、MNN 换脸管线（SCRFD / ArcFace / inswapper / CodeFormer）、结果写入 `creative_assets` + Gallery；内嵌 **Model Store** tab（`HfModelStore` / `HfModelCatalog`），非 Fancy Store 下载状态机 |
| **依赖** | `filesDir/models/*` + SHA-256；`MnnNative` / `MnnAuraBackend`；`WorldStore.saveCreativeAsset`；与 Gallery 联动 |
| **启动路径** | `openDesktopApp(AuraSwap)` → `aura_swap` → `AuraSwapScreen` |
| **测试** | androidTest：`AuraSwapSmokeTest`（7：UI、下载重试、MNN 假定设备）；`AuraRuntimeE2eTest`。unit：`HfModelStoreTest`（11） |
| **迁移改动** | ① 目录 `requiredDownloadBytes` 与商店 `beginAppInstall` 对齐；② 安装守卫：未装模型时商店引导下载 vs 应用内 Model Store；③ Hub 过滤 |

**迁移改动量：大**（模型包 + 商店下载生命周期 + 现有内嵌 Store 重复）

---

### 3.7 Games

| 维度 | 现状 |
|------|------|
| **入口** | Entertainment Hub → `hub-app-games` |
| **实现程度** | **低（占位）**：`GamesHubScreen` 列出 6 个 `GamesHubEntry`；点击 → `GamePlaceholderScreen`（文案 `game_placeholder_status`：「玩法占位 · 无 Pro 付费墙」） |
| **依赖** | 仅 `GamesHubEntries` 静态配置 + 字符串资源；无 WorldStore 玩法表 |
| **启动路径** | `openDesktopApp(Games)` → `games` → Hub 或 `activeGameId` → Placeholder |
| **测试** | androidTest：`MainActivitySmokeTest`（3：`opensGamesHubWithoutPaywall`、`opensAllSixGamePlaceholdersWithoutPaywall`）；**无**独立 `GamesSmokeTest` |
| **迁移改动** | **COMING_SOON**（见 §4）；Hub 在迁移期可隐藏或展示为锁定 |

**迁移改动量：小**（策略为主）；**产品风险：低**（本就占位）

---

### 3.8 Phone

| 维度 | 现状 |
|------|------|
| **入口** | Social Hub → `hub-app-phone` |
| **实现程度** | **低–中（联系人壳）**：`PhoneContactsScreen` 列出 `characters`、搜索、拨号 → **`openMessenger(characterId)`**，无语音通话 / TTS / 前台 Call Service |
| **依赖** | `WorldStore.characters`；Messenger 路由；参考 APK 要求 Pro + 语音引擎 |
| **启动路径** | `openDesktopApp(Phone)` → `phone` → `PhoneContactsScreen` |
| **测试** | androidTest：`PhoneContactsSmokeTest`（1）；`MainActivitySmokeTest.presetsRootInPhone` |
| **迁移改动** | **COMING_SOON**；若未来开放，需独立 Voice 栈，与商店安装正交 |

**迁移改动量：小**（策略）；**产品风险：中**（Hub 现可进，迁移后需明确 disabled UX）

---

## 4. Games / Phone：COMING_SOON 特殊处理

### 4.1 参考 APP vs 本仓库现状

| 项 | 参考 APK（`fancy_store.json`） | 本仓库现状 |
|----|--------------------------------|------------|
| `games` / `phone` | `requirements: ["Fancy AI Pro"]` | **无 Pro 墙**；Hub **直接可点** |
| 商店态 | Pro 未满足 → 不可安装 / 即将开放 | 未实现商店；`DesktopNavigator.isPaywalled` 恒 `false` |
| 玩法 / 通话 | 完整（Pro） | Games：6 个 Placeholder；Phone：跳转 Messenger |

### 4.2 占位实现位置（现状）

- **枚举**：`DesktopApp.Games` / `DesktopApp.Phone`（`DesktopRoutes.kt`）
- **Hub**：`DesktopHub.Social`（Phone）、`DesktopHub.Entertainment`（Games）
- **UI**：`GamesHubScreen`、`GamePlaceholderScreen`、`PhoneContactsScreen`（`SystemDesktopScreens.kt`）
- **状态文案**：`R.string.game_placeholder_status`、`GamesHubEntry.statusNoteRes` 各游戏一条
- **测试契约**：`MainActivitySmokeTest` 断言 **无 "Pro" / "$14.99"** 文案

### 4.3 迁移后目标行为（对齐 `store-install-model.md` §5.3）

```
StoreAvailabilityPolicy.isComingSoon(productId)
  games, phone → true（不落库 app_install）
```

| 触点 | 行为 |
|------|------|
| Fancy Store 列表/详情 | 显示 COMING_SOON；**无** Install / Add to Home 主按钮 |
| `FancyStoreInstallDefaults` | **不**为 `games` / `phone` 写 `installed` |
| Desktop Hub | **方案 A（推荐）**：Hub 仍展示图标但点击 → 商店详情 COMING_SOON；**方案 B**：Hub 隐藏，仅商店可见 |
| `openDesktopApp(Games/Phone)` | 若经深链调用：重定向商店或 Toast；**不应**在无安装记录时静默打开 |
| 测试 | `MainActivitySmokeTest` 中 Games 用例改为：**经商店策略**或 **seed 模拟已安装**；新增 `StoreAvailabilityPolicyTest` |

---

## 5. 迁移影响：代码触点清单

| 文件 / 模块 | 改动类型 |
|-------------|----------|
| `DesktopRoutes.kt` | Hub 列表改为「目录定义 + 安装态过滤」；`games`/`phone` 策略；可选 `DesktopApp.storeId` |
| `SystemDesktopScreens.kt` | Hub 文件夹预览格、Sheet 仅显示已安装 App；COMING_SOON 角标 |
| `MainActivity.kt` | `openDesktopApp` 安装守卫；商店回调；`Storage` 直开 `StorageScreen` |
| `WorldStore.kt` | v25 `app_install` 表 + CRUD（见 store-install-model） |
| `FancyStoreCatalog` / `assets/fancy_store.json` | 新产品清单（待加 assets） |
| `FancyStoreInstallDefaults` | 升级迁移：8 个中 6 个可安装 App 写 `installed`；排除 games/phone |
| `DesktopSeed.kt` | 不变（Root 种子与商店正交） |
| `DesktopSmokeTestSupport.kt` | 新增 `seedStoreInstallForSmoke(storeId)` 供仪器测试 |
| `DesktopRoutesTest` | `launchTarget` 映射 + COMING_SOON 单测 |

**不必改（仅复用）**：各 App 的 `*Screens.kt`、`BinderOrchestrator`、`YGeneration.kt`、`WorldEngine` 社交逻辑。

---

## 6. 迁移优先级建议

| 优先级 | App | 理由 | 风险 |
|--------|-----|------|------|
| **P0** | Storage | 无外部依赖；改动面小；可验证 `openDesktopApp` + 安装种子端到端 | 低 |
| **P0** | Binder | 单屏；Provider 依赖清晰 | 低（Provider 未配置时生成失败，已有 UI） |
| **P1** | Y、Ustagram、Rebbit | 核心社交；测试覆盖最好；需统一 Hub 过滤与 World Event 深链 | 中（多入口、多测试 setup） |
| **P2** | Aura Swap | 功能完整但模型下载与商店 Package 语义重叠 | **高**（大文件、SHA、与内嵌 Model Store 双轨） |
| **P3** | Games、Phone | 仅策略 + COMING_SOON；实现本身不要求先迁 | 低（策略）；Phone 未来功能风险高 |

**建议切片顺序**：`WorldStore.app_install` + Catalog 解析 → Storage/Binder 试点 → 社交三件套 Hub 过滤 → Aura Swap 模型与商店下载对齐 → COMING_SOON 策略与测试更新。

---

## 7. 测试影响清单

### 7.1 现有测试（按 App）

| App | androidTest 文件 | @Test 数 | unit 相关 |
|-----|------------------|----------|-----------|
| Y | `YFeedFlowSmokeTest` | 1 | `YFeedTest` ×9 |
| Ustagram | `UstagramFlowSmokeTest` | 1 | `UstagramChromeTest` ×8 |
| Rebbit | `RebbitFlowSmokeTest` | 1 | `RebbitSubredditTest` ×9 |
| Binder | `BinderFlowSmokeTest` | 1 | `BinderDomainTest` ×4；`BinderOrchestratorValidationTest` ×3 |
| Storage | （经 `SystemSettingsSmokeTest`） | 0 专用 | — |
| Aura Swap | `AuraSwapSmokeTest` | 7 | `HfModelStoreTest` ×11 |
| Games | `MainActivitySmokeTest`（2 用例） | 2 | `DesktopRoutesTest` |
| Phone | `PhoneContactsSmokeTest` | 1 | — |
| 跨 App | `MainActivitySmokeTest` | 7 | `DesktopRoutesTest` ×5 |

**共性 setup**：`writeWelcomeGuideCompleted` + `DesktopSeed.ensureDesktopWorld` + `activity.recreate()`（`DesktopSmokeTestSupport.seedDesktopShellForSmoke`）。

### 7.2 迁移后需调整的测试

| 测试 | 调整 |
|------|------|
| 所有经 **Hub** 打开的 Smoke | `@Before` 调用 `seedStoreInstallForSmoke` 写入对应 `app_id`，或测试专用「绕过守卫」开关 |
| `MainActivitySmokeTest.opensSocialHubAppAndReturnsToDesktop` | 种子 `ustagram` installed |
| `MainActivitySmokeTest` Games 用例 | 若 Hub 隐藏 Games：改为从商店 seed 安装或测 COMING_SOON 卡片 |
| `MainActivitySmokeTest.presetsRootInPhone` | Phone COMING_SOON 后删除或改为商店详情断言 |
| `DesktopRoutesTest` | Hub 应用列表不再断言固定 6 项 Social；改为 catalog + policy |
| `SystemSettingsSmokeTest` | Storage 仍应从 Settings 可达（不依赖商店安装） |

### 7.3 建议新增测试

| 文件 | 类型 | 覆盖 |
|------|------|------|
| `FancyStoreCatalogTest` | unit | 解析 `fancy_store.json`；8 id 存在 |
| `StoreAvailabilityPolicyTest` | unit | `games`/`phone` → COMING_SOON |
| `StoreLaunchTargetTest` | unit | `launchTarget` ↔ `DesktopApp.route`（含 `aura`→`imaging`） |
| `WorldStoreContractsTest` | androidTest | v24→v25 `app_install` 升级 + 种子 |
| `StoreInstallFlowSmokeTest` | androidTest | 安装 `storage` → Hub 出现 → Open → `StorageScreen` |
| `StorageHubSmokeTest` | androidTest | System Core Hub 直达存储详情（可选） |

---

## 8. 附录：8 App 一行摘要

| App | 实现程度 | 入口 | 迁移改动量 |
|-----|----------|------|------------|
| Y | 高（完整 Feed + AI） | Social Hub | **中** |
| Ustagram | 高（图文社交） | Social Hub + Settings | **中** |
| Rebbit | 高（论坛 + 子版块） | Social Hub + Settings | **中** |
| Binder | 中高（匹配 + Provider） | Social Hub | **小–中** |
| Storage | 中（真屏在 Settings） | System Core Hub + Settings | **小** |
| Aura Swap | 中高（MNN + 内嵌模型店） | Creative Suite | **大** |
| Games | 低（Hub + 6 占位） | Entertainment Hub | **小**（策略） |
| Phone | 低–中（联系人 → Messenger） | Social Hub | **小**（策略） |

---

*文档供 Issue #37 / Fancy Store 商店接入切片使用；实现时以 `docs/agents/store-install-model.md` 为权威状态机规格。*
