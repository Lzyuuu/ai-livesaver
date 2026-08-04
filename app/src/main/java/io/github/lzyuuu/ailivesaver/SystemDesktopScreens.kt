package io.github.lzyuuu.ailivesaver

import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
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
) {
    val now = remember { Date() }
    val time = remember(now) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
    }
    val date = remember(now) {
        SimpleDateFormat("EEEE, MMMM d", Locale.US).format(now).uppercase(Locale.US)
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
        ) {
            Spacer(modifier = Modifier.height(44.dp))
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

            Spacer(modifier = Modifier.height(18.dp))
            Text(
                "快速入口",
                color = Color(0xFFADB4C2),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DesktopHub.entries.forEach { hub ->
                    HubFolder(
                        hub = hub,
                        onClick = { onOpenHub(hub) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .border(1.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(26.dp))
                .background(Color(0xFF202326).copy(alpha = 0.94f))
                .padding(horizontal = 8.dp, vertical = 12.dp)
                .testTag("desktop-dock"),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            DesktopDockApps.forEach { app ->
                DockIcon(app = app, onClick = { onOpenApp(app) })
            }
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
internal fun DesktopBackBar(onBack: () -> Unit, title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(FancyNavy)
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onBack,
            modifier = Modifier.testTag("desktop-back-bar"),
        ) {
            Text(stringResource(R.string.desktop_back_to_home), color = FancyGold)
        }
        Text(
            title,
            color = FancyCream,
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
    var query by rememberSaveable { mutableStateOf("") }
    val visibleCharacters = characters.filter { query.isBlank() || it.name.contains(query, true) || it.persona.contains(query, true) }
    PlaceholderAppScreen(
        title = "Phone",
        summary = "联系人列表。拨号会进入对应角色的 Messenger 对话。",
        contentPadding = contentPadding,
        onBack = onBack,
    ) {
        androidx.compose.material3.OutlinedTextField(value=query, onValueChange={query=it}, label={Text("搜索联系人")}, modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp).testTag("phone-search"))
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(visibleCharacters, key = { it.id }) { character ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onCall(character.id) }
                        .background(FancyNavyMid)
                        .padding(14.dp)
                        .testTag("phone-contact-${character.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(character.name, color = FancyCream, fontWeight = FontWeight.SemiBold)
                        Text(
                            character.persona,
                            color = FancyCream.copy(alpha = 0.65f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { onCall(character.id) }) {
                        Icon(Icons.Default.Call, contentDescription = "拨号", tint = Color(0xFF5CC8A8))
                    }
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(83.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(cardSurface)
                        .clickable { onOpenGame(game) }
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
    DesktopApp.Games -> Icons.Default.Star
    DesktopApp.AuraSwap -> Icons.Default.Favorite
    DesktopApp.Storage -> Icons.Default.Info
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
