package io.github.lzyuuu.ailivesaver

import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File

/**
 * 性能实验室（SO-10，参考 ref-79）：本地 Gemma 基准。
 * chat（GGUF）走 llama.cpp 定长序列计时（tokens/s）；
 * litert（.litertlm）走 LiteRT-LM 逐次生成墙钟计时（s/次，公开运行时无 token 计数 API，
 * 不伪造 tokens/s）。结果按次保留最近 3 条。基准执行 seam 可注入，供 JVM 单测。
 */
internal fun interface LlamaBenchmark {
    fun run(modelPath: String, steps: Int, internals: EngineInternals): Float
}

internal fun defaultLlamaBenchmark(modelPath: String, steps: Int, internals: EngineInternals): Float {
    val cores = Runtime.getRuntime().availableProcessors()
    return LlamaNative.nativeBenchmark(
        modelPath,
        steps,
        resolveThreads(internals.decodeThreads, decodeAutoThreads(cores), cores),
        resolveThreads(internals.prefillThreads, prefillAutoThreads(cores), cores),
        internals.batchSize,
    )
}

internal fun interface LiteRtBenchmark {
    fun run(modelPath: String, runs: Int): Double
}

internal fun formatBenchmarkScores(scores: List<Float>): String =
    "%.2f tokens/s（%d 次平均）".format(scores.average(), scores.size)

internal fun formatLiteRtBenchmark(avgSeconds: Double, runs: Int): String =
    "%.2f s/次（%d 次平均 · LiteRT 墙钟）".format(avgSeconds, runs)

internal val BENCHMARK_FAIL_LINE = "基准失败（请检查模型与内存）"

@Composable
internal fun BenchmarkAppScreen(
    contentPadding: PaddingValues,
    onOpenModelsEngine: () -> Unit,
    onBack: () -> Unit,
    llamaBenchmark: LlamaBenchmark = LlamaBenchmark(::defaultLlamaBenchmark),
    liteRtBenchmark: LiteRtBenchmark? = null,
) {
    val context = LocalContext.current
    val liteRtRun = liteRtBenchmark ?: LiteRtBenchmark { path, runs ->
        LiteRtLm.benchmark(context, path, runs)
    }
    val entries = remember { LocalModels.load(context) }
    val activeEntry = remember { entries.firstOrNull { it.id == LocalModels.activeEngineId(context) } }
    val internals = remember { EngineInternalsStore.read(context) }
    // 设备行与参考一致：厂商 + 型号（模拟器无厂商时显示 Unknown）。
    val device = remember {
        val manufacturer = android.os.Build.MANUFACTURER.trim()
            .replaceFirstChar { it.uppercase() }
            .ifEmpty { "Unknown" }
        "$manufacturer ${android.os.Build.MODEL}".trim()
    }
    var repeat by rememberSaveable { mutableStateOf(3) }
    var running by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf(listOf<String>()) }
    // 能力检测 + 选择/回退（纯逻辑，见 InferenceBackend.kt）：基准运行在有效后端上，
    // GPU/NPU 无 native backend 时显式回落 CPU，不在界面上声称加速。
    val supportedBackends = remember {
        LlamaNative.supportedBackends + LiteRtLm.supportedBackends
    }
    val backend = remember { selectedInferenceBackend(context) }
    val effective = remember { effectiveInferenceBackend(backend, supportedBackends) }

    fun runBenchmark() {
        val entry = activeEntry ?: return
        if (running) return
        running = true
        Thread {
            val modelPath = File(HfModelStore.directory(context), "${entry.id}/${entry.fileName}").absolutePath
            val line = if (entry.type == "litert") {
                val avg = runCatching { liteRtRun.run(modelPath, repeat) }
                    .getOrNull()
                    ?.takeIf { it > 0.0 }
                if (avg != null) formatLiteRtBenchmark(avg, repeat) else BENCHMARK_FAIL_LINE
            } else {
                val scores = buildList {
                    repeat(repeat) {
                        runCatching { llamaBenchmark.run(modelPath, 32, internals) }
                            .getOrNull()
                            ?.takeIf { it > 0f }
                            ?.let { add(it) }
                    }
                }
                if (scores.isNotEmpty()) formatBenchmarkScores(scores) else BENCHMARK_FAIL_LINE
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                results = (listOf(line) + results).take(3)
                running = false
            }
        }.start()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("benchmark-screen"),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            end = 20.dp,
            bottom = contentPadding.calculateBottomPadding() + 72.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("benchmark-back"),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                Text(
                    stringResource(R.string.benchmark_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        item {
            Text(
                stringResource(R.string.benchmark_will_run),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            PageCard {
                BenchmarkRow(stringResource(R.string.benchmark_model), activeEntry?.name ?: "无模型")
                BenchmarkRow(stringResource(R.string.benchmark_status), if (running) stringResource(R.string.benchmark_running) else "—")
                BenchmarkRow(stringResource(R.string.benchmark_context), "${internals.contextTokens} 个 token")
                BenchmarkRow(stringResource(R.string.benchmark_device), device)
                Text(
                    stringResource(R.string.benchmark_repeat),
                    fontWeight = FontWeight.Bold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 3, 5).forEach { r ->
                        FilterChip(
                            selected = repeat == r,
                            onClick = { repeat = r },
                            label = {
                                Text(
                                    stringResource(
                                        if (r == 1) R.string.benchmark_times_once else R.string.benchmark_times,
                                        r,
                                    ),
                                )
                            },
                        )
                    }
                }
                if (activeEntry == null) {
                    Text(
                        stringResource(R.string.benchmark_no_model),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        item {
            Button(
                onClick = onOpenModelsEngine,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Text(stringResource(R.string.benchmark_open_engine))
            }
        }
        item {
            Button(
                onClick = ::runBenchmark,
                enabled = !running && activeEntry != null && effective != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("benchmark-run"),
                colors = ButtonDefaults.buttonColors(),
            ) {
                Text(
                    stringResource(if (running) R.string.benchmark_running else R.string.benchmark_run),
                )
            }
        }
        if (results.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.benchmark_history),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            items(results) { line ->
                PageCard { Text(line) }
            }
        }
    }
}

@Composable
private fun BenchmarkRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(0.3f))
        Text(value)
    }
}
