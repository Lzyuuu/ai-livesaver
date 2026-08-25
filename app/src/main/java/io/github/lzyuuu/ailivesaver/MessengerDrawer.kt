package io.github.lzyuuu.ailivesaver

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class ChatControl { AutoImage, AllowY, AllowUstagram, AllowRebbit, WebSearch }

data class ChatControls(
    val autoImageGeneration: Boolean = false,
    val allowPostY: Boolean = false,
    val allowPostUstagram: Boolean = false,
    val allowPostRebbit: Boolean = false,
    val webSearchEnabled: Boolean = false,
) {
    fun withToggle(control: ChatControl, enabled: Boolean) = when (control) {
        ChatControl.AutoImage -> copy(autoImageGeneration = enabled)
        ChatControl.AllowY -> copy(allowPostY = enabled)
        ChatControl.AllowUstagram -> copy(allowPostUstagram = enabled)
        ChatControl.AllowRebbit -> copy(allowPostRebbit = enabled)
        ChatControl.WebSearch -> copy(webSearchEnabled = enabled)
    }
}

// 5 个开关按角色持久化到 member_world_context per-member KV（spec-v451 §1，2026-08-25 访谈定）。
// 授权三开关默认开（维持世界活性），自动配图与网络检索默认关。
fun defaultChatControls() = ChatControls(
    autoImageGeneration = false,
    allowPostY = true,
    allowPostUstagram = true,
    allowPostRebbit = true,
    webSearchEnabled = false,
)

internal fun encodeChatControls(controls: ChatControls): String = listOf(
    "auto_image=${if (controls.autoImageGeneration) 1 else 0}",
    "allow_y=${if (controls.allowPostY) 1 else 0}",
    "allow_ustagram=${if (controls.allowPostUstagram) 1 else 0}",
    "allow_rebbit=${if (controls.allowPostRebbit) 1 else 0}",
    "web_search=${if (controls.webSearchEnabled) 1 else 0}",
).joinToString(",")

internal fun decodeChatControls(raw: String): ChatControls {
    val flags = raw.split(',')
        .mapNotNull { entry ->
            val separator = entry.indexOf('=')
            if (separator <= 0) null else entry.take(separator) to entry.substring(separator + 1)
        }
        .toMap()
    return ChatControls(
        autoImageGeneration = flags["auto_image"]?.let { it == "1" } ?: false,
        allowPostY = flags["allow_y"]?.let { it == "1" } ?: true,
        allowPostUstagram = flags["allow_ustagram"]?.let { it == "1" } ?: true,
        allowPostRebbit = flags["allow_rebbit"]?.let { it == "1" } ?: true,
        webSearchEnabled = flags["web_search"]?.let { it == "1" } ?: false,
    )
}

// P0：ChatControls 必须有 Saver 才能进 rememberSaveable，否则打开 1:1 聊天即崩溃。
val ChatControlsSaver = listSaver<ChatControls, Boolean>(
    save = {
        listOf(
            it.autoImageGeneration,
            it.allowPostY,
            it.allowPostUstagram,
            it.allowPostRebbit,
            it.webSearchEnabled,
        )
    },
    restore = {
        ChatControls(it[0], it[1], it[2], it[3], it[4])
    },
)

fun shouldConfirmChatExit(sending: Boolean, activeReply: Boolean) = sending || activeReply

// 手动添加记忆必须挂在仍处于时间线上的用户消息上（rememberIfCurrent 只认 active 用户消息）。
internal fun latestUserMessageForMemory(messages: List<ChatMessage>): ChatMessage? =
    messages.lastOrNull { it.sender == "user" && it.active }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MessengerControlDrawer(
    controls: ChatControls,
    memories: List<LongTermMemory>,
    onControlsChanged: (ChatControls) -> Unit,
    onAddMemory: (String) -> Unit,
    onPinMemory: (Long, Boolean) -> Unit,
    onDeleteMemory: (Long) -> Unit,
    onConsolidate: () -> Unit,
    onDismiss: () -> Unit,
) {
    var tab by remember { mutableStateOf(0) }
    var draft by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        TabRow(selectedTabIndex = tab) {
            Tab(tab == 0, { tab = 0 }, text = { Text("对话控制") })
            Tab(tab == 1, { tab = 1 }, text = { Text("记忆管理") })
        }
        if (tab == 0) {
            Column(Modifier.padding(16.dp)) {
                ToggleRow("回复后自动配图", controls.autoImageGeneration) { onControlsChanged(controls.withToggle(ChatControl.AutoImage, it)) }
                ToggleRow("允许发布到 Y", controls.allowPostY) { onControlsChanged(controls.withToggle(ChatControl.AllowY, it)) }
                ToggleRow("允许发布到 Ustagram", controls.allowPostUstagram) { onControlsChanged(controls.withToggle(ChatControl.AllowUstagram, it)) }
                ToggleRow("允许发布到 Rebbit", controls.allowPostRebbit) { onControlsChanged(controls.withToggle(ChatControl.AllowRebbit, it)) }
                ToggleRow("DuckDuckGo 网络检索", controls.webSearchEnabled) { onControlsChanged(controls.withToggle(ChatControl.WebSearch, it)) }
            }
        } else {
            Column(Modifier.padding(16.dp)) {
                memories.forEach { memory ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("• ${memory.body}", Modifier.weight(1f))
                        TextButton(onClick = { onPinMemory(memory.id, !memory.pinned) }) {
                            Text(if (memory.pinned) "取消置顶" else "置顶")
                        }
                        TextButton(onClick = { onDeleteMemory(memory.id) }) { Text("删除") }
                    }
                }
                OutlinedTextField(draft, { draft = it }, Modifier.fillMaxWidth(), label = { Text("新增长期记忆") })
                TextButton(onClick = { if (draft.isNotBlank()) { onAddMemory(draft.trim()); draft = "" } }) { Text("添加记忆") }
                TextButton(onClick = onConsolidate) { Text("立即整理记忆") }
            }
        }
    }
}

@Composable private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) { Text(label, Modifier.weight(1f)); Switch(checked, onCheckedChange) }
}

@Composable
internal fun StreamingExitDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("回复正在生成") }, text = { Text("离开将中断当前回复，确定要退出吗？") }, confirmButton = { TextButton(onClick = onConfirm) { Text("退出并中断") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("继续等待") } })
}
