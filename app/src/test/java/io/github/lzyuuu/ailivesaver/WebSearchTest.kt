package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchTest {

    @Test
    fun parsesTitlesAndSnippetsFromDuckDuckGoHtml() {
        val html = """
            <div class="result results_links results_links_deep web-result">
              <h2 class="result__title">
                <a rel="nofollow" class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fkotlinlang.org%2Fcoroutines&amp;rut=abc">Kotlin coroutines guide</a>
              </h2>
              <a class="result__snippet" href="//duckduckgo.com/l/?uddg=x">Kotlin coroutines let you write <b>async</b> code sequentially &amp; safely.</a>
            </div>
            <div class="result">
              <h2><a class="result__a" href="//duckduckgo.com/l/?uddg=y">Second result</a></h2>
              <a class="result__snippet">&#x27;Quoted&#39; snippet with &lt;tags&gt;</a>
            </div>
        """.trimIndent()
        val results = parseDuckDuckGoResults(html)
        assertEquals(2, results.size)
        assertEquals("Kotlin coroutines guide", results[0].title)
        assertEquals("Kotlin coroutines let you write async code sequentially & safely.", results[0].snippet)
        assertEquals("Second result", results[1].title)
        assertEquals("'Quoted' snippet with <tags>", results[1].snippet)
    }

    @Test
    fun parsingLimitsResults() {
        val html = (1..6).joinToString("") { index ->
            """<a class="result__a" href="x">$index</a><a class="result__snippet">s$index</a>"""
        }
        assertEquals(DEFAULT_WEB_RESULT_LIMIT, parseDuckDuckGoResults(html).size)
        assertEquals(2, parseDuckDuckGoResults(html, limit = 2).size)
    }

    @Test
    fun parsingDegradesSilentlyOnBlankAnomalyOrPartialMarkup() {
        assertTrue(parseDuckDuckGoResults("").isEmpty())
        assertTrue(parseDuckDuckGoResults("<html><body>If this error persists</body></html>").isEmpty())
        val titleOnly = parseDuckDuckGoResults("""<a class="result__a" href="x">Only title</a>""")
        assertEquals(1, titleOnly.size)
        assertEquals("Only title", titleOnly.single().title)
        assertEquals("", titleOnly.single().snippet)
    }

    @Test
    fun buildsEncodedLiteEndpointUrl() {
        assertEquals(
            "https://html.duckduckgo.com/html/?q=kotlin+coroutines%3F",
            duckDuckGoSearchUrl(" kotlin coroutines? "),
        )
        assertEquals(
            "https://html.duckduckgo.com/html/?q=",
            duckDuckGoSearchUrl("   "),
        )
    }

    @Test
    fun formatsResultsAsSystemPromptSection() {
        assertEquals("", webResultsForPrompt(emptyList()))
        val section = webResultsForPrompt(
            listOf(WebSearchResult("T1", "S1"), WebSearchResult("T2", "S2")),
        )
        assertTrue(section.contains("DuckDuckGo"))
        assertTrue(section.contains("- T1: S1"))
        assertTrue(section.contains("- T2: S2"))
    }

    @Test
    fun chatSystemPromptEmbedsWebResultsSection() {
        val prompt = buildChatSystemPrompt(
            character = ResidentCharacter(id = 1L, name = "Aria", persona = "curious"),
            memories = emptyList(),
            recap = null,
            webResults = listOf(WebSearchResult("Weather", "Sunny in Tokyo today")),
        )
        assertTrue(prompt.contains("- Weather: Sunny in Tokyo today"))
    }
}
