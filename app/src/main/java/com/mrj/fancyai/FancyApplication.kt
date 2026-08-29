package com.mrj.fancyai

import android.app.Application

/**
 * 参考 V4.51 的 Application 占位：libfancy_mnn_diffusion.so 的
 * [com.mrj.fancyai.sd.MnnSd15Diffusion.nativeLoad] 第一个参数类型是本类。
 * 注册为 app Application，使 applicationContext 即为 FancyApplication。
 */
class FancyApplication : Application() {
    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(base)
        u = this
    }

    override fun onCreate() {
        super.onCreate()
        u = this
    }

    companion object {
        @JvmField
        @Volatile
        var u: FancyApplication? = null
    }
}
