package com.github.aashishvibhu.credentialmanagement.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small      = RoundedCornerShape(12.dp),
    medium     = RoundedCornerShape(16.dp),
    large      = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** True when the app is drawing its dark palette; used for avatar and warning colours. */
val LocalDarkTheme = staticCompositionLocalOf { false }

/** Warning colour for the current theme. */
val warningColor: Color
    @Composable get() = if (LocalDarkTheme.current) WarningDark else WarningLight

// Dynamic colour is intentionally off so the app keeps its own neutral look on every device.
@Composable
fun CredentialManagementTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography  = AppTypography,
            shapes      = AppShapes,
            content     = content
        )
    }
}
