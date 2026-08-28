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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
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
 * 性能实验室（SO-10，参考 ref-79）：运行本地 Gemma 基准（llama.cpp 定长序列计时）。
 * 一次性跑 N token（重复=平均值），结果按次保留最近 3 条。
 */
@Composable
internal fun BenchmarkAppScreen(
    contentPadding: PaddingValues,
    onOpenModelsEngine: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val entries = remember { LocalModels.load(context) }
    val activeEntry = remember { entries.firstOrNull { it.id == LocalModels.activeEngineId(context) } }
    val device = remember { android.os.Build.MODEL }
    var repeat by rememberSaveable { mutableStateOf(3) }
    var running by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf(listOf<String>()) }

    val deviceRamMb = remember {
        val info = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.let { it.getMemoryInfo(info) }
        (info.totalMem / (1L shl 20)).toInt()
    }

    fun runBenchmark() {
        val entry = activeEntry ?: return
        if (running) return
        running = true
        Thread {
            val modelPath = File(HfModelStore.directory(context), "${entry.id}/${entry.fileName}").absolutePath
            val scores = buildList {
                repeat(repeat) {
                    runCatching { LlamaNative.nativeBenchmark(modelPath, 32, 4) }
                        .getOrNull()
                        ?.takeIf { it > 0f }
                        ?.let { add(it) }
                }
            }
            val line = if (scores.isNotEmpty()) {
                val avg = scores.average()
                "%.2f tokens/s（%d 次平均）".format(avg, scores.size)
            } else {
                "基准失败（请检查模型与内存）"
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
            ScreenBackButton(onBack)
            Text(
                stringResource(R.string.benchmark_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
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
                BenchmarkRow(stringResource(R.string.benchmark_context), "2048 个 token (${deviceRamMb} MB)")
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
                            label = { Text(stringResource(R.string.benchmark_times, r)) },
                        )
                    }
                }
                if (activeEntry == null) {
                    Text(
                        stringResource(R.string.benchmark_no_model),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        if (activeEntry != null) {
            item {
                Button(
                    onClick = ::runBenchmark,
                    enabled = !running,
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
        item {
            Button(
                onClick = onOpenModelsEngine,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Text(stringResource(R.string.benchmark_open_engine))
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
