package io.github.lzyuuu.ailivesaver

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/** 官方 ChatBubbleOutline 矢量路径，material-icons-core 未包含，本地定义。 */
internal val CommentOutlineIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "CommentOutline",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).path(fill = SolidColor(Color.Black)) {
        moveTo(20f, 2f)
        horizontalLineTo(4f)
        curveTo(2.9f, 2f, 2f, 2.9f, 2f, 4f)
        verticalLineToRelative(18f)
        lineToRelative(4f, -4f)
        horizontalLineToRelative(14f)
        curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
        verticalLineTo(4f)
        curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
        close()
        moveTo(20f, 16f)
        horizontalLineTo(6f)
        lineToRelative(-2f, 2f)
        verticalLineTo(4f)
        horizontalLineToRelative(16f)
        verticalLineToRelative(12f)
        close()
    }.build()
}

internal val LikedColor = Color(0xFFFF4D67)
internal val DownvoteColor = Color(0xFF8093F1)

@Composable
internal fun relativeTimeLabel(
    timestamp: Long,
    now: Long = System.currentTimeMillis(),
): String {
    val diff = (now - timestamp).coerceAtLeast(0)
    val minutes = diff / 60_000
    val hours = diff / 3_600_000
    val days = diff / 86_400_000
    return when {
        minutes < 1 -> stringResource(R.string.time_just_now)
        minutes < 60 -> stringResource(R.string.time_minutes_ago, minutes)
        hours < 24 -> stringResource(R.string.time_hours_ago, hours)
        days == 1L -> stringResource(R.string.time_yesterday)
        days < 7 -> stringResource(R.string.time_days_ago, days)
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))
    }
}

private fun sameDay(first: Long, second: Long): Boolean {
    val a = Calendar.getInstance().apply { timeInMillis = first }
    val b = Calendar.getInstance().apply { timeInMillis = second }
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}

/** 会话内日分隔标签：今天 / 昨天 / 具体日期。 */
@Composable
internal fun chatDayLabel(timestamp: Long, now: Long = System.currentTimeMillis()): String = when {
    sameDay(timestamp, now) -> stringResource(R.string.time_today)
    sameDay(timestamp, now - 86_400_000) -> stringResource(R.string.time_yesterday)
    else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))
}

internal fun isSameChatDay(first: Long, second: Long): Boolean = sameDay(first, second)

/**
 * 点赞按钮：Twitter 式 pop 动效。
 * 弹簧回弹、可中断、尊重系统减少动画设置。
 */
@Composable
internal fun AnimatedLikeButton(
    liked: Boolean,
    count: Int,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val animationsEnabled = remember { systemAnimationsEnabled(context) }
    val scale = remember { Animatable(1f) }
    // 首次组合不播动效：只有用户真实切换点赞状态时才 pop
    var seenInitialValue by remember { mutableStateOf(false) }
    LaunchedEffect(liked) {
        if (!seenInitialValue) {
            seenInitialValue = true
            return@LaunchedEffect
        }
        if (liked && animationsEnabled) {
            scale.animateTo(
                1.25f,
                spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessHigh),
            )
            scale.animateTo(
                1f,
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            )
        } else {
            scale.snapTo(1f)
        }
    }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale = if (pressed) 0.95f else 1f
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onToggle,
                role = Role.Button,
            )
            .padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = stringResource(if (liked) R.string.unlike else R.string.like),
            tint = if (liked) LikedColor else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(19.dp)
                .scale(scale.value * pressScale),
        )
        if (count > 0) {
            Text(
                "$count",
                style = MaterialTheme.typography.labelLarge,
                color = if (liked) LikedColor else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Reddit 式顶/踩投票组。 */
@Composable
internal fun VotePill(
    score: Int,
    userVote: Int,
    onVote: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val animationsEnabled = remember { systemAnimationsEnabled(context) }
    val scale = remember { Animatable(1f) }
    // 首次组合不脉冲：滚动回收时保持静止，只在真实投票时反馈
    var seenInitialValue by remember { mutableStateOf(false) }
    LaunchedEffect(score, userVote) {
        if (!seenInitialValue) {
            seenInitialValue = true
            return@LaunchedEffect
        }
        if (animationsEnabled) {
            scale.animateTo(
                1.18f,
                spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessHigh),
            )
            scale.animateTo(
                1f,
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            )
        }
    }
    val upColor = if (userVote == 1) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val downColor = if (userVote == -1) {
        DownvoteColor
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val scoreColor = when {
        userVote == 1 -> MaterialTheme.colorScheme.primary
        userVote == -1 -> DownvoteColor
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 2.dp),
        ) {
            val upInteractionSource = remember { MutableInteractionSource() }
            val upPressed by upInteractionSource.collectIsPressedAsState()
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = upInteractionSource,
                        indication = null,
                        onClick = { onVote(if (userVote == 1) 0 else 1) },
                        role = Role.Button,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.vote_up),
                    tint = upColor,
                    modifier = Modifier
                        .size(18.dp)
                        .scale(if (upPressed) 0.95f else 1f),
                )
            }
            Text(
                "$score",
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                fontWeight = FontWeight.Bold,
                color = scoreColor,
                modifier = Modifier
                    .widthIn(min = 20.dp)
                    .scale(scale.value),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            val downInteractionSource = remember { MutableInteractionSource() }
            val downPressed by downInteractionSource.collectIsPressedAsState()
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = downInteractionSource,
                        indication = null,
                        onClick = { onVote(if (userVote == -1) 0 else -1) },
                        role = Role.Button,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.vote_down),
                    tint = downColor,
                    modifier = Modifier
                        .size(18.dp)
                        .scale(if (downPressed) 0.95f else 1f),
                )
            }
        }
    }
}

/** Telegram 式“正在输入”三点脉冲；系统关闭动画时显示静态省略号。 */
@Composable
internal fun TypingIndicator(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val animationsEnabled = remember { systemAnimationsEnabled(context) }
    if (!animationsEnabled) {
        Text(
            "···",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }
    val transition = rememberInfiniteTransition(label = "typing")
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val offsetY by transition.animateFloat(
                initialValue = 0f,
                targetValue = -4f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1200
                        0f at 0
                        -4f at (150 + index * 150)
                        0f at (420 + index * 150)
                    },
                ),
                label = "dot$index",
            )
            val dotAlpha by transition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1200
                        0.4f at 0
                        1f at (150 + index * 150)
                        0.4f at (420 + index * 150)
                    },
                ),
                label = "dotAlpha$index",
            )
            Box(
                modifier = Modifier
                    .offset(y = offsetY.dp)
                    .alpha(dotAlpha)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }
    }
}

/** 会话内日分隔胶囊。 */
@Composable
internal fun DayDividerLabel(label: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}
