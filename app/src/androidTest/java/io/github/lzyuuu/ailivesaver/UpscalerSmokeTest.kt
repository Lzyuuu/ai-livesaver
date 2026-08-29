package io.github.lzyuuu.ailivesaver

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mrj.fancyai.sd.MnnUpscaler
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 阶段③ HD Upscaler 冒烟：模型（files/upscalers 下的 fp16.mnn，宿主推入）+
 * libfancy_mnn_upscale.so（证书门已补）+ nativeUpscaleImage 4× 放大链路。
 * 源图由测试现场生成（512×512 渐变），避免依赖外部资产。
 */
@RunWith(AndroidJUnit4::class)
class UpscalerSmokeTest {
    @Test
    fun upscaleFourXWithRealEsrganModel() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("upscale lib 未编入或 ABI 不符", MnnUpscaler.available)
        val entry = UpscalerModelStore.catalog[0]
        assertTrue("模型未就位（先推入 files/upscalers/）", UpscalerModelStore.isReady(context, entry))

        val source = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        for (y in 0 until 512 step 32) {
            for (x in 0 until 512 step 32) {
                val c = android.graphics.Color.rgb((x * 7) % 256, (y * 5) % 256, 128)
                source.eraseColor(c)
            }
        }

        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        val upscaled = executor.submit<Bitmap?> {
            UpscalerRuntime.upscale(context, entry, source)
        }.get(600, java.util.concurrent.TimeUnit.SECONDS)
        executor.shutdownNow()

        assertNotNull("放大失败", upscaled)
        assertEquals(2048, upscaled!!.width)
        assertEquals(2048, upscaled.height)
    }
}
