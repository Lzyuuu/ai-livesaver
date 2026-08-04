package io.github.lzyuuu.ailivesaver

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import java.util.UUID

@Composable
internal fun BinderScreen(contentPadding: PaddingValues, store: WorldStore, onBack: () -> Unit, onChanged: () -> Unit, onOpenMessenger: (Long) -> Unit) {
    val draftId = remember { "binder-default" }; var step by rememberSaveable { mutableIntStateOf(0) }
    var answers by remember { mutableStateOf(BinderAnswers()) }; var candidates by remember { mutableStateOf<List<BinderCandidatePayload>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { store.getBinderDraft(draftId)?.let { step=it.step; answers=runCatching { BinderAnswers.fromJson(it.payload) }.getOrDefault(BinderAnswers()) } }
    fun save(next: Int) { step=next; store.putBinderDraft(BinderDraft(draftId,next,answers.toJson(),System.currentTimeMillis())) }
    fun field(label: String, value: String, update: (String)->Unit) { OutlinedTextField(value, update, label={Text(label)}, modifier=Modifier.fillMaxWidth(), minLines=2, colors=OutlinedTextFieldDefaults.colors(focusedTextColor=FancyCream,unfocusedTextColor=FancyCream,focusedLabelColor=FancyGold,unfocusedLabelColor=FancyCream)) }
    fun generate() { busy=true; error=null; val config=ProviderStore(LocalContext.current).loadFor(ProviderTask.World); ProviderTextClient.completeStructured(config,"Generate multiple fictional companion candidates. Output JSON only: {candidates:[{name,persona,relationship,reasons:[string]}]}.",answers.toJson()) { result -> result.onSuccess { response -> runCatching { validateBinderCandidateList(response.text) }.onSuccess { list -> candidates=list; save(5) }.onFailure { error="候选格式无效：${it.message}" } }.onFailure { error="Provider 失败：${it.message}" }; busy=false } }
    Column(Modifier.fillMaxSize().background(FancyInk).padding(contentPadding).padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement=Arrangement.spacedBy(12.dp)) {
        TextButton(onClick=onBack, modifier=Modifier.testTag("binder-back")){Text("返回桌面",color=FancyGold)}; Text("Binder",color=FancyCream,fontFamily=FontFamily.Serif); Text("第 ${minOf(step+1,5)} / 5 步",color=FancyCream)
        if(step<4) { val labels=listOf("关系与偏好" to listOf("relationship" to "期待的关系", "preferences" to "相处偏好"),"人格与沟通" to listOf("personality" to "人格气质", "communication" to "沟通方式"),"兴趣" to listOf("interests" to "兴趣与日常"),"边界与排除" to listOf("boundaries" to "边界", "exclusions" to "明确排除"))[step]; Text(labels.first,color=FancyCream); labels.second.forEach { (key,label) -> val value=when(key){"relationship"->answers.relationship;"preferences"->answers.preferences;"personality"->answers.personality;"communication"->answers.communication;"interests"->answers.interests;"boundaries"->answers.boundaries;else->answers.exclusions}; field(label,value){ v->answers=when(key){"relationship"->answers.copy(relationship=v);"preferences"->answers.copy(preferences=v);"personality"->answers.copy(personality=v);"communication"->answers.copy(communication=v);"interests"->answers.copy(interests=v);"boundaries"->answers.copy(boundaries=v);else->answers.copy(exclusions=v)} } }; Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){if(step>0)TextButton({save(step-1)}){Text("上一步",color=FancyGold)};Button({if(answers.relationship.isBlank()&&step==0) error="请填写关系偏好" else save(step+1)},modifier=Modifier.testTag("binder-next")){Text("下一步")}} } else if(step==4) { Text("Review",color=FancyCream); Text(answers.toJson(),color=FancyCream); Button({generate()},enabled=!busy,modifier=Modifier.testTag("binder-generate")){Text(if(busy)"生成中…" else "生成候选")} } else { Text("选择匹配",color=FancyCream); candidates.forEachIndexed { i,c -> Card(Modifier.fillMaxWidth().testTag("binder-candidate-$i")){Column(Modifier.padding(14.dp)){Text(c.name,color=FancyCream);Text(c.persona,color=FancyCream);Text(c.reasons.joinToString(" · "),color=FancyGold);Button({val id=store.addCharacter(c.name,c.persona,"resident","","","",CharacterCardV2.buildCardJson(c.name,CharacterProfileFields(description=c.persona,relationship=c.relationship))); store.confirmBinderCandidate("${draftId}-$i"); onChanged();onOpenMessenger(id)}){Text("确认并聊天")}}} }; TextButton({save(4)}){Text("重新生成",color=FancyGold)} }; error?.let{Text(it,color=FancyCream,modifier=Modifier.testTag("binder-error"))}
    }
}
