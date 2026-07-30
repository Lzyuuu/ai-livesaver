package io.github.lzyuuu.ailivesaver

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

internal val FancyGold = Color(0xFFC6A15B)
internal val FancyGoldDim = Color(0xFF8F7640)
internal val FancyNavy = Color(0xFF0B1020)
internal val FancyNavyMid = Color(0xFF151B2E)
internal val FancyInk = Color(0xFF070A12)
internal val FancyCream = Color(0xFFE8E4D9)

@Composable
internal fun WelcomeGuideHost(
    onFinished: (WelcomeGuideState) -> Unit,
) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(WelcomeGuideState()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        state = WelcomeGuide.afterNotificationHandled(state)
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            state = WelcomeGuide.afterNotificationHandled(state)
            return@LaunchedEffect
        }
        val granted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            state = WelcomeGuide.afterNotificationHandled(state)
        } else {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(state.step, state.localFailed) {
        if (state.step == WelcomeStep.LocalDownload && !state.localFailed) {
            delay(1_600)
            // 本机无参考侧模型库时对齐失败路径，露出 Walk in。
            state = WelcomeGuide.markLocalFailed(state)
        }
    }

    BackHandler(enabled = state.step != WelcomeStep.RootIntro1 && state.step != WelcomeStep.Finished) {
        state = WelcomeGuide.backFrom(state)
    }

    LaunchedEffect(state.step) {
        if (WelcomeGuide.isFinished(state)) onFinished(state)
    }

    FancyOsBackdrop {
        when (state.step) {
            WelcomeStep.NotificationPermission -> {
                WelcomeDialoguePanel(
                    body = "正在请求通知权限，以便特别关注角色能找到你。",
                    primaryLabel = null,
                )
            }
            WelcomeStep.RootIntro1 -> {
                WelcomeDialoguePanel(
                    body = "……你来了。我是 Root。这里归我管。尽量别被这点能力吓到。",
                    primaryLabel = "继续",
                    onPrimary = { state = WelcomeGuide.continueFrom(state) },
                    showBack = false,
                )
            }
            WelcomeStep.RootIntro2 -> {
                WelcomeDialoguePanel(
                    body = "消息、动态、游戏、通话、按需出图。一部装在手机里的小世界，由我把无聊的部分收干净。",
                    primaryLabel = "继续",
                    onPrimary = { state = WelcomeGuide.continueFrom(state) },
                    onBack = { state = WelcomeGuide.backFrom(state) },
                )
            }
            WelcomeStep.UserName -> {
                WelcomeDialoguePanel(
                    body = "先说正事：我该把你存成什么名字？",
                    primaryLabel = "继续",
                    primaryEnabled = state.canContinueName,
                    onPrimary = { state = WelcomeGuide.continueFrom(state) },
                    onBack = { state = WelcomeGuide.backFrom(state) },
                ) {
                    OutlinedTextField(
                        value = state.userName,
                        onValueChange = { state = WelcomeGuide.withName(state, it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("welcome-name"),
                        placeholder = { Text("你的名字", color = FancyCream.copy(alpha = 0.45f)) },
                        singleLine = true,
                        colors = fancyFieldColors(),
                    )
                }
            }
            WelcomeStep.Appearance -> {
                WelcomeDialoguePanel(
                    body = "……那你希望我以哪副样子点评你的品味？",
                    primaryLabel = "继续",
                    primaryEnabled = state.canContinueAppearance,
                    onPrimary = { state = WelcomeGuide.continueFrom(state) },
                    onBack = { state = WelcomeGuide.backFrom(state) },
                    showAvatar = false,
                ) {
                    AppearanceGrid(
                        selectedId = state.appearanceId,
                        onSelect = { state = WelcomeGuide.withAppearance(state, it) },
                    )
                }
            }
            WelcomeStep.About -> {
                WelcomeDialoguePanel(
                    body = "用一句话介绍你自己。爱好、工作，都行——也可以先跳过。",
                    primaryLabel = "继续",
                    onPrimary = { state = WelcomeGuide.continueFrom(state) },
                    onBack = { state = WelcomeGuide.backFrom(state) },
                    avatarAppearance = state.appearance,
                ) {
                    OutlinedTextField(
                        value = state.about,
                        onValueChange = { state = WelcomeGuide.withAbout(state, it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .testTag("welcome-about"),
                        placeholder = { Text("可选", color = FancyCream.copy(alpha = 0.45f)) },
                        colors = fancyFieldColors(),
                    )
                    TextButton(onClick = {
                        state = WelcomeGuide.withAbout(state, "")
                        state = WelcomeGuide.continueFrom(state)
                    }) {
                        Text("跳过", color = FancyGold)
                    }
                }
            }
            WelcomeStep.SetupMode -> {
                WelcomeDialoguePanel(
                    body = "选择你想如何配置 AI 体验。之后随时可以改。",
                    primaryLabel = null,
                    onBack = { state = WelcomeGuide.backFrom(state) },
                    avatarAppearance = state.appearance,
                ) {
                    ChoiceCard(
                        title = "Easy Setup（推荐）",
                        body = "快速开始，使用简化设置与自动默认值。",
                        selected = state.setupMode == WelcomeSetupMode.Easy,
                        onClick = {
                            state = WelcomeGuide.withSetupMode(state, WelcomeSetupMode.Easy)
                            state = WelcomeGuide.continueFrom(state)
                        },
                        testTag = "welcome-easy",
                    )
                    Spacer(Modifier.height(12.dp))
                    ChoiceCard(
                        title = "Custom Setup（高级）",
                        body = "手动配置本地核心、API Key 与模型参数。",
                        selected = state.setupMode == WelcomeSetupMode.Custom,
                        onClick = {
                            state = WelcomeGuide.withSetupMode(state, WelcomeSetupMode.Custom)
                            state = WelcomeGuide.continueFrom(state)
                        },
                        testTag = "welcome-custom",
                    )
                }
            }
            WelcomeStep.AiMode -> {
                WelcomeDialoguePanel(
                    body = "我该怎么思考？本地更私密离线；云端更快也更强。",
                    primaryLabel = null,
                    onBack = { state = WelcomeGuide.backFrom(state) },
                    avatarAppearance = state.appearance,
                ) {
                    ChoiceCard(
                        title = "On-device AI（私密 & 离线）",
                        body = "完全在本机运行。我们会自动下载一个轻量大脑。",
                        selected = state.aiMode == WelcomeAiMode.Local,
                        onClick = {
                            state = WelcomeGuide.withAiMode(state, WelcomeAiMode.Local)
                            state = WelcomeGuide.continueFrom(state)
                        },
                        testTag = "welcome-local",
                    )
                    Spacer(Modifier.height(12.dp))
                    ChoiceCard(
                        title = "Cloud AI（快速 & 联网）",
                        body = "使用云端模型。需要 OpenRouter / Groq 等免费 API Key。",
                        selected = state.aiMode == WelcomeAiMode.Cloud,
                        onClick = {
                            state = WelcomeGuide.withAiMode(state, WelcomeAiMode.Cloud)
                            state = WelcomeGuide.continueFrom(state)
                        },
                        testTag = "welcome-cloud",
                    )
                }
            }
            WelcomeStep.LocalDownload -> {
                WelcomeDialoguePanel(
                    body = if (state.localFailed) {
                        "本机大脑暂时下不来。你可以先 Walk in，稍后再在 Settings 里补上。"
                    } else {
                        "稍等——正在把大脑拉到你的手机上。落地之后，这些都不会离开你的设备。"
                    },
                    primaryLabel = if (state.localFailed) "Walk in" else null,
                    onPrimary = if (state.localFailed) {
                        { state = WelcomeGuide.walkIn(state) }
                    } else {
                        null
                    },
                    onBack = { state = WelcomeGuide.backFrom(state) },
                    avatarAppearance = state.appearance,
                ) {
                    if (!state.localFailed) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("welcome-local-progress"),
                            color = FancyGold,
                            trackColor = FancyNavyMid,
                        )
                    }
                }
            }
            WelcomeStep.CloudSetup -> {
                WelcomeDialoguePanel(
                    body = "选一个云端入口。密钥可以稍后在 Settings → Chat brain 里补全。",
                    primaryLabel = "进入桌面",
                    primaryEnabled = state.canContinueCloud,
                    onPrimary = { state = WelcomeGuide.continueFrom(state) },
                    onBack = { state = WelcomeGuide.backFrom(state) },
                    avatarAppearance = state.appearance,
                ) {
                    listOf("OpenRouter", "Groq", "DeepInfra").forEach { provider ->
                        ChoiceCard(
                            title = provider,
                            body = "稍后配置 API Key",
                            selected = state.cloudProvider == provider,
                            onClick = { state = WelcomeGuide.withCloudProvider(state, provider) },
                            testTag = "welcome-provider-$provider",
                        )
                    }
                }
            }
            WelcomeStep.Finished -> Unit
        }
    }
}

@Composable
private fun FancyOsBackdrop(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF343A55), Color(0xFF1C2235), FancyInk),
                ),
            )
            .testTag("welcome-guide"),
    ) {
        content()
    }
}

@Composable
private fun WelcomeDialoguePanel(
    body: String,
    primaryLabel: String?,
    primaryEnabled: Boolean = true,
    onPrimary: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    showBack: Boolean = true,
    showAvatar: Boolean = false,
    avatarAppearance: RootAppearance? = null,
    content: @Composable () -> Unit = {},
) {
    Column(Modifier.fillMaxSize()) {
        if (showBack && onBack != null) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.padding(top = 24.dp, start = 4.dp),
            ) {
                Text("返回", color = FancyCream.copy(alpha = 0.75f), fontSize = 11.sp)
            }
        } else {
            Spacer(Modifier.height(48.dp))
        }
        if (showAvatar || avatarAppearance != null) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 48.dp)
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(148.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .border(1.dp, FancyGold.copy(alpha = 0.35f), RoundedCornerShape(24.dp)),
                ) {
                    RootPortrait(
                        appearance = avatarAppearance ?: RootAppearance.AnimeBlonde,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 390.dp)
                .background(Color(0xFF080D14).copy(alpha = 0.98f))
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                "Root",
                color = FancyGold,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(body, color = FancyCream, fontSize = 13.sp, lineHeight = 19.sp)
            content()
            if (primaryLabel != null && onPrimary != null) {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onPrimary,
                    enabled = primaryEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(
                            if (primaryLabel == "Walk in") "welcome-walk-in"
                            else "welcome-continue",
                        ),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FancyGold,
                        contentColor = FancyInk,
                        disabledContainerColor = FancyNavyMid,
                        disabledContentColor = FancyCream.copy(alpha = 0.35f),
                    ),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(primaryLabel, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(52.dp))
        }
    }
}

@Composable
private fun AppearanceGrid(
    selectedId: String?,
    onSelect: (RootAppearance) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RootAppearance.entries.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { option ->
                    val selected = option.id == selectedId
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(68.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) FancyGold else FancyGoldDim.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(16.dp),
                            )
                            .clickable { onSelect(option) }
                            .testTag("welcome-appearance-${option.id}"),
                    ) {
                        RootPortrait(
                            appearance = option,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceCard(
    title: String,
    body: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String = "",
) {
    val cardModifier = Modifier
        .fillMaxWidth()
        .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier)
        .clip(RoundedCornerShape(18.dp))
        .border(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) FancyGold else FancyGold.copy(alpha = 0.35f),
            shape = RoundedCornerShape(18.dp),
        )
        .clickable(onClick = onClick)
        .padding(16.dp)
    Column(cardModifier) {
        Text(title, color = FancyGold, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(body, color = FancyCream.copy(alpha = 0.8f), fontSize = 13.sp)
    }
}

@Composable
private fun fancyFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = FancyGold,
    unfocusedBorderColor = FancyGoldDim,
    focusedTextColor = FancyCream,
    unfocusedTextColor = FancyCream,
    cursorColor = FancyGold,
    focusedContainerColor = FancyNavyMid,
    unfocusedContainerColor = FancyNavyMid,
)

@Composable
internal fun RootPortrait(
    appearance: RootAppearance = RootAppearance.AnimeBlonde,
    modifier: Modifier = Modifier,
) {
    val drawable = when (appearance) {
        RootAppearance.AnimeBlonde -> R.drawable.avatar_fancy_root_anime_blonde
        RootAppearance.AnimeBrunette -> R.drawable.avatar_anime_brunette
        RootAppearance.AnimeRedhead -> R.drawable.avatar_anime_redhead
        RootAppearance.RealBlonde -> R.drawable.avatar_real_blonde
        RootAppearance.RealBrunette -> R.drawable.avatar_real_brunette
        RootAppearance.RealRedhead -> R.drawable.avatar_real_redhead
    }
    Image(
        painter = painterResource(drawable),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}
