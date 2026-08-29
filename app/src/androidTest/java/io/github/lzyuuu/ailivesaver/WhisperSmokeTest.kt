package io.github.lzyuuu.ailivesaver

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mrj.fancyai.FancyApplication
import com.mrj.fancyai.service.voice.WhisperEngine
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 阶段② SE-05 ASR 冒烟：ggml-tiny.bin（设置页下载就位）+ 16kHz 单声道 wav
 * （宿主 macOS say 合成，push 到 /data/local/tmp/voice-test.wav），断言转写命中关键词。
 */
@RunWith(AndroidJUnit4::class)
class WhisperSmokeTest {
    @Test
    fun transcribesRealSpeechWithinTimeout() {
        assertTrue("libfancy_whisper 未编入或 ABI 不符", WhisperEngine.available)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model = File(context.filesDir, "voice-models/asr-whisper-tiny/ggml-tiny.bin")
        if (!model.isFile) {
            Log.w("WhisperSmokeTest", "模型未就位（先在设置→语音模型下载），跳过")
            return
        }
        val app = context.applicationContext as FancyApplication
        assertTrue("whisper init 失败", WhisperEngine.init(app, model.absolutePath))
        val wav = File("/data/local/tmp/voice-test.wav")
        if (!wav.isFile) {
            Log.w("WhisperSmokeTest", "测试语音未 push，跳过")
            return
        }
        val pcm = decodeWavTo16kMonoFloat(wav)
        assertTrue("wav 解码为空", pcm.isNotEmpty())
        val executor = Executors.newSingleThreadExecutor()
        val text = executor.submit<String> { WhisperEngine.transcribe(pcm, threads = 2, lang = "en") }
            .get(300, TimeUnit.SECONDS)
        executor.shutdownNow()
        Log.i("WhisperSmokeTest", "transcribed: $text")
        assertTrue("转写为空", text.isNotBlank())
        val normalized = text.lowercase()
        assertTrue(
            "未命中关键词: $text",
            listOf("hello", "fancy", "voice", "test").any { normalized.contains(it) },
        )
    }

    private fun decodeWavTo16kMonoFloat(file: File): FloatArray {
        val bytes = file.readBytes()
        // 标准 44 字节头；找到 data 块后按 int16 LE 归一化。
        var offset = 12
        var dataOffset = 44
        while (offset + 8 <= bytes.size) {
            val id = String(bytes, offset, 4, Charsets.US_ASCII)
            val size = readLeInt(bytes, offset + 4)
            if (id == "data") {
                dataOffset = offset + 8
                break
            }
            offset += 8 + size + (size and 1)
        }
        val count = (bytes.size - dataOffset) / 2
        val pcm = FloatArray(count)
        for (i in 0 until count) {
            val lo = bytes[dataOffset + i * 2].toInt() and 0xFF
            val hi = bytes[dataOffset + i * 2 + 1].toInt()
            pcm[i] = ((hi shl 8) or lo).toShort() / 32768f
        }
        return pcm
    }

    private fun readLeInt(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or
            ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)
}
