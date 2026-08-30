package io.github.lzyuuu.ailivesaver

import java.io.File

/**
 * SO-11 存储文件式浏览的受限快照 seam。
 *
 * 只读枚举应用沙盒（[root]）内的逻辑目录/文件层级：
 * - 路径解析拒绝 `..`、绝对路径与任何逃逸到 [root] 之外的相对路径；
 * - 每层目录最多枚举 [StorageBrowser.MAX_ENTRIES_PER_DIRECTORY] 项，超出标记 truncated；
 * - 子项计数与体积统计均有访问上限，避免无界 walkTopDown 阻塞调用线程。
 *
 * 纯 JVM、无 Android 依赖，配合 JVM 单测验证目录映射与边界。
 */

/** 单条存储条目（只读视图）。 */
data class StorageEntry(
    /** 显示名（文件名）。 */
    val name: String,
    /** 相对沙盒根的路径段（"a/b"），根目录直接子项即为 name。 */
    val relativePath: String,
    val isDirectory: Boolean,
    /** 目录：直接子项数（受限统计，触上限时截断）。文件恒为 0。 */
    val childCount: Int = 0,
    /** 目录子项统计是否触上限截断。 */
    val childrenTruncated: Boolean = false,
    /** 文件体积（字节）；目录恒为 0。 */
    val sizeBytes: Long = 0L,
)

/** 一层目录的受限快照。 */
data class StorageDirectoryListing(
    /** 相对沙盒根的路径（"" 表示根）。 */
    val relativePath: String,
    val entries: List<StorageEntry>,
    /** 本层条目数触上限被截断。 */
    val truncated: Boolean,
) {
    val isRoot: Boolean get() = relativePath.isEmpty()
}

/** 受限体积统计结果。 */
data class StorageSizeSnapshot(
    val totalBytes: Long,
    val visitedFiles: Int,
    /** 触上限提前停止，totalBytes 为下界。 */
    val truncated: Boolean,
)

object StorageBrowser {
    /** 单层目录最多枚举的条目数。 */
    const val MAX_ENTRIES_PER_DIRECTORY = 200

    /** 目录子项计数上限（避免巨型目录 listFiles 拖慢）。 */
    const val MAX_CHILD_COUNT = 1_000

    /** 体积统计最多访问的文件数。 */
    const val MAX_WALKED_FILES = 5_000

    /**
     * 将 UI 传入的相对路径解析为 [root] 内的目录。
     * 拒绝：绝对路径、`..` 段、空段、解析后逃逸到 [root] 之外的路径、非目录。
     * @return 目标目录；非法或越界时 null。
     */
    fun resolveDirectory(root: File, relativePath: String): File? {
        if (relativePath.isEmpty()) return root
        if (relativePath.startsWith("/") || relativePath.startsWith("\\")) return null
        val segments = relativePath.split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." }) return null
        val rootCanonical = root.canonicalFile
        var current = rootCanonical
        segments.forEach { segment ->
            current = File(current, segment)
        }
        val resolved = current.canonicalFile
        if (resolved != rootCanonical && !resolved.path.startsWith(rootCanonical.path + File.separator)) {
            return null
        }
        return resolved.takeIf { it.isDirectory }
    }

    /** 目录相对路径的父路径（"a/b" -> "a"，"a" -> ""）。 */
    fun parentPath(relativePath: String): String =
        relativePath.substringBeforeLast('/', missingDelimiterValue = "")

    /**
     * 枚举一层目录的受限快照。目录在前、文件在后，各按名称排序（对齐参考 ref-80）。
     * 路径非法或越界时返回空根快照，绝不访问 [root] 之外。
     */
    fun listDirectory(
        root: File,
        relativePath: String,
        maxEntries: Int = MAX_ENTRIES_PER_DIRECTORY,
        maxChildCount: Int = MAX_CHILD_COUNT,
    ): StorageDirectoryListing {
        val directory = resolveDirectory(root, relativePath)
            ?: return StorageDirectoryListing("", emptyList(), truncated = false)
        val children = directory.listFiles()?.toList().orEmpty()
        val sorted = children.sortedWith(
            compareByDescending<File> { it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
        )
        val truncated = sorted.size > maxEntries
        val entries = sorted.take(maxEntries).map { child ->
            val childRelative = if (relativePath.isEmpty()) child.name else "$relativePath/${child.name}"
            if (child.isDirectory) {
                val grandChildren = child.listFiles()
                val childCount = grandChildren?.size?.coerceAtMost(maxChildCount) ?: 0
                StorageEntry(
                    name = child.name,
                    relativePath = childRelative,
                    isDirectory = true,
                    childCount = childCount,
                    childrenTruncated = (grandChildren?.size ?: 0) > maxChildCount,
                )
            } else {
                StorageEntry(
                    name = child.name,
                    relativePath = childRelative,
                    isDirectory = false,
                    sizeBytes = if (child.isFile) child.length() else 0L,
                )
            }
        }
        return StorageDirectoryListing(relativePath, entries, truncated)
    }

    /**
     * 受限递归体积统计：最多访问 [maxFiles] 个文件，触上限提前停止并标记 truncated。
     * 只在调用线程工作，调用方需放到 IO 调度器。
     */
    fun boundedSizeBytes(root: File, maxFiles: Int = MAX_WALKED_FILES): StorageSizeSnapshot {
        var total = 0L
        var visited = 0
        var truncated = false
        val pending = ArrayDeque<File>()
        pending.add(root)
        while (pending.isNotEmpty() && !truncated) {
            val current = pending.removeFirst()
            val children = current.listFiles() ?: continue
            for (child in children) {
                if (child.isDirectory) {
                    pending.add(child)
                } else if (child.isFile) {
                    if (visited >= maxFiles) {
                        truncated = true
                        break
                    }
                    total += child.length()
                    visited++
                }
            }
        }
        if (pending.isNotEmpty()) truncated = true
        return StorageSizeSnapshot(totalBytes = total, visitedFiles = visited, truncated = truncated)
    }
}
