# Fancy Store 安装状态机与数据模型

> **Issue**: [#36 Research: 商店安装状态机与数据模型](https://github.com/Lzyuuu/ai-livesaver/issues/36)  
> **Parent map**: [#32 v4.50 商店形态改造](https://github.com/Lzyuuu/ai-livesaver/issues/32)  
> **状态**: 设计稿（实现前评审）  
> **当前 `WORLD_DATABASE_VERSION`**: `24` → 建议升至 **`25`**

## 1. 背景与目标

v4.50 将 Fancy Store 作为独立 App / Pack 的唯一分发入口。目录来自 **assets 内置 `fancy_store.json`**（对齐参考 APK），用户侧安装状态与「添加到首页」偏好必须 **跨重启持久化**，并纳入 `world.db` 备份/恢复与擦除语义。

本设计回答：

1. `WorldStore` 新增表的形状与迁移方式  
2. App（本地解锁）与 Pack（真实下载）的状态机差异  
3. 目录 JSON 与 SQLite 的合并规则  
4. 与 `DesktopApp` / 导航的映射  
5. 测试与备份影响

### 1.1 已定产品约束（来自 map #32）

| 约束 | 决策 |
|------|------|
| 目录来源 | `assets/fancy_store.json` |
| 状态持久化 | SQLite（`world.db`） |
| App 安装语义 | 模拟下载动画 + 本地解锁（无网络） |
| Pack 安装语义 | 真实下载到 `filesDir`（对齐 `HfModelStore` / Aura 模型包） |
| 「添加到首页」 | Characters 顶栏图标行，可增删 |
| Pro 墙 | 不引入（`requirements` 仅展示，不阻断安装） |

### 1.2 参考目录快照（v4.50 `fancy_store.json`）

共 **17** 个 `products`：`14` 个 `kind=app` + `3` 个 `kind=package`。

| id | kind | launchTarget | builtIn | requiredDownloadBytes |
|----|------|--------------|---------|------------------------|
| y | app | y | true | — |
| ustagram | app | ustagram | true | — |
| rebbit | app | rebbit | true | — |
| binder | app | binder | true | — |
| games | app | games | true | — |
| phone | app | phone | true | — |
| groups | app | groups | true | — |
| root-creator | app | root_creator | true | — |
| aura | app | aura | true | — |
| aura-swap | app | aura_swap | true | 561837652 |
| benchmark | app | benchmark | true | — |
| storage | app | storage | true | — |
| lorebook | app | lorebook | true | — |
| root-producer | app | root_producer | true | — |
| hd-upscalers | package | aura | — | 42638892 |
| face-enhance | package | aura | — | 26328320 |
| memory-pack | package | memory | — | 45949216 |

`games` / `phone` 在参考 JSON 中带 `requirements: ["Fancy AI Pro"]`；本仓库 **不实现 Pro 墙**，UI 上按 map 标为 **即将开放（COMING_SOON）**，不可发起安装。

---

## 2. 现有代码基线（调研摘要）

### 2.1 `WorldStore` 模式

- 单库 `world.db`，版本常量 `WORLD_DATABASE_VERSION = 24`（`WorldStore.kt`）。
- **建表**：`onCreate` 调用领域 `create*Tables()`；`onUpgrade` 用 `if (oldVersion < N)` 链式迁移。
- **加列**：`addColumnIfMissing()` + `ALTER TABLE … ADD COLUMN`（v19–v20 等）。
- **新域表**：v24 的 `createSettingsDomainTables()`（`messenger_groups`、`binder_*`、`creative_assets`）—— **Fancy Store 应沿用同一模式**。
- **默认数据种子**：运行时方法（如 `ensureDefaultRebbitSubreddits()`），非 `onUpgrade` 内硬编码；商店种子建议在 `FancyStoreCatalog.ensureInstallDefaults()` 中按目录 + 升级策略执行。

### 2.2 `DesktopRoutes` / 导航

- `DesktopApp`：`route` / `label` / `openEntry`；`fromRoute(route)` 解析。
- 4 Tab 壳层对应：`Messenger`（Chat）、`Characters`、`Gallery`、`Settings` —— **不走商店**，视为始终可用。
- `MainActivity.openDesktopApp(app)` 设置 `desktopRouteKey = app.route`；`OPEN_DESKTOP_APP_EXTRA`（`"open_desktop_app"`）传入 route 字符串后 `DesktopApp.fromRoute` 打开。
- 商店安装完成后启动 App：**写入 `installed` 行 → `openDesktopApp(mappedDesktopApp)` 或发 Intent extra**。

### 2.3 `SharedPreferences`（`APP_PREFERENCES = "app_settings"`）

现有用途：欢迎引导、`root_appearance_id`、主题、语音等 **全局 UI/引导** 偏好。

| 数据 | 推荐存储 | 理由 |
|------|----------|------|
| 安装状态 / 安装时间 | **SQLite** | 与世界数据同生命周期；`WorldBackup` / `rebuildWorld` / `eraseAll` 已以 `world.db` 为准 |
| 「添加到首页」`on_home` | **SQLite** | 与角色/世界绑定；需随备份恢复；重建世界应清除 |
| 商店 UI 临时态（当前 Tab、搜索词） | Compose `rememberSaveable` 即可 | 无需持久化 |
| 目录版本戳（可选） | SQLite `store_meta` 或 assets 内 `catalogVersion` 字段 | 用于判断是否需要重跑种子，**不**存安装状态 |

**结论**：`on_home` 与安装状态 **不放** `SharedPreferences`；与 `WELCOME_GUIDE_COMPLETED_KEY` 等设备级设置分离。

### 2.4 测试覆盖

- **仪器测试** `WorldStoreContractsTest`：独立库名、`PRAGMA user_version` 模拟旧版、`v23→v24` 升级保留数据 —— 新表应增加 `v24UpgradeCreatesAppInstallTable` 同类用例。
- **JVM** `WorldBackupTest.supportsDatabaseVersion`：升级后需同步接受 version `25`。
- **烟雾测试** `WorldDataErasureSmokeTest`：删库后商店状态随 `world.db` 消失（无需额外断言 prefs）。
- 大量 `*SmokeTest` 直接 `WorldStore(context)` 默认库；新表 **无行 = 未安装**，需注意默认种子策略避免破坏「商店自装」验收。

---

## 3. 表结构建议

### 3.1 主表 `app_install`

存储用户对目录中 **每一个 product id** 的安装与首页偏好。App 与 Pack 共用一张表，用 `kind` 区分行为分支。

```sql
CREATE TABLE IF NOT EXISTS app_install (
    app_id TEXT PRIMARY KEY,              -- 对齐 fancy_store.json products[].id
    kind TEXT NOT NULL,                   -- 'app' | 'package'
    status TEXT NOT NULL,                 -- 见 §4 状态枚举
    installed_at INTEGER,                 -- epoch ms；未安装为 NULL
    on_home INTEGER NOT NULL DEFAULT 0,   -- 0/1；仅 kind=app 且 status=installed 时有效
    home_order INTEGER,                   -- 顶栏排序；NULL = 按 installed_at
    catalog_version TEXT,                 -- 写入时 assets 目录版本（可选，便于迁移对账）
    download_bytes_total INTEGER,         -- Pack：预期总字节；App 模拟安装可为 NULL
    download_bytes_done INTEGER NOT NULL DEFAULT 0,
    error TEXT NOT NULL DEFAULT '',       -- 失败原因（Pack 下载 / 校验）
    updated_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_app_install_status ON app_install(status);
CREATE INDEX IF NOT EXISTS idx_app_install_on_home ON app_install(on_home, home_order);
```

**命名说明**：issue 草案称 `app_install`；行内 `app_id` 对 **Pack** 同样使用 product id（如 `hd-upscalers`），避免 `package_install` 第二套表。

### 3.2 可选元数据表 `store_meta`（v25 可一并创建）

```sql
CREATE TABLE IF NOT EXISTS store_meta (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
);
-- 示例：('catalog_asset_hash', '<sha256 of fancy_store.json>')
-- 示例：('install_defaults_seeded', '25')
```

用于「仅执行一次」的升级种子，与 `rebbit_subreddits` 的 `ensure*` 模式一致。

### 3.3 Kotlin 模型（建议）

```kotlin
enum class StoreProductKind { App, Package }

enum class StoreInstallStatus {
    NotInstalled,   // 无行或显式 not_installed（推荐无行）
    Installing,     // App 模拟安装中
    Downloading,    // Pack 真实下载中
    Installed,
    Failed,         // Pack 下载/校验失败；App 一般不落库（动画失败可回滚为无行）
    // COMING_SOON 不存库，仅由目录 + 策略推导
}

data class StoreInstallRecord(
    val appId: String,
    val kind: StoreProductKind,
    val status: StoreInstallStatus,
    val installedAt: Long?,
    val onHome: Boolean,
    val homeOrder: Int?,
    val downloadBytesTotal: Long?,
    val downloadBytesDone: Long,
    val error: String,
    val updatedAt: Long,
)

/** 目录项 + 安装行合并后的 UI 模型 */
data class StoreProductView(
    val catalog: StoreCatalogProduct,
    val install: StoreInstallRecord?,      // null => NotInstalled
    val effectiveStatus: StoreEffectiveStatus,
)
```

---

## 4. 状态机

### 4.1 有效状态（UI / 合并层）

| 状态 | 来源 | 可执行动作 |
|------|------|------------|
| `COMING_SOON` | 目录策略（§5.3），**无 DB 行** | 仅查看详情 |
| `NOT_INSTALLED` | 无行或 `not_installed` | App：开始安装；Pack：开始下载 |
| `INSTALLING` | App：`status=installing` | 等待动画完成（本地事务提交） |
| `DOWNLOADING` | Pack：`status=downloading` | 进度更新；可取消 → `NOT_INSTALLED` |
| `INSTALLED` | `status=installed` | 打开 / 移除 / 切换 on_home |
| `FAILED` | Pack：`status=failed` | 重试下载 |

`COMING_SOON` **只读**，不写入 `app_install`。

### 4.2 App（本地解锁）状态机

```text
                    ┌─────────────────┐
                    │  COMING_SOON    │  （目录策略，不可安装）
                    └─────────────────┘

  (无行) NOT_INSTALLED
        │ beginInstall(appId)
        ▼
   INSTALLING  ──动画完成──►  INSTALLED
        │                        │
        │ 动画取消/失败           │ uninstall / remove
        └────────────────────────┴──► 删除行 → NOT_INSTALLED
```

**事务边界（`WorldStore.beginAppInstall` / `completeAppInstall`）**：

1. `beginAppInstall`：单事务 `INSERT OR REPLACE`，`status=installing`，`updated_at=now`。
2. UI 播放进度（无网络）；完成后 `completeAppInstall`：同事务 `status=installed`，`installed_at=now`。
3. 若进程在 `installing` 中杀死：**启动时** `recoverInterruptedInstalls()` 将孤立 `installing` 删行或标 `failed`（推荐 **删行**，用户可重试）。

### 4.3 Pack（真实下载）状态机

```text
  NOT_INSTALLED
        │ beginPackageDownload
        ▼
   DOWNLOADING ──进度──► DOWNLOADING …
        │ success + SHA 校验
        ▼
    INSTALLED
        │ fail
        ▼
     FAILED ──retry──► DOWNLOADING
```

- 字节字段：`download_bytes_total` ← `requiredDownloadBytes`；`download_bytes_done` 由下载器更新。
- 文件落盘路径 **不** 进 `app_install`（沿用 `filesDir/models/` 或 pack 专属目录）；表只记录 **逻辑已安装**。
- 校验策略对齐 `HfModelStore`（SHA-256）；校验失败 → `FAILED` + `error`。
- `aura-swap` 在参考目录中 `kind=app` 但带 `requiredDownloadBytes`：**实现上按 App 解锁 + 依赖 Pack/模型文件就绪**；可拆为「商店显示为 app，内部检查模型文件」—— v1 可简化为 app 安装后跳转 Aura Swap，由现有 Model Store 负责真实文件。

### 4.4 「添加到首页」(`on_home`)

- 仅 `kind=app` 且 `effectiveStatus=INSTALLED` 可设 `on_home=1`。
- `setOnHome(appId, enabled, order?)`：单事务更新；卸载时强制 `on_home=0`。
- 顶栏渲染：`SELECT * FROM app_install WHERE on_home=1 ORDER BY home_order, installed_at` → 映射 `DesktopApp` → 图标。
- **排序/上限**：留给 UI prototype #35；数据层预留 `home_order INTEGER`，默认 `NULL` 按安装时间。

---

## 5. `fancy_store.json` 加载与合并

### 5.1 资产与解析

```kotlin
// 建议：FancyStoreCatalog.kt
internal object FancyStoreCatalog {
    fun load(context: Context): StoreCatalog {
        val json = context.assets.open("fancy_store.json")
            .bufferedReader().use { it.readText() }
        return StoreCatalog.parse(JSONObject(json))
    }
}
```

- 解析字段：`id`, `name`, `symbol`, `tagline`, `description`, `category`, `launchTarget`, `kind`, `builtIn`, `version`, `features`, `requirements`, `requiredDownloadBytes`（可选）。
- **单元测试**：JVM 读取 `src/main/assets/fancy_store.json`（或 test assets）校验 schema 与 id 唯一性。

### 5.2 合并规则

```text
effectiveStatus(product) =
  if policy.isComingSoon(product)     → COMING_SOON
  else if install == null           → NOT_INSTALLED
  else map install.status           → INSTALLING | DOWNLOADING | INSTALLED | FAILED
```

UI 列表 = `catalog.products` 左连接 `app_install`（**不以 DB 行为目录来源**）。目录新增 product → 自动出现在商店；目录移除 id → UI 过滤，DB 遗留行可 `pruneOrphanInstalls(catalogIds)` 清理。

### 5.3 COMING_SOON 策略（map #32）

| product id | 有效状态 |
|------------|----------|
| games, phone | COMING_SOON |
| groups, root-creator, aura, root-producer, benchmark, lorebook | COMING_SOON |
| hd-upscalers, face-enhance, memory-pack | COMING_SOON（第一期 Pack 不真实开放） |
| y, ustagram, rebbit, binder, storage, aura-swap | 可安装（NOT_INSTALLED → …） |

策略集中在 `StoreAvailabilityPolicy`（纯 Kotlin），**不写入 DB**，便于改里程碑而不迁移。

### 5.4 与 `builtIn` 的关系

参考 JSON 中多数 app `builtIn: true` 表示「目录内置、可分发」，**不等于**用户已安装。  
4 Tab 壳层 App **不出现在** `fancy_store.json` 安装检查中。

---

## 6. 迁移策略（v24 → v25）

### 6.1 Schema 变更

```kotlin
internal const val WORLD_DATABASE_VERSION = 25

// onCreate: 在 createSettingsDomainTables 之后
createStoreInstallTables(database)

// onUpgrade:
if (oldVersion < 25) createStoreInstallTables(database)
```

`createStoreInstallTables` 使用 `CREATE TABLE IF NOT EXISTS`，与 v24 settings 域一致。

### 6.2 现有用户数据保留

- **不修改**既有 `characters` / `messages` / `social_*` 等表。
- `onUpgrade` **仅加表**；种子在首次打开商店或 `MainActivity` 启动时调用 `FancyStoreInstallDefaults.seedIfNeeded(store, catalog)`。

### 6.3 升级种子（Hub → Store 迁移）

对从 v4.47 Hub 升级的用户，避免「曾开放入口突然全锁」：

| 条件 | 种子行为 |
|------|----------|
| 新安装（`characters` 为空或首次 v25） | 不自动安装商店 App；仅壳层 4 Tab |
| 自 v24 升级且世界非空 | 对 **当前已有 `DesktopApp` 实现** 且 **非 COMING_SOON** 的 store id 写入 `installed`，`installed_at=迁移时间` |
| `rebuildWorld` | 删除 `world.db` → 商店状态清空 |
| `eraseAll` | 删库 + 清 `APP_PREFERENCES` → 商店状态清空 |

种子 id 列表（与 `DesktopApp` 有实现且第一期开放）：

`y`, `ustagram`, `rebbit`, `binder`, `storage`, `aura-swap`

**不**自动种子：`games`, `phone`, 无 `DesktopApp` 的 `groups`, `aura`（route 为 `imaging`）等。

### 6.4 备份兼容

- `WorldBackup.supportsDatabaseVersion` 上限改为 `25`。
- 恢复 v25 备份到 v25 应用：包含 `app_install` 行。
- 恢复旧备份（v24）：打开后 `onUpgrade` 建表 + 跑种子策略。

---

## 7. 与 `DesktopApp` / `DesktopHub` 映射

### 7.1 `launchTarget` → `DesktopApp`

| launchTarget (catalog) | DesktopApp | route | 第一期 |
|------------------------|------------|-------|--------|
| y | Y | y | 商店安装后打开 |
| ustagram | Ustagram | ustagram | ✓ |
| rebbit | Rebbit | rebbit | ✓ |
| binder | Binder | binder | ✓ |
| games | Games | games | COMING_SOON |
| phone | Phone | phone | COMING_SOON |
| aura | Imaging | imaging | COMING_SOON（Aura 品牌） |
| aura_swap | AuraSwap | aura_swap | ✓ |
| storage | Storage | storage | ✓ |
| groups | — | — | 未实现 |
| root_creator | — | — | 未实现 |
| benchmark | — | — | 未实现 |
| lorebook | — | — | 未实现 |
| root_producer | — | — | 未实现 |
| memory (package) | — | — | 未实现 |

建议新增：

```kotlin
fun StoreCatalogProduct.resolveDesktopApp(): DesktopApp? =
    when (launchTarget) {
        "aura" -> DesktopApp.Imaging
        else -> DesktopApp.fromRoute(launchTarget)
    }
```

### 7.2 安装后启动

```kotlin
// 商店内「打开」
fun openInstalledProduct(context: Context, product: StoreCatalogProduct) {
    val app = product.resolveDesktopApp() ?: return
    require(store.isInstalled(product.id)) { "not installed" }
    // 壳层内：
    openDesktopApp(app)
    // 或冷启动：
    // startActivity(intent.putExtra(OPEN_DESKTOP_APP_EXTRA, app.route))
}
```

`DesktopHub` / `DesktopDockApps` 在 v4.50 壳层中 **退役**；Hub 分组仅作历史参考，商店 category 字段（Social / Creative / …）替代 Hub 分组。

### 7.3 `openEntry` 与商店门控

第二期可在 `openDesktopApp` 增加：

```kotlin
if (productRequiresStoreInstall(app) && !store.isInstalled(app)) {
    // 导航到 Fancy Store 详情
    return
}
```

第一期仍可从 Characters 顶栏 / 商店进入；Hub 代码删除前保持 `openEntry=true` 以免测试断裂。

---

## 8. `WorldStore` API 草案

```kotlin
// 查询
fun getInstall(appId: String): StoreInstallRecord?
fun listInstalls(): List<StoreInstallRecord>
fun listHomeScreenApps(): List<StoreInstallRecord>
fun isInstalled(appId: String): Boolean

// App 安装
fun beginAppInstall(appId: String, kind: StoreProductKind, catalogVersion: String?)
fun completeAppInstall(appId: String, installedAt: Long = System.currentTimeMillis())
fun cancelAppInstall(appId: String)  // 删除 installing 行

// Pack 下载
fun beginPackageDownload(appId: String, totalBytes: Long)
fun updatePackageDownloadProgress(appId: String, bytesDone: Long)
fun completePackageInstall(appId: String)
fun failPackageInstall(appId: String, error: String)

// 首页
fun setOnHome(appId: String, onHome: Boolean, order: Int? = null)

// 维护
fun recoverInterruptedInstalls()
fun pruneOrphanInstalls(validIds: Set<String>)
```

所有写操作在 `writableDatabase` 事务内完成，与 `savePersistedMessengerGroup` / `confirmBinderCandidate` 风格一致。

---

## 9. 测试影响评估

| 区域 | 影响 | 建议 |
|------|------|------|
| `WorldStoreContractsTest` | 需 v24→v25 升级用例 | 复制 `v23UpgradePreservesExistingData…` 模式 |
| `WorldBackupTest` | version 上限 | `assertTrue(supportsDatabaseVersion(25))` |
| `DesktopRoutesTest` | 无 DB 依赖 | 增加 `launchTarget` 映射单测 |
| 新增 `FancyStoreCatalogTest` | JVM 解析 assets | schema + COMING_SOON 策略 |
| 新增 `StoreInstallStateMachineTest` | JVM 或 androidTest | installing→installed、失败恢复 |
| 现有 `*SmokeTest` | 默认空表 | 升级种子仅对世界非空触发；测试用独立 `databaseName` 不受影响 |
| `WorldDataErasureSmokeTest` | 无变更 | 删库即清除安装状态 |

---

## 10. 实现清单（供后续 ticket）

1. 复制 `v4.50` 的 `fancy_store.json` → `app/src/main/assets/`
2. `WORLD_DATABASE_VERSION = 25` + `createStoreInstallTables`
3. `FancyStoreCatalog` + `StoreAvailabilityPolicy`
4. `WorldStore` CRUD + `recoverInterruptedInstalls`
5. `FancyStoreInstallDefaults.seedIfNeeded`（升级路径）
6. `WorldBackup.supportsDatabaseVersion(25)`
7. 仪器测试：`v24UpgradeCreatesAppInstallTable`
8. 商店 UI（#35）与 4 Tab 壳层（#34）消费 `StoreProductView`

---

## 11. 核心决策摘要

| 项 | 决策 |
|----|------|
| **表名** | `app_install`（App + Pack 共用） |
| **主键** | `app_id` = `fancy_store.json` 的 `products[].id` |
| **状态枚举（DB）** | `installing` / `downloading` / `installed` / `failed`；无行 = 未安装 |
| **只读态** | `COMING_SOON` 由 `StoreAvailabilityPolicy` 推导，**不落库** |
| **on_home** | SQLite `on_home` + `home_order`，**不用** SharedPreferences |
| **目录** | assets `fancy_store.json` 只读；运行时 LEFT JOIN `app_install` |
| **迁移版本** | **25**（v24 仅 settings 域；v25 增加商店安装域） |
| **升级种子** | 非空世界自 v24 升级时，为已有 Desktop 实现的开放 App 写入 `installed` |
| **启动 App** | `launchTarget` → `DesktopApp.route` → `openDesktopApp` / `OPEN_DESKTOP_APP_EXTRA` |
