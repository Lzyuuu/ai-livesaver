package io.github.lzyuuu.ailivesaver

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertTrue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 阶段②开工项 2 首轮推理冒烟（#73）：
 * 模型由脚本预置到 app 私有目录（宿主下载→push→run-as 拷贝），
 * 断言 llama.cpp 加载 Gemma 4 E4B Q4_0 并产出非空流式回复。
 * 仅在 LLAMA_CPP_DIR 构建变体（libllama_jni.so 存在）+ 模型就位的设备上运行。
 */
@RunWith(AndroidJUnit4::class)
class LlamaSmokeTest {
    @Test
    fun streamsWithSamplingChainWithinTimeout() {
        assertTrue("libllama_jni.so 未编入", LlamaNative.isAvailable)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelFile = java.io.File(context.filesDir, "gemma.gguf")
        if (!modelFile.isFile) {
            Log.w("LlamaSmokeTest", "模型未就位，跳过")
            return
        }
        // 完整采样链路径（encodeSampling，与聊天路线一致）+ 420s 超时保护；
        // 8G AVD 纯 CPU 实测：首次预填充 ~73s + 每 token 1-3s（模拟器无 GPU）。
        val executor = Executors.newSingleThreadExecutor()
        val future = executor.submit<String> {
            val handle = LlamaNative.nativeLoadModel(modelFile.absolutePath, 2048, 4)
            Log.d("LlamaSmokeTest", "sampling-chain: loaded handle=$handle")
            assertTrue("模型加载失败", handle != 0L)
            val reply = StringBuilder()
            val n = LlamaNative.nativeStreamCompletion(
                handle,
                "用一句话介绍你自己。",
                64,
                LlamaChat.encodeSampling(GenerationSettings()),
            ) { token -> reply.append(token) }
            LlamaNative.nativeFree(handle)
            Log.d("LlamaSmokeTest", "sampling-chain reply($n): $reply")
            reply.toString()
        }
        val result = future.get(420, TimeUnit.SECONDS)
        executor.shutdownNow()
        assertTrue("空回复", result.isNotBlank())
    }

    @Test
    fun loadsGemmaAndStreamsReply() {
        assertTrue("libllama_jni.so 未编入", LlamaNative.isAvailable)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelFile = File(context.filesDir, "gemma.gguf")
        if (!modelFile.isFile) {
            Log.w("LlamaSmokeTest", "模型未就位，跳过（脚本预置模型后重跑）")
            return
        }
        val started = System.currentTimeMillis()
        val handle = LlamaNative.nativeLoadModel(modelFile.absolutePath, 2048, 4)
        assertTrue("模型加载失败", handle != 0L)
        Log.d("LlamaSmokeTest", "load ${System.currentTimeMillis() - started} ms")
        val reply = StringBuilder()
        val genStart = System.currentTimeMillis()
        val n = LlamaNative.nativeStreamCompletion(
            handle,
            "用一句话介绍你自己。",
            48,
            null,
        ) { token -> reply.append(token) }
        LlamaNative.nativeFree(handle)
        val elapsed = System.currentTimeMillis() - genStart
        Log.d("LlamaSmokeTest", "reply(${n} chars, ${elapsed} ms): $reply")
        assertTrue("流式补全失败", n > 0)
        assertTrue("回复为空", reply.isNotBlank())
    }
}
