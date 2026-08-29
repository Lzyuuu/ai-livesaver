package io.github.lzyuuu.ailivesaver

import android.content.Context

/**
 * 商店真实下载（修正"计时器假进度"）：把商店产品映射到带官方 SHA-256 的
 * 真实资产，逐项下载并校验。
 * - hd-upscalers → UpscalerModelStore 两个 Real-ESRGAN MNN 模型（hf-mirror 镜像）；
 * - aura_swap → HfModelCatalog 四个 MNN 模型（URL 改走 hf-mirror 镜像，直连不可达；
 *   SHA-256 仍用目录钉住的官方值，镜像不改变校验依据）。
 * 其他产品（纯应用）无下载需求 → 空列表，安装即完成。
 */
internal data class StoreDownloadItem(
    val url: String,
    val fileName: String,
    val sha256: String,
) {
    val dirKind: DirKind
        get() = if (fileName.endsWith(".fp16.mnn") && UpscalerModelStore.catalog.any { it.file == fileName }) {
            DirKind.UPSCALER
        } else {
            DirKind.HF_MODEL
        }

    enum class DirKind { UPSCALER, HF_MODEL }
}

internal val HUGGINGFACE_TO_MIRROR = "https://huggingface.co/" to "https://hf-mirror.com/"

internal fun storeDownloadItems(productId: String): List<StoreDownloadItem> = when (productId) {
    "hd-upscalers" -> UpscalerModelStore.catalog.map {
        StoreDownloadItem("${UpscalerModelStore.URL_BASE}/${it.file}", it.file, it.sha256)
    }
    "aura_swap" -> HfModelCatalog.map {
        StoreDownloadItem(it.url.replace(HUGGINGFACE_TO_MIRROR.first, HUGGINGFACE_TO_MIRROR.second), it.file, it.sha256)
    }
    else -> emptyList()
}

internal object StoreDownloads {
    /**
     * 逐项真实下载（HTTP + 原子落盘 + SHA-256 校验，见 HfModelStore.downloadFile）。
     * 任一项失败即抛出，由调用方将安装态回退 NOT_INSTALLED；半成品 .part 文件由下载器清理。
     * onProgress(doneCount, total) 在每项完成后回调。
     */
    fun downloadAll(
        context: Context,
        items: List<StoreDownloadItem>,
        onProgress: (doneCount: Int, total: Int) -> Unit = { _, _ -> },
    ) {
        require(items.isNotEmpty()) { "该产品没有可下载资产" }
        items.forEachIndexed { index, item ->
            val dir = when (item.dirKind) {
                StoreDownloadItem.DirKind.UPSCALER -> UpscalerModelStore.dir(context)
                StoreDownloadItem.DirKind.HF_MODEL -> HfModelStore.directory(context)
            }
            val file = HfModelStore.downloadFile(dir, item.url, item.fileName, item.sha256)
            if (!HfModelStore.matchesTrustedSha(file, item.sha256)) error("校验失败：${item.fileName}")
            onProgress(index + 1, items.size)
        }
    }
}
