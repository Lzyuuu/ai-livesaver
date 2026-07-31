package io.github.lzyuuu.ailivesaver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun BinderScreen(
    contentPadding: PaddingValues,
    store: WorldStore,
    onBack: () -> Unit,
    onChanged: () -> Unit,
    onOpenMessenger: (Long) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var vibe by rememberSaveable { mutableStateOf("") }
    var interests by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    fun buildMatch() {
        error = null
        runCatching {
            val trimmedName = name.trim()
            require(trimmedName.isNotEmpty()) { "请先给匹配对象起个名字。" }
            require(vibe.trim().isNotEmpty()) { "请填写你期待的相处感觉。" }
            val description = "一个适合与你相处的世界成员。兴趣：${interests.trim().ifBlank { "待探索" }}"
            val fields = CharacterProfileFields(
                handle = CharacterCardV2.slugHandle(trimmedName),
                description = description,
                personality = vibe.trim(),
                relationship = "由 Binder 匹配生成的伙伴",
            )
            val cardJson = CharacterCardV2.buildCardJson(trimmedName, fields)
            store.addCharacter(
                name = trimmedName,
                persona = CharacterCardV2.composePersona(fields),
                attentionTier = "resident",
                appearance = "由 Binder 生成的视觉身份",
                clothing = "",
                negativePrompt = "",
                cardJson = cardJson,
            )
        }.onSuccess { id ->
            onChanged()
            onOpenMessenger(id)
        }.onFailure { throwable -> error = throwable.message ?: "匹配失败，请重试。" }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(FancyInk)
            .padding(contentPadding)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = onBack, modifier = Modifier.testTag("binder-back")) {
            Text("返回桌面", color = FancyGold)
        }
        Text("Binder", color = FancyCream, fontFamily = FontFamily.Serif)
        Text("填写几项偏好，生成一位可以立即聊天的世界成员。", color = FancyCream)
        Card(colors = CardDefaults.cardColors(containerColor = FancyNavyMid)) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("名字") },
                    colors = binderFieldColors(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("binder-name"),
                )
                OutlinedTextField(
                    value = vibe,
                    onValueChange = { vibe = it; error = null },
                    label = { Text("期待的相处感觉") },
                    colors = binderFieldColors(),
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("binder-vibe"),
                )
                OutlinedTextField(
                    value = interests,
                    onValueChange = { interests = it; error = null },
                    label = { Text("兴趣偏好（可选）") },
                    colors = binderFieldColors(),
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("binder-interests"),
                )
            }
        }
        error?.let {
            Text(it, color = FancyCream, modifier = Modifier.testTag("binder-error"))
            TextButton(onClick = ::buildMatch, modifier = Modifier.testTag("binder-retry")) {
                Text("重试", color = FancyGold)
            }
        }
        Button(
            onClick = ::buildMatch,
            modifier = Modifier.fillMaxWidth().testTag("binder-build"),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = FancyGold,
                contentColor = FancyInk,
            ),
        ) { Text("Build my first match") }
    }
}

@Composable
private fun binderFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = FancyCream,
    unfocusedTextColor = FancyCream,
    focusedLabelColor = FancyGold,
    unfocusedLabelColor = FancyCream.copy(alpha = 0.75f),
    focusedBorderColor = FancyGold,
    unfocusedBorderColor = FancyGoldDim,
    cursorColor = FancyGold,
)
