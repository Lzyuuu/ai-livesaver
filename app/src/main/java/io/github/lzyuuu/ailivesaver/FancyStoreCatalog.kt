package io.github.lzyuuu.ailivesaver

import org.json.JSONObject

/** 商店产品可用性三档。目录 JSON 显式标注时以目录为准，否则按内置规则推导。 */
enum class StoreAvailability {
    AVAILABLE,
    COMING_SOON,
    UPCOMING,
    ;
}

/** 商店目录产品。字段对齐 v4.50 fancy_store.json 调研清单（research/fancy-store-catalog.md）。 */
data class StoreProduct(
    val id: String,
    val name: String,
    val symbol: String,
    val tagline: String,
    val description: String,
    val category: String,
    val launchTarget: String,
    val kind: String,
    val builtIn: Boolean,
    val version: String,
    val features: List<String>,
    val requirements: List<String>,
    val requiredDownloadBytes: Long,
    val featured: Boolean,
    val availability: StoreAvailability,
)

/** 安装状态。COMING_SOON / UPCOMING 由目录可用性推导，不落库。 */
enum class InstallStatus {
    NOT_INSTALLED,
    INSTALLING,
    INSTALLED,
    ;

    companion object {
        fun fromRaw(raw: String?): InstallStatus = entries.firstOrNull { it.name == raw } ?: NOT_INSTALLED
    }
}

/** 安装状态机纯逻辑：输入当前状态与操作，输出新状态。 */
object InstallStateMachine {
    fun transition(current: InstallStatus, action: InstallAction): InstallStatus = when (action) {
        InstallAction.BEGIN -> if (current == InstallStatus.NOT_INSTALLED) InstallStatus.INSTALLING else current
        InstallAction.COMPLETE -> if (current == InstallStatus.INSTALLING) InstallStatus.INSTALLED else current
        InstallAction.UNINSTALL -> if (current == InstallStatus.INSTALLED) InstallStatus.NOT_INSTALLED else current
    }
}

enum class InstallAction { BEGIN, COMPLETE, UNINSTALL }

/** 目录解析与归一化纯逻辑：输入 JSON 文本，输出产品列表与三档分类。 */
object FancyStoreCatalogParser {

    fun parse(json: String): List<StoreProduct> {
        val root = JSONObject(json)
        val array = root.optJSONArray("products") ?: throw IllegalArgumentException("目录缺少 products 数组")
        val seen = mutableSetOf<String>()
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val id = o.optString("id").trim()
                val name = o.optString("name").trim()
                val symbol = o.optString("symbol").trim()
                val tagline = o.optString("tagline").trim()
                val description = o.optString("description").trim()
                val category = o.optString("category").trim()
                val launchTarget = o.optString("launchTarget").trim().ifBlank { id }
                if (id.isEmpty() || name.isEmpty() || symbol.isEmpty() || tagline.isEmpty() ||
                    description.isEmpty() || category.isEmpty()
                ) continue
                if (!seen.add(id.lowercase())) continue
                val kind = o.optString("kind").trim().lowercase().ifBlank { "app" }
                val downloadBytes = o.optLong("requiredDownloadBytes", 0L).coerceAtLeast(0L)
                val availability = when (o.optString("availability").trim()) {
                    "coming_soon" -> StoreAvailability.COMING_SOON
                    "upcoming" -> StoreAvailability.UPCOMING
                    else -> if (kind == "package") StoreAvailability.UPCOMING else StoreAvailability.AVAILABLE
                }
                add(
                    StoreProduct(
                        id = id,
                        name = name,
                        symbol = symbol,
                        tagline = tagline,
                        description = description,
                        category = category,
                        launchTarget = launchTarget,
                        kind = kind,
                        builtIn = o.optBoolean("builtIn", false),
                        version = o.optString("version", "1.0"),
                        features = stringList(o, "features"),
                        requirements = stringList(o, "requirements"),
                        requiredDownloadBytes = downloadBytes,
                        featured = o.optBoolean("featured", false),
                        availability = availability,
                    ),
                )
            }
        }
    }

    private fun stringList(o: JSONObject, key: String): List<String> {
        val array = o.optJSONArray(key) ?: return emptyList()
        return buildList { for (i in 0 until array.length()) array.optString(i).trim().takeIf { it.isNotEmpty() }?.let(::add) }
    }
}

/** 目录三档分类：把解析结果按可用性分桶。 */
data class StoreCatalogBuckets(
    val available: List<StoreProduct>,
    val comingSoon: List<StoreProduct>,
    val upcoming: List<StoreProduct>,
) {
    companion object {
        fun from(products: List<StoreProduct>): StoreCatalogBuckets = StoreCatalogBuckets(
            available = products.filter { it.availability == StoreAvailability.AVAILABLE },
            comingSoon = products.filter { it.availability == StoreAvailability.COMING_SOON },
            upcoming = products.filter { it.availability == StoreAvailability.UPCOMING },
        )
    }
}
