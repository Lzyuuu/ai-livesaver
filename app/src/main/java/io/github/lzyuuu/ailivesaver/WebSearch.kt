package io.github.lzyuuu.ailivesaver

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * DuckDuckGo 检索（spec-v451 §1 / ticket #51）：请求 html 端点、解析标题+摘要，
 * 失败或超时一律静默降级为空结果，绝不阻塞聊天主流程。
 */

internal data class WebSearchResult(
    val title: String,
    val snippet: String,
)

internal const val DEFAULT_WEB_RESULT_LIMIT = 4
internal const val WEB_SEARCH_TIMEOUT_MS = 8_000

private val webResultTitleRegex =
    Regex("""<a[^>]*class="result__a"[^>]*>(.*?)</a>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
private val webResultSnippetRegex =
    Regex("""<a[^>]*class="result__snippet"[^>]*>(.*?)</a>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

internal fun duckDuckGoSearchUrl(query: String): String {
    val cleanQuery = query.trim()
    if (cleanQuery.isEmpty()) return "https://html.duckduckgo.com/html/?q="
    return "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(cleanQuery, "UTF-8")
}

internal fun decodeHtmlEntities(raw: String): String {
    if (!raw.contains('&')) return raw
    return raw.replace(Regex("&(amp|lt|gt|quot|apos|nbsp|#[0-9]+|#x[0-9a-fA-F]+);")) { match ->
        when (val token = match.groupValues[1]) {
            "amp" -> "&"
            "lt" -> "<"
            "gt" -> ">"
            "quot" -> "\""
            "apos" -> "'"
            "nbsp" -> " "
            else -> runCatching {
                if (token.startsWith("#x", ignoreCase = true)) {
                    token.substring(2).toInt(16).toChar().toString()
                } else {
                    token.drop(1).toInt().toChar().toString()
                }
            }.getOrDefault(match.value)
        }
    }
}

internal fun stripWebMarkup(raw: String): String =
    decodeHtmlEntities(raw.replace(Regex("<[^>]*>"), "")).replace(Regex("\\s+"), " ").trim()

internal fun parseDuckDuckGoResults(html: String, limit: Int = DEFAULT_WEB_RESULT_LIMIT): List<WebSearchResult> {
    if (limit <= 0) return emptyList()
    val titles = webResultTitleRegex.findAll(html).toList()
    if (titles.isEmpty()) return emptyList()
    val snippets = webResultSnippetRegex.findAll(html).map { stripWebMarkup(it.groupValues[1]) }.toList()
    return titles.take(limit).mapIndexed { index, match ->
        WebSearchResult(
            title = stripWebMarkup(match.groupValues[1]),
            snippet = snippets.getOrElse(index) { "" },
        )
    }.filter { it.title.isNotBlank() || it.snippet.isNotBlank() }
}

internal fun webResultsForPrompt(results: List<WebSearchResult>): String {
    if (results.isEmpty()) return ""
    return buildString {
        append("\nFresh DuckDuckGo web search results (reference material; may be incomplete or stale):\n")
        results.forEach { result ->
            append("- ${result.title}: ${result.snippet}\n")
        }
    }
}

/**
 * 同步检索；调用方负责放到后台线程。任何异常（网络不可用、超时、解析失败）都返回空列表。
 */
internal fun searchDuckDuckGo(
    query: String,
    timeoutMs: Int = WEB_SEARCH_TIMEOUT_MS,
    limit: Int = DEFAULT_WEB_RESULT_LIMIT,
): List<WebSearchResult> {
    if (query.trim().isEmpty()) return emptyList()
    return runCatching {
        val connection = URL(duckDuckGoSearchUrl(query)).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.instanceFollowRedirects = true
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36",
            )
            if (connection.responseCode != 200) return@runCatching emptyList()
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                reader.readText()
            }
            parseDuckDuckGoResults(body, limit)
        } finally {
            connection.disconnect()
        }
    }.getOrElse { emptyList() }
}
