package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 模型导入纯逻辑：类型识别、大小上限、注册表往返与清单合并。 */
class LocalModelsImportTest {
    private fun importedEntry(
        id: String = "import-abc123",
        name: String = "my-model.Q4_0.gguf",
        type: String = "chat",
        sha: String = "a".repeat(64),
    ) = LocalModelEntry(
        id = id,
        name = name,
        type = type,
        quant = "import",
        description = "从文件导入",
        minRamMb = 0,
        url = "imported/$name",
        sizeBytes = 1024L,
        sha256 = sha,
    )

    @Test
    fun importTypeDetectedByExtensionCaseInsensitive() {
        assertEquals("chat", LocalModels.importedModelType("Model.Q4_0.GGUF"))
        assertEquals("litert", LocalModels.importedModelType("gemma-4-E2B-it.litertlm"))
        assertNull(LocalModels.importedModelType("weights.bin"))
        assertNull(LocalModels.importedModelType("archive.gguf.zip"))
        assertNull(LocalModels.importedModelType(""))
    }

    @Test
    fun importSizeMustBePositiveAndWithinCap() {
        assertTrue(LocalModels.isImportSizeAllowed(1L))
        assertTrue(LocalModels.isImportSizeAllowed(LocalModels.IMPORT_SIZE_CAP_BYTES))
        assertFalse(LocalModels.isImportSizeAllowed(0L))
        assertFalse(LocalModels.isImportSizeAllowed(-1L))
        assertFalse(LocalModels.isImportSizeAllowed(LocalModels.IMPORT_SIZE_CAP_BYTES + 1))
    }

    @Test
    fun importedRegistryRoundTripsAndDerivesFileName() {
        val entry = importedEntry()
        val json = LocalModels.encodeImportedModels(listOf(entry))
        val parsed = LocalModels.parseImportedModels(json)
        assertEquals(listOf(entry), parsed)
        assertEquals("my-model.Q4_0.gguf", parsed.single().fileName)
        assertEquals("import", parsed.single().quant)
        assertTrue(parsed.single().imported)
    }

    @Test
    fun importedRegistryRejectsForeignOrMalformedEntries() {
        val good = importedEntry()
        // 非本 APP 写入形态：非 import- 前缀 / 未知类型 / 哈希截断 / 文件名缺失，都应被拒绝。
        val goodJson = LocalModels.encodeImportedModels(listOf(good))
            .removePrefix("{\"models\":[").removeSuffix("]}")
        val polluted = """
            {"models":[
                {"id":"download-1","name":"x.gguf","type":"chat","fileName":"x.gguf","sizeBytes":1,"sha256":"${"a".repeat(64)}"},
                {"id":"import-2","name":"x.gguf","type":"onnx","fileName":"x.onnx","sizeBytes":1,"sha256":"${"a".repeat(64)}"},
                {"id":"import-3","name":"x.gguf","type":"chat","fileName":"x.gguf","sizeBytes":1,"sha256":"short"},
                {"id":"import-4","name":"x.gguf","type":"chat","fileName":"","sizeBytes":1,"sha256":"${"a".repeat(64)}"},
                $goodJson
            ]}
        """.trimIndent()
        assertEquals(listOf(good), LocalModels.parseImportedModels(polluted))
        assertEquals(emptyList<LocalModelEntry>(), LocalModels.parseImportedModels("not json"))
        assertEquals(emptyList<LocalModelEntry>(), LocalModels.parseImportedModels(""))
    }

    @Test
    fun mergeAppendsImportedWithoutDuplicatingManifestIds() {
        val manifest = listOf(
            LocalModelEntry(
                id = "gguf-gemma4-e4b",
                name = "manifest",
                type = "chat",
                quant = "Q4_0",
                description = "",
                minRamMb = 0,
                url = "https://example.com/m.gguf",
                sizeBytes = 1L,
                sha256 = "b".repeat(64),
            ),
        )
        val imported = listOf(importedEntry(), importedEntry(id = manifest.single().id))
        val merged = LocalModels.mergeManifestAndImported(manifest, imported)
        // 清单条目在前；与清单同 id 的导入条目被丢弃。
        assertEquals(2, merged.size)
        assertEquals(manifest, merged.take(1))
        assertEquals("import-abc123", merged.last().id)
    }
}
