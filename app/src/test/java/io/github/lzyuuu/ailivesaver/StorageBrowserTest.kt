package io.github.lzyuuu.ailivesaver

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * SO-11 StorageBrowser seam：目录映射（相对路径解析/排序/计数）与边界
 * （条目上限、子项计数上限、体积访问上限、越界拒绝）。
 */
class StorageBrowserTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var root: File

    @Before
    fun setUp() {
        root = tmp.newFolder("sandbox")
    }

    private fun file(relative: String, bytes: Int = 4): File {
        val target = File(root, relative)
        target.parentFile?.mkdirs()
        target.writeBytes(ByteArray(bytes))
        return target
    }

    @Test
    fun `root listing maps directories first then files sorted by name`() {
        file("zeta/inner.txt")
        file("alpha/a.txt")
        file("notes.txt", 8)
        file("avatar.png", 16)

        val listing = StorageBrowser.listDirectory(root, "")

        assertTrue(listing.isRoot)
        assertFalse(listing.truncated)
        assertEquals(
            listOf("alpha", "zeta", "avatar.png", "notes.txt"),
            listing.entries.map { it.name },
        )
        val alpha = listing.entries[0]
        assertTrue(alpha.isDirectory)
        assertEquals("alpha", alpha.relativePath)
        assertEquals(1, alpha.childCount)
        val png = listing.entries[2]
        assertFalse(png.isDirectory)
        assertEquals(16, png.sizeBytes)
    }

    @Test
    fun `nested directory listing uses relative path mapping`() {
        file("files/media/a.bin")
        file("files/media/b.bin")
        file("files/db/world.db")

        val listing = StorageBrowser.listDirectory(root, "files")

        assertEquals("files", listing.relativePath)
        assertEquals(listOf("db", "media"), listing.entries.map { it.name })
        assertEquals("files/media", listing.entries[1].relativePath)
        assertEquals(2, listing.entries[1].childCount)
        assertEquals("files", StorageBrowser.parentPath("files/media"))
        assertEquals("", StorageBrowser.parentPath("files"))
    }

    @Test
    fun `path escapes outside sandbox are rejected`() {
        assertNull(StorageBrowser.resolveDirectory(root, ".."))
        assertNull(StorageBrowser.resolveDirectory(root, "../etc"))
        assertNull(StorageBrowser.resolveDirectory(root, "/data"))
        assertNull(StorageBrowser.resolveDirectory(root, "a//b"))
        assertNull(StorageBrowser.resolveDirectory(root, "a/./b"))
        // 越界路径落到空根快照，不访问沙盒外。
        val listing = StorageBrowser.listDirectory(root, "../../..")
        assertTrue(listing.isRoot)
        assertTrue(listing.entries.isEmpty())
    }

    @Test
    fun `missing directory resolves to null and empty root snapshot`() {
        assertNull(StorageBrowser.resolveDirectory(root, "nope"))
        val listing = StorageBrowser.listDirectory(root, "nope")
        assertTrue(listing.isRoot)
        assertTrue(listing.entries.isEmpty())
    }

    @Test
    fun `entries are capped at maxEntries and flagged truncated`() {
        repeat(10) { file("dir-$it/keep.txt") }

        val listing = StorageBrowser.listDirectory(root, "", maxEntries = 4)

        assertTrue(listing.truncated)
        assertEquals(4, listing.entries.size)
    }

    @Test
    fun `directory child count is capped and flagged`() {
        repeat(25) { file("crowded/f$it.txt") }

        val listing = StorageBrowser.listDirectory(root, "", maxChildCount = 10)

        val crowded = listing.entries.single { it.name == "crowded" }
        assertEquals(10, crowded.childCount)
        assertTrue(crowded.childrenTruncated)
    }

    @Test
    fun `bounded size walk stops at file budget and reports lower bound`() {
        repeat(20) { file("bulk/f$it.bin", bytes = 100) }

        val snapshot = StorageBrowser.boundedSizeBytes(root, maxFiles = 5)

        assertTrue(snapshot.truncated)
        assertEquals(5, snapshot.visitedFiles)
        assertEquals(500L, snapshot.totalBytes)
    }

    @Test
    fun `bounded size walk totals everything under the budget`() {
        file("a/x.bin", bytes = 7)
        file("b/y.bin", bytes = 3)

        val snapshot = StorageBrowser.boundedSizeBytes(root)

        assertFalse(snapshot.truncated)
        assertEquals(2, snapshot.visitedFiles)
        assertEquals(10L, snapshot.totalBytes)
    }
}
