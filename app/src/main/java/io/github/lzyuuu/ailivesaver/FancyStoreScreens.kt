package io.github.lzyuuu.ailivesaver

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 商店屏：Store/你的库分段 + 搜索 + 三区列表 + 整页详情（对齐参考 ref-61/ref-64）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FancyStoreScreen(
    contentPadding: PaddingValues,
    store: WorldStore,
    onChanged: () -> Unit,
    onOpenApp: (DesktopApp) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val catalog = remember { StoreRepository(context) }
    var products by remember { mutableStateOf(catalog.loadProducts()) }
    var seg by remember { mutableStateOf("store") }
    var query by remember { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf("全部") }
    val storeCategories = remember(products) {
        listOf("全部") + products.map { it.category }.distinct()
    }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var installs by remember { mutableStateOf(catalog.loadInstallStatus(products)) }
    val downloading = remember { mutableStateListOf<String>() }
    val downloadProgress = remember { mutableStateMapOf<String, Int>() }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var firstVisit by remember { mutableStateOf(catalog.isFirstVisit()) }
    val scope = rememberCoroutineScope()

    fun refreshInstalls() {
        installs = catalog.loadInstallStatus(products)
    }

    fun beginDownload(id: String) {
        val product = products.firstOrNull { it.id == id } ?: return
        if (downloading.contains(id)) return
        downloading.add(id)
        downloadError = null
        store.saveAppInstall(
            PersistedAppInstall(id, InstallStatus.INSTALLING, null, false, 0, product.version.ifBlank { "1.0" }, System.currentTimeMillis()),
        )
        onChanged()
        // 真实下载：带官方 SHA-256 的资产逐项 HTTP 下载并校验（StoreDownloads）；
        // 纯应用（无下载资产）直接完成安装。失败回退 NOT_INSTALLED，绝不假装成功。
        Thread {
            var failure: Throwable? = null
            try {
                val items = storeDownloadItems(id)
                if (items.isNotEmpty()) {
                    StoreDownloads.downloadAll(context, items) { done, total ->
                        downloadProgress[id] = done * 100 / total
                    }
                }
            } catch (t: Throwable) {
                failure = t
            }
            val status = if (failure == null) InstallStatus.INSTALLED else InstallStatus.NOT_INSTALLED
            store.saveAppInstall(
                PersistedAppInstall(
                    id,
                    status,
                    if (failure == null) System.currentTimeMillis() else null,
                    false,
                    0,
                    product.version.ifBlank { "1.0" },
                    System.currentTimeMillis(),
                ),
            )
            val message = failure?.message?.let { "安装失败：$it" }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                downloading.remove(id)
                downloadProgress.remove(id)
                downloadError = message
                onChanged()
                refreshInstalls()
            }
        }.start()
    }

    val detailProduct = detailId?.let { id -> products.firstOrNull { it.id == id } }
    // 整页详情打开时，系统返回键只回商店列表（导航栈在 FancyStoreScreen 内部，不落到宿主）。
    BackHandler(enabled = detailProduct != null) { detailId = null }

    if (detailProduct != null) {
        val id = detailProduct.id
        StoreDetailPage(
            product = detailProduct,
            status = installs[id] ?: InstallStatus.NOT_INSTALLED,
            downloading = downloading.contains(id),
            progress = downloadProgress[id] ?: 0,
            onHome = catalog.isOnHome(id),
            contentPadding = contentPadding,
            onBack = { detailId = null },
            onGet = { beginDownload(id) },
            onOpen = {
                detailId = null
                onOpenApp(storeAppForProduct(detailProduct))
            },
            onAddToHome = {
                catalog.setOnHome(id, true)
                onChanged()
                refreshInstalls()
            },
            onRemoveFromHome = {
                catalog.removeFromHome(id)
                onChanged()
                refreshInstalls()
            },
            onUninstall = {
                // 卸载后停在详情页，状态机回落为「获取」，可立即重新安装。
                catalog.uninstallApp(id)
                onChanged()
                refreshInstalls()
            },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FancyNavy),
    ) {
        DesktopBackBar(onBack = onBack, title = "应用商店")

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 10.dp),
        ) {
            StoreSegment(
                label = "商店",
                selected = seg == "store",
                onClick = { seg = "store" },
                modifier = Modifier.weight(1f),
                testTag = "store-seg-store",
            )
            Spacer(modifier = Modifier.width(6.dp))
            StoreSegment(
                label = "你的库",
                selected = seg == "lib",
                onClick = { seg = "lib" },
                modifier = Modifier.weight(1f),
                testTag = "store-seg-lib",
            )
        }

        val filtered = products.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .testTag("store-list"),
            contentPadding = PaddingValues(top = 12.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            if (seg == "lib") {
                val installedProducts = installs.filter { it.value == InstallStatus.INSTALLED }.keys
                    .mapNotNull { id -> products.find { it.id == id } }
                if (installedProducts.isEmpty()) {
                    item {
                        if (firstVisit) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("store-lib-empty-guide")
                                    .padding(vertical = 30.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("⌂", color = FancyGold, fontSize = 30.sp)
                                Spacer(Modifier.height(10.dp))
                                Text("你的库还是空的", color = FancyCream, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "从商店安装应用或包后，它们会出现在这里。",
                                    color = FancyCream.copy(alpha = .72f),
                                    fontSize = 12.5.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                                Spacer(Modifier.height(14.dp))
                                Button(
                                    onClick = {
                                        firstVisit = false
                                        catalog.markFirstVisitSeen()
                                        seg = "store"
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = FancyGold,
                                        contentColor = FancyInk,
                                    ),
                                    modifier = Modifier.testTag("store-lib-go-browse"),
                                ) {
                                    Text("去逛逛商店", fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("store-lib-empty")
                                    .padding(vertical = 30.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("你安装的应用和包会显示在这里。", color = FancyCream.copy(alpha = .6f), fontSize = 12.5.sp)
                            }
                        }
                    }
                } else {
                    items(installedProducts) { p ->
                        StoreProductRow(
                            product = p,
                            status = InstallStatus.INSTALLED,
                            downloading = false,
                            onRowClick = { detailId = p.id },
                            onAction = { onOpenApp(storeAppForProduct(p)) },
                            actionLabel = "打开",
                        )
                    }
                    val homeProducts = installs.filter { (_, s) -> s == InstallStatus.INSTALLED }
                        .filterKeys { id -> catalog.isOnHome(id) }
                        .keys.mapNotNull { id -> products.find { it.id == id } }
                    if (homeProducts.isNotEmpty()) {
                        item {
                            StoreSectionLabel("已添加到首页 · ${homeProducts.size}")
                        }
                        items(homeProducts) { p ->
                            StoreProductRow(
                                product = p,
                                status = InstallStatus.INSTALLED,
                                downloading = false,
                                onRowClick = { detailId = p.id },
                                onAction = {
                                    catalog.removeFromHome(p.id)
                                    onChanged()
                                    refreshInstalls()
                                },
                                actionLabel = "移除",
                            )
                        }
                    }
                }
            } else {
                item {
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        textStyle = TextStyle(color = FancyCream, fontSize = 13.sp),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(13.dp))
                            .background(FancyNavyMid)
                            .border(1.dp, FancyGoldDim.copy(alpha = .4f), RoundedCornerShape(13.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("store-search"),
                        decorationBox = { inner ->
                            Box {
                                if (query.isEmpty()) Text("搜索 Fancy Store", color = FancyCream.copy(alpha = .5f), fontSize = 13.sp)
                                inner()
                            }
                        },
                    )
                }
                // 分类过滤行（参考 V4.51：全部/Social/Characters/… 横向滚动 chips）。
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        storeCategories.forEach { category ->
                            val selected = selectedCategory == category
                            FilterChip(
                                selected = selected,
                                onClick = { selectedCategory = category },
                                label = { Text(category) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = Color.Transparent,
                                    selectedContainerColor = FancyGold.copy(alpha = .18f),
                                    labelColor = FancyCream.copy(alpha = .75f),
                                    selectedLabelColor = FancyGold,
                                ),
                            )
                        }
                    }
                }
                val categoryFiltered = if (selectedCategory == "全部") {
                    filtered
                } else {
                    filtered.filter { it.category == selectedCategory }
                }
                // 精选位（参考：Root Producer 大卡）。
                val featured = categoryFiltered.firstOrNull { it.featured }
                if (featured != null) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { detailId = featured.id }
                                .testTag("store-featured"),
                            colors = CardDefaults.cardColors(
                                containerColor = FancyGold.copy(alpha = .14f),
                            ),
                        ) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("精选", color = FancyGold, fontSize = 11.sp)
                                Text(
                                    featured.name,
                                    color = Color.White,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(featured.tagline, color = FancyCream.copy(alpha = .7f), fontSize = 14.sp)
                            }
                        }
                    }
                }
                item { StoreSectionLabel("发现") }
                // 发现网格（参考：2 列卡片，全部可用态可获取）。
                val gridRows = categoryFiltered.chunked(2)
                gridRows.forEach { rowProducts ->
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            rowProducts.forEach { p ->
                                StoreGridTile(
                                    product = p,
                                    installed = (installs[p.id] ?: InstallStatus.NOT_INSTALLED) == InstallStatus.INSTALLED,
                                    downloading = downloading.contains(p.id),
                                    onClick = { detailId = p.id },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (rowProducts.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/**
 * 商店详情整页（对齐参考 ref-61/ref-64）：
 * 顶部返回栏（返回箭头 + 居中双行标题 + 分隔线）→ 金色英雄横幅 → 身份区
 * （tagline 大标题 + Fancy AI · 分类 · 状态元信息）→ 页面操作区 → 关于 → 已包含 → 要求。
 * 操作区状态机：下载中 / 已安装（未上主页：添加主页为主操作；已上主页：打开 + 从主页移除）
 * / 可获取 / 即将开放 / 即将推出。
 */
@Composable
private fun StoreDetailPage(
    product: StoreProduct,
    status: InstallStatus,
    downloading: Boolean,
    progress: Int,
    onHome: Boolean,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onGet: () -> Unit,
    onOpen: () -> Unit,
    onAddToHome: () -> Unit,
    onRemoveFromHome: () -> Unit,
    onUninstall: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FancyNavy)
            .testTag("store-detail-page"),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .testTag("store-detail-back"),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.store_detail_back),
                    tint = Color.White,
                )
            }
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.store_detail_eyebrow),
                    color = FancyCream.copy(alpha = .55f),
                    fontSize = 12.sp,
                    letterSpacing = 2.sp,
                )
                Text(
                    product.name,
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(FancyGoldDim.copy(alpha = .18f)),
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = contentPadding.calculateBottomPadding() + 24.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(FancyGold.copy(alpha = .26f), FancyGold.copy(alpha = .05f)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    product.symbol,
                    color = FancyGold,
                    fontSize = 72.sp,
                    fontFamily = FontFamily.Serif,
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                product.tagline,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            val metaStatus = when {
                downloading -> stringResource(R.string.store_detail_downloading, progress)
                status == InstallStatus.INSTALLED -> stringResource(R.string.store_detail_included)
                product.availability == StoreAvailability.COMING_SOON ->
                    stringResource(R.string.store_detail_coming_soon)
                product.availability == StoreAvailability.UPCOMING ->
                    stringResource(R.string.store_detail_upcoming)
                else -> stringResource(R.string.store_detail_meta_free)
            }
            Text(
                stringResource(R.string.store_detail_meta, product.category, metaStatus),
                color = FancyCream.copy(alpha = .6f),
                fontSize = 13.5.sp,
            )
            Spacer(Modifier.height(18.dp))
            StoreDetailActions(
                product = product,
                status = status,
                downloading = downloading,
                progress = progress,
                onHome = onHome,
                onGet = onGet,
                onOpen = onOpen,
                onAddToHome = onAddToHome,
                onRemoveFromHome = onRemoveFromHome,
                onUninstall = onUninstall,
            )

            StoreDetailSectionTitle(stringResource(R.string.store_detail_about))
            Text(
                product.description,
                color = FancyCream.copy(alpha = .72f),
                fontSize = 14.sp,
                lineHeight = 22.sp,
            )

            if (product.features.isNotEmpty()) {
                StoreDetailSectionTitle(stringResource(R.string.store_detail_included))
                product.features.forEach { feature ->
                    Row(
                        modifier = Modifier.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("✓", color = FancyGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(10.dp))
                        Text(feature, color = FancyCream.copy(alpha = .85f), fontSize = 14.sp)
                    }
                }
            }

            if (product.requirements.isNotEmpty()) {
                StoreDetailSectionTitle(stringResource(R.string.store_detail_requirements))
                product.requirements.forEach { requirement ->
                    Text(
                        "⚠  $requirement",
                        color = FancyCream.copy(alpha = .72f),
                        fontSize = 12.5.sp,
                        lineHeight = 19.sp,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StoreDetailSectionTitle(text: String) {
    Text(
        text,
        color = Color.White,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 24.dp, bottom = 10.dp),
    )
}

@Composable
private fun StoreDetailActions(
    product: StoreProduct,
    status: InstallStatus,
    downloading: Boolean,
    progress: Int,
    onHome: Boolean,
    onGet: () -> Unit,
    onOpen: () -> Unit,
    onAddToHome: () -> Unit,
    onRemoveFromHome: () -> Unit,
    onUninstall: () -> Unit,
) {
    val pillShape = RoundedCornerShape(50)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            downloading -> {
                OutlinedButton(
                    onClick = {},
                    enabled = false,
                    shape = pillShape,
                    border = BorderStroke(1.dp, FancyGoldDim.copy(alpha = .5f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        disabledContentColor = FancyGold,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("store-detail-downloading"),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = FancyGold,
                        strokeWidth = 2.5.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.store_detail_downloading, progress),
                        fontSize = 14.sp,
                    )
                }
            }
            status == InstallStatus.INSTALLED -> {
                if (onHome) {
                    StoreDetailPrimaryButton(
                        label = stringResource(R.string.store_detail_open),
                        onClick = onOpen,
                        testTag = "store-detail-open",
                        shape = pillShape,
                    )
                    StoreDetailSecondaryButton(
                        label = stringResource(R.string.store_detail_remove_home),
                        onClick = onRemoveFromHome,
                        testTag = "store-detail-remove-home",
                        shape = pillShape,
                    )
                } else {
                    // 参考 ref-61：已安装未上主页时，「添加到主页」为金色主操作。
                    StoreDetailPrimaryButton(
                        label = stringResource(R.string.store_detail_add_home),
                        onClick = onAddToHome,
                        testTag = "store-detail-add-home",
                        shape = pillShape,
                    )
                    StoreDetailSecondaryButton(
                        label = stringResource(R.string.store_detail_open),
                        onClick = onOpen,
                        testTag = "store-detail-open",
                        shape = pillShape,
                    )
                }
                TextButton(
                    onClick = onUninstall,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .testTag("store-detail-uninstall"),
                ) {
                    Text(
                        stringResource(R.string.store_detail_uninstall),
                        color = Color(0xFFE08A8A),
                        fontSize = 13.sp,
                    )
                }
            }
            product.availability == StoreAvailability.AVAILABLE -> {
                StoreDetailPrimaryButton(
                    label = if (product.requiredDownloadBytes > 0) {
                        stringResource(
                            R.string.store_detail_download_with_size,
                            product.requiredDownloadBytes / (1024 * 1024),
                        )
                    } else {
                        stringResource(R.string.store_detail_get)
                    },
                    onClick = onGet,
                    testTag = "store-detail-get",
                    shape = pillShape,
                )
            }
            else -> {
                val comingSoon = product.availability == StoreAvailability.COMING_SOON
                OutlinedButton(
                    onClick = {},
                    enabled = false,
                    shape = pillShape,
                    border = BorderStroke(1.dp, FancyCream.copy(alpha = .18f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        disabledContentColor = FancyCream.copy(alpha = .45f),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag(
                            if (comingSoon) "store-detail-coming-soon" else "store-detail-upcoming",
                        ),
                ) {
                    Text(
                        stringResource(
                            if (comingSoon) {
                                R.string.store_detail_coming_soon
                            } else {
                                R.string.store_detail_upcoming
                            },
                        ),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun StoreDetailPrimaryButton(
    label: String,
    onClick: () -> Unit,
    testTag: String,
    shape: RoundedCornerShape,
) {
    Button(
        onClick = onClick,
        shape = shape,
        colors = ButtonDefaults.buttonColors(containerColor = FancyGold, contentColor = FancyInk),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag(testTag),
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StoreDetailSecondaryButton(
    label: String,
    onClick: () -> Unit,
    testTag: String,
    shape: RoundedCornerShape,
) {
    OutlinedButton(
        onClick = onClick,
        shape = shape,
        border = BorderStroke(1.dp, FancyCream.copy(alpha = .28f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = FancyCream),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag(testTag),
    ) {
        Text(label, fontSize = 15.sp)
    }
}

@Composable
private fun StoreSegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(if (selected) FancyGold else FancyNavyMid)
            .border(1.dp, if (selected) FancyGold else FancyGoldDim.copy(alpha = .35f), RoundedCornerShape(13.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) FancyInk else FancyCream.copy(alpha = .7f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun StoreSectionLabel(text: String) {
    Text(
        text,
        color = FancyCream.copy(alpha = .6f),
        fontSize = 11.sp,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun StoreSymbol(symbol: String, size: Int = 46) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(FancyNavyMid)
            .border(1.dp, FancyGoldDim.copy(alpha = .4f), RoundedCornerShape(13.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, color = FancyGold, fontSize = (size * 0.42f).sp)
    }
}

@Composable
private fun StatusBadge(product: StoreProduct, status: InstallStatus, downloading: Boolean) {
    val (text, color) = when {
        downloading -> "下载中" to Color(0xFFA9C4EC)
        status == InstallStatus.INSTALLED -> "已安装" to Color(0xFF9CC79A)
        product.availability == StoreAvailability.COMING_SOON -> "即将开放" to Color(0xFFE08A8A)
        product.availability == StoreAvailability.UPCOMING -> "即将推出" to FancyCream.copy(alpha = .5f)
        else -> "" to Color.Transparent
    }
    if (text.isNotEmpty()) {
        Text(
            text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(color.copy(alpha = .14f))
                .padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun StoreGridTile(
    product: StoreProduct,
    installed: Boolean,
    downloading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("store-tile-${product.id}"),
        colors = CardDefaults.cardColors(containerColor = FancyNavyMid.copy(alpha = .8f)),
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(FancyGold.copy(alpha = .12f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    product.symbol,
                    color = FancyGold,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(product.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    product.tagline,
                    color = FancyCream.copy(alpha = .6f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    when {
                        downloading -> "获取中…"
                        installed -> "已安装"
                        else -> "+"
                    },
                    color = if (downloading) FancyCream.copy(alpha = .6f) else FancyGold,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun StoreProductRow(
    product: StoreProduct,
    status: InstallStatus,
    downloading: Boolean,
    onRowClick: () -> Unit = {},
    onAction: () -> Unit,
    actionLabel: String?,
    disabled: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(FancyNavyMid.copy(alpha = .55f))
            .border(1.dp, FancyGoldDim.copy(alpha = .25f), RoundedCornerShape(16.dp))
            .padding(12.dp)
            .testTag("store-row-${product.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onRowClick),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StoreSymbol(product.symbol)
                Spacer(Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(product.name, color = FancyCream, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        StatusBadge(product, status, downloading)
                    }
                    Text(
                        product.tagline,
                        color = FancyCream.copy(alpha = .65f),
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        buildString {
                            append(storeCategoryLabel(product.category))
                            if (product.requiredDownloadBytes > 0) append(" · ${product.requiredDownloadBytes / (1024 * 1024)} MiB")
                        },
                        color = FancyCream.copy(alpha = .45f),
                        fontSize = 10.sp,
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        when {
            downloading -> {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("store-row-${product.id}-downloading")) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = FancyGold, strokeWidth = 2.5.dp)
                }
            }
            disabled -> {
                Text(
                    actionLabel ?: "即将推出",
                    color = FancyCream.copy(alpha = .45f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("store-row-${product.id}-disabled"),
                )
            }
            status == InstallStatus.INSTALLED -> {
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(containerColor = FancyNavyMid, contentColor = FancyCream),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
                    modifier = Modifier.testTag("store-row-${product.id}-open"),
                ) {
                    Text(actionLabel ?: "打开", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            else -> {
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(containerColor = FancyGold, contentColor = FancyInk),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
                    modifier = Modifier.testTag("store-row-${product.id}-get"),
                ) {
                    Text(actionLabel ?: "获取", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun storeCategoryLabel(category: String): String = when (category) {
    "Social" -> "社交"
    "Characters" -> "角色"
    "Creative" -> "创作"
    "Entertainment" -> "娱乐"
    "Tools" -> "工具"
    "Packages" -> "包"
    else -> category
}

internal fun storeAppForProduct(product: StoreProduct): DesktopApp =
    DesktopApp.fromRoute(product.launchTarget) ?: DesktopApp.Settings

/** 商店数据仓库：目录资产加载 + 安装态持久化。 */
internal class StoreRepository(private val context: android.content.Context) {

    fun loadProducts(): List<StoreProduct> =
        FancyStoreCatalogParser.parse(
            context.assets.open("fancy_store.json").bufferedReader().use { it.readText() },
        )

    fun loadInstallStatus(products: List<StoreProduct>): Map<String, InstallStatus> {
        val map = mutableMapOf<String, InstallStatus>()
        WorldStore(context).use { store ->
            products.forEach { p ->
                map[p.id] = store.loadAppInstall(p.id)?.status ?: InstallStatus.NOT_INSTALLED
            }
        }
        return map
    }

    fun isOnHome(appId: String): Boolean {
        WorldStore(context).use { store ->
            return store.loadAppInstall(appId)?.onHome ?: false
        }
    }

    fun setOnHome(appId: String, onHome: Boolean) {
        if (onHome) {
            addToHome(appId)
        } else {
            removeFromHome(appId)
        }
    }

    fun addToHome(appId: String) {
        WorldStore(context).use { store ->
            val existing = store.loadAppInstall(appId) ?: return
            val nextOrder = (store.loadHomeApps().maxOfOrNull { it.homeOrder } ?: 0) + 1
            store.saveAppInstall(
                existing.copy(
                    onHome = true,
                    homeOrder = nextOrder,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun removeFromHome(appId: String) {
        WorldStore(context).use { store ->
            val existing = store.loadAppInstall(appId) ?: return
            store.saveAppInstall(
                existing.copy(
                    onHome = false,
                    homeOrder = 0,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun uninstallApp(appId: String) {
        WorldStore(context).use { store ->
            store.deleteAppInstall(appId)
        }
    }

    fun isFirstVisit(): Boolean =
        context.getSharedPreferences(APP_PREFERENCES, android.content.Context.MODE_PRIVATE)
            .getBoolean("store_first_visit_seen", false).not()

    fun markFirstVisitSeen() {
        context.getSharedPreferences(APP_PREFERENCES, android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("store_first_visit_seen", true).apply()
    }
}
