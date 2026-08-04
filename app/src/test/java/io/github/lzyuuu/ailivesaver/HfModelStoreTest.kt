package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.net.ServerSocket
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Model Store 下载完整性 JVM 单测：目录可信 SHA-256 钉住、下载校验与重试、
 * HTTP HEAD 解析 HF LFS 元数据，以及无法可信获得 SHA 时阻断下载（绝不无校验安装）。
 */
class HfModelStoreTest {

    private val sha256Hex = Regex("[0-9a-f]{64}")

    @Test
    fun `catalog pins a trusted sha256 for every model`() {
        HfModelCatalog.forEach { model ->
            assertTrue("${model.file} 必须钉住 64 位十六进制 SHA-256（禁止编造或留空）", sha256Hex.matches(model.sha256))
        }
    }

    @Test
    fun `sha256 computes the standard digest`() {
        val file = Files.createTempFile("sha", ".bin").toFile().apply { writeText("abc") }
        try {
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", HfModelStore.sha256(file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `downloadFile rejects a fabricated non-hex sha256 before touching the network`() {
        val dir = tempDir()
        try {
            val error = assertThrows(IllegalArgumentException::class.java) {
                HfModelStore.downloadFile(dir, "http://127.0.0.1:1/model.bin", "model.bin", expectedSha256 = "not-a-real-hash")
            }
            assertTrue(error.message!!.contains("可信的 SHA-256"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `downloadFile rejects checksum mismatch and leaves no target file`() {
        val dir = tempDir()
        FakeServer { "HTTP/1.1 200 OK\r\nContent-Length: 5\r\nConnection: close\r\n\r\n" to "wrong".toByteArray() }.use { server ->
            val error = assertThrows(IOException::class.java) {
                HfModelStore.downloadFile(dir, server.url(), "model.bin", expectedSha256 = "a".repeat(64), maxAttempts = 1)
            }
            assertTrue(error.message!!.contains("SHA-256 校验失败"))
            assertFalse(File(dir, "model.bin").exists())
            assertTrue(dir.listFiles().isNullOrEmpty())
        }
        dir.deleteRecursively()
    }

    @Test
    fun `downloadFile retries transient failures then verifies the payload`() {
        val dir = tempDir()
        val payload = "model-payload".toByteArray()
        val expectedSha = HfModelStore.sha256(File(dir, "expected").apply { writeBytes(payload) })
        val attempts = AtomicInteger(0)
        FakeServer {
            when (attempts.incrementAndGet()) {
                1, 2 -> "HTTP/1.1 503 Service Unavailable\r\nContent-Length: 0\r\nConnection: close\r\n\r\n" to ByteArray(0)
                else -> "HTTP/1.1 200 OK\r\nContent-Length: ${payload.size}\r\nConnection: close\r\n\r\n" to payload
            }
        }.use { server ->
            val target = HfModelStore.downloadFile(dir, server.url(), "model.bin", expectedSha256 = expectedSha, maxAttempts = 3)
            assertEquals(3, attempts.get())
            assertEquals(payload.toList(), target.readBytes().toList())
            assertFalse(File(dir, ".model.bin.part-1").exists())
            assertFalse(File(dir, ".model.bin.part-2").exists())
        }
        dir.deleteRecursively()
    }

    @Test
    fun `downloadWithResolvedSha blocks when the official sha cannot be obtained`() {
        val dir = tempDir()
        try {
            val resolver = HfShaResolver { null }
            val error = assertThrows(IOException::class.java) {
                HfModelStore.downloadWithResolvedSha(dir, "http://127.0.0.1:1/model.bin", "model.bin", resolver)
            }
            assertTrue(error.message!!.contains("阻断"))
            assertTrue(dir.listFiles().isNullOrEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `downloadWithResolvedSha verifies against the sha from hf metadata`() {
        val dir = tempDir()
        val payload = "official-model".toByteArray()
        val sha = HfModelStore.sha256(File(dir, "expected").apply { writeBytes(payload) })
        FakeServer { "HTTP/1.1 200 OK\r\nContent-Length: ${payload.size}\r\nConnection: close\r\n\r\n" to payload }.use { server ->
            val target = HfModelStore.downloadWithResolvedSha(dir, server.url(), "model.bin", HfShaResolver { sha })
            assertEquals(payload.toList(), target.readBytes().toList())

            // 服务器内容与解析出的 SHA 不符时同样必须失败，且不留下目标文件。
            val error = assertThrows(IOException::class.java) {
                HfModelStore.downloadWithResolvedSha(dir, server.url(), "model2.bin", HfShaResolver { "0".repeat(64) }, maxAttempts = 1)
            }
            assertTrue(error.message!!.contains("SHA-256 校验失败"))
            assertFalse(File(dir, "model2.bin").exists())
        }
        dir.deleteRecursively()
    }

    @Test
    fun `httpShaResolver reads x-linked-etag from the HEAD redirect`() {
        val sha = "a799477ec3eb87f09b380f31452adf5f26f837e07aacfc6b75474f9f1b6056d1"
        var requestLine: String? = null
        FakeServer { line ->
            requestLine = line
            "HTTP/1.1 302 Found\r\nLocation: https://cdn.example.com/object\r\nX-Linked-Etag: \"$sha\"\r\nX-Linked-Size: 8485268\r\nContent-Length: 0\r\nConnection: close\r\n\r\n" to ByteArray(0)
        }.use { server ->
            assertEquals(sha, HfHttpShaResolver.resolveSha256(server.url()))
        }
        assertTrue(requestLine!!.startsWith("HEAD"))
    }

    @Test
    fun `httpShaResolver falls back to the oid embedded in the redirect location`() {
        val oid = "09a0e01fb942cb237c5204d166a48e163d4a6d601b7b5c33eec7c210c36e918b"
        FakeServer { "HTTP/1.1 302 Found\r\nLocation: https://cdn-lfs.huggingface.co/repos/ab12/$oid/model.bin?x=1\r\nContent-Length: 0\r\nConnection: close\r\n\r\n" to ByteArray(0) }.use { server ->
            assertEquals(oid, HfHttpShaResolver.resolveSha256(server.url()))
        }
    }

    @Test
    fun `httpShaResolver returns null when metadata is absent or not a redirect`() {
        FakeServer { "HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n" to ByteArray(0) }.use { server ->
            assertNull(HfHttpShaResolver.resolveSha256(server.url()))
        }
        FakeServer { "HTTP/1.1 302 Found\r\nLocation: https://cdn.example.com/plain-object\r\nContent-Length: 0\r\nConnection: close\r\n\r\n" to ByteArray(0) }.use { server ->
            assertNull(HfHttpShaResolver.resolveSha256(server.url()))
        }
    }

    @Test
    fun `matchesTrustedSha accepts verified files and rejects corrupt or missing ones`() {
        val dir = tempDir()
        try {
            val good = File(dir, "good.bin").apply { writeBytes("trusted-model".toByteArray()) }
            val sha = HfModelStore.sha256(good)
            assertTrue(HfModelStore.matchesTrustedSha(good, sha))
            good.writeBytes("tampered-content".toByteArray())
            assertFalse(HfModelStore.matchesTrustedSha(good, sha))
            assertFalse(HfModelStore.matchesTrustedSha(File(dir, "missing.bin"), sha))
        } finally {
            dir.deleteRecursively()
        }
    }
}

/** 极简本地 HTTP 服务：每次连接读请求行，交给 handler 返回响应头与响应体。 */
private class FakeServer(private val handler: (requestLine: String) -> Pair<String, ByteArray>) : Closeable {
    private val server = ServerSocket(0)
    private val worker = thread {
        try {
            while (true) {
                server.accept().use { socket ->
                    val line = socket.getInputStream().bufferedReader().readLine() ?: continue
                    val (head, body) = handler(line)
                    socket.getOutputStream().use { out ->
                        out.write(head.toByteArray())
                        out.write(body)
                        out.flush()
                    }
                }
            }
        } catch (_: Exception) {
            // 服务端关闭或测试结束即退出。
        }
    }

    fun url() = "http://127.0.0.1:${server.localPort}/model.bin"

    override fun close() {
        server.close()
        worker.join(2_000)
    }
}

private fun tempDir(): File = Files.createTempDirectory("hf-model-store-test").toFile()
