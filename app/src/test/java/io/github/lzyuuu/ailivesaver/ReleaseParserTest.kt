package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseParserTest {
    @Test
    fun selectsNewestTestingApkAndChecksum() {
        val result = ReleaseParser.selectTestingUpdate(
            json = """
                [
                  {
                    "tag_name": "0.1.0-m2",
                    "draft": false,
                    "prerelease": true,
                    "body": "Second milestone",
                    "html_url": "https://github.com/Lzyuuu/ai-livesaver/releases/tag/0.1.0-m2",
                    "assets": [
                      {
                        "name": "ai-livesaver-0.1.0-m2.apk",
                        "browser_download_url": "https://example.test/m2.apk",
                        "size": 12582912
                      },
                      {
                        "name": "ai-livesaver-0.1.0-m2.apk.sha256",
                        "browser_download_url": "https://example.test/m2.apk.sha256",
                        "size": 96
                      }
                    ]
                  },
                  {
                    "tag_name": "0.1.0-m1",
                    "draft": false,
                    "prerelease": true,
                    "body": "First milestone",
                    "html_url": "https://example.test/m1",
                    "assets": []
                  }
                ]
            """.trimIndent(),
            currentVersion = "0.1.0-m1",
        )

        assertTrue(result is UpdateState.Available)
        val release = (result as UpdateState.Available).release
        assertEquals("0.1.0-m2", release.tagName)
        assertEquals("https://example.test/m2.apk", release.downloadUrl)
        assertEquals(12_582_912L, release.apkSizeBytes)
        assertEquals("https://example.test/m2.apk.sha256", release.checksumUrl)
    }

    @Test
    fun ignoresDraftsAndReportsUpToDate() {
        val result = ReleaseParser.selectTestingUpdate(
            json = """
                [
                  {
                    "tag_name": "0.1.0-m9",
                    "draft": true,
                    "prerelease": true,
                    "html_url": "https://example.test/draft",
                    "assets": []
                  },
                  {
                    "tag_name": "0.1.0-m1",
                    "draft": false,
                    "prerelease": true,
                    "html_url": "https://example.test/m1",
                    "assets": []
                  }
                ]
            """.trimIndent(),
            currentVersion = "0.1.0-m1",
        )

        assertEquals(UpdateState.UpToDate, result)
    }

    @Test
    fun stableVersionSortsAfterMilestones() {
        assertTrue(ReleaseParser.compareVersions("0.1.0", "0.1.0-m4") > 0)
        assertTrue(ReleaseParser.compareVersions("0.1.0-m3", "0.1.0-m2") > 0)
        assertTrue(ReleaseParser.compareVersions("v0.2.0-m1", "0.1.0") > 0)
    }
}
