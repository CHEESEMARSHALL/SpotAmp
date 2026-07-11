package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Curated customizable dark themes
private val DefaultDarkColorScheme = darkColorScheme(
    primary = Color(0xFF6366F1), // Indigo
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF8B5CF6), // Violet
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFF080808),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF0F0F15),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF161622),
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF334155)
)

private val EmeraldAuroraColorScheme = darkColorScheme(
    primary = Color(0xFF10B981), // Emerald
    onPrimary = Color(0xFF022C22),
    secondary = Color(0xFF06B6D4), // Cyan
    onSecondary = Color(0xFF083344),
    background = Color(0xFF022C22),
    onBackground = Color(0xFFECFDF5),
    surface = Color(0xFF064E3B),
    onSurface = Color(0xFFF0FDF4),
    surfaceVariant = Color(0xFF047857),
    onSurfaceVariant = Color(0xFFD1FAE5),
    outline = Color(0xFF065F46)
)

private val CyberpunkNeonColorScheme = darkColorScheme(
    primary = Color(0xFFEC4899), // Neon Pink
    onPrimary = Color(0xFF500724),
    secondary = Color(0xFFA855F7), // Neon Purple
    onSecondary = Color(0xFF3B0764),
    background = Color(0xFF03000A),
    onBackground = Color(0xFFFDF2F8),
    surface = Color(0xFF130026),
    onSurface = Color(0xFFFDF2F8),
    surfaceVariant = Color(0xFF26004C),
    onSurfaceVariant = Color(0xFFF5D0FE),
    outline = Color(0xFF581C87)
)

private val SunsetGlowColorScheme = darkColorScheme(
    primary = Color(0xFFF97316), // Orange
    onPrimary = Color(0xFF431407),
    secondary = Color(0xFFEF4444), // Red
    onSecondary = Color(0xFF450A0A),
    background = Color(0xFF180C0B),
    onBackground = Color(0xFFFFF7ED),
    surface = Color(0xFF2E1513),
    onSurface = Color(0xFFFFF7ED),
    surfaceVariant = Color(0xFF4D1D19),
    onSurfaceVariant = Color(0xFFFFEDD5),
    outline = Color(0xFF7C2D12)
)

private val CosmicObsidianColorScheme = darkColorScheme(
    primary = Color(0xFFF8FAFC), // Pure White-Silver
    onPrimary = Color(0xFF020617),
    secondary = Color(0xFF94A3B8), // Cool Gray
    onSecondary = Color(0xFF020617),
    background = Color(0xFF000000), // Pitch Black
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF0A0A0A),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF121212),
    onSurfaceVariant = Color(0xFFE2E8F0),
    outline = Color(0xFF262626)
)

@Composable
fun MyApplicationTheme(
    activeTheme: String = "Default Dark",
    content: @Composable () -> Unit
) {
    val colorScheme = when (activeTheme) {
        "Emerald Aurora" -> EmeraldAuroraColorScheme
        "Cyberpunk Neon" -> CyberpunkNeonColorScheme
        "Sunset Glow" -> SunsetGlowColorScheme
        "Cosmic Obsidian" -> CosmicObsidianColorScheme
        else -> DefaultDarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
