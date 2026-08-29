package io.github.lzyuuu.ailivesaver

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/** SE-05 语音与通话页的语音模型管理区：Whisper ASR + sherpa-onnx TTS 声音（须在 LazyColumn 内调用）。 */
internal fun LazyListScope.voiceModelsSection() {
    item(key = "voice-models-header") {
        Column {
            Text("语音模型", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Whisper 转写与 sherpa-onnx 朗读声音，全部离线运行。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    item(key = "voice-models-body") {
        VoiceModelsBody()
    }
}

@Composable
private fun VoiceModelsBody() {
    val context = LocalContext.current
    var revision by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var downloadingId by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0f) }

    val asr = remember(revision) { VoiceModelStore.asrModels(context) }
    val tts = remember(revision) { VoiceModelStore.ttsVoices(context) }

    fun startDownload(entry: VoiceModelEntry) {
        if (downloadingId != null) return
        downloadingId = entry.id
        progress = 0f
        VoiceModelStore.download(
            context = context,
            entry = entry,
            onProgress = { done, total -> if (total > 0) progress = done.toFloat() / total },
            onDone = {
                if (it.isSuccess) {
                    when (entry.kind) {
                        VoiceKind.ASR -> VoiceModelStore.setActiveAsr(context, entry.id)
                        VoiceKind.TTS -> VoiceModelStore.setActiveTts(context, entry.id)
                    }
                }
                downloadingId = null
                revision++
            },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        asr.forEach { entry ->
            VoiceModelRow(entry, downloadingId, progress, ::startDownload) {
                VoiceModelStore.remove(context, it)
                revision++
            }
        }
        tts.forEach { entry ->
            VoiceModelRow(entry, downloadingId, progress, ::startDownload) {
                VoiceModelStore.remove(context, it)
                revision++
            }
        }
    }
}

@Composable
private fun VoiceModelRow(
    entry: VoiceModelEntry,
    downloadingId: String?,
    progress: Float,
    onDownload: (VoiceModelEntry) -> Unit,
    onRemove: (VoiceModelEntry) -> Unit,
) {
    val context = LocalContext.current
    val ready = remember(entry.id, downloadingId) { VoiceModelStore.isReady(context, entry) }
    val active = when (entry.kind) {
        VoiceKind.ASR -> VoiceModelStore.activeAsrId(context) == entry.id
        VoiceKind.TTS -> VoiceModelStore.activeTtsId(context) == entry.id
    }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(entry.name, fontWeight = FontWeight.SemiBold)
                Text(
                    buildString {
                        append(entry.language.ifEmpty { if (entry.kind == VoiceKind.ASR) "多语言" else "" })
                        if (entry.bytes > 0) {
                            if (isNotEmpty()) append(" · ")
                            append(formatBytes(entry.bytes))
                        }
                        when {
                            downloadingId == entry.id -> append(" · 下载中 ${(progress * 100).toInt()}%")
                            ready && active -> append(if (entry.kind == VoiceKind.ASR) " · 已就绪（默认转写）" else " · 已就绪（默认朗读）")
                            ready -> append(" · 已就绪")
                        }
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (downloadingId == entry.id) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                }
            }
            if (ready) {
                IconButton(onClick = { onRemove(entry) }) {
                    Icon(Icons.Default.Delete, contentDescription = "删除 ${entry.name}")
                }
            } else {
                IconButton(onClick = { onDownload(entry) }, enabled = downloadingId == null) {
                    Icon(Icons.Default.Download, contentDescription = "下载 ${entry.name}")
                }
            }
        }
    }
}

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes.toDouble() / (1L shl 30))
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes.toDouble() / (1L shl 20))
    bytes >= 1L shl 10 -> "%.0f KB".format(bytes.toDouble() / (1L shl 10))
    else -> "$bytes B"
}

/** 聊天输入行的按住说话按钮：录音 → Whisper 转写 → 回调插入文本。 */
@Composable
internal fun VoiceInputButton(onTranscribed: (String) -> Unit) {
    val context = LocalContext.current
    var recording by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) recording = true
    }
    var pendingRecord by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(40.dp)
            .testTag("messenger-voice-input")
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!granted) {
                            pendingRecord = true
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            return@detectTapGestures
                        }
                        recording = true
                        val pcm = runCatching {
                            val recorder = VoiceRuntime.startRecording() ?: return@runCatching FloatArray(0)
                            VoiceRuntime.readPcm(recorder, maxSeconds = 20)
                        }.getOrDefault(FloatArray(0))
                        recording = false
                        if (pcm.isNotEmpty()) {
                            val text = VoiceRuntime.transcribe(context, pcm)
                            if (text.isNotBlank()) onTranscribed(text)
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (recording) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Default.GraphicEq, contentDescription = "按住说话", tint = FancyGold)
        }
    }
    if (pendingRecord && recording) pendingRecord = false
}

/** 消息朗读按钮：TTS 就绪时后台合成并播放。 */
@Composable
internal fun SpeakMessageButton(text: String) {
    val context = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    IconButton(
        onClick = {
            if (busy) return@IconButton
            busy = true
            Thread {
                VoiceRuntime.speak(context, text)
                busy = false
            }.start()
        },
        modifier = Modifier
            .size(28.dp)
            .testTag("messenger-speak"),
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp)
        } else {
            Icon(Icons.Default.VolumeUp, contentDescription = "朗读", tint = FancyGold)
        }
    }
}
