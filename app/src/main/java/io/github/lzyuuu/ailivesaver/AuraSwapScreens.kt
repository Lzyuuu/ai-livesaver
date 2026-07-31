package io.github.lzyuuu.ailivesaver

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

internal data class HfModelEntry(val id: String, val title: String, val size: String, val file: String, val role: String)

internal val HfModelCatalog = listOf(
    HfModelEntry("inswapper-128", "InsightFace inswapper_128", "ONNX · ~529 MB", "inswapper_128.onnx", "swapper"),
    HfModelEntry("buffalo-l", "InsightFace buffalo_l", "ONNX bundle · ~326 MB", "buffalo_l.zip", "detector"),
)

internal data class AuraSwapRequest(val source: File, val target: File, val swapper: File, val detector: File)

internal interface AuraSwapBackend {
    fun run(context: Context, request: AuraSwapRequest): File
}

/**
 * The Android ONNX adapter boundary. The dependency and licensed model bundle must be supplied
 * before this can execute; it deliberately refuses to emit a fabricated image.
 */
internal object OnnxRuntimeAuraBackend : AuraSwapBackend {
    override fun run(context: Context, request: AuraSwapRequest): File {
        require(request.source.isFile && request.target.isFile) { "Source 和 Target 图片不存在" }
        require(request.swapper.isFile && request.detector.isFile) { "inswapper_128 和 buffalo_l 模型必须同时安装" }
        require(request.swapper.extension == "onnx") { "换脸模型必须是 ONNX 格式" }
        throw UnsupportedOperationException(
            "真实 Aura 推理未启用：需要 ONNX Runtime Android、InsightFace buffalo_l 与 " +
                "inswapper_128 的授权模型文件；当前不会生成伪造结果。",
        )
    }
}

internal object HfModelStore {
    private const val PREFS = "hf_model_store"
    private const val KEY_URL = "download_url"

    fun directory(context: Context) = File(context.filesDir, "models").apply { mkdirs() }
    fun installed(context: Context): List<File> = directory(context).listFiles()?.filter { it.isFile } ?: emptyList()
    fun downloadUrl(context: Context): String = context.getSharedPreferences(PREFS, 0).getString(KEY_URL, "").orEmpty()
    fun saveDownloadUrl(context: Context, value: String) = context.getSharedPreferences(PREFS, 0).edit().putString(KEY_URL, value.trim()).apply()

    internal fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    internal fun downloadFile(
        context: Context,
        url: String,
        targetName: String,
        expectedSha256: String? = null,
        maxAttempts: Int = 3,
    ): File {
        require(url.startsWith("https://") || url.startsWith("http://")) { "请输入有效的 HTTP(S) 下载地址" }
        require(targetName.matches(Regex("[A-Za-z0-9._-]+"))) { "模型文件名无效" }
        val target = File(directory(context), targetName)
        val expected = expectedSha256?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        var lastError: Throwable? = null
        repeat(maxAttempts.coerceAtLeast(1)) { attempt ->
            val temp = File(directory(context), ".$targetName.part-$attempt")
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 8_000
                    connection.readTimeout = 60_000
                    connection.instanceFollowRedirects = true
                    connection.connect()
                    if (connection.responseCode !in 200..299) error("HF HTTP ${connection.responseCode}")
                    connection.inputStream.use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
                } finally {
                    connection.disconnect()
                }
                if (temp.length() == 0L) error("下载文件为空")
                if (expected != null && sha256(temp) != expected) error("SHA-256 校验失败")
                if (target.exists() && !target.delete()) error("无法替换旧模型文件")
                if (!temp.renameTo(target)) error("无法原子保存模型文件")
                return target
            } catch (error: Throwable) {
                lastError = error
                temp.delete()
                if (attempt + 1 == maxAttempts.coerceAtLeast(1)) throw error
            }
        }
        throw IOException(lastError?.message ?: "下载失败")
    }

    fun download(context: Context, url: String, targetName: String, expectedSha256: String? = null, onDone: (Result<File>) -> Unit) {
        Thread {
            val result = runCatching { downloadFile(context, url, targetName, expectedSha256) }
            android.os.Handler(android.os.Looper.getMainLooper()).post { onDone(result) }
        }.start()
    }

    internal fun validateOnnxModel(file: File) {
        require(file.isFile && file.length() > 8) { "模型文件不存在或为空" }
        file.inputStream().use { input ->
            val header = ByteArray(8)
            require(input.read(header) == 8 && header.copyOfRange(0, 4).contentEquals(byteArrayOf(0x08, 0x00, 0x00, 0x00))) {
                "模型不是可识别的 ONNX protobuf 文件"
            }
        }
    }
}

@Composable
internal fun AuraSwapScreen(contentPadding: PaddingValues, onBack: () -> Unit) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf("aura") }
    Column(Modifier.fillMaxSize().background(FancyInk).padding(contentPadding).testTag("aura-swap-screen")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("aura-back")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = FancyCream) }
            Text("Aura Swap", color = FancyCream, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = { tab = if (tab == "aura") "store" else "aura" }, modifier = Modifier.testTag("aura-store-toggle")) {
                Icon(if (tab == "aura") Icons.Default.Download else Icons.Default.Refresh, "切换", tint = FancyGold)
            }
        }
        if (tab == "store") ModelStoreContent(context) else AuraSwapContent(context)
    }
}

@Composable
private fun AuraSwapContent(context: Context) {
    var source by rememberSaveable { mutableStateOf("") }
    var target by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Swap the aura between two images.", color = FancyCream)
        Text("Source image", color = FancyGold, fontSize = 12.sp)
        OutlinedTextField(source, { source = it }, label = { Text("Source path") }, modifier = Modifier.fillMaxWidth().testTag("aura-source"), colors = auraFieldColors())
        Text("Target image", color = FancyGold, fontSize = 12.sp)
        OutlinedTextField(target, { target = it }, label = { Text("Target path") }, modifier = Modifier.fillMaxWidth().testTag("aura-target"), colors = auraFieldColors())
        Button(onClick = {
            val sourceFile = File(source.trim()); val targetFile = File(target.trim())
            status = when {
                !sourceFile.isFile || !targetFile.isFile -> "请选择存在的 Source 和 Target 图片。"
                HfModelStore.installed(context).size < 2 -> "请先下载 inswapper_128 和 buffalo_l 两个模型。"
                else -> runCatching {
                    val models = HfModelStore.installed(context)
                    val swapper = models.first { it.name == "inswapper_128.onnx" }
                    val detector = models.first { it.name == "buffalo_l.zip" }
                    HfModelStore.validateOnnxModel(swapper)
                    OnnxRuntimeAuraBackend.run(context, AuraSwapRequest(sourceFile, targetFile, swapper, detector))
                    "Aura Swap 完成"
                }.getOrElse { "Aura Swap 失败：${it.message}" }
            }
        }, modifier = Modifier.fillMaxWidth().testTag("aura-run"), colors = ButtonDefaults.buttonColors(containerColor = FancyGold, contentColor = FancyInk)) { Text("Run Aura Swap") }
        status?.let { Text(it, color = if (it.startsWith("Aura Swap 完成")) FancyGold else FancyCream, modifier = Modifier.testTag("aura-status")) }
    }
}

@Composable
private fun ModelStoreContent(context: Context) {
    var url by rememberSaveable { mutableStateOf(HfModelStore.downloadUrl(context)) }
    var downloading by remember { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Model Store", color = FancyCream, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text("需要 inswapper_128 + buffalo_l；仅下载已获授权的模型。", color = FancyCream, fontSize = 12.sp)
        HfModelCatalog.forEach { model ->
            Row(Modifier.fillMaxWidth().background(FancyNavyMid, RoundedCornerShape(12.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(model.title, color = FancyCream, fontWeight = FontWeight.SemiBold); Text(model.size, color = FancyGoldDim, fontSize = 11.sp) }
                Icon(Icons.Default.Check, null, tint = if (HfModelStore.installed(context).any { it.name == model.file }) FancyGold else FancyGoldDim)
            }
        }
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(url, { url = it; HfModelStore.saveDownloadUrl(context, it) }, label = { Text("Hugging Face download URL") }, modifier = Modifier.fillMaxWidth().testTag("hf-url"), colors = auraFieldColors())
        Button(onClick = {
            status = null; downloading = true
            HfModelStore.download(context, url, HfModelCatalog.first().file) { result -> downloading = false; status = result.fold({ "下载完成：${it.name}（尚未完成推理配对）" }, { "下载失败：${it.message}" }) }
        }, enabled = !downloading, modifier = Modifier.fillMaxWidth().testTag("hf-download"), colors = ButtonDefaults.buttonColors(containerColor = FancyGold, contentColor = FancyInk)) {
            Icon(Icons.Default.Download, null); Text(if (downloading) "Downloading…" else "Download from Hugging Face")
        }
        status?.let { Text(it, color = FancyCream, modifier = Modifier.testTag("hf-status")) }
        Text("已安装模型：${HfModelStore.installed(context).joinToString { it.name }.ifBlank { "暂无" }}", color = FancyCream, fontSize = 12.sp, modifier = Modifier.testTag("hf-installed"))
    }
}

@Composable
private fun auraFieldColors() = OutlinedTextFieldDefaults.colors(focusedTextColor = FancyCream, unfocusedTextColor = FancyCream, focusedLabelColor = FancyGold, unfocusedLabelColor = FancyCream.copy(alpha = .7f), focusedBorderColor = FancyGold, unfocusedBorderColor = FancyGoldDim, cursorColor = FancyGold)
