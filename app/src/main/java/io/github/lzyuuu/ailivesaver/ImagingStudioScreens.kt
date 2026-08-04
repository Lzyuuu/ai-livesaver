package io.github.lzyuuu.ailivesaver

import android.app.Activity
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import kotlin.random.Random

internal enum class ImagingBackend(val label: String, val route: String) {
    OnDevice("On-device", "on_device"),
    Forge("Forge", "forge"),
    LocalDream("Local Dream", "local_dream"),
    ;

    companion object {
        fun fromRoute(route: String?): ImagingBackend =
            entries.firstOrNull { it.route == route } ?: OnDevice
    }
}

internal data class ImagingStudioSettings(
    val backend: ImagingBackend = ImagingBackend.OnDevice,
    val forgeUrl: String = DEFAULT_FORGE_URL,
    val steps: Int = 20,
    val cfg: Double = 7.0,
    val seed: Long = 0L,
    val seedLocked: Boolean = false,
    val width: Int = 512,
    val height: Int = 512,
    val scheduler: String = "dpm",
    val negativePrompt: String = "",
) {
    companion object {
        const val DEFAULT_FORGE_URL = "http://127.0.0.1:7860"
    }
}

private const val IMAGING_PREFS = "imaging_studio"
private const val KEY_BACKEND = "backend"
private const val KEY_FORGE_URL = "forge_url"
private const val KEY_STEPS = "steps"
private const val KEY_CFG = "cfg"
private const val KEY_SEED = "seed"
private const val KEY_SEED_LOCKED = "seed_locked"
private const val KEY_WIDTH = "width"
private const val KEY_HEIGHT = "height"
private const val KEY_SCHEDULER = "scheduler"
private const val KEY_NEGATIVE = "negative_prompt"

internal object ImagingStudioStore {
    fun load(context: Context): ImagingStudioSettings {
        val prefs = context.getSharedPreferences(IMAGING_PREFS, Context.MODE_PRIVATE)
        return ImagingStudioSettings(
            backend = ImagingBackend.fromRoute(prefs.getString(KEY_BACKEND, null)),
            forgeUrl = prefs.getString(KEY_FORGE_URL, ImagingStudioSettings.DEFAULT_FORGE_URL)
                ?: ImagingStudioSettings.DEFAULT_FORGE_URL,
            steps = prefs.getInt(KEY_STEPS, 20).coerceIn(1, 100),
            cfg = prefs.getFloat(KEY_CFG, 7.0f).toDouble().coerceIn(1.0, 30.0),
            seed = prefs.getLong(KEY_SEED, 0L),
            seedLocked = prefs.getBoolean(KEY_SEED_LOCKED, false),
            width = prefs.getInt(KEY_WIDTH, 512).coerceIn(64, 1536),
            height = prefs.getInt(KEY_HEIGHT, 512).coerceIn(64, 1536),
            scheduler = prefs.getString(KEY_SCHEDULER, "dpm") ?: "dpm",
            negativePrompt = prefs.getString(KEY_NEGATIVE, "").orEmpty(),
        )
    }

    fun save(context: Context, settings: ImagingStudioSettings) {
        context.getSharedPreferences(IMAGING_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BACKEND, settings.backend.route)
            .putString(KEY_FORGE_URL, settings.forgeUrl.trim())
            .putInt(KEY_STEPS, settings.steps.coerceIn(1, 100))
            .putFloat(KEY_CFG, settings.cfg.toFloat())
            .putLong(KEY_SEED, settings.seed)
            .putBoolean(KEY_SEED_LOCKED, settings.seedLocked)
            .putInt(KEY_WIDTH, settings.width)
            .putInt(KEY_HEIGHT, settings.height)
            .putString(KEY_SCHEDULER, settings.scheduler)
            .putString(KEY_NEGATIVE, settings.negativePrompt)
            .apply()
    }
}

internal fun normalizeForgeBaseUrl(raw: String): String {
    val trimmed = raw.trim().trimEnd('/')
    return trimmed.ifBlank { ImagingStudioSettings.DEFAULT_FORGE_URL }
}

internal object ForgeClient {
    fun probe(baseUrl: String, callback: (Result<String>) -> Unit) {
        Thread {
            val result = runCatching {
                val root = normalizeForgeBaseUrl(baseUrl)
                val body = get("$root/sdapi/v1/sd-models")
                val count = org.json.JSONArray(body).length()
                "Connected to Forge at $root · $count models"
            }
            Handler(Looper.getMainLooper()).post { callback(result) }
        }.start()
    }

    fun generate(
        baseUrl: String,
        prompt: String,
        negativePrompt: String,
        steps: Int,
        cfg: Double,
        seed: Long,
        width: Int,
        height: Int,
        callback: (Result<ByteArray>) -> Unit,
    ) {
        Thread {
            val result = runCatching {
                val root = normalizeForgeBaseUrl(baseUrl)
                val request = JSONObject()
                    .put("prompt", prompt)
                    .put("negative_prompt", negativePrompt)
                    .put("steps", steps)
                    .put("cfg_scale", cfg)
                    .put("seed", seed)
                    .put("width", width)
                    .put("height", height)
                val body = postJson("$root/sdapi/v1/txt2img", request)
                val images = JSONObject(body).optJSONArray("images")
                    ?: throw IOException("Forge response missing images")
                if (images.length() == 0) throw IOException("Forge returned no images")
                Base64.getDecoder().decode(images.getString(0))
            }
            Handler(Looper.getMainLooper()).post { callback(result) }
        }.start()
    }

    private fun get(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 3_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (connection.responseCode !in 200..299) {
                throw IOException("Forge HTTP ${connection.responseCode}")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun postJson(url: String, body: JSONObject): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 3_000
            readTimeout = 10 * 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            if (connection.responseCode !in 200..299) {
                throw IOException("Forge HTTP ${connection.responseCode}")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

private val ImagingBg = Color(0xFF0B0B0F)
private val ImagingCard = Color(0xFF18181F)
private val ImagingCardBorder = Color(0xFF2E2E38)
private val ImagingSegment = Color(0xFF121218)
private val ImagingSegmentSelected = Color(0xFF27344A)
private val ImagingSegmentSelectedBorder = Color(0xFF3D4F66)
private val ImagingPreviewInner = Color(0xFF22222C)
private val ImagingMuted = Color(0xFF8E8E9A)
private val ImagingCta = Color(0xFFC4A05A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImagingStudioScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    var settings by remember { mutableStateOf(ImagingStudioStore.load(context)) }
    var prompt by rememberSaveable { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var progressStep by remember { mutableIntStateOf(0) }
    var progressTotal by remember { mutableIntStateOf(0) }
    var generating by remember { mutableStateOf(false) }
    var probing by remember { mutableStateOf(false) }
    var resultPath by remember { mutableStateOf<String?>(null) }
    var showAdvanced by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousStatus = controller?.isAppearanceLightStatusBars
        val previousNav = controller?.isAppearanceLightNavigationBars
        window?.statusBarColor = android.graphics.Color.TRANSPARENT
        window?.navigationBarColor = android.graphics.Color.parseColor("#0C0C10")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window?.isNavigationBarContrastEnforced = false
        }
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            previousStatus?.let { controller?.isAppearanceLightStatusBars = it }
            previousNav?.let { controller?.isAppearanceLightNavigationBars = it }
        }
    }

    fun persist(next: ImagingStudioSettings) {
        settings = next
        ImagingStudioStore.save(context, next)
    }

    fun runGenerate() {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty()) {
            status = "Enter a generation prompt first."
            return
        }
        val seed = if (settings.seedLocked) {
            settings.seed
        } else {
            Random.nextLong(0, Int.MAX_VALUE.toLong()).also { rolled ->
                persist(settings.copy(seed = rolled))
            }
        }
        generating = true
        progressStep = 0
        progressTotal = settings.steps
        status = null
        resultPath = null
        when (settings.backend) {
            ImagingBackend.OnDevice -> {
                generating = false
                status = "No on-device (Aura) model selected — pick one in Imaging or download a model."
            }
            ImagingBackend.Forge -> {
                ForgeClient.generate(
                    baseUrl = settings.forgeUrl,
                    prompt = trimmed,
                    negativePrompt = settings.negativePrompt,
                    steps = settings.steps,
                    cfg = settings.cfg,
                    seed = seed,
                    width = settings.width,
                    height = settings.height,
                ) { result ->
                    generating = false
                    result.fold(
                        onSuccess = { bytes ->
                            val saved = runCatching {
                                val directory = java.io.File(context.filesDir, "media").apply { mkdirs() }
                                val file = java.io.File(directory, "forge-${System.currentTimeMillis()}.png")
                                file.writeBytes(bytes)
                                file.absolutePath
                            }
                            saved.fold(
                                onSuccess = {
                                    resultPath = it
                                    WorldStore(context).use { store ->
                                        store.saveCreativeAsset(CreativeAsset(0, it, "image", settings.backend.route, trimmed, null, "", null, null, "ready", "", System.currentTimeMillis()))
                                    }
                                    status = "Forge generation ready · seed $seed"
                                },
                                onFailure = { status = "Studio generation failed: ${it.message}" },
                            )
                        },
                        onFailure = {
                            status = "Studio generation failed: ${it.message.orEmpty()}"
                        },
                    )
                }
            }
            ImagingBackend.LocalDream -> {
                val job = MediaJob(
                    postId = -1L,
                    revision = 0L,
                    prompt = trimmed,
                    negativePrompt = settings.negativePrompt,
                    steps = settings.steps,
                    cfg = settings.cfg,
                    scheduler = settings.scheduler.ifBlank { "dpm" },
                    width = settings.width,
                    height = settings.height,
                    seed = seed,
                    status = "pending",
                    error = "",
                )
                LocalDreamClient.generate(
                    context = context,
                    job = job,
                    onProgress = { step, total ->
                        progressStep = step
                        progressTotal = total
                        status = "$step / $total"
                    },
                ) { result ->
                    generating = false
                    result.fold(
                        onSuccess = { image ->
                            resultPath = image.path
                            WorldStore(context).use { store ->
                                store.saveCreativeAsset(CreativeAsset(0, image.path, "image", settings.backend.route, trimmed, null, "", null, null, "ready", "", System.currentTimeMillis()))
                            }
                            status = "Local Dream generation ready · seed ${image.seed}"
                            WorldStore(context).use { store ->
                                store.createImportedMediaPost(
                                    body = "Imaging Studio",
                                    path = image.path,
                                    description = trimmed,
                                    audience = "world",
                                    audienceCharacterIds = "",
                                    aiResponsesEnabled = false,
                                    prompt = trimmed,
                                    negativePrompt = settings.negativePrompt,
                                    seed = image.seed,
                                    steps = settings.steps,
                                    cfg = settings.cfg,
                                    scheduler = settings.scheduler,
                                    width = image.stats.width.takeIf { it > 0 } ?: settings.width,
                                    height = image.stats.height.takeIf { it > 0 } ?: settings.height,
                                )
                            }
                        },
                        onFailure = {
                            status = "Studio generation failed: ${it.message.orEmpty()}"
                        },
                    )
                }
            }
        }
    }

    fun testConnection() {
        probing = true
        status = null
        when (settings.backend) {
            ImagingBackend.OnDevice -> {
                probing = false
                status = "No on-device (Aura) model selected."
            }
            ImagingBackend.Forge -> {
                ForgeClient.probe(settings.forgeUrl) { result ->
                    probing = false
                    status = result.fold(
                        onSuccess = { it },
                        onFailure = { "Forge unavailable: ${it.message.orEmpty()}" },
                    )
                }
            }
            ImagingBackend.LocalDream -> {
                LocalDreamClient.probe { result ->
                    probing = false
                    status = result.fold(
                        onSuccess = { "Connected to Local Dream at http://127.0.0.1:8081 · CLIP $it tokens" },
                        onFailure = { "Local Dream unavailable: ${it.message.orEmpty()}" },
                    )
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ImagingBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("imaging-studio"),
    ) {
        ImagingTopBar(
            onBack = onBack,
            onRefresh = { showClearConfirm = true },
            onAdvanced = { showAdvanced = true },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp)
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BackendSelector(
                backend = settings.backend,
                onSelect = { persist(settings.copy(backend = it)) },
            )
            Text(
                when (settings.backend) {
                    ImagingBackend.OnDevice ->
                        "Generates on this phone from an installed model."
                    ImagingBackend.Forge ->
                        "Generates on an AUTOMATIC1111 / Forge server on your network. Set the address in Settings → Image Generation."
                    ImagingBackend.LocalDream ->
                        "Generates on a Local Dream server on your network. Set the address in Settings → Image Generation."
                },
                color = ImagingMuted,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .padding(bottom = 14.dp),
            )

            when (settings.backend) {
                ImagingBackend.OnDevice -> OnDeviceModelCard()
                ImagingBackend.Forge, ImagingBackend.LocalDream -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(ImagingCard)
                            .padding(horizontal = 12.dp, vertical = 18.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, ImagingCardBorder, RoundedCornerShape(12.dp))
                                .clickable(enabled = !probing && !generating) { testConnection() }
                                .testTag("imaging-test-connection"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (probing) "Testing…" else "Test Connection",
                                color = FancyCream,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }

            Spacer(
                Modifier.height(
                    when (settings.backend) {
                        ImagingBackend.OnDevice -> 7.dp
                        ImagingBackend.Forge, ImagingBackend.LocalDream -> 4.dp
                    },
                ),
            )
            PreviewCard(
                resultPath = resultPath,
                generating = generating,
                progressStep = progressStep,
                progressTotal = progressTotal,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp),
            )

            status?.let { message ->
                Text(
                    message,
                    color = if (
                        message.contains("failed", ignoreCase = true) ||
                        message.contains("unavailable", ignoreCase = true) ||
                        message.contains("No on-device", ignoreCase = true)
                    ) {
                        Color(0xFFE08A8A)
                    } else {
                        ImagingMuted
                    },
                    fontSize = 11.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("imaging-status"),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Create,
                    contentDescription = null,
                    tint = ImagingCta,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text("Prompt", color = ImagingCta, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .testTag("imaging-prompt"),
                label = { Text("Generation Prompt") },
                colors = imagingFieldColors(),
                shape = RoundedCornerShape(10.dp),
            )
            Button(
                onClick = { if (!generating) runGenerate() },
                enabled = !generating,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("imaging-generate"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ImagingCta,
                    contentColor = ImagingBg,
                    disabledContainerColor = ImagingCard,
                    disabledContentColor = FancyCream.copy(alpha = 0.35f),
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    if (generating) "Generating…" else "Generate",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
            }
        }
    }

    if (showClearConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            confirmButton = { Button(onClick = { prompt = ""; resultPath = null; status = null; progressStep = 0; progressTotal = 0; showClearConfirm = false }) { Text("Clear") } },
            dismissButton = { Button(onClick = { showClearConfirm = false }) { Text("Cancel") } },
            title = { Text("Clear Studio?") },
            text = { Text("Only clears this studio input, preview, and temporary state. Gallery assets are kept.") },
        )
    }

    if (showAdvanced) {
        ModalBottomSheet(
            onDismissRequest = { showAdvanced = false },
            sheetState = sheetState,
            containerColor = ImagingCard,
            contentColor = FancyCream,
        ) {
            AdvancedSheet(
                settings = settings,
                onChange = { persist(it) },
            )
        }
    }
}

@Composable
private fun ImagingTopBar(
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onAdvanced: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.testTag("imaging-back"),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = ImagingMuted,
            )
        }
        Text(
            "Imaging Studio",
            color = FancyCream,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Start,
        )
        IconButton(onClick = onRefresh) {
            Icon(Icons.Default.Refresh, contentDescription = "刷新", tint = ImagingMuted)
        }
        IconButton(
            onClick = onAdvanced,
            modifier = Modifier.testTag("imaging-advanced"),
        ) {
            ImagingSlidersGlyph(modifier = Modifier.size(22.dp), color = ImagingMuted)
        }
    }
}

@Composable
private fun BackendSelector(
    backend: ImagingBackend,
    onSelect: (ImagingBackend) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ImagingCard)
            .padding(horizontal = 15.dp, vertical = 20.dp)
            .testTag("imaging-backend-card"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, ImagingCardBorder, RoundedCornerShape(14.dp))
                .background(ImagingSegment)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            ImagingBackend.entries.forEach { option ->
                val selected = option == backend
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (selected) ImagingSegmentSelected else Color.Transparent)
                        .then(
                            if (selected) {
                                Modifier.border(1.dp, ImagingSegmentSelectedBorder, RoundedCornerShape(11.dp))
                            } else {
                                Modifier
                            },
                        )
                        .clickable { onSelect(option) }
                        .padding(horizontal = 4.dp)
                        .testTag("imaging-backend-${option.route}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (selected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(3.dp))
                    }
                    Text(
                        option.label,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun OnDeviceModelCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ImagingCard)
            .padding(horizontal = 16.dp, vertical = 18.dp)
            .testTag("imaging-on-device-card"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Selected model", color = ImagingMuted, fontSize = 11.sp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "None selected",
                color = Color(0xFFA6A6B0),
                fontWeight = FontWeight.Normal,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = ImagingMuted,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            "No image model installed yet — download one to generate images on-device.",
            color = ImagingMuted,
            fontSize = 11.sp,
            lineHeight = 15.sp,
        )
        Spacer(Modifier.height(2.dp))
        Button(
            onClick = { /* Model Store is issue #29; CTA matches reference */ },
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .testTag("imaging-download-model"),
            colors = ButtonDefaults.buttonColors(
                containerColor = ImagingCta,
                contentColor = ImagingBg,
            ),
            shape = RoundedCornerShape(12.dp),
        ) {
            ImagingDownloadGlyph(modifier = Modifier.size(16.dp), color = ImagingBg)
            Spacer(Modifier.width(8.dp))
            Text(
                "Download CyberRealistic (SD 1.5 · 1.3 GB)",
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun PreviewCard(
    resultPath: String?,
    generating: Boolean,
    progressStep: Int,
    progressTotal: Int,
    modifier: Modifier = Modifier,
) {
    val bitmap = remember(resultPath) {
        resultPath?.let { path ->
            runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(ImagingCard)
            .padding(14.dp)
            .testTag("imaging-preview"),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(10.dp))
                .background(ImagingPreviewInner),
            contentAlignment = Alignment.Center,
        ) {
            when {
                bitmap != null -> {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Generated image",
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
                generating -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = ImagingCta)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (progressTotal > 0) "$progressStep / $progressTotal" else "Generating…",
                            color = FancyCream,
                        )
                    }
                }
                else -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ImagingPaletteGlyph(modifier = Modifier.size(32.dp), color = ImagingMuted)
                        Spacer(Modifier.height(8.dp))
                        Text("Your image will appear here", color = ImagingMuted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AdvancedSheet(
    settings: ImagingStudioSettings,
    onChange: (ImagingStudioSettings) -> Unit,
) {
    var stepsText by remember(settings.steps) { mutableStateOf(settings.steps.toString()) }
    var cfgText by remember(settings.cfg) { mutableStateOf(settings.cfg.toString()) }
    var seedText by remember(settings.seed) { mutableStateOf(settings.seed.toString()) }
    var forgeUrl by remember(settings.forgeUrl) { mutableStateOf(settings.forgeUrl) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .padding(bottom = 20.dp)
            .testTag("imaging-advanced-sheet"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Advanced", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            OutlinedTextField(
                value = stepsText,
                onValueChange = {
                    stepsText = it
                    it.toIntOrNull()?.let { steps ->
                        onChange(settings.copy(steps = steps.coerceIn(1, 100)))
                    }
                },
                modifier = Modifier.weight(1f).testTag("imaging-steps"),
                label = { Text("Steps") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = imagingOutlinedFieldColors(),
                shape = RoundedCornerShape(12.dp),
            )
            OutlinedTextField(
                value = cfgText,
                onValueChange = {
                    cfgText = it
                    it.toDoubleOrNull()?.let { cfg ->
                        onChange(settings.copy(cfg = cfg.coerceIn(1.0, 30.0)))
                    }
                },
                modifier = Modifier.weight(1f).testTag("imaging-cfg"),
                label = { Text("CFG Scale") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = imagingOutlinedFieldColors(),
                shape = RoundedCornerShape(12.dp),
            )
        }
        Text(
            "Steps is the number of refinement passes. CFG controls how strictly the model follows your prompt.",
            color = ImagingMuted,
            fontSize = 11.sp,
            lineHeight = 15.sp,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            OutlinedTextField(
                value = seedText,
                onValueChange = {
                    seedText = it
                    it.toLongOrNull()?.let { seed -> onChange(settings.copy(seed = seed)) }
                },
                modifier = Modifier.weight(1f).testTag("imaging-seed"),
                label = { Text("Seed") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = imagingOutlinedFieldColors(),
                shape = RoundedCornerShape(12.dp),
            )
            IconButton(
                onClick = { onChange(settings.copy(seedLocked = !settings.seedLocked)) },
                modifier = Modifier
                    .size(40.dp)
                    .testTag("imaging-seed-lock"),
            ) {
                if (settings.seedLocked) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = "Locked seed",
                        tint = ImagingCta,
                        modifier = Modifier.size(22.dp),
                    )
                } else {
                    ImagingUnlockedGlyph(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                    )
                }
            }
            IconButton(
                onClick = {
                    val rolled = Random.nextLong(0, Int.MAX_VALUE.toLong())
                    seedText = rolled.toString()
                    onChange(settings.copy(seed = rolled))
                },
                modifier = Modifier
                    .size(40.dp)
                    .testTag("imaging-seed-roll"),
            ) {
                ImagingDiceGlyph(modifier = Modifier.size(22.dp), color = Color.White)
            }
        }
        Text(
            if (settings.seedLocked) {
                "Locked: the same seed is reused for every run."
            } else {
                "Unlocked: a fresh random seed is rolled for every run."
            },
            color = ImagingMuted,
            fontSize = 11.sp,
            lineHeight = 15.sp,
        )
        if (settings.backend == ImagingBackend.Forge) {
            OutlinedTextField(
                value = forgeUrl,
                onValueChange = {
                    forgeUrl = it
                    onChange(settings.copy(forgeUrl = normalizeForgeBaseUrl(it)))
                },
                modifier = Modifier.fillMaxWidth().testTag("imaging-forge-url"),
                label = { Text("Forge Server URL") },
                singleLine = true,
                colors = imagingOutlinedFieldColors(),
                shape = RoundedCornerShape(12.dp),
            )
            Text(
                "Use 10.0.2.2 if the server is on your host PC (emulator only).",
                color = ImagingMuted,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
        }
    }
}

@Composable
private fun ImagingSlidersGlyph(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.10f
        val ys = listOf(size.height * 0.28f, size.height * 0.50f, size.height * 0.72f)
        val knobs = listOf(size.width * 0.62f, size.width * 0.35f, size.width * 0.70f)
        ys.forEachIndexed { index, y ->
            drawLine(color, Offset(size.width * 0.12f, y), Offset(size.width * 0.88f, y), stroke, StrokeCap.Round)
            drawCircle(color, radius = size.minDimension * 0.11f, center = Offset(knobs[index], y))
        }
    }
}

@Composable
private fun ImagingDownloadGlyph(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.10f
        drawLine(color, Offset(size.width * 0.50f, size.height * 0.12f), Offset(size.width * 0.50f, size.height * 0.62f), stroke, StrokeCap.Round)
        val arrow = Path().apply {
            moveTo(size.width * 0.28f, size.height * 0.42f)
            lineTo(size.width * 0.50f, size.height * 0.66f)
            lineTo(size.width * 0.72f, size.height * 0.42f)
        }
        drawPath(arrow, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
        drawLine(color, Offset(size.width * 0.20f, size.height * 0.82f), Offset(size.width * 0.80f, size.height * 0.82f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun ImagingPaletteGlyph(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // Kidney / artist-palette silhouette (thumb hole on the right).
        val body = Path().apply {
            moveTo(w * 0.18f, h * 0.42f)
            cubicTo(w * 0.12f, h * 0.18f, w * 0.38f, h * 0.06f, w * 0.58f, h * 0.14f)
            cubicTo(w * 0.78f, h * 0.22f, w * 0.92f, h * 0.34f, w * 0.86f, h * 0.52f)
            cubicTo(w * 0.80f, h * 0.72f, w * 0.62f, h * 0.88f, w * 0.40f, h * 0.84f)
            cubicTo(w * 0.18f, h * 0.80f, w * 0.10f, h * 0.62f, w * 0.18f, h * 0.42f)
            close()
        }
        drawPath(body, color)
        // Punch holes against the preview inner canvas.
        val holeColor = ImagingPreviewInner
        drawCircle(holeColor, radius = size.minDimension * 0.12f, center = Offset(w * 0.70f, h * 0.52f))
        val wells = listOf(
            Offset(w * 0.34f, h * 0.34f),
            Offset(w * 0.50f, h * 0.28f),
            Offset(w * 0.42f, h * 0.52f),
            Offset(w * 0.32f, h * 0.66f),
        )
        wells.forEach { drawCircle(holeColor, radius = size.minDimension * 0.06f, center = it) }
    }
}

@Composable
private fun ImagingUnlockedGlyph(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.10f
        val bodyTop = size.height * 0.48f
        drawRoundRect(
            color,
            topLeft = Offset(size.width * 0.22f, bodyTop),
            size = Size(size.width * 0.56f, size.height * 0.40f),
            cornerRadius = CornerRadius(size.minDimension * 0.08f),
            style = Stroke(width = stroke),
        )
        // Open shackle (gap on the left)
        val path = Path().apply {
            moveTo(size.width * 0.34f, bodyTop)
            lineTo(size.width * 0.34f, size.height * 0.28f)
            quadraticTo(size.width * 0.34f, size.height * 0.12f, size.width * 0.50f, size.height * 0.12f)
            quadraticTo(size.width * 0.66f, size.height * 0.12f, size.width * 0.66f, size.height * 0.28f)
            lineTo(size.width * 0.66f, bodyTop)
        }
        drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}

@Composable
private fun ImagingDiceGlyph(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val inset = size.minDimension * 0.12f
        drawRoundRect(
            color,
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2, size.height - inset * 2),
            cornerRadius = CornerRadius(size.minDimension * 0.12f),
            style = Stroke(width = size.minDimension * 0.08f),
        )
        val r = size.minDimension * 0.07f
        listOf(
            Offset(size.width * 0.32f, size.height * 0.32f),
            Offset(size.width * 0.68f, size.height * 0.32f),
            Offset(size.width * 0.50f, size.height * 0.50f),
            Offset(size.width * 0.32f, size.height * 0.68f),
            Offset(size.width * 0.68f, size.height * 0.68f),
        ).forEach { drawCircle(color, r, it) }
    }
}

@Composable
private fun imagingFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = ImagingCta,
    unfocusedBorderColor = ImagingCardBorder,
    focusedLabelColor = ImagingCta,
    unfocusedLabelColor = ImagingMuted,
    cursorColor = ImagingCta,
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedContainerColor = ImagingSegment,
    unfocusedContainerColor = ImagingSegment,
)

/** Advanced sheet: transparent fill so floating labels interrupt the outline like Fancy AI. */
@Composable
private fun imagingOutlinedFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = ImagingMuted,
    unfocusedBorderColor = ImagingMuted.copy(alpha = 0.65f),
    focusedLabelColor = ImagingMuted,
    unfocusedLabelColor = ImagingMuted,
    cursorColor = ImagingCta,
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
)
