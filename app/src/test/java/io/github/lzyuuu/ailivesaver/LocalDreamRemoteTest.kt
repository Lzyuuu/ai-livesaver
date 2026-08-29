package io.github.lzyuuu.ailivesaver

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Local Dream 遥控协议 mock server 测试：覆盖 /info 与 /models（8808 控制端口）；
 * /generate 的 mock 全链路覆盖见 instrumentation LocalDreamHttpSmokeTest。
 */
class LocalDreamRemoteTest {
    private lateinit var server: HttpServer
    private var port: Int = 0

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/info") { exchange ->
            val body = """{"app":"localdream","protocol":1,"version":"2.8.1","device":"V2546A"}"""
            exchange.sendResponseHeaders(200, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
        server.createContext("/models") { exchange ->
            val body = """
                {"use_img2img":true,"models":[
                  {"id":"anima_turbo","name":"ANIMA Turbo","run_on_cpu":false,"is_sdxl":false},
                  {"id":"sdxl_dmd2","name":"SDXL DMD2","run_on_cpu":false,"is_sdxl":true},
                  {"id":"","name":"跳过缺 id 条目","run_on_cpu":false,"is_sdxl":false}
                ]}
            """.trimIndent()
            exchange.sendResponseHeaders(200, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
        server.start()
        port = server.address.port
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    private fun baseUrl(): String = "http://127.0.0.1:$port"

    @Test
    fun fetchInfoParsesProtocolEnvelope() {
        val info = LocalDreamClient.fetchInfo(baseUrl())
        assertEquals("localdream", info.app)
        assertEquals(1, info.protocol)
        assertEquals("2.8.1", info.version)
        assertEquals("V2546A", info.device)
    }

    @Test
    fun fetchModelsParsesListAndSkipsInvalidEntries() {
        val models = LocalDreamClient.fetchModels(baseUrl())
        assertEquals(2, models.size)
        assertEquals("anima_turbo", models[0].id)
        assertEquals("ANIMA Turbo", models[0].name)
        assertTrue(models[1].isSdxl)
    }

    @Test
    fun controlBaseUrlDerives8081To8808() {
        assertEquals("http://192.168.31.75:8808", LocalDreamClient.controlBaseUrl("http://192.168.31.75:8081"))
        assertEquals("http://127.0.0.1:8808", LocalDreamClient.controlBaseUrl(LocalDreamEndpoint.DEFAULT_BASE_URL))
        assertEquals(null, LocalDreamClient.controlBaseUrl("ftp://bad"))
    }
}
