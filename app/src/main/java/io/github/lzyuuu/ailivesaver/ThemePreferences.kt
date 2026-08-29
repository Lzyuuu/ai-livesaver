package io.github.lzyuuu.ailivesaver

internal enum class ThemeMode {
    System,
    Light,
    Dark,
}

internal fun readThemeMode(context: android.content.Context): ThemeMode =
    parseThemeMode(
        context.getSharedPreferences(APP_PREFERENCES, android.content.Context.MODE_PRIVATE)
            .getString(THEME_MODE_KEY, null),
    )

internal fun parseThemeMode(raw: String?): ThemeMode =
    runCatching { ThemeMode.valueOf(raw ?: "") }
        .getOrDefault(ThemeMode.Dark)

internal fun writeThemeMode(context: android.content.Context, mode: ThemeMode) {
    context.getSharedPreferences(APP_PREFERENCES, android.content.Context.MODE_PRIVATE)
        .edit()
        .putString(THEME_MODE_KEY, mode.name)
        .apply()
}

internal fun readDynamicColor(context: android.content.Context): Boolean =
    context.getSharedPreferences(APP_PREFERENCES, android.content.Context.MODE_PRIVATE)
        .getBoolean(DYNAMIC_COLOR_KEY, false)

internal fun writeDynamicColor(context: android.content.Context, enabled: Boolean) {
    context.getSharedPreferences(APP_PREFERENCES, android.content.Context.MODE_PRIVATE)
        .edit()
        .putBoolean(DYNAMIC_COLOR_KEY, enabled)
        .apply()
}

internal fun resolveDarkTheme(themeMode: ThemeMode, systemDark: Boolean): Boolean = when (themeMode) {
    ThemeMode.System -> systemDark
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

internal const val THEME_MODE_KEY = "theme_mode"
internal const val DYNAMIC_COLOR_KEY = "dynamic_color"

internal fun buildAiLivesaverColorScheme(
    context: android.content.Context,
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    systemDark: Boolean,
): androidx.compose.material3.ColorScheme {
    val dark = resolveDarkTheme(themeMode, systemDark)
    return if (dynamicColor && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        if (dark) {
            androidx.compose.material3.dynamicDarkColorScheme(context)
        } else {
            androidx.compose.material3.dynamicLightColorScheme(context)
        }
    } else if (dark) {
        androidx.compose.material3.darkColorScheme(
            primary = FancyGold,
            onPrimary = FancyInk,
            primaryContainer = androidx.compose.ui.graphics.Color(0xFF3A2F16),
            onPrimaryContainer = FancyCream,
            secondary = androidx.compose.ui.graphics.Color(0xFF9BB0D8),
            secondaryContainer = FancyNavyMid,
            onSecondaryContainer = FancyCream,
            background = FancyInk,
            surface = FancyNavy,
            surfaceVariant = androidx.compose.ui.graphics.Color(0xFF1F2738),
            onSurface = FancyCream,
            onSurfaceVariant = FancyCream.copy(alpha = 0.72f),
            surfaceContainerLowest = androidx.compose.ui.graphics.Color(0xFF05070D),
            surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFF0E1422),
            surfaceContainer = FancyNavyMid,
            surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFF1C2436),
            surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFF252E42),
            outline = FancyGoldDim,
            outlineVariant = androidx.compose.ui.graphics.Color(0xFF333B4F),
        )
    } else {
        androidx.compose.material3.lightColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF5C6F00),
            onPrimary = androidx.compose.ui.graphics.Color.White,
            primaryContainer = androidx.compose.ui.graphics.Color(0xFFE2F5A4),
            onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF2C3905),
            secondary = androidx.compose.ui.graphics.Color(0xFF4F609E),
            onSecondary = androidx.compose.ui.graphics.Color.White,
            secondaryContainer = androidx.compose.ui.graphics.Color(0xFFE7EAf5),
            onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF26376F),
            background = androidx.compose.ui.graphics.Color(0xFFFAFBF3),
            onBackground = androidx.compose.ui.graphics.Color(0xFF1A1C18),
            surface = androidx.compose.ui.graphics.Color(0xFFFFFCF5),
            onSurface = androidx.compose.ui.graphics.Color(0xFF1A1C18),
            surfaceVariant = androidx.compose.ui.graphics.Color(0xFFEDEFE4),
            onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF5A5D52),
            surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFFF3F5EC),
            outline = androidx.compose.ui.graphics.Color(0xFFB8BBAF),
            outlineVariant = androidx.compose.ui.graphics.Color(0xFFD8DBD0),
        )
    }
}
