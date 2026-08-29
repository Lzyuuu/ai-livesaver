package io.github.lzyuuu.ailivesaver

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mrj.fancyai.sd.MnnSd15Diffusion
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * legacy packaging（jniLibs 解压安装）下 libMNN/libfancy_mnn_diffusion 的加载与
 * 符号绑定冒烟。nativeLoad 全链路回归需要 11 文件模型就位（HF 不可达时跳过）。
 */
@RunWith(AndroidJUnit4::class)
class MnnSdLoadSmokeTest {
    @Test
    fun loadsDiffusionLibAndResolvesSymbols() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as com.mrj.fancyai.FancyApplication
        assertEquals(2, MnnSd15Diffusion.nativeAbiVersion())
        // nativeLoad 只在模型 11 文件齐且 SHA 校验通过时执行；否则按 isReady 直接跳过。
        val ready = ImagingModelStore.isReady(context)
        Log.i("MnnSdLoadSmokeTest", "ready=$ready")
        if (ready) {
            val loaded = MnnSd.ensureLoaded(context)
            Log.i("MnnSdLoadSmokeTest", "nativeLoad=$loaded")
            org.junit.Assert.assertTrue("nativeLoad 失败", loaded)
        }
    }
}
