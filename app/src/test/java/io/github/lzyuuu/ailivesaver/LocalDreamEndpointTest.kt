package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Local Dream 服务地址：默认本机、可配置远程受控端、URL 校验只接受 http/https。 */
class LocalDreamEndpointTest {
    @Test
    fun defaultRemainsLocalLoopback() {
        assertEquals("http://127.0.0.1:8081", LocalDreamEndpoint.DEFAULT_BASE_URL)
    }

    @Test
    fun normalizeAcceptsHttpHttpsAndHostPortShorthand() {
        assertEquals("http://192.168.31.75:8081", LocalDreamEndpoint.normalize("192.168.31.75:8081"))
        assertEquals("http://192.168.31.75:8081", LocalDreamEndpoint.normalize("http://192.168.31.75:8081"))
        assertEquals("http://10.0.2.2", LocalDreamEndpoint.normalize("10.0.2.2"))
        assertEquals("https://example.local:8443", LocalDreamEndpoint.normalize("  https://example.local:8443  "))
    }

    @Test
    fun normalizeRejectsNonHttpSchemesAndBlankHosts() {
        assertNull(LocalDreamEndpoint.normalize("ftp://192.168.1.8:8081"))
        assertNull(LocalDreamEndpoint.normalize("file:///etc/passwd"))
        assertNull(LocalDreamEndpoint.normalize("http://"))
        assertNull(LocalDreamEndpoint.normalize(""))
        assertNull(LocalDreamEndpoint.normalize("   "))
        assertNull(LocalDreamEndpoint.normalize(":not a url"))
    }

    @Test
    fun normalizedFormIsStableRoundTrip() {
        val normalized = LocalDreamEndpoint.normalize("http://192.168.31.75:8081")!!
        assertEquals(normalized, LocalDreamEndpoint.normalize(normalized))
        assertTrue(normalized.startsWith("http://"))
    }
}
