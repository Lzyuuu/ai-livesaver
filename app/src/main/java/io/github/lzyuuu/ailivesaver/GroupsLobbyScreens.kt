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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Groups 大厅（阶段③，ref-76/76b/76c 对齐）：「FANCY 群组」eyebrow +「群组」标题 +
 * 金色「新建」；空态文案；底部弹层「新群组」（名称 + 发生了什么（可选）+ 成员多选 +
 * 底部「创建」）。创建复用 Messenger 群组落库（persisted group + group: character）。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun GroupsLobbyScreen(
    store: WorldStore,
    revision: Int,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onChanged: () -> Unit,
    onOpenGroup: (Long) -> Unit,
) {
    var showNewSheet by remember { mutableStateOf(false) }
    var groupName by remember { mutableStateOf("") }
    var groupPrompt by remember { mutableStateOf("") }
    var selectedMemberIds by remember { mutableStateOf(emptySet<Long>()) }
    var createError by remember { mutableStateOf<String?>(null) }
    var createdGroupId by remember { mutableStateOf(0L) }

    val residents = remember(revision) { store.characters(includeDeparted = false).filter { !it.cardJson.startsWith("group:") } }
    val groups = remember(revision, createdGroupId) { store.persistedMessengerGroups() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ReferencePalette.PageBg)
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
            )
            .testTag("groups-lobby"),
    ) {
        Box(Modifier.fillMaxWidth()) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 4.dp)
                    .testTag("groups-back"),
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
                    "FANCY 群组",
                    color = FancyCream.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    letterSpacing = 3.sp,
                )
                Text(
                    "群组",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                "新建",
                color = FancyGold,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 20.dp)
                    .clickable { showNewSheet = true }
                    .testTag("groups-new"),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(ReferencePalette.Hairline),
        )
        if (groups.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                Text(
                    "还没有群组。点击“新建”，让多个角色进入同一个房间。",
                    color = FancyCream.copy(alpha = 0.55f),
                    fontSize = 15.sp,
                    modifier = Modifier
                        .padding(horizontal = 32.dp)
                        .testTag("groups-empty"),
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(groups, key = { it.id }) { group ->
                    val groupCharacter = remember(group.id, revision) {
                        store.characters().firstOrNull { it.cardJson == "group:${group.id}" }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable {
                                val id = groupCharacter?.id ?: return@clickable
                                onOpenGroup(id)
                            }
                            .background(ReferencePalette.Card)
                            .padding(14.dp)
                            .testTag("groups-row"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(group.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${group.memberIds.size} 位成员",
                                color = FancyCream.copy(alpha = 0.55f),
                                fontSize = 12.sp,
                            )
                        }
                        Text("进入", color = FancyGold, fontSize = 14.sp)
                    }
                }
            }
        }
    }

    if (showNewSheet) {
        ModalBottomSheet(
            onDismissRequest = { showNewSheet = false },
            sheetState = sheetState,
            containerColor = Color(0xFF232326),
        ) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(
                    "新群组",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("groups-sheet-title"),
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    placeholder = { Text("群组名称", color = FancyCream.copy(alpha = 0.4f)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("groups-new-name"),
                    colors = sheetFieldColors(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = groupPrompt,
                    onValueChange = { groupPrompt = it },
                    placeholder = { Text("发生了什么（可选）", color = FancyCream.copy(alpha = 0.4f)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("groups-new-prompt"),
                    colors = sheetFieldColors(),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "选择成员（至少 2 位）",
                    color = FancyCream.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(6.dp))
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .testTag("groups-member-list"),
                ) {
                    items(residents, key = { it.id }) { resident ->
                        val selected = resident.id in selectedMemberIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedMemberIds = if (selected) {
                                        selectedMemberIds - resident.id
                                    } else {
                                        selectedMemberIds + resident.id
                                    }
                                }
                                .padding(vertical = 8.dp)
                                .testTag("groups-member-${resident.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(resident.name.take(1).uppercase(), 36.dp, "")
                            Spacer(Modifier.width(12.dp))
                            Text(resident.name, color = Color.White, modifier = Modifier.weight(1f))
                            Box(
                                Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (selected) FancyGold else Color.Transparent),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) Text("✓", color = Color(0xFF1B1B1E), fontSize = 13.sp)
                            }
                        }
                    }
                }
                createError?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = MaterialThemeError, fontSize = 13.sp)
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        when {
                            groupName.isBlank() -> createError = "请填写群组名称"
                            selectedMemberIds.size < 2 -> createError = "至少选择 2 位成员"
                            else -> {
                                val memberNames = residents
                                    .filter { it.id in selectedMemberIds }
                                    .joinToString(", ") { it.name }
                                val persistedId = store.savePersistedMessengerGroup(
                                    PersistedMessengerGroup(0L, groupName.trim(), groupPrompt.trim(), selectedMemberIds.toList()),
                                )
                                val id = store.addCharacter(
                                    name = groupName.trim(),
                                    persona = "Group chat with $memberNames.",
                                    attentionTier = "resident",
                                    appearance = "",
                                    clothing = "",
                                    negativePrompt = "",
                                    cardJson = "group:$persistedId",
                                )
                                selectedMemberIds.forEach { memberId ->
                                    store.addMessage(id, "character:$memberId", "我加入了这个群组。")
                                }
                                createdGroupId = persistedId
                                showNewSheet = false
                                groupName = ""
                                groupPrompt = ""
                                selectedMemberIds = emptySet()
                                createError = null
                                onChanged()
                                onOpenGroup(id)
                            }
                        }
                    },
                    enabled = true,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (groupName.isBlank() && selectedMemberIds.isEmpty()) {
                            Color(0xFF3A3A3E)
                        } else {
                            FancyGold
                        },
                        contentColor = Color(0xFF1B1B1E),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                        .testTag("groups-create"),
                ) {
                    Text("创建", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun sheetFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = FancyCream,
    unfocusedTextColor = FancyCream,
    focusedBorderColor = FancyCream.copy(alpha = 0.35f),
    unfocusedBorderColor = FancyCream.copy(alpha = 0.28f),
    cursorColor = FancyGold,
)

private val MaterialThemeError = Color(0xFFB3261E)
