package io.github.lzyuuu.ailivesaver

import android.content.Context
import java.io.File

/**
 * Imaging On-device 模型（参考 V4.51 "Download CyberRealistic (SD 1.5 · 1.3 GB)"）。
 * 源：参考 APP 官方 HF 仓库 Mr-J-369/Fancy-AI 的 CyberRealistic-LCM-Startup-model
 * （MNN 权重，与 AuraSwap 同一运行时栈），共 11 个文件合计 1252.3 MB。
 * 校验：逐文件官方 SHA-256（LFS OID / tokenizer.json 实测值），复用 HfModelStore
 * 的重试下载与内容校验原语；全部就绪才算"已安装"，损坏文件不算可用。
 */
internal data class ImagingModelFile(val file: String, val bytes: Long, val sha256: String)

internal object ImagingModelStore {
    internal const val URL_BASE =
        "https://huggingface.co/Mr-J-369/Fancy-AI/resolve/main/CyberRealistic-LCM-Startup-model"
    private const val DIR_NAME = "CyberRealistic-LCM"
    private const val PREFS = "imaging_model_store"

    val catalog: List<ImagingModelFile> = listOf(
        ImagingModelFile("clip_v2.mnn", 147192, "0383c3a1b1d3435f320ed8a942b749c59182ab758b2d18ecf68c76f8bf1eebb4"),
        ImagingModelFile("pos_emb.bin", 236544, "790fb5de2fd1756121be6b90ec6dbfa4f749f819accf64aacae06064fd5e7df3"),
        ImagingModelFile("vae_encoder.mnn", 121904, "d3ad5c728b9ba9c9ad5af3fe5dc1e9f00d5a2f261ad9647149eaed86b202b53d"),
        ImagingModelFile("vae_decoder.mnn", 153688, "01b2e0444ca917c81e04aa8a884852f5728e78f6fc4d34e192a577e42067f63c"),
        ImagingModelFile("unet.mnn", 1107376, "6b5314275c54386fdd01e898276ea66d3767ae2bfaac8b061dc63d427f01b5ce"),
        ImagingModelFile("tokenizer.json", 3642034, "5f120510b5c01ccdb9a7d29d2beca07cac667f76472cab123ffb1c2f0b27cdeb"),
        ImagingModelFile("token_emb.bin", 75890688, "2497ae10858500dee05b79ef4acb7a2aad5e37bd7260b996b16ef6e34f935e26"),
        ImagingModelFile("vae_encoder.mnn.weight", 68317120, "89e1a9d8a74c64c5226f650e9603550337f042e63cb10086b2356e3edd0acd1d"),
        ImagingModelFile("vae_decoder.mnn.weight", 98963772, "f97ef277f3d0345c18906cfc07d4ad93f99ed605f7164747013b601f11a36cbe"),
        ImagingModelFile("clip_v2.mnn.weight", 156158976, "7401ebb1691e56a870ea8f56de1b2606ba8afc48286515e41f6d258af54b476d"),
        ImagingModelFile("unet.mnn.weight", 908377536, "faa31de878c7eb06c947df5f34a008048a8d48a37cd7b6174219774d99c1d9b7"),
    )

    const val TOTAL_BYTES: Long = 1313116830L

    fun dir(context: Context): File =
        File(HfModelStore.directory(context), DIR_NAME).apply { mkdirs() }

    private fun verified(context: Context, entry: ImagingModelFile): Boolean {
        val file = File(dir(context), entry.file)
        if (!HfModelStore.matchesTrustedSha(file, entry.sha256)) return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("v_" + entry.file, "${entry.sha256}|${file.length()}-${file.lastModified()}")
            .apply()
        return true
    }

    /** 快速就绪判定：指纹命中直接算通过，未命中才重算哈希。 */
    fun isReady(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return catalog.all { entry ->
            val file = File(dir(context), entry.file)
            if (!file.isFile || file.length() != entry.bytes) return@all false
            val fingerprint = prefs.getString("v_" + entry.file, null)
            fingerprint == "${entry.sha256}|${file.length()}-${file.lastModified()}" ||
                verified(context, entry)
        }
    }

    fun readyBytes(context: Context): Long = catalog.sumOf { entry ->
        val file = File(dir(context), entry.file)
        if (file.isFile && (file.length() == entry.bytes || verified(context, entry))) entry.bytes else 0L
    }

    fun remove(context: Context) {
        dir(context).deleteRecursively()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        catalog.forEach { prefs.edit().remove("v_" + it.file).apply() }
    }

    /**
     * 串行下载全部文件（小文件优先，失败快速暴露）；已校验通过的文件跳过。
     * onProgress(doneBytes, totalBytes) 按官方字节数汇报。任一文件重试耗尽即整体失败。
     */
    fun downloadAll(
        context: Context,
        onProgress: (Long, Long) -> Unit,
        onDone: (Result<Unit>) -> Unit,
    ): Boolean {
        val target = dir(context)
        val pending = catalog.filter { !verified(context, it) }
        val total = pending.sumOf { it.bytes }
        Thread {
            var done = 0L
            val result = runCatching {
                pending.forEach { entry ->
                    val before = done
                    HfModelStore.downloadFile(
                        target,
                        "$URL_BASE/${entry.file}",
                        entry.file,
                        entry.sha256,
                    )
                    if (!verified(context, entry)) {
                        error("校验失败：${entry.file}")
                    }
                    done = before + entry.bytes
                    onProgress(done, total)
                }
            }
            result.onFailure { error ->
                android.util.Log.e("ImagingModelStore", "download failed", error)
            }
            onDone(result)
        }.start()
        return true
    }
}
