package com.github.aashishvibhu.credentialmanagement.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Neutral, near-monochrome palette. Colour is reserved for avatars, status and destructive actions.

internal val LightColors = lightColorScheme(
    primary              = Color(0xFF1B1B1F),
    onPrimary            = Color(0xFFFFFFFF),
    primaryContainer     = Color(0xFFE8E8EC),
    onPrimaryContainer   = Color(0xFF1B1B1F),
    secondary            = Color(0xFF5E5E66),
    onSecondary          = Color(0xFFFFFFFF),
    secondaryContainer   = Color(0xFFEDEDF1),
    onSecondaryContainer = Color(0xFF1B1B1F),
    tertiary             = Color(0xFF2F6B4F),
    onTertiary           = Color(0xFFFFFFFF),
    background           = Color(0xFFFAFAFB),
    onBackground         = Color(0xFF1B1B1F),
    surface              = Color(0xFFFAFAFB),
    onSurface            = Color(0xFF1B1B1F),
    surfaceVariant       = Color(0xFFF0F0F3),
    onSurfaceVariant     = Color(0xFF6B6B73),
    surfaceContainerLowest  = Color(0xFFFFFFFF),
    surfaceContainerLow     = Color(0xFFF5F5F7),
    surfaceContainer        = Color(0xFFF0F0F3),
    surfaceContainerHigh    = Color(0xFFEBEBEE),
    surfaceContainerHighest = Color(0xFFE5E5E9),
    outline              = Color(0xFFC9C9CF),
    outlineVariant       = Color(0xFFE6E6EA),
    error                = Color(0xFFC6352F),
    onError              = Color(0xFFFFFFFF),
    errorContainer       = Color(0xFFFCE8E6),
    onErrorContainer     = Color(0xFF7A1D18),
)

internal val DarkColors = darkColorScheme(
    primary              = Color(0xFFEDEDF0),
    onPrimary            = Color(0xFF16161A),
    primaryContainer     = Color(0xFF2C2C31),
    onPrimaryContainer   = Color(0xFFEDEDF0),
    secondary            = Color(0xFFA6A6AE),
    onSecondary          = Color(0xFF16161A),
    secondaryContainer   = Color(0xFF26262B),
    onSecondaryContainer = Color(0xFFEDEDF0),
    tertiary             = Color(0xFF7BC8A0),
    onTertiary           = Color(0xFF0C2A1C),
    background           = Color(0xFF111114),
    onBackground         = Color(0xFFEDEDF0),
    surface              = Color(0xFF111114),
    onSurface            = Color(0xFFEDEDF0),
    surfaceVariant       = Color(0xFF1E1E22),
    onSurfaceVariant     = Color(0xFF9A9AA3),
    surfaceContainerLowest  = Color(0xFF0C0C0F),
    surfaceContainerLow     = Color(0xFF17171B),
    surfaceContainer        = Color(0xFF1C1C20),
    surfaceContainerHigh    = Color(0xFF232328),
    surfaceContainerHighest = Color(0xFF2A2A30),
    outline              = Color(0xFF45454C),
    outlineVariant       = Color(0xFF2A2A30),
    error                = Color(0xFFFF8A80),
    onError              = Color(0xFF4A0E0A),
    errorContainer       = Color(0xFF3A1714),
    onErrorContainer     = Color(0xFFFFDAD5),
)

/** Amber used for "fair" password strength and low-backup-code warnings. */
val WarningLight = Color(0xFFBA7517)
val WarningDark  = Color(0xFFEF9F27)

/** Background/foreground pair used for a service's letter avatar. */
data class AvatarColors(val container: Color, val content: Color)

private val LightAvatars = listOf(
    AvatarColors(Color(0xFFEEEDFE), Color(0xFF3C3489)), // violet
    AvatarColors(Color(0xFFE1F5EE), Color(0xFF085041)), // teal
    AvatarColors(Color(0xFFFAECE7), Color(0xFF712B13)), // coral
    AvatarColors(Color(0xFFE6F1FB), Color(0xFF0C447C)), // blue
    AvatarColors(Color(0xFFFBEAF0), Color(0xFF72243E)), // pink
    AvatarColors(Color(0xFFFAEEDA), Color(0xFF633806)), // amber
    AvatarColors(Color(0xFFEAF3DE), Color(0xFF27500A)), // green
)

private val DarkAvatars = listOf(
    AvatarColors(Color(0xFF3C3489), Color(0xFFCECBF6)),
    AvatarColors(Color(0xFF085041), Color(0xFF9FE1CB)),
    AvatarColors(Color(0xFF712B13), Color(0xFFF5C4B3)),
    AvatarColors(Color(0xFF0C447C), Color(0xFFB5D4F4)),
    AvatarColors(Color(0xFF72243E), Color(0xFFF4C0D1)),
    AvatarColors(Color(0xFF633806), Color(0xFFFAC775)),
    AvatarColors(Color(0xFF27500A), Color(0xFFC0DD97)),
)

/** Stable colour for a service name, so "Google" always gets the same avatar colour. */
fun avatarColorsFor(name: String, dark: Boolean): AvatarColors {
    val palette = if (dark) DarkAvatars else LightAvatars
    val index = Math.floorMod(name.trim().lowercase().hashCode(), palette.size)
    return palette[index]
}
