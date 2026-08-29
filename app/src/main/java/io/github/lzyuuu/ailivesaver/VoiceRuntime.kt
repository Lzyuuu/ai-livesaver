package io.github.lzyuuu.ailivesaver

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.mrj.fancyai.FancyApplication
import com.mrj.fancyai.service.voice.WhisperEngine
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 语音运行时（阶段② SE-05）：TTS 引擎常驻 + 16kHz PCM 录音 → Whisper 转写。
 * TTS 模型为 sherpa-onnx VITS 声音包（解包目录自动探测 model/tokens/espeak-ng-data）。
 */
internal object VoiceRuntime {
    private const val TAG = "VoiceRuntime"
    private const val SAMPLE_RATE = 16000

    private var tts: OfflineTts? = null
    private var ttsEntryId: String? = null
    private val playing = AtomicBoolean(false)

    @Synchronized
    fun ttsReady(context: Context): Boolean = ensureTts(context) != null

    @Synchronized
    private fun ensureTts(context: Context): OfflineTts? {
        val entry = VoiceModelStore.activeTtsEntry(context) ?: return null
        if (tts != null && ttsEntryId == entry.id) return tts
        tts?.free()
        tts = null
        val dir = VoiceModelStore.dir(context, entry)
        val model = VoiceModelStore.ttsModelFile(context, entry) ?: return null
        val tokens = dir.listFiles().orEmpty().firstOrNull { it.name == "tokens.txt" }
        val lexicon = dir.listFiles().orEmpty().firstOrNull { it.name.startsWith("lexicon") }
        val dataDir = dir.listFiles().orEmpty().firstOrNull { it.isDirectory && it.name.startsWith("espeak") }
        return runCatching {
            OfflineTts(
                assetManager = null,
                config = OfflineTtsConfig(
                    model = OfflineTtsModelConfig(
                        vits = OfflineTtsVitsModelConfig(
                            model = model.absolutePath,
                            lexicon = lexicon?.absolutePath ?: "",
                            tokens = tokens?.absolutePath ?: "",
                            dataDir = dataDir?.absolutePath ?: "",
                        ),
                        numThreads = 2,
                    ),
                ),
            )
        }.onSuccess {
            tts = it
            ttsEntryId = entry.id
        }.getOrNull()
    }

    /** 文本转语音并阻塞播放；返回时长 ms（未就绪/失败返回 -1）。 */
    fun speak(context: Context, text: String): Int {
        val engine = ensureTts(context) ?: run {
            android.util.Log.i(TAG, "speak: tts not ready")
            return -1
        }
        val trimmed = text.trim()
        if (trimmed.isEmpty() || !playing.compareAndSet(false, true)) return -1
        return try {
            val started = System.currentTimeMillis()
            val audio = runCatching { engine.generate(trimmed, sid = 0, speed = 1.0f) }
                .onFailure { android.util.Log.e(TAG, "generate failed", it) }
                .getOrNull()
                ?: return -1
            val duration = play(audio)
            android.util.Log.i(
                TAG,
                "speak ok: chars=${trimmed.length} samples=${audio.samples?.size} rate=${audio.sampleRate} " +
                    "genMs=${System.currentTimeMillis() - started} playMs=$duration",
            )
            duration
        } finally {
            playing.set(false)
        }
    }

    private fun play(audio: GeneratedAudio): Int {
        val samples = audio.samples ?: return -1
        if (samples.isEmpty()) return -1
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(audio.sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * 4)
            .build()
        return runCatching {
            track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            track.play()
            (samples.size * 1000L / audio.sampleRate).toInt()
        }.getOrElse {
            track.release()
            -1
        }
    }

    fun asrReady(context: Context): Boolean {
        if (!WhisperEngine.available) return false
        return VoiceModelStore.activeAsrEntry(context) != null
    }

    /** 压按说话：录音 [maxSeconds] 秒或到 stop()，返回 16kHz float PCM。 */
    fun startRecording(): AudioRecord? {
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        if (minBuffer <= 0) return null
        return runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_FLOAT,
                maxOf(minBuffer, SAMPLE_RATE * 4 * 30),
            )
        }.getOrNull()
    }

    fun readPcm(recorder: AudioRecord, maxSeconds: Int = 30): FloatArray {
        val chunk = FloatArray(SAMPLE_RATE)
        val buffer = mutableListOf<Float>()
        recorder.startRecording()
        val deadline = System.currentTimeMillis() + maxSeconds * 1000L
        while (System.currentTimeMillis() < deadline && buffer.size < SAMPLE_RATE * maxSeconds) {
            val read = recorder.read(chunk, 0, chunk.size, AudioRecord.READ_BLOCKING)
            if (read <= 0) break
            for (i in 0 until read) buffer.add(chunk[i])
            if (buffer.size >= SAMPLE_RATE) {
                // 已有至少 1 秒：检测尾部静音提前结束。
                val tail = buffer.takeLast(SAMPLE_RATE / 2)
                if (tail.maxOfOrNull { kotlin.math.abs(it) } ?: 0f < 0.01f) break
            }
        }
        recorder.stop()
        recorder.release()
        return buffer.toFloatArray()
    }

    fun transcribe(context: Context, pcm: FloatArray): String {
        if (pcm.size < SAMPLE_RATE / 4) return ""
        val entry = VoiceModelStore.activeAsrEntry(context) ?: return ""
        val app = context.applicationContext as? FancyApplication ?: FancyApplication.u ?: return ""
        if (!WhisperEngine.available) return ""
        val modelFile = VoiceModelStore.asrFile(context, entry) ?: return ""
        if (!WhisperEngine.nativeWhisperLoaded()) {
            if (!WhisperEngine.init(app, modelFile.absolutePath)) return ""
        }
        return WhisperEngine.transcribe(pcm, threads = 2, lang = "")
    }
}
