package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InferenceBackendTest {
    @Test fun unknownBackendFallsBackToCpu() = assertEquals(InferenceBackend.CPU, InferenceBackend.fromWireName("other"))
    @Test fun backendWireNamesAreStable() {
        assertEquals("cpu", InferenceBackend.CPU.wireName)
        assertEquals("gpu", InferenceBackend.GPU.wireName)
        assertEquals("npu", InferenceBackend.NPU.wireName)
    }

    // —— 能力检测（supportedInferenceBackends）——

    @Test fun nativeMissingMeansNoLocalBackend() =
        assertTrue(supportedInferenceBackends(nativeAvailable = false).isEmpty())

    @Test fun nativeAvailableMeansCpuOnly() =
        assertEquals(setOf(InferenceBackend.CPU), supportedInferenceBackends(nativeAvailable = true))

    // —— 选择与回退（effectiveInferenceBackend）——

    @Test fun cpuSelectionIsHonoredWhenNativeAvailable() {
        val supported = supportedInferenceBackends(nativeAvailable = true)
        assertEquals(InferenceBackend.CPU, effectiveInferenceBackend(InferenceBackend.CPU, supported))
    }

    @Test fun gpuSelectionFallsBackToCpuWhenUnsupported() {
        // 本机构建无 GPU native backend：显式回退 CPU，不伪造加速。
        val supported = supportedInferenceBackends(nativeAvailable = true)
        assertEquals(InferenceBackend.CPU, effectiveInferenceBackend(InferenceBackend.GPU, supported))
    }

    @Test fun npuSelectionFallsBackToCpuWhenUnsupported() {
        val supported = supportedInferenceBackends(nativeAvailable = true)
        assertEquals(InferenceBackend.CPU, effectiveInferenceBackend(InferenceBackend.NPU, supported))
    }

    @Test fun gpuSelectionHonoredWhenSupportedByFutureBuild() {
        // 未来接入加速后端后选择原样保留（不篡改、不替用户降级）。
        assertEquals(InferenceBackend.GPU, effectiveInferenceBackend(InferenceBackend.GPU, setOf(InferenceBackend.CPU, InferenceBackend.GPU)))
    }

    @Test fun noBackendWhenNativeMissingEvenForCpu() {
        val supported = supportedInferenceBackends(nativeAvailable = false)
        assertNull(effectiveInferenceBackend(InferenceBackend.CPU, supported))
        assertNull(effectiveInferenceBackend(InferenceBackend.GPU, supported))
    }
}