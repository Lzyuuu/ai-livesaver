package io.github.lzyuuu.ailivesaver

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

/** Root Producer 曲目卡（本地持久化：方案 + 歌词）。 */
internal data class ProducerTrack(
    val id: Long,
    val idea: String,
    val plan: String,
)

internal fun producerTracksLoad(prefs: android.content.SharedPreferences): List<ProducerTrack> =
    runCatching {
        val array = JSONArray(prefs.getString("tracks", "[]").orEmpty())
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            ProducerTrack(o.optLong("id"), o.optString("idea"), o.optString("plan"))
        }
    }.getOrDefault(emptyList())

internal fun producerTracksSave(prefs: android.content.SharedPreferences, tracks: List<ProducerTrack>) {
    val array = JSONArray()
    tracks.forEach { track ->
        array.put(JSONObject().put("id", track.id).put("idea", track.idea).put("plan", track.plan))
    }
    prefs.edit().putString("tracks", array.toString()).apply()
}

internal fun producerPlanPrompt(idea: String): String =
    "你是 Root——一位毒舌但可靠的制作人。请为下面的歌曲想法写一份「音乐方案」：包含风格、节奏、" +
        "编曲要点与一段原创歌词（主歌 + 副歌）。直接输出方案正文，不要寒暄。\n\n歌曲想法：$idea"

/**
 * Root Producer（阶段③，ref-83 对齐）：eyebrow「ROOT 工作室」+ 标题 + 说明 +
 * 「你的歌曲想法」输入 + 「让 Root 编写方案」（LLM 生成）+「你的曲目」列表。
 */
@Composable
internal fun RootProducerScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("root_producer", android.content.Context.MODE_PRIVATE) }
    var idea by rememberSaveable { mutableStateOf("") }
    var generating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var tracks by remember { mutableStateOf(producerTracksLoad(prefs)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ReferencePalette.PageBg)
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
            )
            .testTag("root-producer"),
    ) {
        Box(Modifier.fillMaxWidth()) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 4.dp)
                    .testTag("root-producer-back"),
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
                    "ROOT 工作室",
                    color = FancyCream.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    letterSpacing = 3.sp,
                )
                Text(
                    "Root Producer",
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
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "告诉 Root 你希望歌曲呈现怎样的感觉。她会先写方案和歌词，让你能在付费生成前修改一切。",
                    color = FancyCream.copy(alpha = 0.75f),
                    fontSize = 15.sp,
                )
            }
            item {
                OutlinedTextField(
                    value = idea,
                    onValueChange = { idea = it },
                    placeholder = { Text("你的歌曲想法", color = FancyCream.copy(alpha = 0.4f)) },
                    minLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("producer-idea"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = FancyCream,
                        unfocusedTextColor = FancyCream,
                        focusedBorderColor = FancyCream.copy(alpha = 0.35f),
                        unfocusedBorderColor = FancyCream.copy(alpha = 0.28f),
                        cursorColor = FancyGold,
                    ),
                )
            }
            item {
                Button(
                    onClick = {
                        if (generating) return@Button
                        val trimmed = idea.trim()
                        if (trimmed.isEmpty()) return@Button
                        val config = ProviderStore(context).loadTask(ProviderTask.Chat)
                        if (config == null) {
                            error = "尚未配置 Chat brain——先在设置里连接 Provider。"
                            return@Button
                        }
                        generating = true
                        error = null
                        // 方案是自然语言正文，走普通补全（Structured 能力探测未标记的 Provider 也可用）。
                        ProviderTextClient.complete(
                            config,
                            ProviderStore(context).loadDefaultGeneration(),
                            "你是 Root——一位毒舌但可靠的制作人。",
                            producerPlanPrompt(trimmed),
                        ) { result ->
                            generating = false
                            result.fold(
                                onSuccess = { response ->
                                    val plan = response.text.trim()
                                    if (plan.isBlank()) {
                                        error = "Root 没有返回方案，请重试。"
                                    } else {
                                        tracks = listOf(ProducerTrack(System.currentTimeMillis(), trimmed, plan)) + tracks
                                        producerTracksSave(prefs, tracks)
                                        idea = ""
                                    }
                                },
                                onFailure = { error = it.message ?: "生成失败，请重试。" },
                            )
                        }
                    },
                    enabled = idea.isNotBlank() && !generating,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (idea.isBlank()) Color(0xFF2A2A2E) else FancyGold,
                        contentColor = if (idea.isBlank()) FancyCream.copy(alpha = 0.4f) else Color(0xFF1B1B1E),
                    ),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("producer-plan"),
                ) {
                    if (generating) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("让 Root 编写方案", fontWeight = FontWeight.SemiBold)
                }
                error?.let {
                    Text(it, color = Color(0xFFB3261E), fontSize = 13.sp)
                }
            }
            item {
                Text("你的曲目", color = FancyCream.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                if (tracks.isEmpty()) {
                    Text(
                        "完成的歌曲会显示在这里。除非你将歌曲保存到“音乐”文件夹，否则它们始终保持秘密。",
                        color = FancyCream.copy(alpha = 0.5f),
                        fontSize = 14.sp,
                        modifier = Modifier.testTag("producer-empty"),
                    )
                }
            }
            items(tracks.size) { index ->
                val track = tracks[index]
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(ReferencePalette.Card)
                        .padding(14.dp)
                        .testTag("producer-track"),
                ) {
                    Text(track.idea, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(track.plan, color = FancyCream.copy(alpha = 0.75f), fontSize = 13.sp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = {
                            tracks = tracks.filterNot { it.id == track.id }
                            producerTracksSave(prefs, tracks)
                        }) { Text("删除", color = FancyCream.copy(alpha = 0.6f), fontSize = 12.sp) }
                    }
                }
            }
        }
    }
}
