package io.github.lzyuuu.ailivesaver

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal data class GitHubRelease(
    val tagName: String,
    val notes: String,
    val downloadUrl: String,
    val apkSizeBytes: Long?,
    val checksumUrl: String?,
)

internal sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: GitHubRelease) : UpdateState
    data class Failed(val message: String) : UpdateState
}

internal class GitHubUpdateChecker(
    private val releasesUrl: String =
        "https://api.github.com/repos/Lzyuuu/ai-livesaver/releases?per_page=20",
    private val feedUrl: String =
        "https://github.com/Lzyuuu/ai-livesaver/releases.atom",
) {
    fun checkAsync(
        currentVersion: String,
        callback: (Result<UpdateState>) -> Unit,
    ) {
        Thread {
            val result = runCatching {
                runCatching {
                    ReleaseParser.selectTestingUpdate(fetch(releasesUrl), currentVersion)
                }.getOrElse {
                    ReleaseParser.selectTestingUpdateFromAtom(fetch(feedUrl), currentVersion)
                }
            }
            Handler(Looper.getMainLooper()).post { callback(result) }
        }.start()
    }

    private fun fetch(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "AI-Livesaver/${BuildConfig.VERSION_NAME}")
        return try {
            if (connection.responseCode !in 200..299) {
                throw IOException("GitHub HTTP ${connection.responseCode}")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

internal object ReleaseParser {
    fun selectTestingUpdate(
        json: String,
        currentVersion: String,
    ): UpdateState {
        val releases = JSONArray(json)
        var newest: GitHubRelease? = null

        for (index in 0 until releases.length()) {
            val release = releases.getJSONObject(index)
            if (release.optBoolean("draft", false)) continue

            val tag = release.optString("tag_name").trim()
            if (Version.parse(tag) == null) continue

            val assets = release.optJSONArray("assets") ?: JSONArray()
            var apkUrl: String? = null
            var apkSize: Long? = null
            var checksumUrl: String? = null

            for (assetIndex in 0 until assets.length()) {
                val asset = assets.getJSONObject(assetIndex)
                val name = asset.optString("name")
                val url = asset.optString("browser_download_url")
                when {
                    name.endsWith(".apk", ignoreCase = true) && apkUrl == null -> {
                        apkUrl = url
                        apkSize = asset.optLong("size").takeIf { it > 0 }
                    }
                    name.endsWith(".sha256", ignoreCase = true) && checksumUrl == null -> {
                        checksumUrl = url
                    }
                }
            }

            val candidate = GitHubRelease(
                tagName = tag,
                notes = release.optString("body"),
                downloadUrl = apkUrl ?: release.optString("html_url"),
                apkSizeBytes = apkSize,
                checksumUrl = checksumUrl,
            )
            if (candidate.downloadUrl.isBlank()) continue
            if (newest == null || compareVersions(candidate.tagName, newest.tagName) > 0) {
                newest = candidate
            }
        }

        return if (newest != null && compareVersions(newest.tagName, currentVersion) > 0) {
            UpdateState.Available(newest)
        } else {
            UpdateState.UpToDate
        }
    }

    fun selectTestingUpdateFromAtom(xml: String, currentVersion: String): UpdateState {
        val tag = Regex("""href="https://github\.com/Lzyuuu/ai-livesaver/releases/tag/([^"]+)"""")
            .findAll(xml)
            .map { it.groupValues[1] }
            .filter { Version.parse(it) != null }
            .maxWithOrNull(::compareVersions)
            ?: return UpdateState.UpToDate
        return if (compareVersions(tag, currentVersion) > 0) {
            UpdateState.Available(
                GitHubRelease(
                    tagName = tag,
                    notes = "",
                    downloadUrl =
                        "https://github.com/Lzyuuu/ai-livesaver/releases/download/$tag/" +
                            "ai-livesaver-$tag.apk",
                    apkSizeBytes = null,
                    checksumUrl =
                        "https://github.com/Lzyuuu/ai-livesaver/releases/download/$tag/" +
                            "ai-livesaver-$tag.apk.sha256",
                ),
            )
        } else {
            UpdateState.UpToDate
        }
    }

    internal fun compareVersions(left: String, right: String): Int {
        val a = Version.parse(left) ?: return -1
        val b = Version.parse(right) ?: return 1
        return compareValuesBy(a, b, Version::major, Version::minor, Version::patch)
            .takeIf { it != 0 }
            ?: when {
                a.milestone == b.milestone -> a.revision.compareTo(b.revision)
                a.milestone == null -> 1
                b.milestone == null -> -1
                else -> a.milestone.compareTo(b.milestone)
            }
    }

    private data class Version(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val milestone: Int?,
        val revision: Int,
    ) {
        companion object {
            private val pattern = Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:-m(\d+)(?:\.(\d+))?)?$""")

            fun parse(raw: String): Version? {
                val match = pattern.matchEntire(raw.trim()) ?: return null
                return Version(
                    major = match.groupValues[1].toInt(),
                    minor = match.groupValues[2].toInt(),
                    patch = match.groupValues[3].toInt(),
                    milestone = match.groupValues[4].takeIf { it.isNotEmpty() }?.toInt(),
                    revision = match.groupValues[5].takeIf { it.isNotEmpty() }?.toInt() ?: 0,
                )
            }
        }
    }
}
