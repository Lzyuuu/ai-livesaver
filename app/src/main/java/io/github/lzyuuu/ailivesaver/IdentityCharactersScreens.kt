package io.github.lzyuuu.ailivesaver

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun IdentityScreen(
    contentPadding: PaddingValues,
    store: WorldStore,
    onBack: () -> Unit,
    onChanged: () -> Unit,
) {
    val identity = remember { store.identity() }
    var name by rememberSaveable { mutableStateOf(identity.name.takeUnless { it == "你" }.orEmpty()) }
    var address by rememberSaveable { mutableStateOf(identity.addressPreference) }
    var bio by rememberSaveable { mutableStateOf(identity.bio) }
    var status by remember { mutableStateOf<String?>(null) }
    val saved = stringResource(R.string.identity_saved)
    val incomplete = stringResource(R.string.identity_name_required)

    SettingsList(contentPadding) {
        item {
            ScreenHeading(onBack, R.string.user_identity, R.string.user_identity_summary)
        }
        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.current_nickname)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text(stringResource(R.string.address_preference)) },
                supportingText = { Text(stringResource(R.string.address_preference_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it },
                label = { Text(stringResource(R.string.identity_bio)) },
                minLines = 4,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        status = incomplete
                    } else {
                        store.updateIdentity(name, address, bio)
                        onChanged()
                        status = saved
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.save_identity))
            }
        }
        status?.let { item { StatusCard(it) } }
    }
}

@Composable
internal fun CharacterManagerScreen(
    contentPadding: PaddingValues,
    store: WorldStore,
    revision: Int,
    onBack: () -> Unit,
    onChanged: () -> Unit,
) {
    val characters = remember(revision) { store.characters() }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    val editing = characters.firstOrNull { it.id == editingId }

    BackHandler(adding || editing != null) {
        adding = false
        editingId = null
    }
    if (adding || editing != null) {
        CharacterEditor(
            contentPadding = contentPadding,
            character = editing,
            canLeave = characters.count(ResidentCharacter::active) > 1,
            onBack = {
                adding = false
                editingId = null
            },
            onSave = { name, persona, tier, appearance, clothing, negative ->
                if (editing == null) {
                    store.addCharacter(name, persona, tier, appearance, clothing, negative)
                } else {
                    store.updateCharacter(
                        editing.copy(
                            name = name,
                            persona = persona,
                            attentionTier = tier,
                            appearance = appearance,
                            clothing = clothing,
                            negativePrompt = negative,
                        ),
                    )
                }
                onChanged()
                adding = false
                editingId = null
            },
            onActiveChange = editing?.let { character ->
                {
                    store.setCharacterActive(character.id, !character.active)
                    onChanged()
                    editingId = null
                }
            },
        )
        return
    }

    SettingsList(contentPadding) {
        item {
            ScreenHeading(onBack, R.string.world_members, R.string.world_members_summary)
            Button(
                onClick = { adding = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.add_resident_character))
            }
        }
        if (characters.isEmpty()) {
            item { StatusCard(stringResource(R.string.no_characters_yet)) }
        }
        items(characters, key = ResidentCharacter::id) { character ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { editingId = character.id },
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(character.name, fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(
                                when {
                                    !character.active -> R.string.departed
                                    character.attentionTier == "special_focus" ->
                                        R.string.special_focus
                                    else -> R.string.resident
                                },
                            ),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        character.persona,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                    )
                }
            }
        }
    }
}

@Composable
private fun CharacterEditor(
    contentPadding: PaddingValues,
    character: ResidentCharacter?,
    canLeave: Boolean,
    onBack: () -> Unit,
    onSave: (String, String, String, String, String, String) -> Unit,
    onActiveChange: (() -> Unit)?,
) {
    var name by rememberSaveable(character?.id) { mutableStateOf(character?.name.orEmpty()) }
    var persona by rememberSaveable(character?.id) { mutableStateOf(character?.persona.orEmpty()) }
    var tier by rememberSaveable(character?.id) {
        mutableStateOf(character?.attentionTier ?: "resident")
    }
    var appearance by rememberSaveable(character?.id) {
        mutableStateOf(character?.appearance.orEmpty())
    }
    var clothing by rememberSaveable(character?.id) {
        mutableStateOf(character?.clothing.orEmpty())
    }
    var negative by rememberSaveable(character?.id) {
        mutableStateOf(character?.negativePrompt.orEmpty())
    }

    SettingsList(contentPadding) {
        item {
            ScreenHeading(
                onBack,
                if (character == null) R.string.add_resident_character else R.string.edit_character,
                R.string.character_history_preserved,
            )
        }
        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.character_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = persona,
                onValueChange = { persona = it },
                label = { Text(stringResource(R.string.character_persona)) },
                minLines = 5,
                maxLines = 10,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Text(stringResource(R.string.attention_tier), fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = tier == "special_focus",
                    onClick = { tier = "special_focus" },
                    label = { Text(stringResource(R.string.special_focus)) },
                )
                FilterChip(
                    selected = tier == "resident",
                    onClick = { tier = "resident" },
                    label = { Text(stringResource(R.string.resident)) },
                )
            }
        }
        item {
            Text(stringResource(R.string.visual_identity), fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.visual_identity_summary),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            OutlinedTextField(
                value = appearance,
                onValueChange = { appearance = it },
                label = { Text(stringResource(R.string.fixed_appearance)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = clothing,
                onValueChange = { clothing = it },
                label = { Text(stringResource(R.string.usual_clothing)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = negative,
                onValueChange = { negative = it },
                label = { Text(stringResource(R.string.negative_prompt)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Button(
                onClick = {
                    onSave(name.trim(), persona.trim(), tier, appearance, clothing, negative)
                },
                enabled = name.isNotBlank() && persona.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.save))
            }
        }
        if (character != null && onActiveChange != null) {
            item {
                TextButton(
                    onClick = onActiveChange,
                    enabled = !character.active || canLeave,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (character.active) R.string.let_character_leave
                            else R.string.bring_character_back,
                        ),
                    )
                }
                if (character.active && !canLeave) {
                    Text(
                        stringResource(R.string.last_active_character),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsList(
    contentPadding: PaddingValues,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            end = 20.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
private fun ScreenHeading(onBack: () -> Unit, title: Int, summary: Int) {
    TextButton(onClick = onBack) { Text("‹  ${stringResource(R.string.back)}") }
    Text(
        stringResource(title),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
    )
    Text(
        stringResource(summary),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
