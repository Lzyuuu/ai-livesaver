package io.github.lzyuuu.ailivesaver

import androidx.activity.ComponentActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random
import kotlinx.coroutines.launch

private const val DESKTOP_PAGE_HOME = 0
private const val DESKTOP_PAGE_APPS = 1
private const val DESKTOP_PAGE_COUNT = 2

@Composable
internal fun SystemDesktopScreen(
    contentPadding: PaddingValues,
    rootName: String = DesktopSeed.ROOT_NAME,
    rootAppearance: RootAppearance = RootAppearance.AnimeBlonde,
    rootStatus: String,
    rootActionLabel: String,
    onOpenRoot: () -> Unit,
    onOpenHub: (DesktopHub) -> Unit,
    onOpenApp: (DesktopApp) -> Unit,
    onRemoveFromHome: (String) -> Unit = {},
    onUninstallHomeApp: (String) -> Unit = {},
    homeApps: List<DesktopApp> = emptyList(),
    homeGridApps: List<DesktopHomeGridEntry> = emptyList(),
    allGridApps: List<DesktopHomeGridEntry> = emptyList(),
) {
    val now = remember { Date() }
    val time = remember(now) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
    }
    val date = remember(now) {
        SimpleDateFormat("EEEE, MMMM d", Locale.US).format(now).uppercase(Locale.US)
    }

    val dockBottomPadding = 88.dp + contentPadding.calculateBottomPadding()
    // DT-02：pager 页状态只用 remember（刻意不用 rememberSaveable）——每次返回桌面都是
    // 新的 SystemDesktopScreen 组合，恒定落回第一页主页，不会停留在第二页应用网格。
    val pagerState = rememberPagerState(initialPage = DESKTOP_PAGE_HOME) { DESKTOP_PAGE_COUNT }
    val pagerScope = rememberCoroutineScope()
    val homeRoutes = remember(homeGridApps) {
        homeGridApps.mapTo(HashSet()) { it.app.route }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1A1D1E), Color(0xFF090E14), Color(0xFF080A0D)),
                ),
            )
            .padding(contentPadding)
            .testTag("system-desktop"),
    ) {
        // DT-02：真正的两页 HorizontalPager——第一页主页（时钟/日期/Root 卡），
        // 第二页完整应用网格；Dock 与页码指示器固定在 pager 之外，两页均可见。
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("desktop-pager"),
        ) { page ->
            when (page) {
                DESKTOP_PAGE_HOME -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = 14.dp,
                            end = 14.dp,
                            top = 44.dp,
                            bottom = dockBottomPadding + 30.dp,
                        )
                        .testTag("desktop-page-home"),
                ) {
                    Text(
                        time,
                        color = FancyCream,
                        fontFamily = FontFamily.Serif,
                        fontSize = 78.sp,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    Text(
                        date,
                        color = Color(0xFFB9BFCA),
                        fontSize = 11.sp,
                        letterSpacing = 1.8.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1BE8C0)),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "FANCY OS · 开放入口",
                            color = FancyGold,
                            fontSize = 10.sp,
                            letterSpacing = .8.sp,
                        )
                    }
                    Spacer(modifier = Modifier.height(48.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .border(1.dp, FancyGold.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF20201B), Color(0xFF151715)),
                                ),
                            )
                            .clickable(onClick = onOpenRoot)
                            .padding(horizontal = 14.dp, vertical = 16.dp)
                            .testTag("desktop-root-card"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RootPortrait(
                            appearance = rootAppearance,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .border(1.dp, Color(0xFF2D7C78), CircleShape),
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                rootName,
                                color = FancyCream,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 21.sp,
                            )
                            Text(
                                rootStatus,
                                color = Color(0xFFBEC2CB),
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(rootActionLabel, color = FancyGold, fontWeight = FontWeight.SemiBold)
                        }
                        Box(
                            modifier = Modifier
                                .size(width = 28.dp, height = 22.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(Color(0xFF2B2A20)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = FancyGold,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                }
                else -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("desktop-page-apps"),
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("desktop-app-grid"),
                        contentPadding = PaddingValues(
                            start = 14.dp,
                            end = 14.dp,
                            top = 44.dp,
                            bottom = dockBottomPadding + 30.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(allGridApps, key = { it.app.route }) { entry ->
                            DesktopGridTile(
                                entry = entry,
                                // 首页网格项保留 desktop-grid-* 标签（点击/长按/门控语义不变）；
                                // 其余可达应用用 desktop-apps-* 标签，避免与首页网格混淆。
                                testTag = if (entry.app.route in homeRoutes) {
                                    entry.testTag
                                } else {
                                    "desktop-apps-${entry.app.route}"
                                },
                                onClick = { onOpenApp(entry.app) },
                                onRemoveFromHome = onRemoveFromHome,
                                onUninstallHomeApp = onUninstallHomeApp,
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.testTag("desktop-page-indicator"),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(pagerState.pageCount) { index ->
                    val selected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .testTag("desktop-page-dot-$index")
                            .clickable {
                                pagerScope.launch { pagerState.animateScrollToPage(index) }
                            }
                            .padding(6.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) {
                                    FancyGold
                                } else {
                                    Color(0xFFB9BFCA).copy(alpha = .35f)
                                },
                            ),
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .border(1.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(26.dp))
                    .background(Color(0xFF202326).copy(alpha = 0.94f))
                    .padding(horizontal = 8.dp, vertical = 12.dp)
                    .testTag("desktop-dock"),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                DesktopNavigator.composeDock().forEach { app ->
                    DockIcon(app = app, onClick = { onOpenApp(app) })
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DesktopGridTile(
    entry: DesktopHomeGridEntry,
    onClick: () -> Unit,
    onRemoveFromHome: (String) -> Unit,
    onUninstallHomeApp: (String) -> Unit,
    testTag: String = entry.testTag,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var showUninstallConfirm by remember { mutableStateOf(false) }
    val productId = entry.productId

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (entry.supportsLongPressMenu && productId != null) {
                    Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = { menuOpen = true },
                    )
                } else {
                    Modifier.clickable(onClick = onClick)
                },
            )
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            DesktopGridIcon(entry = entry)
            if (entry.supportsLongPressMenu && productId != null) {
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    modifier = Modifier.testTag("desktop-grid-menu-${entry.app.route}"),
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.desktop_grid_menu_open)) },
                        onClick = {
                            menuOpen = false
                            onClick()
                        },
                        modifier = Modifier.testTag("desktop-grid-menu-open-${entry.app.route}"),
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.desktop_grid_menu_remove_from_home)) },
                        onClick = {
                            menuOpen = false
                            onRemoveFromHome(productId)
                        },
                        modifier = Modifier.testTag("desktop-grid-menu-remove-${entry.app.route}"),
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.desktop_grid_menu_uninstall)) },
                        onClick = {
                            menuOpen = false
                            showUninstallConfirm = true
                        },
                        modifier = Modifier.testTag("desktop-grid-menu-uninstall-${entry.app.route}"),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            entry.label,
            color = FancyCream,
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            lineHeight = 13.sp,
        )
    }

    if (showUninstallConfirm && productId != null) {
        AlertDialog(
            onDismissRequest = { showUninstallConfirm = false },
            title = { Text(stringResource(R.string.desktop_grid_uninstall_title, entry.label)) },
            text = { Text(stringResource(R.string.desktop_grid_uninstall_message, entry.label)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUninstallConfirm = false
                        onUninstallHomeApp(productId)
                    },
                    modifier = Modifier.testTag("desktop-grid-uninstall-confirm-${entry.app.route}"),
                ) {
                    Text(stringResource(R.string.desktop_grid_menu_uninstall))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showUninstallConfirm = false },
                    modifier = Modifier.testTag("desktop-grid-uninstall-cancel-${entry.app.route}"),
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
            modifier = Modifier.testTag("desktop-grid-uninstall-dialog-${entry.app.route}"),
        )
    }
}

@Composable
private fun DesktopGridIcon(entry: DesktopHomeGridEntry) {
    Box(
        modifier = Modifier
            .size(58.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Color.White.copy(alpha = .16f), RoundedCornerShape(16.dp))
            .background(Color(0xFF292B2E)),
        contentAlignment = Alignment.Center,
    ) {
        if (entry.symbol.isNullOrBlank()) {
            Icon(
                iconFor(entry.app),
                contentDescription = entry.label,
                tint = FancyCream,
                modifier = Modifier.size(26.dp),
            )
        } else {
            Text(
                entry.symbol,
                color = FancyGold,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun HubFolder(
    hub: DesktopHub,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .testTag("desktop-hub-${hub.route}"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, Color.White.copy(alpha = .18f), RoundedCornerShape(24.dp))
                .background(Color(0xFF1B1E22))
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                List(4) { hub.apps[it % hub.apps.size] }.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(Color(0xFF34383C)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = iconFor(it),
                                    contentDescription = null,
                                    tint = hubTint(hub),
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            hub.label,
            color = FancyCream,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = if (
                hub == DesktopHub.CreativeSuite || hub == DesktopHub.Entertainment
            ) {
                Modifier.width(74.dp)
            } else {
                Modifier.fillMaxWidth()
            },
        )
    }
}

@Composable
private fun DockIcon(app: DesktopApp, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag("desktop-dock-${app.route}"),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, Color.White.copy(alpha = .12f), RoundedCornerShape(14.dp))
                .background(Color(0xFF2A2D30)),
            contentAlignment = Alignment.Center,
        ) {
            DockGlyph(app)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            app.label,
            color = Color(0xFFBEC4CF),
            fontSize = 11.sp,
            letterSpacing = .8.sp,
        )
    }
}

@Composable
internal fun DesktopHubSheet(
    hub: DesktopHub,
    onOpenApp: (DesktopApp) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f))
            .clickable(onClick = onDismiss)
            .testTag("desktop-hub-sheet"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF1B1C20))
                .border(1.dp, Color.White.copy(alpha = .17f), RoundedCornerShape(22.dp))
                .clickable(enabled = false) {}
                .padding(horizontal = 16.dp, vertical = 18.dp),
        ) {
            Text(
                hub.label,
                color = FancyCream,
                fontFamily = FontFamily.Serif,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(modifier = Modifier.height(18.dp))
            hub.apps.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    row.forEach { app ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .padding(8.dp)
                                .clickable { onOpenApp(app) }
                                .testTag("hub-app-${app.route}"),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .border(1.dp, Color.White.copy(alpha = .16f), RoundedCornerShape(22.dp))
                                    .background(Color(0xFF292B2E)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(iconFor(app), null, tint = FancyCream, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(app.label, color = FancyCream, fontSize = 12.sp)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .testTag("desktop-hub-close"),
            ) {
                Text(stringResource(R.string.desktop_hub_close), color = FancyGold)
            }
        }
    }
}

@Composable
internal fun DesktopBackBar(
    onBack: () -> Unit,
    title: String,
    useThemeColors: Boolean = false,
) {
    val backgroundColor = if (useThemeColors) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        FancyNavy
    }
    val accentColor = if (useThemeColors) {
        MaterialTheme.colorScheme.primary
    } else {
        FancyGold
    }
    val titleColor = if (useThemeColors) {
        MaterialTheme.colorScheme.onSurface
    } else {
        FancyCream
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onBack,
            modifier = Modifier.testTag("desktop-back-bar"),
        ) {
            Text(stringResource(R.string.desktop_back_to_home), color = accentColor)
        }
        Text(
            title,
            color = titleColor,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
internal fun PlaceholderAppScreen(
    title: String,
    summary: String,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    extra: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FancyInk),
    ) {
        DesktopBackBar(onBack = onBack, title = title)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = contentPadding.calculateBottomPadding())
                .testTag("placeholder-app"),
        ) {
        Text(
            summary,
            color = FancyCream.copy(alpha = 0.75f),
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        extra?.invoke()
        }
    }
}

@Composable
internal fun PhoneContactsScreen(
    characters: List<ResidentCharacter>,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onCall: (Long) -> Unit,
) {
    // 对齐参考 ref-75：eyebrow「电话」+「通话」大标题、金色「联系人」节、
    // 头像 + 名字 + 在线绿点的联系人行（整行点击拨号）。
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ReferencePalette.PageBg)
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
            )
            .testTag("phone-screen"),
    ) {
        Box(Modifier.fillMaxWidth()) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 4.dp)
                    .testTag("phone-back"),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.desktop_back_to_home),
                    tint = Color.White,
                )
            }
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "电话",
                    color = FancyCream.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    letterSpacing = 2.sp,
                )
                Text(
                    "通话",
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
                .background(ReferencePalette.Hairline),
        )
        Text(
            "联系人",
            color = FancyGold,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(characters, key = { it.id }) { character ->
                val avatarPath = remember(character.id, character.cardJson) {
                    CharacterCardV2.profileFields(character).avatarPath
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onCall(character.id) }
                        .padding(vertical = 10.dp, horizontal = 2.dp)
                        .testTag("phone-contact-${character.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(character.name.take(1).uppercase(), 56.dp, avatarPath)
                    Spacer(Modifier.width(18.dp))
                    Text(
                        character.name,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        Modifier
                            .size(12.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Color(0xFF22A55B)),
                    )
                }
            }
        }
    }
}

@Composable
internal fun GamesHubScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenGame: (GamesHubEntry) -> Unit,
) {
    FancyDarkSystemBars()

    val gamesBg = Color(0xFF0D141C)
    val cardSurface = Color(0xFF35343A)
    val titleColor = Color(0xFFF2F2F2)
    val sectionColor = Color(0xFF9AA0A8)
    val descColor = Color(0xFFA8ADB6)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(gamesBg)
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
            )
            .testTag("games-hub"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.desktop_back_to_home),
                    tint = Color.White,
                )
            }
            Text(
                stringResource(R.string.games_hub_title),
                color = titleColor,
                fontFamily = FontFamily.Serif,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.2).sp,
            )
        }
        Text(
            stringResource(R.string.games_hub_section),
            color = sectionColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.15.sp,
            modifier = Modifier.padding(start = 52.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
        )
        LazyColumn(
            contentPadding = PaddingValues(start = 15.dp, end = 15.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(GamesHubEntries, key = { it.id }) { game ->
                val gameTitle = stringResource(game.titleRes)
                val gameStatus = stringResource(game.statusNoteRes)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(83.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(cardSurface)
                        .clickable { onOpenGame(game) }
                        .semantics { contentDescription = "$gameTitle: $gameStatus" }
                        .padding(horizontal = 15.dp)
                        .testTag("games-entry-${game.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(game.iconRes),
                        contentDescription = stringResource(game.titleRes),
                        modifier = Modifier
                            .size(53.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit,
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            stringResource(game.titleRes),
                            color = titleColor,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            lineHeight = 20.sp,
                        )
                        Text(
                            stringResource(game.descriptionRes),
                            color = descColor,
                            fontWeight = FontWeight.Normal,
                            fontSize = 13.sp,
                            lineHeight = 17.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FancyDarkSystemBars() {
    val view = LocalView.current
    val activity = view.context as? ComponentActivity
    DisposableEffect(activity, view) {
        if (activity == null) {
            onDispose { }
        } else {
            val window = activity.window
            val controller = WindowCompat.getInsetsController(window, view)
            val prevLightStatus = controller.isAppearanceLightStatusBars
            val prevLightNav = controller.isAppearanceLightNavigationBars
            @Suppress("DEPRECATION")
            val prevStatusColor = window.statusBarColor
            @Suppress("DEPRECATION")
            val prevNavColor = window.navigationBarColor
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
            @Suppress("DEPRECATION")
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = android.graphics.Color.BLACK
            onDispose {
                controller.isAppearanceLightStatusBars = prevLightStatus
                controller.isAppearanceLightNavigationBars = prevLightNav
                @Suppress("DEPRECATION")
                window.statusBarColor = prevStatusColor
                @Suppress("DEPRECATION")
                window.navigationBarColor = prevNavColor
            }
        }
    }
}

@Composable
internal fun GamePlaceholderScreen(
    entry: GamesHubEntry,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    FancyDarkSystemBars()
    PlaceholderAppScreen(
        title = stringResource(entry.titleRes),
        summary = stringResource(entry.statusNoteRes),
        contentPadding = contentPadding,
        onBack = onBack,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .testTag("game-placeholder-${entry.id}"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painter = painterResource(entry.iconRes),
                contentDescription = stringResource(entry.titleRes),
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(18.dp)),
                contentScale = ContentScale.Crop,
            )
            Text(
                stringResource(entry.descriptionRes),
                color = FancyCream.copy(alpha = 0.75f),
                fontSize = 14.sp,
            )
            Text(
                stringResource(R.string.game_placeholder_status),
                color = FancyGold,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * 统一的可玩游戏会话屏：专属标题 + 规则卡片 + 开始按钮 + 核心操作控件 + 结果状态。
 * 六个 Games Hub 入口共用本屏；玩法逻辑由 [GamesEngine] 纯函数驱动，便于单测。
 */
@Composable
internal fun GameSessionScreen(
    entry: GamesHubEntry,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    FancyDarkSystemBars()

    val spec = GamesEngine.specFor(entry.id)
    if (spec == null) {
        // 未来新增但尚未接入玩法的游戏保持原占位路径，不阻断入口。
        GamePlaceholderScreen(entry = entry, contentPadding = contentPadding, onBack = onBack)
        return
    }

    val sessionBg = Color(0xFF0D141C)
    val cardSurface = Color(0xFF35343A)
    val optionSurface = Color(0xFF2E2D33)
    val titleColor = Color(0xFFF2F2F2)
    val descColor = Color(0xFFA8ADB6)

    var started by rememberSaveable(entry.id) { mutableStateOf(false) }
    var outcome by remember(entry.id) { mutableStateOf<GameSessionOutcome?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(sessionBg)
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
            )
            .testTag("game-session-${entry.id}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, end = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.desktop_back_to_home),
                    tint = Color.White,
                )
            }
            Image(
                painter = painterResource(entry.iconRes),
                contentDescription = stringResource(entry.titleRes),
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Fit,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                stringResource(entry.titleRes),
                color = titleColor,
                fontFamily = FontFamily.Serif,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 15.dp, end = 15.dp, top = 6.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(cardSurface)
                    .padding(horizontal = 14.dp, vertical = 13.dp)
                    .testTag("game-session-rules-${entry.id}"),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        stringResource(R.string.game_session_rules_label),
                        color = FancyGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                    )
                    Text(
                        stringResource(spec.rulesRes),
                        color = descColor,
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp,
                    )
                }
            }

            if (!started) {
                Button(
                    onClick = { started = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("game-session-start-${entry.id}"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FancyGold,
                        contentColor = Color(0xFF201A08),
                    ),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        stringResource(R.string.game_session_start),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    )
                }
            } else {
                DeepGameBoard(entry = entry, spec = spec)
            }
        }
    }
}

/** 六游戏深化玩法面板：按入口分发到各自规则状态机（每回合都有结果卡，结束给胜负终局）。 */
@Composable
private fun DeepGameBoard(entry: GamesHubEntry, spec: GameSessionSpec) {
    when (entry.id) {
        "dice_duel_rpg" -> BattleBoard(spec, dice = true)
        "tactical_command" -> BattleBoard(spec, dice = false)
        "world_adventure" -> JourneyBoard(spec)
        "truth_or_dare" -> PromptBoard(spec)
        "two_truths_lie" -> TwoTruthsBoard(spec)
        "the_oracle" -> OracleBoard(spec)
        else -> Unit
    }
}

@Composable
private fun GameOptionRow(tag: String, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF2E2D33))
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 15.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = Color(0xFFF2F2F2),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = FancyGold,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun GameResultCard(tag: String, kind: GameResultKind, title: String, detail: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(
                1.dp,
                when (kind) {
                    GameResultKind.WIN -> FancyGold.copy(alpha = 0.8f)
                    GameResultKind.LOSE -> Color(0xFFE06050).copy(alpha = 0.7f)
                    GameResultKind.NEUTRAL -> Color.White.copy(alpha = 0.14f)
                },
                RoundedCornerShape(18.dp),
            )
            .background(Color(0xFF222127))
            .padding(horizontal = 15.dp, vertical = 14.dp)
            .testTag(tag),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            when (kind) {
                GameResultKind.WIN -> Text(
                    stringResource(R.string.game_outcome_win),
                    color = FancyGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                )
                GameResultKind.LOSE -> Text(
                    stringResource(R.string.game_outcome_lose),
                    color = Color(0xFFE06050),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                )
                GameResultKind.NEUTRAL -> Unit
            }
            Text(
                title,
                color = Color(0xFFF2F2F2),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                detail,
                color = Color(0xFFA8ADB6),
                fontSize = 13.5.sp,
                lineHeight = 19.sp,
            )
        }
    }
}

@Composable
private fun GamePlayAgain(tag: String, onReset: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TextButton(
            onClick = onReset,
            modifier = Modifier.testTag(tag),
        ) {
            Text(stringResource(R.string.game_session_play_again), color = FancyGold)
        }
    }
}

/** 骰子对决 / 战术指挥：多回合 HP 战斗，每回合给叙事结果，终局给胜负。 */
@Composable
private fun BattleBoard(spec: GameSessionSpec, dice: Boolean) {
    var battle by remember(spec.entryId) { mutableStateOf(GameBattleState()) }
    var lastRolls by remember(spec.entryId) { mutableStateOf<Pair<Int, Int>?>(null) }
    var lastTurn by remember(spec.entryId) { mutableStateOf<BattleTurn?>(null) }
    var flavorIndex by remember(spec.entryId) { mutableStateOf(-1) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.game_battle_hp, battle.playerHp, battle.enemyHp),
            color = FancyGold,
            fontWeight = FontWeight.Bold,
        )
        spec.options.forEachIndexed { index, option ->
            GameOptionRow("game-option-${spec.entryId}-${option.id}", stringResource(option.labelRes)) {
                if (battle.finished) return@GameOptionRow
                if (dice) {
                    val turn = GamesRules.diceDuelRound(battle, Random.Default)
                    battle = turn.state
                    lastRolls = Pair(turn.args[0], turn.args[1])
                } else {
                    val turn = GamesRules.tacticalRound(battle, index, Random.Default)
                    battle = turn.state
                    lastTurn = turn
                    flavorIndex = index
                }
            }
        }
        // 每回合叙事（组合期解析字符串）。
        val roundNarrative = if (dice) {
            lastRolls?.let { (mine, theirs) ->
                stringResource(R.string.game_dice_round_detail, mine, theirs)
            }
        } else {
            lastTurn?.let { turn ->
                if (flavorIndex in spec.outcomes.indices) {
                    stringResource(spec.outcomes[flavorIndex].detailRes) + "\n" +
                        stringResource(turn.detailRes, *turn.args.toTypedArray())
                } else {
                    null
                }
            }
        }
        roundNarrative?.let { narrative ->
            val kind = when {
                !battle.finished -> GameResultKind.NEUTRAL
                battle.won -> GameResultKind.WIN
                else -> GameResultKind.LOSE
            }
            val (title, detail) = when (kind) {
                GameResultKind.WIN -> if (dice) {
                    stringResource(R.string.game_dice_duel_rpg_outcome_hit_title) to
                        stringResource(R.string.game_dice_duel_rpg_outcome_hit_detail)
                } else {
                    stringResource(R.string.game_tactical_victory_title) to
                        stringResource(R.string.game_tactical_victory_detail)
                }
                GameResultKind.LOSE -> if (dice) {
                    stringResource(R.string.game_dice_duel_rpg_outcome_miss_title) to
                        stringResource(R.string.game_dice_duel_rpg_outcome_miss_detail)
                } else {
                    stringResource(R.string.game_tactical_defeat_title) to
                        stringResource(R.string.game_tactical_defeat_detail)
                }
                GameResultKind.NEUTRAL -> "" to narrative
            }
            GameResultCard(
                tag = "game-session-result-${spec.entryId}",
                kind = kind,
                title = title,
                detail = if (kind == GameResultKind.NEUTRAL) narrative else "$detail\n$narrative",
            )
            GamePlayAgain("game-play-again-${spec.entryId}") {
                battle = GameBattleState()
                lastRolls = null
                lastTurn = null
                flavorIndex = -1
            }
        }
    }
}

/** 世界冒险：5 站行程 + 8 回合限制；每回合的剧情文本来自选项固定结局。 */
@Composable
private fun JourneyBoard(spec: GameSessionSpec) {
    var journey by remember(spec.entryId) { mutableStateOf(GameJourneyState()) }
    var lastChoice by remember(spec.entryId) { mutableStateOf(-1) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.game_journey_progress, journey.step, journey.total, journey.rounds),
            color = FancyGold,
            fontWeight = FontWeight.Bold,
        )
        spec.options.forEachIndexed { index, option ->
            GameOptionRow("game-option-${spec.entryId}-${option.id}", stringResource(option.labelRes)) {
                if (journey.finished) return@GameOptionRow
                journey = GamesRules.worldAdventureStep(journey, Random.Default)
                lastChoice = index
            }
        }
        if (lastChoice in spec.outcomes.indices) {
            val narrative = stringResource(spec.outcomes[lastChoice].detailRes)
            val kind = when {
                !journey.finished -> GameResultKind.NEUTRAL
                journey.won -> GameResultKind.WIN
                else -> GameResultKind.LOSE
            }
            val (title, detail) = when (kind) {
                GameResultKind.WIN ->
                    stringResource(R.string.game_world_victory_title) to stringResource(R.string.game_world_victory_detail)
                GameResultKind.LOSE ->
                    stringResource(R.string.game_world_defeat_title) to stringResource(R.string.game_world_defeat_detail)
                GameResultKind.NEUTRAL -> "" to narrative
            }
            GameResultCard(
                tag = "game-session-result-${spec.entryId}",
                kind = kind,
                title = title,
                detail = if (kind == GameResultKind.NEUTRAL) narrative else "$detail\n$narrative",
            )
            GamePlayAgain("game-play-again-${spec.entryId}") {
                journey = GameJourneyState()
                lastChoice = -1
            }
        }
    }
}

/** 真话/大冒险：选择后从题库抽任务；剧情结局文本保留在结果卡中。 */
@Composable
private fun PromptBoard(spec: GameSessionSpec) {
    var promptRes by remember(spec.entryId) { mutableStateOf(0) }
    var choiceIndex by remember(spec.entryId) { mutableStateOf(-1) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        spec.options.forEachIndexed { index, option ->
            GameOptionRow("game-option-${spec.entryId}-${option.id}", stringResource(option.labelRes)) {
                promptRes = GamesRules.truthOrDarePrompt(index, Random.Default)
                choiceIndex = index
            }
        }
        if (promptRes != 0 && choiceIndex in spec.outcomes.indices) {
            GameResultCard(
                tag = "game-session-result-${spec.entryId}",
                kind = GameResultKind.NEUTRAL,
                title = stringResource(spec.outcomes[choiceIndex].titleRes),
                detail = stringResource(spec.outcomes[choiceIndex].detailRes) + "\n" +
                    stringResource(R.string.game_tot_result_title) + "：" + stringResource(promptRes),
            )
            GamePlayAgain("game-play-again-${spec.entryId}") {
                promptRes = 0
                choiceIndex = -1
            }
        }
    }
}

/** 两真一假：每轮三条陈述，猜中谎言才算胜；赢法随轮次变化。 */
@Composable
private fun TwoTruthsBoard(spec: GameSessionSpec) {
    var round by remember(spec.entryId) { mutableStateOf(GamesRules.twoTruthsRound(Random.Default)) }
    var guessed by remember(spec.entryId) { mutableStateOf(-1) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.game_ttl_question),
            color = Color(0xFFA8ADB6),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        round.statements.forEachIndexed { index, res ->
            GameOptionRow("game-option-${spec.entryId}-${spec.options[index].id}", stringResource(res)) {
                guessed = index
            }
        }
        if (guessed >= 0) {
            val correct = GamesRules.twoTruthsGuess(round, guessed)
            GameResultCard(
                tag = "game-session-result-${spec.entryId}",
                kind = if (correct) GameResultKind.WIN else GameResultKind.LOSE,
                title = stringResource(
                    if (correct) R.string.game_ttl_correct_title else R.string.game_ttl_wrong_title,
                ),
                detail = stringResource(
                    if (correct) R.string.game_ttl_correct_detail else R.string.game_ttl_wrong_detail,
                ) + "\n" + stringResource(
                    spec.outcomes.getOrElse(guessed) { spec.outcomes.first() }.detailRes,
                ),
            )
            GamePlayAgain("game-play-again-${spec.entryId}") {
                round = GamesRules.twoTruthsRound(Random.Default)
                guessed = -1
            }
        }
    }
}

/** 神谕：单次抽牌 + 逆位。 */
@Composable
private fun OracleBoard(spec: GameSessionSpec) {
    var draw by remember(spec.entryId) { mutableStateOf<OracleDraw?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        spec.options.forEach { option ->
            GameOptionRow("game-option-${spec.entryId}-${option.id}", stringResource(option.labelRes)) {
                draw = GamesRules.oracleDraw(Random.Default)
            }
        }
        draw?.let { current ->
            GameResultCard(
                tag = "game-session-result-${spec.entryId}",
                kind = GameResultKind.NEUTRAL,
                title = stringResource(current.card.titleRes) +
                    if (current.reversed) stringResource(R.string.game_oracle_reversed_suffix) else "",
                detail = stringResource(current.card.detailRes),
            )
            GamePlayAgain("game-play-again-${spec.entryId}") { draw = null }
        }
    }
}

private fun iconFor(app: DesktopApp): ImageVector = when (app) {
    DesktopApp.Messenger -> Icons.Default.Home
    DesktopApp.Imaging -> Icons.Default.Star
    DesktopApp.Gallery -> Icons.Default.Share
    DesktopApp.Settings -> Icons.Default.Settings
    DesktopApp.Characters -> Icons.Default.Person
    DesktopApp.Binder -> Icons.Default.Favorite
    DesktopApp.Ustagram -> Icons.Default.Share
    DesktopApp.Rebbit -> Icons.Default.Home
    DesktopApp.Y -> Icons.Default.Info
    DesktopApp.Phone -> Icons.Default.Call
    DesktopApp.Groups -> Icons.Default.Star
    DesktopApp.RootProducer -> Icons.Default.Star
    DesktopApp.Lorebook -> Icons.Default.Info
    DesktopApp.RootCreator -> Icons.Default.Person
    DesktopApp.Games -> Icons.Default.Star
    DesktopApp.AuraSwap -> Icons.Default.Favorite
    DesktopApp.Storage -> Icons.Default.Info
    DesktopApp.Benchmark -> Icons.Default.Info
    DesktopApp.Store -> Icons.Default.Star
}

@Composable
private fun DockGlyph(app: DesktopApp) {
    if (app == DesktopApp.Settings) {
        Icon(Icons.Default.Settings, contentDescription = app.label, tint = FancyCream)
        return
    }
    Canvas(Modifier.size(28.dp)) {
        val white = FancyCream
        when (app) {
            DesktopApp.Messenger -> {
                drawRoundRect(
                    white,
                    topLeft = androidx.compose.ui.geometry.Offset(size.width * .12f, size.height * .18f),
                    size = androidx.compose.ui.geometry.Size(size.width * .76f, size.height * .56f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * .08f),
                )
                val tail = Path().apply {
                    moveTo(size.width * .25f, size.height * .70f)
                    lineTo(size.width * .20f, size.height * .90f)
                    lineTo(size.width * .45f, size.height * .72f)
                    close()
                }
                drawPath(tail, white)
                repeat(3) { index ->
                    drawLine(
                        Color(0xFF2A2D30),
                        androidx.compose.ui.geometry.Offset(size.width * .28f, size.height * (.34f + index * .12f)),
                        androidx.compose.ui.geometry.Offset(size.width * .70f, size.height * (.34f + index * .12f)),
                        strokeWidth = size.width * .055f,
                    )
                }
            }
            DesktopApp.Imaging -> {
                listOf(.22f to .60f, .62f to .30f, .70f to .72f).forEach { (x, y) ->
                    drawCircle(white, size.width * .12f, androidx.compose.ui.geometry.Offset(size.width * x, size.height * y))
                    drawLine(
                        white,
                        androidx.compose.ui.geometry.Offset(size.width * (x - .20f), size.height * y),
                        androidx.compose.ui.geometry.Offset(size.width * (x + .20f), size.height * y),
                        strokeWidth = size.width * .07f,
                    )
                    drawLine(
                        white,
                        androidx.compose.ui.geometry.Offset(size.width * x, size.height * (y - .20f)),
                        androidx.compose.ui.geometry.Offset(size.width * x, size.height * (y + .20f)),
                        strokeWidth = size.width * .07f,
                    )
                }
            }
            DesktopApp.Gallery -> {
                drawRoundRect(
                    white,
                    topLeft = androidx.compose.ui.geometry.Offset(size.width * .14f, size.height * .16f),
                    size = androidx.compose.ui.geometry.Size(size.width * .72f, size.height * .64f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * .05f),
                )
                val mountain = Path().apply {
                    moveTo(size.width * .24f, size.height * .67f)
                    lineTo(size.width * .43f, size.height * .45f)
                    lineTo(size.width * .55f, size.height * .57f)
                    lineTo(size.width * .68f, size.height * .40f)
                    lineTo(size.width * .80f, size.height * .67f)
                    close()
                }
                drawPath(mountain, Color(0xFF2A2D30))
                drawLine(
                    white,
                    androidx.compose.ui.geometry.Offset(size.width * .06f, size.height * .34f),
                    androidx.compose.ui.geometry.Offset(size.width * .06f, size.height * .88f),
                    strokeWidth = size.width * .07f,
                )
                drawLine(
                    white,
                    androidx.compose.ui.geometry.Offset(size.width * .06f, size.height * .88f),
                    androidx.compose.ui.geometry.Offset(size.width * .70f, size.height * .88f),
                    strokeWidth = size.width * .07f,
                )
            }
            DesktopApp.Store -> {
                // 商店：货架 + 袋子
                drawRoundRect(
                    white,
                    topLeft = androidx.compose.ui.geometry.Offset(size.width * .14f, size.height * .32f),
                    size = androidx.compose.ui.geometry.Size(size.width * .72f, size.height * .14f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * .04f),
                )
                drawLine(
                    white,
                    androidx.compose.ui.geometry.Offset(size.width * .14f, size.height * .66f),
                    androidx.compose.ui.geometry.Offset(size.width * .86f, size.height * .66f),
                    strokeWidth = size.width * .06f,
                )
                val bag = Path().apply {
                    moveTo(size.width * .30f, size.height * .56f)
                    lineTo(size.width * .38f, size.height * .84f)
                    lineTo(size.width * .62f, size.height * .84f)
                    lineTo(size.width * .70f, size.height * .56f)
                    close()
                }
                drawPath(bag, white)
            }
            else -> Unit
        }
    }
}

private fun hubTint(hub: DesktopHub): Color = when (hub) {
    DesktopHub.Social -> Color(0xFF3EA7E0)
    DesktopHub.CreativeSuite -> Color(0xFFB66DCF)
    DesktopHub.SystemCore -> Color(0xFF738291)
    DesktopHub.Entertainment -> Color(0xFFD94E3F)
}
