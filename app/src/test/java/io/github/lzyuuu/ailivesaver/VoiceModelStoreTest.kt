package io.github.lzyuuu.ailivesaver

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceModelStoreTest {
    @Test
    fun parsesAsrAndTtsEntriesFromManifestSnippet() {
        val json = """
        {"components":[
          {"id":"asr-whisper-tiny","type":"asr","name":"Speech-to-Text — Whisper Tiny","url":"https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin","sizeBytes":77691713,"sha256":"be07e048e1000000000000000000000000000000000000000000000000000000"},
          {"id":"tts-zh-cn-huayan","type":"tts","name":"Huayan","language":"Chinese","url":"https://huggingface.co/Mr-J-369/TTS-Chinese/resolve/main/tts-zh-cn-huayan.zip","sizeBytes":67437012,"sha256":"0c1acb7b77890000000000000000000000000000000000000000000000000000"},
          {"id":"gguf-gemma4-e4b","type":"llm","name":"ignored","url":"https://example.com/x","sizeBytes":1,"sha256":"aa"}
        ]}
        """.trimIndent()
        val entries = VoiceModelStore.parseManifest(json)
        assertEquals(2, entries.size)
        val asr = entries.first { it.kind == VoiceKind.ASR }
        assertEquals("asr-whisper-tiny", asr.id)
        assertEquals("Whisper Tiny", asr.name)
        assertEquals(77691713L, asr.bytes)
        val tts = entries.first { it.kind == VoiceKind.TTS }
        assertEquals("Chinese", tts.language)
        assertTrue(tts.url.endsWith(".zip"))
    }

    @Test
    fun rejectsEntriesWithoutHttpsOrSha256() {
        val json = """
        {"components":[
          {"id":"bad-url","type":"tts","name":"x","url":"http://insecure/x.zip","sizeBytes":1,"sha256":"${"a".repeat(64)}"},
          {"id":"bad-sha","type":"asr","name":"y","url":"https://ok/x.bin","sizeBytes":1,"sha256":"short"}
        ]}
        """.trimIndent()
        assertEquals(0, VoiceModelStore.parseManifest(json).size)
    }

    @Test
    fun bundledManifestCoversWhisperAndChineseTts() {
        val file = File("src/main/assets/fancy/manifest.json")
        assertTrue("manifest.json 缺失", file.isFile)
        val entries = VoiceModelStore.parseManifest(file.readText())
        val asr = entries.filter { it.kind == VoiceKind.ASR }
        val tts = entries.filter { it.kind == VoiceKind.TTS }
        assertTrue("缺 Whisper 条目", asr.any { it.id == "asr-whisper-tiny" })
        assertTrue(asr.all { it.url.startsWith("https://huggingface.co/ggerganov/whisper.cpp/") })
        assertTrue("缺中文 TTS", tts.any { it.language == "Chinese" })
        assertTrue(tts.all { it.url.startsWith("https://huggingface.co/") })
        entries.forEach { assertTrue("${it.id} sha", Regex("[0-9a-f]{64}").matches(it.sha256)) }
    }
}
