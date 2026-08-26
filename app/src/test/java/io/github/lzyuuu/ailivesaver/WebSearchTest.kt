package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Closeable
import java.net.ServerSocket
import kotlin.concurrent.thread

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

    @Test(timeout = 4_000)
    fun searchReturnsEmptyWithinWallClockWhenHttpIgnoresSocketTimeout() {
        LocalHttpServer { Thread.sleep(60_000) }.use { server ->
            val startedAt = System.nanoTime()
            val results = searchDuckDuckGo(
                query = "sunset harbor",
                timeoutMs = 300,
                endpointUrl = server.url(),
                socketTimeoutMs = 20_000,
            )
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
            assertTrue(results.isEmpty())
            assertTrue("wall-clock ${elapsedMs}ms exceeded 1500ms", elapsedMs < 1_500)
        }
    }

    @Test
    fun searchTreatsChallengeAndNonOkStatusAsEmpty() {
        resultServer(202, "<a class=\"result__a\">Should not surface</a>").use { accepted ->
            assertTrue(
                searchDuckDuckGo("sunset", endpointUrl = accepted.url()).isEmpty(),
            )
        }
        resultServer(403, "<a class=\"result__a\">Forbidden page</a>").use { forbidden ->
            assertTrue(
                searchDuckDuckGo("sunset", endpointUrl = forbidden.url()).isEmpty(),
            )
        }
    }

    @Test
    fun searchParsesOkHtmlFromInjectedEndpoint() {
        val html = """<a class="result__a" href="x">Harbor lights</a><a class="result__snippet">Sunset over still water</a>"""
        resultServer(200, html).use { ok ->
            val results = searchDuckDuckGo("sunset", endpointUrl = ok.url())
            assertEquals(1, results.size)
            assertEquals("Harbor lights", results.single().title)
            assertEquals("Sunset over still water", results.single().snippet)
        }
    }
}

/** 本地 HTTP：handler 决定挂起或回写，用来钉住 wall-clock 与状态码降级。 */
private class LocalHttpServer(private val handle: (java.net.Socket) -> Unit) : Closeable {
    private val server = ServerSocket(0)
    private val worker = thread {
        try {
            while (true) {
                server.accept().use(handle)
            }
        } catch (_: Exception) {
            // 测试结束关闭套接字。
        }
    }

    fun url() = "http://127.0.0.1:${server.localPort}/"

    override fun close() {
        server.close()
        worker.interrupt()
        worker.join(2_000)
    }
}

private fun resultServer(status: Int, body: String): LocalHttpServer {
    val reason = when (status) {
        200 -> "OK"
        202 -> "Accepted"
        403 -> "Forbidden"
        else -> "Status"
    }
    val payload = body.toByteArray(Charsets.UTF_8)
    return LocalHttpServer { socket ->
        socket.getInputStream().bufferedReader().readLine()
        val head =
            "HTTP/1.1 $status $reason\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${payload.size}\r\nConnection: close\r\n\r\n"
        socket.getOutputStream().use { out ->
            out.write(head.toByteArray())
            out.write(payload)
            out.flush()
        }
    }
}
