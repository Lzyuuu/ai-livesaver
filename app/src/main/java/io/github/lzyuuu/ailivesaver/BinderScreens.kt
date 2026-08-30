package io.github.lzyuuu.ailivesaver

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BinderErrorRed = Color(0xFFE08A8A)

private data class BinderSection(val titleRes: Int, val fields: List<Pair<String, Int>>)

private val binderSections = listOf(
    BinderSection(
        R.string.binder_section_relationship,
        listOf(
            "relationship" to R.string.binder_field_relationship,
            "preferences" to R.string.binder_field_preferences,
        ),
    ),
    BinderSection(
        R.string.binder_section_personality,
        listOf(
            "personality" to R.string.binder_field_personality,
            "communication" to R.string.binder_field_communication,
        ),
    ),
    BinderSection(
        R.string.binder_section_interests,
        listOf("interests" to R.string.binder_field_interests),
    ),
    BinderSection(
        R.string.binder_section_boundaries,
        listOf(
            "boundaries" to R.string.binder_field_boundaries,
            "exclusions" to R.string.binder_field_exclusions,
        ),
    ),
)

@Composable
internal fun BinderScreen(contentPadding: PaddingValues, store: WorldStore, onBack: () -> Unit, onChanged: () -> Unit, onOpenMessenger: (Long) -> Unit) {
    val context = LocalContext.current
    val draftId = remember { "binder-default" }
    var answers by remember { mutableStateOf(BinderAnswers()) }
    var candidates by remember { mutableStateOf<List<BinderCandidatePayload>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        // 单页问卷不再按 step 隐藏分区；旧分步草稿只恢复答案内容，UI 不读取 step。
        store.getBinderDraft(draftId)?.let { draft ->
            answers = runCatching { BinderAnswers.fromJson(draft.payload) }.getOrDefault(BinderAnswers())
        }
    }

    fun answerOf(key: String): String = when (key) {
        "relationship" -> answers.relationship
        "preferences" -> answers.preferences
        "personality" -> answers.personality
        "communication" -> answers.communication
        "interests" -> answers.interests
        "boundaries" -> answers.boundaries
        else -> answers.exclusions
    }

    fun updateAnswer(key: String, value: String) {
        answers = when (key) {
            "relationship" -> answers.copy(relationship = value)
            "preferences" -> answers.copy(preferences = value)
            "personality" -> answers.copy(personality = value)
            "communication" -> answers.copy(communication = value)
            "interests" -> answers.copy(interests = value)
            "boundaries" -> answers.copy(boundaries = value)
            else -> answers.copy(exclusions = value)
        }
        // 编辑答案使既有候选失效：草稿回到未生成状态，确认门禁仍要求生成成功后 step==5。
        candidates = emptyList()
        store.putBinderDraft(BinderDraft(draftId, 0, answers.toJson(), System.currentTimeMillis()))
    }

    fun generate() {
        if (busy) return
        if (answers.relationship.isBlank()) {
            error = context.getString(R.string.binder_error_relationship_required)
            return
        }
        busy = true
        error = null
        BinderOrchestrator.generate(context, draftId, answers) { result ->
            result
                .onSuccess { generation -> candidates = generation.candidates }
                .onFailure { error = context.getString(R.string.binder_error_provider, it.message.orEmpty()) }
            busy = false
        }
    }

    LaunchedEffect(candidates) {
        if (candidates.isNotEmpty()) scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(FancyInk)
            .padding(contentPadding)
            .padding(horizontal = 20.dp)
            .verticalScroll(scrollState),
    ) {
        Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart).testTag("binder-back"),
            ) { Text(stringResource(R.string.desktop_back_to_home), color = FancyGold) }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.binder_eyebrow), color = FancyGold, fontSize = 12.sp, letterSpacing = 4.sp)
                Text(
                    stringResource(R.string.binder_title),
                    color = FancyCream,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                )
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(top = 12.dp).testTag("binder-questionnaire"),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("✦", color = FancyGold, fontSize = 26.sp)
                Text(
                    stringResource(R.string.binder_headline),
                    color = FancyCream,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.binder_subhead),
                    color = FancyCream.copy(alpha = .7f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }
            binderSections.forEach { section ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(section.titleRes),
                        color = FancyCream,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                    section.fields.forEach { (key, labelRes) ->
                        OutlinedTextField(
                            value = answerOf(key),
                            onValueChange = { updateAnswer(key, it) },
                            label = { Text(stringResource(labelRes)) },
                            modifier = Modifier.fillMaxWidth().testTag("binder-field-$key"),
                            minLines = 2,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = FancyCream,
                                unfocusedTextColor = FancyCream,
                                focusedLabelColor = FancyGold,
                                unfocusedLabelColor = FancyCream,
                            ),
                        )
                    }
                }
            }
            Button(
                onClick = { generate() },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("binder-generate"),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FancyGold, contentColor = FancyInk),
            ) {
                Text(
                    stringResource(if (busy) R.string.binder_generating else R.string.binder_generate),
                    fontWeight = FontWeight.Bold,
                )
            }
            error?.let {
                Text(it, color = BinderErrorRed, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().testTag("binder-error"))
            }
        }
        if (candidates.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 24.dp).testTag("binder-results"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.binder_results_title),
                    color = FancyCream,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
                candidates.forEachIndexed { index, candidate ->
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("binder-candidate-$index"),
                        shape = RoundedCornerShape(13.dp),
                        colors = CardDefaults.cardColors(containerColor = FancyNavyMid),
                        border = BorderStroke(1.dp, FancyGoldDim.copy(alpha = .4f)),
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(candidate.name, color = FancyCream, fontWeight = FontWeight.Bold)
                            Text(candidate.persona, color = FancyCream)
                            Text(candidate.reasons.joinToString(" · "), color = FancyGold, fontSize = 13.sp)
                            Button(
                                onClick = {
                                    val id = BinderOrchestrator.confirm(context, draftId, index, candidate)
                                    onChanged()
                                    onOpenMessenger(id)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = FancyGold, contentColor = FancyInk),
                            ) { Text(stringResource(R.string.binder_confirm_chat)) }
                        }
                    }
                }
            }
        } else {
            Spacer(Modifier.height(24.dp))
        }
    }
}
