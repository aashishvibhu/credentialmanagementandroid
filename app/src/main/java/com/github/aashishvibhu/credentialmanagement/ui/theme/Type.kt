package com.github.aashishvibhu.credentialmanagement.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Base = Typography()

// Two weights only (Normal / Medium) and slightly tighter headlines for a quieter look.
internal val AppTypography = Typography(
    displaySmall   = Base.displaySmall.copy(fontWeight = FontWeight.Medium, letterSpacing = (-0.5).sp),
    headlineLarge  = Base.headlineLarge.copy(fontWeight = FontWeight.Medium, letterSpacing = (-0.5).sp),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = (-0.25).sp),
    headlineSmall  = Base.headlineSmall.copy(fontWeight = FontWeight.Medium),
    titleLarge     = Base.titleLarge.copy(fontWeight = FontWeight.Medium),
    titleMedium    = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall     = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge      = Base.bodyLarge,
    bodyMedium     = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall      = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge     = Base.labelLarge.copy(fontWeight = FontWeight.Medium),
    labelMedium    = Base.labelMedium.copy(fontWeight = FontWeight.Medium),
    labelSmall     = Base.labelSmall.copy(fontWeight = FontWeight.Medium),
)
