package io.github.lzyuuu.ailivesaver

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.Base64

class LocalDreamContractTest {
    @Test
    fun parsesSseEventsAndIgnoresNonDataLines() {
        assertEquals(
            LocalDreamSseEvent.Progress(3, 20),
            parseLocalDreamSseLine("data: {\"type\":\"progress\",\"step\":3,\"total_steps\":20}"),
        )
        assertTrue(parseLocalDreamSseLine("data: {\"type\":\"complete\",\"seed\":9}") is LocalDreamSseEvent.Complete)
        assertEquals(
            LocalDreamSseEvent.Error("generation failed"),
            parseLocalDreamSseLine("data: {\"type\":\"error\",\"message\":\"generation failed\"}"),
        )
        assertTrue(parseLocalDreamSseLine("data: [DONE]") === LocalDreamSseEvent.Done)
        assertEquals(null, parseLocalDreamSseLine(": keep-alive"))
    }

    @Test
    fun decodesRgbIntoArgbPixels() {
        val rgb = ByteArray(8 * 8 * 3).also {
            it[0] = 0xFF.toByte()
            it[1] = 0x00
            it[2] = 0x80.toByte()
            it[3] = 0x10
            it[4] = 0x20
            it[5] = 0x30
        }
        val event = JSONObject()
            .put("width", 8)
            .put("height", 8)
            .put("channels", 3)
            .put("seed", 42)
            .put("image", Base64.getEncoder().encodeToString(rgb))
        val image = decodeLocalDreamRgb(event)

        assertEquals(8, image.width)
        assertEquals(8, image.height)
        assertEquals(42L, image.seed)
        assertEquals(64, image.pixels.size)
        assertEquals(0xFFFF0080.toInt(), image.pixels[0])
        assertEquals(0xFF102030.toInt(), image.pixels[1])
        assertTrue(image.pixels.drop(2).all { it == 0xFF000000.toInt() })
    }

    @Test
    fun rejectsUnsupportedRgbShapeAndLength() {
        val unsupported = JSONObject().put("width", 8).put("height", 8).put("channels", 4)
        val shapeError = runCatching { decodeLocalDreamRgb(unsupported) }.exceptionOrNull()
        assertTrue(shapeError is IOException)

        val incomplete = JSONObject().put("width", 8).put("height", 8).put("image", "AA==")
        val lengthError = runCatching { decodeLocalDreamRgb(incomplete) }.exceptionOrNull()
        assertTrue(lengthError is IOException)
    }
}
