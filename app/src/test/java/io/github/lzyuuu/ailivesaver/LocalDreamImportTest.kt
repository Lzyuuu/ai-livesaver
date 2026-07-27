package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalDreamImportTest {
    @Test
    fun readsJsonAndKeyValueLocalDreamParameters() {
        val json = parseLocalDreamParameters(
            """{"prompt":"rainy station","negative_prompt":"blurry","seed":42,"steps":24,"cfg_scale":6.5,"width":768,"height":512}""",
        )
        assertEquals("rainy station", json.prompt)
        assertEquals("blurry", json.negativePrompt)
        assertEquals(42L, json.seed)
        assertEquals(24, json.steps)
        assertEquals(6.5, json.cfg ?: -1.0, 0.0)
        assertEquals(768, json.width)
        assertEquals(512, json.height)

        val text = parseLocalDreamParameters(
            "prompt: quiet library\nnegative_prompt: text, watermark\nseed: 7\nscheduler: dpm++",
        )
        assertEquals("quiet library", text.prompt)
        assertEquals("text, watermark", text.negativePrompt)
        assertEquals(7L, text.seed)
        assertEquals("dpm++", text.scheduler)
    }
}
