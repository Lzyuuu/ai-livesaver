package io.github.lzyuuu.ailivesaver

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RootProducerTest {
    @Test
    fun trackJsonRoundTripMatchesLoadFormat() {
        val tracks = listOf(
            ProducerTrack(1L, "夏夜公路", "风格：合成器流行…"),
            ProducerTrack(2L, "雨天咖啡馆", "风格：爵士三重奏…"),
        )
        val array = JSONArray()
        tracks.forEach { track ->
            array.put(JSONObject().put("id", track.id).put("idea", track.idea).put("plan", track.plan))
        }
        // 与 producerTracksLoad 相同的反序列化路径。
        val parsed = JSONArray(array.toString())
        val loaded = (0 until parsed.length()).mapNotNull { i ->
            val o = parsed.optJSONObject(i) ?: return@mapNotNull null
            ProducerTrack(o.optLong("id"), o.optString("idea"), o.optString("plan"))
        }
        assertEquals(tracks, loaded)
    }

    @Test
    fun planPromptContainsIdeaAndStructure() {
        val prompt = producerPlanPrompt("夏夜公路")
        assertTrue(prompt.contains("夏夜公路"))
        assertTrue(prompt.contains("歌词"))
        assertTrue(prompt.contains("方案"))
    }
}
