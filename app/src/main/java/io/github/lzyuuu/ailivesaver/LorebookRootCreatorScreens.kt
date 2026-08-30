package io.github.lzyuuu.ailivesaver

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
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
import org.json.JSONObject

// —— Lorebook 纯逻辑 ——

/** 世界事实条目归一化：去首尾空白、压缩连续空白并截断，空输入返回空串。 */
internal fun normalizeWorldFactEntry(raw: String): String =
    raw.trim().replace(Regex("\\s+"), " ").take(600)

/** 关键词输入归一化：中英文逗号/顿号/分号/空白分隔，去重、去空、各截 24 字，最多 12 个。 */
internal fun parseLorebookKeywords(raw: String): List<String> =
    raw.split(Regex("[,，、;；\\s]+"))
        .map { it.trim().take(24) }
        .filter { it.isNotEmpty() }
        .distinct()
        .take(12)

/** 关键词序列 → 存库字符串（统一 "，" 分隔）。 */
internal fun encodeLorebookKeywords(keywords: List<String>): String = keywords.joinToString("，")

// —— 书册 / 分组（SO-12，对齐参考 ref-81「已分享 / Root Sudo」两本书册）——

/** 书册名归一化：去首尾空白、压缩连续空白并截 24 字；空输入回退默认书册「已分享」。 */
internal fun normalizeLorebookBookName(raw: String): String =
    raw.trim().replace(Regex("\\s+"), " ").take(24).ifEmpty { LOREBOOK_SHARED_BOOK }

/** 分组名归一化：去首尾空白并截 24 字；空输入表示未分组。 */
internal fun normalizeLorebookGroupName(raw: String): String = raw.trim().take(24)

/** 书册展示顺序：默认「已分享」居首、「Root Sudo」次之（即使尚无条目也始终展示），
 *  其余按名称排序；去重。 */
internal fun orderedLorebookBooks(books: Collection<String>): List<String> {
    val ordered = mutableListOf<String>()
    fun addOnce(name: String) {
        if (name !in ordered) ordered += name
    }
    addOnce(LOREBOOK_SHARED_BOOK)
    addOnce(LOREBOOK_ROOT_BOOK)
    books.sortedWith(String.CASE_INSENSITIVE_ORDER).forEach { addOnce(it) }
    return ordered
}

/** 对话文本是否命中任一关键词（大小写不敏感）；无关键词 = 永不触发。 */
internal fun lorebookKeywordTriggered(keywords: List<String>, conversationText: String): Boolean {
    if (keywords.isEmpty()) return false
    val haystack = conversationText.lowercase()
    return keywords.any { keyword -> haystack.contains(keyword.lowercase()) }
}

/**
 * 世界书注入选择（对齐参考「使用世界书知识」语义）：
 * 总开关关闭 → 空；条目禁用 → 排除；带关键词条目仅在对话命中时注入；
 * 不带关键词的全局事实始终注入。输入顺序即优先级（pinned 已在查询中排前）。
 */
internal fun selectLorebookFacts(
    facts: List<WorldFact>,
    conversationText: String,
    lorebookEnabled: Boolean = true,
    limit: Int = 20,
): List<WorldFact> {
    if (!lorebookEnabled) return emptyList()
    return facts.asSequence()
        .filter { it.enabled }
        .filter { fact ->
            val keywords = parseLorebookKeywords(fact.keywords)
            keywords.isEmpty() || lorebookKeywordTriggered(keywords, conversationText)
        }
        .take(limit)
        .toList()
}

internal const val LOREBOOK_PREFS = "lorebook"
internal const val LOREBOOK_ENABLED_KEY = "lorebook_enabled"

fun readLorebookEnabled(prefs: android.content.SharedPreferences): Boolean =
    prefs.getBoolean(LOREBOOK_ENABLED_KEY, true)

// —— Root Creator 纯逻辑 ——

/** 描述驱动生成的角色卡草稿。 */
internal data class RootCreatorDraft(
    val name: String,
    val description: String,
    val personality: String,
    val scenario: String,
    val firstMessage: String,
)

/** 从描述里提取角色名：优先引号（「」『』“”""）内的词，否则取首个空白/标点分隔的词。 */
internal fun rootCreatorExtractName(description: String): String {
    val trimmed = description.trim()
    if (trimmed.isEmpty()) return ""
    val quoted = Regex("\"([^\"]+)\"|“([^”]+)”|『([^』]+)』|「([^」]+)」")
        .find(trimmed)
        ?.groupValues
        ?.drop(1)
        ?.firstOrNull { it.isNotBlank() }
    val name = quoted?.trim() ?: trimmed
        .split(Regex("[\\s，,。.、；;:：！!？?]"))
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
    return name.trim().take(24)
}

/** 从描述生成最小可用草稿；空描述返回 null。 */
internal fun rootCreatorDraftFromDescription(raw: String): RootCreatorDraft? {
    val description = raw.trim()
    if (description.isEmpty()) return null
    val name = rootCreatorExtractName(description)
    return RootCreatorDraft(
        name = name,
        description = description.take(1200),
        personality = description.take(800),
        scenario = "与 $name 在 {{user}} 的日常中展开互动。",
        firstMessage = "你好，我是${name}。很高兴见到你。",
    )
}

/** 草稿 -> 可保存的角色卡（复用 CharacterCardV2 的 SillyTavern V2 卡生成）。 */
internal data class RootCreatorCard(
    val fields: CharacterProfileFields,
    val cardJson: String,
)

internal fun rootCreatorBuildCard(draft: RootCreatorDraft): RootCreatorCard {
    val extracted = CharacterCardV2.extractAppearanceKeywords(draft.description)
    val fields = CharacterProfileFields(
        handle = CharacterCardV2.slugHandle(draft.name),
        description = draft.description,
        personality = draft.personality,
        scenario = draft.scenario,
        firstMessage = draft.firstMessage,
        visualStyle = extracted.visualStyle,
        age = extracted.age,
        ethnicity = extracted.ethnicity,
        skin = extracted.skin,
        eyes = extracted.eyes,
        hair = extracted.hair,
        body = extracted.body,
        appearanceSupplement = extracted.supplement,
        appearancePrompt = extracted.appearancePrompt,
        clothing = extracted.clothing,
        negativePrompt = extracted.negativePrompt,
    )
    return RootCreatorCard(
        fields = fields,
        cardJson = CharacterCardV2.buildCardJson(draft.name, fields),
    )
}

// —— 页面 ——

/**
 * Lorebook（对齐参考 ref-81「世界书」）：使用世界书知识总开关 + 书册分组与条目层级
 * （默认「已分享」「Root Sudo」两本书册，书内可选分组；条目含关键词触发、启用开关），
 * 存 world_facts（v28：keywords/enabled/book/group）。
 */
@Composable
internal fun LorebookScreen(
    contentPadding: PaddingValues,
    store: WorldStore,
    revision: Int,
    onBack: () -> Unit,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    var entry by rememberSaveable { mutableStateOf("") }
    var keywords by rememberSaveable { mutableStateOf("") }
    var groupName by rememberSaveable { mutableStateOf("") }
    var selectedBook by rememberSaveable { mutableStateOf(LOREBOOK_SHARED_BOOK) }
    var creatingBook by rememberSaveable { mutableStateOf(false) }
    var newBookName by rememberSaveable { mutableStateOf("") }
    var bookMenuOpen by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val facts = remember(revision) { store.worldFacts() }
    val books = remember(facts) {
        orderedLorebookBooks(facts.map { it.book } + listOf(LOREBOOK_SHARED_BOOK, LOREBOOK_ROOT_BOOK))
    }
    val lorebookPrefs = remember { context.getSharedPreferences(LOREBOOK_PREFS, android.content.Context.MODE_PRIVATE) }
    var lorebookEnabled by remember(revision) {
        mutableStateOf(readLorebookEnabled(lorebookPrefs))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ReferencePalette.PageBg)
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
            )
            .testTag("lorebook"),
    ) {
        Box(Modifier.fillMaxWidth()) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 4.dp)
                    .testTag("lorebook-back"),
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
                    "知识",
                    color = FancyCream.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    letterSpacing = 3.sp,
                )
                Text(
                    "世界书",
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
                // 总开关（对齐参考）。
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(ReferencePalette.Card)
                        .padding(14.dp)
                        .testTag("lorebook-master"),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("使用世界书知识", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Switch(
                            checked = lorebookEnabled,
                            onCheckedChange = {
                                lorebookPrefs.edit().putBoolean(LOREBOOK_ENABLED_KEY, it).apply()
                                lorebookEnabled = it
                                onChanged()
                            },
                            modifier = Modifier.testTag("lorebook-master-toggle"),
                        )
                    }
                    Text(
                        "在对话中关键字出现时注入匹配的事实。",
                        color = FancyCream.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = entry,
                    onValueChange = { entry = it },
                    placeholder = { Text("世界事实，例如：这座城市永远下着细雨。", color = FancyCream.copy(alpha = 0.4f)) },
                    minLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lorebook-entry"),
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
                OutlinedTextField(
                    value = keywords,
                    onValueChange = { keywords = it },
                    placeholder = { Text("触发关键词（逗号分隔，留空 = 始终注入）", color = FancyCream.copy(alpha = 0.4f)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lorebook-keywords"),
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
                // 书册选择（SO-12）：默认「已分享」「Root Sudo」，可新建书册。
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ReferencePalette.Card.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        stringResource(R.string.lorebook_book_picker_label),
                        color = FancyCream.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                    )
                    Box {
                        TextButton(
                            onClick = { bookMenuOpen = true },
                            modifier = Modifier.testTag("lorebook-book-picker"),
                        ) {
                            Text(
                                if (creatingBook) {
                                    stringResource(R.string.lorebook_book_new_option)
                                } else {
                                    selectedBook
                                },
                                color = FancyGold,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        DropdownMenu(
                            expanded = bookMenuOpen,
                            onDismissRequest = { bookMenuOpen = false },
                        ) {
                            books.forEach { book ->
                                DropdownMenuItem(
                                    text = { Text(book) },
                                    onClick = {
                                        selectedBook = book
                                        creatingBook = false
                                        bookMenuOpen = false
                                    },
                                    modifier = Modifier.testTag("lorebook-book-option-$book"),
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.lorebook_book_new_option)) },
                                onClick = {
                                    creatingBook = true
                                    bookMenuOpen = false
                                },
                                modifier = Modifier.testTag("lorebook-book-option-new"),
                            )
                        }
                    }
                    if (creatingBook) {
                        OutlinedTextField(
                            value = newBookName,
                            onValueChange = { newBookName = it },
                            label = { Text(stringResource(R.string.lorebook_book_new_hint), color = FancyCream.copy(alpha = 0.4f)) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("lorebook-book-new"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = FancyCream,
                                unfocusedTextColor = FancyCream,
                                focusedBorderColor = FancyCream.copy(alpha = 0.35f),
                                unfocusedBorderColor = FancyCream.copy(alpha = 0.28f),
                                cursorColor = FancyGold,
                            ),
                        )
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    placeholder = { Text(stringResource(R.string.lorebook_group_hint), color = FancyCream.copy(alpha = 0.4f)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lorebook-group"),
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
                        val body = normalizeWorldFactEntry(entry)
                        if (body.isEmpty()) {
                            notice = "先写点什么再保存。"
                            return@Button
                        }
                        val parsed = parseLorebookKeywords(keywords)
                        val targetBook = if (creatingBook) {
                            normalizeLorebookBookName(newBookName)
                        } else {
                            normalizeLorebookBookName(selectedBook)
                        }
                        store.addWorldFact(
                            body,
                            keywords = encodeLorebookKeywords(parsed),
                            book = targetBook,
                            group = normalizeLorebookGroupName(groupName),
                        )
                        entry = ""
                        keywords = ""
                        groupName = ""
                        newBookName = ""
                        creatingBook = false
                        selectedBook = targetBook
                        notice = if (targetBook == LOREBOOK_SHARED_BOOK) {
                            if (parsed.isEmpty()) "已保存到世界知识（始终注入）。" else "已保存，关键词命中时注入。"
                        } else {
                            context.getString(R.string.lorebook_saved_to_book, targetBook)
                        }
                        onChanged()
                    },
                    enabled = entry.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (entry.isBlank()) Color(0xFF2A2A2E) else FancyGold,
                        contentColor = if (entry.isBlank()) FancyCream.copy(alpha = 0.4f) else Color(0xFF1B1B1E),
                    ),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lorebook-save"),
                ) {
                    Text("保存世界事实", fontWeight = FontWeight.SemiBold)
                }
                notice?.let {
                    Text(it, color = FancyCream.copy(alpha = 0.7f), fontSize = 13.sp)
                }
            }
            item {
                Text(
                    "世界知识 · ${facts.size}",
                    color = FancyCream.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (facts.isEmpty()) {
                    Text(
                        "还没有事实条目。保存后它们会出现在这里。",
                        color = FancyCream.copy(alpha = 0.5f),
                        fontSize = 14.sp,
                        modifier = Modifier.testTag("lorebook-empty"),
                    )
                }
            }
            items(books, key = { it }) { book ->
                val bookFacts = facts.filter { it.book == book }
                val groups = bookFacts
                    .map { it.group }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .sorted()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(ReferencePalette.Card)
                        .padding(14.dp)
                        .testTag("lorebook-book-$book"),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(book, color = FancyGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(R.string.lorebook_entries_count, bookFacts.size),
                            color = FancyCream.copy(alpha = 0.55f),
                            fontSize = 12.sp,
                        )
                    }
                    if (bookFacts.isEmpty()) {
                        Text(
                            stringResource(R.string.lorebook_book_empty),
                            color = FancyCream.copy(alpha = 0.45f),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 8.dp).testTag("lorebook-empty-$book"),
                        )
                    } else {
                        Spacer(Modifier.height(8.dp))
                        bookFacts
                            .filter { it.group.isEmpty() }
                            .forEach { fact -> LorebookFactCard(fact, store, onChanged) { notice = it } }
                        groups.forEach { group ->
                            Text(
                                group,
                                color = FancyCream,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .padding(top = 10.dp, bottom = 2.dp)
                                    .testTag("lorebook-group-$book-$group"),
                            )
                            bookFacts
                                .filter { it.group == group }
                                .forEach { fact -> LorebookFactCard(fact, store, onChanged) { notice = it } }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 单条世界事实卡片（保留旧 testTag：lorebook-fact / lorebook-enable-{id} / lorebook-delete-{id}）。
 */
@Composable
private fun LorebookFactCard(
    fact: WorldFact,
    store: WorldStore,
    onChanged: () -> Unit,
    onNotice: (String) -> Unit,
) {
    val factKeywords = parseLorebookKeywords(fact.keywords)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(ReferencePalette.Card.copy(alpha = 0.7f))
            .padding(12.dp)
            .testTag("lorebook-fact"),
    ) {
        Text(fact.body, color = Color.White, fontSize = 14.sp)
        if (factKeywords.isNotEmpty()) {
            Text(
                "关键词：${factKeywords.joinToString("、")}",
                color = FancyGold.copy(alpha = 0.85f),
                fontSize = 12.sp,
            )
        } else {
            Text(
                "始终注入",
                color = FancyCream.copy(alpha = 0.45f),
                fontSize = 12.sp,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (fact.enabled) "启用" else "已停用",
                color = FancyCream.copy(alpha = 0.55f),
                fontSize = 12.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = fact.enabled,
                    onCheckedChange = { next ->
                        store.setWorldFactEnabled(fact.id, next)
                        onChanged()
                    },
                    modifier = Modifier.testTag("lorebook-enable-${fact.id}"),
                )
                TextButton(
                    onClick = {
                        store.deleteWorldFact(fact.id)
                        onNotice("已删除一条世界事实。")
                        onChanged()
                    },
                    modifier = Modifier.testTag("lorebook-delete-${fact.id}"),
                ) { Text("删除", color = FancyCream.copy(alpha = 0.6f), fontSize = 12.sp) }
            }
        }
    }
}

/**
 * Root Creator（对齐商店「Root 造卡」）：描述输入 -> 本地草稿成卡（复用 CharacterCardV2）-> 展示结果 ->
 * 复用 WorldStore.addCharacter 保存角色。
 */
@Composable
internal fun RootCreatorScreen(
    contentPadding: PaddingValues,
    store: WorldStore,
    onBack: () -> Unit,
    onChanged: () -> Unit,
) {
    var description by rememberSaveable { mutableStateOf("") }
    var draft by remember { mutableStateOf<RootCreatorDraft?>(null) }
    var card by remember { mutableStateOf<RootCreatorCard?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ReferencePalette.PageBg)
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
            )
            .testTag("root-creator"),
    ) {
        Box(Modifier.fillMaxWidth()) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 4.dp)
                    .testTag("root-creator-back"),
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
                    "ROOT 造卡",
                    color = FancyCream.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    letterSpacing = 3.sp,
                )
                Text(
                    "Root Creator",
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
                    "给 Root 一个简单的角色想法，她写出与其他地方一致的 SillyTavern 风格角色卡，交给你审核后保存。",
                    color = FancyCream.copy(alpha = 0.75f),
                    fontSize = 15.sp,
                )
            }
            item {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = { Text("例如：小满，穿黑色风衣的女法医，冷静毒舌。", color = FancyCream.copy(alpha = 0.4f)) },
                    minLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("root-creator-input"),
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
                        val nextDraft = rootCreatorDraftFromDescription(description)
                        if (nextDraft == null) {
                            notice = "先描述一下角色。"
                            return@Button
                        }
                        draft = nextDraft
                        card = rootCreatorBuildCard(nextDraft)
                        notice = null
                    },
                    enabled = description.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (description.isBlank()) Color(0xFF2A2A2E) else FancyGold,
                        contentColor = if (description.isBlank()) FancyCream.copy(alpha = 0.4f) else Color(0xFF1B1B1E),
                    ),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("root-creator-generate"),
                ) {
                    Text("让 Root 写卡", fontWeight = FontWeight.SemiBold)
                }
                notice?.let {
                    Text(it, color = FancyCream.copy(alpha = 0.7f), fontSize = 13.sp)
                }
            }
            item {
                val current = card
                if (current == null) {
                    Text(
                        "生成的角色卡会显示在这里，审核后再保存到角色。",
                        color = FancyCream.copy(alpha = 0.5f),
                        fontSize = 14.sp,
                        modifier = Modifier.testTag("root-creator-empty"),
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(ReferencePalette.Card)
                            .padding(14.dp)
                            .testTag("root-creator-result"),
                    ) {
                        Text(current.fields.handle, color = FancyGold, fontSize = 12.sp, letterSpacing = 1.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(CharacterCardV2.composePersona(current.fields), color = Color.White, fontSize = 14.sp)
                        Spacer(Modifier.height(10.dp))
                        val preview = JSONObject(current.cardJson).toString(2)
                        OutlinedTextField(
                            value = preview,
                            onValueChange = {},
                            readOnly = true,
                            minLines = 8,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("root-creator-card-json"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = FancyCream.copy(alpha = 0.85f),
                                unfocusedTextColor = FancyCream.copy(alpha = 0.85f),
                                focusedBorderColor = FancyCream.copy(alpha = 0.25f),
                                unfocusedBorderColor = FancyCream.copy(alpha = 0.2f),
                                cursorColor = FancyGold,
                            ),
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                val target = card ?: return@Button
                                val savedName = draft?.name ?: return@Button
                                val saved = store.addCharacter(
                                    savedName,
                                    CharacterCardV2.composePersona(target.fields),
                                    "resident",
                                    CharacterCardV2.composeFixedFeature(target.fields),
                                    target.fields.clothing,
                                    target.fields.negativePrompt,
                                    target.cardJson,
                                )
                                notice = "已保存角色：$savedName（#$saved）"
                                draft = null
                                card = null
                                description = ""
                                onChanged()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = FancyGold,
                                contentColor = Color(0xFF1B1B1E),
                            ),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("root-creator-save"),
                        ) {
                            Text("保存为角色", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}