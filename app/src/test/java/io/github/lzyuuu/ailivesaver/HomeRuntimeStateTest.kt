package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeRuntimeStateTest {
    @Test fun mapsFourStatesExactly() {
        assertEquals(HomeRuntimeState.UNCONFIGURED, homeRuntimeState(null, false, false))
        val cloud = ProviderConfig(apiKey = "key")
        assertEquals(HomeRuntimeState.CLOUD_READY, homeRuntimeState(cloud, false, false))
        assertEquals(HomeRuntimeState.LOCAL_READY, homeRuntimeState(cloud, true, false))
        assertEquals(HomeRuntimeState.FAULT, homeRuntimeState(cloud, false, true))
    }
}
