# Fancy Store 目录与商店 UI 完整清单（v4.50）

> 来源：`v4.50-github-release.apk`（解包路径 `/tmp/v450-check`，jadx 反编译 `/tmp/v450-jadx`）  
> 对照实现：本仓库 `app/src/main/java/io/github/lzyuuu/ailivesaver/`  
> 调研日期：2026-08-12

---

## 1. 完整产品表（`assets/fancy_store.json`）

**产品总数：17**（`app` × 14，`package` × 3）

| # | id | name | symbol | tagline | description | category | launchTarget | kind | builtIn | version | features | requirements | requiredDownloadBytes | featured | 当前 APP 实现状态 |
|---|-----|------|--------|---------|-------------|----------|--------------|------|---------|---------|----------|--------------|------------------------|----------|------------------|
| 1 | `y` | Y | Y | The character timeline | A fast social feed where your characters publish thoughts, reply to comments, and pull other characters into conversations with @mentions. | Social | `y` | app | true | 1.0 | Character posts; Threaded replies; Context-aware @mentions | — | — | — | **已实现**：`YScreen` |
| 2 | `ustagram` | Ustagram | ◎ | Their life, in pictures | An image-first social experience where characters share travel, fashion, hobbies, work, and ordinary moments from their lives. | Social | `ustagram` | app | true | 1.0 | Character photo posts; Comments and replies; Your image instructions | — | — | — | **已实现**：`UstagramAppScreen` |
| 3 | `rebbit` | Rebbit | r/ | Communities made personal | Characters discover communities that fit their personalities, publish image posts, and join discussions shaped by the communities you create. | Social | `rebbit` | app | true | 1.0 | Custom r/ communities; Mandatory image posts; Character discussions | — | — | — | **已实现**：`RebbitScreen` |
| 4 | `binder` | Binder | ♥︎ | Meet someone unexpected | A character matchmaker that creates complete, portrait-ready characters from the chemistry and energy you choose. | Characters | `binder` | app | true | 1.0 | Original character matches; Generated portraits; One-tap character import | — | — | — | **已实现**：`BinderScreen` |
| 5 | `games` | Games | ♜ | Seven games, every personality | Play adventures, duels, strategy, party games, and readings with any character. Their full character card and image preference stay active while you play. | Entertainment | `games` | app | true | 1.0 | Seven character-driven games; Shared JSON reply pipeline; Per-character game images | Fancy AI Pro | — | — | **部分实现**：`GamesHubScreen` 仅入口/列表，无玩法 |
| 6 | `phone` | Phone | ☎︎ | Call your characters | Have a private, foreground voice call with any character. Your speech becomes a character-aware reply and every answer is spoken back. | Entertainment | `phone` | app | true | 1.0 | Hands-free character calls; Live and saved transcripts; Device or installed voice engines | Fancy AI Pro | — | — | **部分实现**：`PhoneContactsScreen` 联系人列表，拨号跳转 Messenger，无真实语音通话 |
| 7 | `groups` | Groups | ⁂ | Everyone in one room | Put several characters and yourself in one chat. Mentions decide who answers, replies pull each other in, and every member keeps their own voice, memory, and pictures. | Social | `groups` | app | true | 1.0 | Multi-character rooms; @mention turn passing; Scene images in the flow | — | — | — | **部分实现**：Messenger 内群组会话（`ChatsScreen`），无独立 Groups 桌面 App |
| 8 | `root-creator` | Root Creator | ◇ | Describe them. Root writes the card. | Give Root a simple character idea. She creates the same SillyTavern-style card used everywhere else, then hands it to you to review, edit, and save. | Characters | `root_creator` | app | true | 1.0 | Simple idea input; JSON character-card writer; Review before saving | — | — | — | **未实现** |
| 9 | `aura` | Aura | ❋ | Create entirely on your device | Fancy AI's image studio for generating artwork locally with your selected image model and generation settings. | Creative | `aura` | app | true | 1.0 | On-device generation; Prompt controls; Gallery integration | A compatible image model | — | — | **部分实现**：`ImagingStudioScreen`（路由 `imaging`），功能近似但未以 Fancy Store / Aura 品牌与商店状态机呈现 |
| 10 | `aura-swap` | Aura Swap | ◐ | Put a face into a new moment | A private on-device face-swap studio with a simple source-and-target workflow and direct saving to your gallery. | Creative | `aura_swap` | app | true | 1.0 | On-device processing; Two-photo workflow; Gallery export | 536 MiB model package; 6 GB RAM or more | 561837652 (~536 MiB) | — | **部分实现**：`AuraSwapScreen` + 本地 MNN 管线；无 Fancy Store 下载/安装状态机 |
| 11 | `hd-upscalers` | HD Upscaler Pack | 4× | Photo and drawing enlargement | Installs both on-device Real-ESRGAN upscalers used by Aura's Enhance HD tool. Nothing downloads until you ask for this pack. | Packages | `aura` | package | — | 1.0 | Photo upscaler; Drawing upscaler; Works offline after installation | — | 42638892 (~41 MiB) | — | **未实现**（无商店包下载/检测） |
| 12 | `face-enhance` | Face Enhance Pack | ◉ | Focused face correction for Aura | Installs the on-device face parser Aura uses to find and redraw faces after generation. It is never downloaded silently. | Packages | `aura` | package | — | 1.0 | On-device face detection; Local face correction; Works offline after installation | — | 26328320 (~25 MiB) | — | **未实现** |
| 13 | `memory-pack` | Semantic Memory Pack | ◆ | Paraphrase-aware recall on device | Installs the tiny on-device MiniLM embedding model for semantic memory recall. Memory writing always uses your selected LLM, so this pack never loads a second conversational model. | Packages | `memory` | package | — | 1.0 | Paraphrase-aware memory recall; Tiny offline embedding model; No silent download | — | 45949216 (~44 MiB) | — | **未实现** |
| 14 | `benchmark` | Benchmark | ◈ | Measure your local brain | Run the app's built-in inference benchmark when you want real performance numbers from this device. | Tools | `benchmark` | app | true | 1.0 | On-device measurements; Backend comparison; No permanent launcher clutter | — | — | — | **未实现** |
| 15 | `storage` | Storage | ▰ | See what Fancy AI keeps | Browse and manage the files Fancy AI stores on your device without burying the tool in Settings. | Tools | `storage` | app | true | 1.0 | App storage browser; File import and export; Optional launcher tile | — | — | — | **部分实现**：`StorageScreen`（Settings）+ 桌面 `PlaceholderAppScreen` |
| 16 | `lorebook` | Lorebook | ▤ | World knowledge when you need it | Create and manage lore entries that characters can pull into conversation when their keys appear. | Tools | `lorebook` | app | true | 1.0 | Custom lore entries; Character assignment; Keyword activation | — | — | — | **部分实现**：`WorldKnowledgeScreen`（Settings 内世界知识），无独立 Lorebook App |
| 17 | `root-producer` | Root Producer | ♫ | Your idea. Root's studio. | Tell Root what your song should feel like. She writes a music plan and original lyrics for you to edit before Lyria creates the track. | Creative | `root_producer` | app | true | 1.0 | Root-written music plans and lyrics; 30-second clips or full songs; Private playback and Music-folder export | An OpenRouter API key; Paid preview generation | — | **true** | **未实现** |

### 分类汇总

| category | 数量 | 产品 id |
|----------|------|---------|
| Social | 4 | y, ustagram, rebbit, groups |
| Characters | 2 | binder, root-creator |
| Entertainment | 2 | games, phone |
| Creative | 3 | aura, aura-swap, root-producer |
| Tools | 3 | benchmark, storage, lorebook |
| Packages | 3 | hd-upscalers, face-enhance, memory-pack |

### 三个 Package 的 requirements 说明

三个 `kind=package` 产品在 JSON 中 **`requirements` 均为空数组**；约束通过 `description` / `features` / `requiredDownloadBytes` 表达：

| id | requirements（JSON） | 隐含要求（来自文案与代码） |
|----|---------------------|---------------------------|
| `hd-upscalers` | `[]` | 需用户主动从商店下载；安装后供 Aura Enhance HD 使用；检测路径 `files/upscalers/` |
| `face-enhance` | `[]` | 需用户主动下载；供 Aura 人脸修正；检测 `files/faceparser/face_parsing_resnet18.fp16.mnn`（26328320 字节） |
| `memory-pack` | `[]` | 需用户主动下载 MiniLM embedding；检测 `files/models/*minilm*`；缺失时错误串 `store_memory_missing` |

---

## 2. `store_*` UI 字符串清单（默认 locale `values`）

共 **35** 个 `store_*` 字符串资源（`aapt2 dump resources` 默认配置）。中文为释义，英文为 APK 默认值。

| 资源名 | 英文默认值 | 中文释义 | 用途 |
|--------|-----------|----------|------|
| `store_about` | About | 关于 | 产品详情页「关于」区块标题 |
| `store_all` | All | 全部 | 商店分类/筛选「全部」 |
| `store_cloud_download` | ☁ %1$s | ☁ %1$s（大小） | 卡片状态：需云端下载的包（%1$s 为 MiB 或 Included） |
| `store_delete_downloaded` | Delete downloaded package | 删除已下载的包 | 库页/详情：卸载已下载 package |
| `store_download_failed` | Download failed | 下载失败 | 下载错误提示 |
| `store_empty_library` | Apps and packages you add will appear here. | 你添加的应用和包会显示在这里。 | Library 空状态文案 |
| `store_explore` | Explore | 探索 | 商店浏览区标题（无搜索时） |
| `store_fancy_os` | FANCY OS | FANCY OS | 商店顶栏副标题（系统桌面语境） |
| `store_fancy_store` | FANCY STORE | FANCY STORE | 商店品牌副标题 |
| `store_featured` | FEATURED | 精选 | 精选产品区标签（如 Root Producer） |
| `store_included` | Included | 已内置 | 详情「Included」功能列表标题；零字节产品尺寸 |
| `store_label_add_home` | + Add to Home | + 添加到首页 | 主操作按钮：将 app 加入首页图标区 |
| `store_label_download_cloud` | ☁ Download · %1$s | ☁ 下载 · %1$s | 主操作按钮：下载 package / aura-swap 模型 |
| `store_label_downloading` | Downloading %1$d%% | 正在下载 %1$d%% | 主操作按钮：下载进行中 |
| `store_label_installed` | ✓ Installed | ✓ 已安装 | 主操作按钮：package 已安装态 |
| `store_label_open` | Open | 打开 | 主操作按钮：已添加首页或已安装 app 可直接打开 |
| `store_memory_missing` | Semantic Memory is missing from the catalogue. | 目录中缺少 Semantic Memory（组件）。 | memory-pack 下载时组件目录缺失错误 |
| `store_ok` | OK | 确定 | 对话框确认 |
| `store_product_meta` | Fancy AI · %1$s · %2$s | Fancy AI · %1$s · %2$s | 产品元信息行（分类 · 大小） |
| `store_remove_failed` | Couldn't remove %1$s. | 无法移除 %1$s。 | 从首页移除或删除包失败 |
| `store_remove_from_home` | Remove from Home | 从首页移除 | 库页操作：取消固定到首页 |
| `store_requirements` | Requirements | 要求 | 详情「Requirements」列表标题 |
| `store_results` | %1$d results | %1$d 条结果 | 搜索结果显示数量 |
| `store_search_label` | Search the Store | 搜索 Fancy Store | 搜索框无障碍/标签 |
| `store_size_format` | %1$d MiB | %1$d MiB | 下载包大小格式化 |
| `store_size_included` | Included | 已内置 | `requiredDownloadBytes <= 0` 时显示 |
| `store_status_added` | ADDED | 已添加 | 卡片角标：app 已在 `home_app_ids` |
| `store_status_downloading` | DOWNLOADING %1$d%% | 正在下载 %1$d%% | 卡片角标：下载中 |
| `store_status_installed` | INSTALLED | 已安装 | 卡片角标：package 已安装 |
| `store_status_plus` | + | + | 卡片角标：可添加到首页 |
| `store_tab_library` | Library | 库 | 底栏 Tab：已添加/已安装库 |
| `store_tab_store` | Store | 商店 | 底栏 Tab：浏览商店 |
| `store_title` | Fancy Store | Fancy Store | 页面主标题 |
| `store_unknown_package` | Unknown package: %1$s | 未知包：%1$s | 无法识别的 package id |
| `store_your_library` | Your Library | 你的库 | Library 页标题 |

### 相关 `home_app_*` 字符串（桌面图标标签，非 `store_` 前缀）

| 资源名 | 用途 |
|--------|------|
| `home_app_chat` | Messenger / Chat |
| `home_app_characters` | Characters |
| `home_app_gallery` | Gallery |
| `home_app_settings` | Settings |
| `home_app_store` | Fancy Store 入口 |
| `home_app_aura` | Aura |
| `home_app_y` / `home_app_ustagram` / `home_app_rebbit` / … | 各商店产品桌面图标文案 |

### ARSC 中存在但未在默认 `values` 输出的 `store_*` 键（仅在其他 locale 或配置中引用）

在 `resources.arsc` 二进制扫描中发现、但默认 dump 无英文值的键：`store_confirm_body`、`store_confirm_title`、`store_fidelity`、`store_note`、`store_owner`、`store_purchases`、`store_starters`。实现中未在默认路径引用，可能为预留或多语言条目。

---

## 3. 数据结构与状态机推断

### 3.1 `FancyStoreProduct` 数据类

路径：`com.mrj.fancyai.domain.store.FancyStoreProduct`

| 字段 | 类型 | JSON 键 | 说明 |
|------|------|---------|------|
| `id` | String | `id` | 产品唯一 id（小写规范化） |
| `name` | String | `name` | 显示名 |
| `symbol` | String | `symbol` | 卡片符号/图标字符 |
| `tagline` | String | `tagline` | 一句话卖点 |
| `description` | String | `description` | 长描述 |
| `category` | String | `category` | 分类（Social / Creative / …） |
| `launchTarget` | String | `launchTarget` | 启动路由/Activity 目标（如 `y`、`aura_swap`） |
| `kind` | String | `kind` | `app` 或 `package`，默认 `app` |
| `featured` | Boolean | `featured` | 是否精选展示 |
| `builtIn` | Boolean | `builtIn` | 是否内置（package 无此字段则为 false） |
| `version` | String | `version` | 版本号 |
| `features` | List\<String\> | `features` | 功能要点列表 |
| `requirements` | List\<String\> | `requirements` | 使用/requirements 文案列表 |
| `requiredDownloadBytes` | Long | `requiredDownloadBytes` | 需下载字节数，0 表示 Included |

### 3.2 `FancyStoreCatalog` 结构

```text
assets/fancy_store.json
└── products: FancyStoreProduct[]

解析类：
  FancyStoreCatalog$Document(products: List<FancyStoreProduct>?)
  FancyStoreCatalog.a.a(json) -> List<FancyStoreProduct>  // 归一化、去重、过滤空字段
```

加载流程（`com.mrj.fancyai.domain.store.a`）：
1. Gson 反序列化为 `Document`
2. trim 各字符串字段；`kind` 转小写，空则 `"app"`
3. 过滤 `requiredDownloadBytes < 0` 为 0
4. 丢弃 id/name/symbol/tagline/description/category/launchTarget 任一为空的条目
5. 按 id 小写去重

### 3.3 `home_app_ids` 用法

| 项目 | 说明 |
|------|------|
| 存储 | `SharedPreferences` 键 **`home_app_ids`**（`Set<String>`） |
| 读写 | `i65.v()` 读取；`i65.p0(id, add)` 增删（id 转小写） |
| 语义 | 用户已「添加到首页」的 **app 产品 id** 集合（非 package） |
| 默认首页 | `g82` 定义：`chat`、`characters`、`gallery`、`settings` 为默认 Dock；`store` 亦为首页可见 app |
| 可选 app | aura, y, ustagram, rebbit, binder, aura-swap, games, root-creator, benchmark, storage, lorebook, phone, groups, root-producer |
| UI 影响 | `zm1.a`（addedApps）来自 `home_app_ids`；角标 `ADDED` / `+`；按钮 `Open` vs `+ Add to Home` |

### 3.4 商店 ViewModel 状态（`zm1` / `dn1`）

```text
State(
  addedApps: Set<String>,        // = home_app_ids
  installedPackages: Set<String>, // 本地文件检测结果
  downloadingId: String?,          // 当前下载产品 id
  progress: Int,                 // 0..100
  error: String?,
  openTarget: String?            // 下载/添加后待打开 launchTarget
)
```

**`installedPackages` 检测逻辑**（`dn1.h()`）：

| 产品 id | 检测条件 |
|---------|----------|
| `hd-upscalers` | `files/upscalers/` 下 Real-ESRGAN 模型文件大小匹配 |
| `face-enhance` | `files/faceparser/face_parsing_resnet18.fp16.mnn` 长度 = 26328320 |
| `aura-swap` | `no0.j(fl1.a())`（faceswap 四模型是否齐全） |
| `memory-pack` | `files/models/` 下含 `minilm` 且非 `.part` 临时文件 |

**卡片状态文案**（`lv6.r0()`）优先级：

1. `downloadingId == product.id` → `store_status_downloading`
2. `kind == package` 且已安装 → `store_status_installed`
3. `kind == package` 未安装 → `store_cloud_download`
4. `id == aura-swap` 且模型未安装 → `store_cloud_download`
5. `addedApps` 含 id → `store_status_added`
6. 否则 → `store_status_plus`

**主按钮文案**（`vn1`）：

| 条件 | 字符串 |
|------|--------|
| 正在下载 | `store_label_downloading` |
| package 已安装 | `store_label_installed` |
| package 未安装 | `store_label_download_cloud` |
| app 已在首页 | `store_label_open` |
| aura-swap 已安装未在首页 | `store_label_open` / `store_label_add_home` |
| 其他 app | `store_label_add_home` |

**用户操作**：
- **Add to Home**：`i65.p0(product.id, true)` 写入 `home_app_ids`
- **Remove from Home**：`i65.p0(product.id, false)`
- **Download package**：`dn1.e(product, …)` 触发组件下载，`memory-pack` 走 `embed-minilm-l6-v2` 组件
- **Delete downloaded**：删除本地包文件并从 `installedPackages` 移除

---

## 4. 当前 APP 实现对照总览

### 4.1 按实现程度

| 状态 | 数量 | 产品 |
|------|------|------|
| **已实现（核心可用）** | 4 | y, ustagram, rebbit, binder |
| **部分实现** | 8 | games, phone, groups, aura, aura-swap, storage, lorebook |
| **未实现** | 5 | root-creator, benchmark, root-producer, hd-upscalers, face-enhance, memory-pack |

> 注：部分实现 = 有功能入口或子集，但缺少 Fancy Store 目录、首页添加状态、或参考 APP 完整体验。

### 4.2 平台能力缺口（相对 v4.50）

| 能力 | v4.50 | 当前 APP |
|------|-------|----------|
| Fancy Store UI（Store / Library 双 Tab） | ✅ | ❌ 无 `fancy_store.json`、无商店屏 |
| `home_app_ids` 持久化与首页动态图标 | ✅ | ❌ 桌面入口写死在 `DesktopApp` / Hub |
| Package 下载状态机（hd / face / memory） | ✅ | ❌ |
| aura-swap 商店式模型下载 | ✅ | ⚠️ 仅 AuraSwap 内嵌 Model Store tab |
| Root Creator / Root Producer | ✅ | ❌ |
| Benchmark | ✅ | ❌ |
| Games / Phone Pro 门闸 | ✅（requirements） | ❌ 本项目约定开放入口、无 Pro 墙 |

---

## 5. 其他发现（Assets 关联等）

### 5.1 APK `assets/` 与商店产品关联

| 路径 | 大小（约） | 关联产品 | 说明 |
|------|-----------|----------|------|
| `assets/fancy_store.json` | 9.9 KB | Fancy Store 全目录 | 商店唯一产品清单来源 |
| `assets/aura_sd15/` | ~5 MB | **aura**（间接） | 内置 SD1.5 管线：`clip_v2.mnn`、`tokenizer.json`、`unet.mnn`、`vae_decoder.mnn`、`vae_encoder.mnn`；**不在** `fancy_store.json` 中列为可下载包，属 Aura 本地生图基础资产 |
| `assets/root_portraits/root_avatar.webp` | 小 | Root 角色 | Root 内置头像，非商店产品 |
| `assets/qnn/`、`assets/dsp/` | 大 | 推理后端 | QNN / Hexagon DSP 库，与商店无直接 SKU 对应 |
| `assets/dexopt/` | 小 | — | 性能配置 |

**结论**：除 `fancy_store.json` 外，**没有**与 `hd-upscalers` / `face-enhance` / `memory-pack` / `aura-swap` 对应的预置包；这些均在首次使用时从组件目录**按需下载**。`aura_sd15` 是 Aura app 的内置轻量模型，与 Store 中的 package SKU 是不同层级的资产。

### 5.2 `launchTarget` 与桌面路由映射（参考 APP）

| launchTarget | 典型含义 |
|--------------|----------|
| `y` / `ustagram` / `rebbit` / `games` / `phone` / `groups` | 独立社交/娱乐 App |
| `binder` / `root_creator` / `root_producer` | 角色/创作工具 |
| `aura` / `aura_swap` | 创意工作室 |
| `benchmark` / `storage` / `lorebook` | 工具 App |
| `memory` | package 安装目标（语义记忆子系统，非桌面图标） |

### 5.3 精选与 Pro 要求

- **唯一 `featured: true`**：`root-producer`
- **`requirements` 含 Fancy AI Pro**：`games`、`phone`（参考 APP 用 Pro 门闸；本复刻项目 `CONTEXT.md` 约定开放入口）
- **`aura`**：要求兼容图像模型（可与 `assets/aura_sd15` 或用户下载模型配合）
- **`aura-swap`**：536 MiB 包 + 6 GB RAM；`requiredDownloadBytes = 561837652`

---

## 6. 关键结论摘要

| 指标 | 数值 |
|------|------|
| **产品总数** | **17** |
| **app** | **14** |
| **package** | **3**（hd-upscalers, face-enhance, memory-pack） |
| **三个 Pack 的 requirements 字段** | 均为 **`[]` 空**；实际约束见 description / 本地文件检测 |
| **当前 APP 已实现（核心）** | **4** / 17 |
| **部分实现** | **8** / 17 |
| **未实现** | **5** / 17（含 3 个 package + root-creator + benchmark + root-producer） |
| **最大缺口** | **Fancy Store 壳层**（双 Tab、搜索、Library）、**`home_app_ids` 首页编排**、**Package 下载生命周期** |

---

*本文件供 Issue #33 / Fancy Store 复刻切片使用。*
