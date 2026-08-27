package io.github.lzyuuu.ailivesaver

import androidx.compose.ui.graphics.Color

/**
 * 参考 V4.51 全局基调色板（D1 设计 token 收敛，goal 阶段①批次 D）。
 * 色值以 ImageMagick 从参考截图实测采样：ref-70（Y 时间线）背景 #0B0E11、
 * 生成按钮金 #E3B366、标题/正文 #FFFFFF；卡片与分隔线为无内容区的近似值。
 * 参考为单一深色主题（设置无主题项）：各壳层的浅色调色板已按此迁移，
 * 「外观」主题切换仅影响 MaterialTheme 语义层，壳层基调保持参考一致。
 */
internal object ReferencePalette {
    /** 页面/工具栏近黑背景。 */
    val PageBg = Color(0xFF0B0E11)

    /** 卡片、输入框、菜单面。 */
    val Card = Color(0xFF15181A)

    /** 卡片描边、分隔线。 */
    val Hairline = Color(0xFF23272A)

    /** 参考 accent 金（生成按钮、强调操作）。 */
    val Gold = Color(0xFFE3B366)

    /** 主文字（标题/正文）。 */
    val TextPrimary = Color.White

    /** 次级文字（副标题、元数据、空态说明）。 */
    val TextSecondary = Color(0xFF9BA0A3)

    /** 输入框空闲描边等更弱的元素。 */
    val Faint = Color(0xFF3A3F42)
}
