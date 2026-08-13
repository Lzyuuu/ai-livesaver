package io.github.lzyuuu.ailivesaver

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 商店屏：Store/你的库分段 + 搜索 + 三区列表 + 底部弹层详情（对齐原型 C 变体）。 */
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
    var detailId by remember { mutableStateOf<String?>(null) }
    var installs by remember { mutableStateOf(catalog.loadInstallStatus(products)) }
    val downloading = remember { mutableStateListOf<String>() }
    var firstVisit by remember { mutableStateOf(catalog.isFirstVisit()) }
    val scope = rememberCoroutineScope()

    fun refreshInstalls() {
        installs = catalog.loadInstallStatus(products)
    }

    fun beginDownload(id: String) {
        if (downloading.contains(id)) return
        downloading.add(id)
        store.saveAppInstall(
            PersistedAppInstall(id, InstallStatus.INSTALLING, null, false, 0, "1.0", System.currentTimeMillis()),
        )
        onChanged()
        scope.launch {
            var progress = 0
            while (progress < 100) {
                delay(300)
                progress += 20
            }
            downloading.remove(id)
            store.saveAppInstall(
                PersistedAppInstall(id, InstallStatus.INSTALLED, System.currentTimeMillis(), false, 0, "1.0", System.currentTimeMillis()),
            )
            onChanged()
            refreshInstalls()
        }
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
                                    catalog.setOnHome(p.id, false)
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

                val available = filtered.filter { it.availability == StoreAvailability.AVAILABLE }
                val soon = filtered.filter { it.availability == StoreAvailability.COMING_SOON }
                val upcoming = filtered.filter { it.availability == StoreAvailability.UPCOMING }

                if (available.isNotEmpty()) {
                    item { StoreSectionLabel("可获取 · ${available.size}") }
                    items(available) { p ->
                        val status = installs[p.id] ?: InstallStatus.NOT_INSTALLED
                        StoreProductRow(
                            product = p,
                            status = status,
                            downloading = downloading.contains(p.id),
                            onRowClick = { detailId = p.id },
                            onAction = {
                                when {
                                    downloading.contains(p.id) -> Unit
                                    status == InstallStatus.INSTALLED -> onOpenApp(storeAppForProduct(p))
                                    else -> beginDownload(p.id)
                                }
                            },
                            actionLabel = if (status == InstallStatus.INSTALLED) "打开" else null,
                        )
                    }
                }
                if (soon.isNotEmpty()) {
                    item { StoreSectionLabel("即将开放 · ${soon.size}") }
                    items(soon) { p ->
                        StoreProductRow(
                            product = p,
                            status = InstallStatus.NOT_INSTALLED,
                            downloading = false,
                            onRowClick = { detailId = p.id },
                            onAction = {},
                            actionLabel = "即将开放",
                            disabled = true,
                        )
                    }
                }
                if (upcoming.isNotEmpty()) {
                    item { StoreSectionLabel("即将推出 · ${upcoming.size}") }
                    items(upcoming) { p ->
                        StoreProductRow(
                            product = p,
                            status = InstallStatus.NOT_INSTALLED,
                            downloading = false,
                            onRowClick = { detailId = p.id },
                            onAction = {},
                            actionLabel = "即将推出",
                            disabled = true,
                        )
                    }
                }
            }
        }
    }

    detailId?.let { id ->
        val product = products.firstOrNull { it.id == id } ?: return@let
        val status = installs[id] ?: InstallStatus.NOT_INSTALLED
        ModalBottomSheet(
            onDismissRequest = { detailId = null },
            containerColor = FancyNavyMid,
            modifier = Modifier.testTag("store-detail-sheet"),
        ) {
            Column(modifier = Modifier.padding(horizontal = 18.dp).padding(bottom = 24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StoreSymbol(product.symbol, size = 64)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(product.name, color = FancyCream, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(product.tagline, color = FancyCream.copy(alpha = .72f), fontSize = 12.5.sp)
                        Spacer(Modifier.height(4.dp))
                        StatusBadge(product, status, downloading.contains(id))
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(product.description, color = FancyCream.copy(alpha = .72f), fontSize = 13.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(14.dp))
                StoreSectionLabel("功能")
                product.features.forEach { f ->
                    Text("•  $f", color = FancyCream, fontSize = 12.5.sp, lineHeight = 20.sp)
                }
                if (product.requirements.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    StoreSectionLabel("要求")
                    product.requirements.forEach { r ->
                        Text("⚠  $r", color = FancyCream.copy(alpha = .72f), fontSize = 12.sp, lineHeight = 19.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    when {
                        downloading.contains(id) -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = FancyGold,
                                    strokeWidth = 2.5.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("下载中…", color = FancyGold, fontSize = 13.sp)
                            }
                        }
                        status == InstallStatus.INSTALLED -> {
                            Button(
                                onClick = { detailId = null; onOpenApp(storeAppForProduct(product)) },
                                colors = ButtonDefaults.buttonColors(containerColor = FancyGold, contentColor = FancyInk),
                                modifier = Modifier.testTag("store-detail-open"),
                            ) {
                                Text("打开", fontWeight = FontWeight.Bold)
                            }
                            if (!catalog.isOnHome(id)) {
                                Button(
                                    onClick = {
                                        catalog.setOnHome(id, true)
                                        onChanged()
                                        refreshInstalls()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = FancyGold, contentColor = FancyInk),
                                    modifier = Modifier.testTag("store-detail-add-home"),
                                ) {
                                    Text("＋ 添加到首页", fontWeight = FontWeight.Bold)
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    store.deleteAppInstall(id)
                                    onChanged()
                                    refreshInstalls()
                                    detailId = null
                                },
                                border = androidx.compose.foundation.BorderStroke(1.dp, FancyGoldDim),
                                modifier = Modifier.testTag("store-detail-uninstall"),
                            ) {
                                Text("卸载", color = FancyGold, fontSize = 12.sp)
                            }
                        }
                        product.availability == StoreAvailability.AVAILABLE -> {
                            Button(
                                onClick = { beginDownload(id) },
                                colors = ButtonDefaults.buttonColors(containerColor = FancyGold, contentColor = FancyInk),
                                modifier = Modifier.testTag("store-detail-get"),
                            ) {
                                val label = if (product.requiredDownloadBytes > 0) {
                                    "☁ 下载 · ${product.requiredDownloadBytes / (1024 * 1024)} MiB"
                                } else {
                                    "获取"
                                }
                                Text(label, fontWeight = FontWeight.Bold)
                            }
                        }
                        else -> {
                            Text(
                                if (product.availability == StoreAvailability.COMING_SOON) "即将开放" else "即将推出",
                                color = FancyCream.copy(alpha = .45f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
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
        WorldStore(context).use { store ->
            val existing = store.loadAppInstall(appId)
            val nextOrder = if (onHome) (store.loadHomeApps().maxOfOrNull { it.homeOrder } ?: 0) + 1 else 0
            store.saveAppInstall(
                PersistedAppInstall(
                    appId = appId,
                    status = existing?.status ?: InstallStatus.NOT_INSTALLED,
                    installedAt = existing?.installedAt,
                    onHome = onHome,
                    homeOrder = nextOrder,
                    catalogVersion = existing?.catalogVersion ?: "1.0",
                    updatedAt = System.currentTimeMillis(),
                ),
            )
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
