package com.example.pleosrantlog.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Figma uses Asta Sans (not bundled); system font used. Sizes = Figma px 1:1 (frame 1700px).
// RentLogScaffold re-scales these by LocalFigmaScale via [scaledBy].
val Typography = Typography(
    displayLarge = TextStyle(fontSize = 57.sp, lineHeight = 68.sp, fontWeight = FontWeight.ExtraBold),
    headlineLarge = TextStyle(fontSize = 56.sp, lineHeight = 66.sp, fontWeight = FontWeight.ExtraBold),
    headlineMedium = TextStyle(fontSize = 48.sp, lineHeight = 58.sp, fontWeight = FontWeight.ExtraBold),
    titleLarge = TextStyle(fontSize = 36.sp, lineHeight = 46.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 30.sp, lineHeight = 40.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 30.sp, lineHeight = 42.sp),
    bodyMedium = TextStyle(fontSize = 28.sp, lineHeight = 38.sp),
    labelLarge = TextStyle(fontSize = 30.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium),
)

private fun TextStyle.scaled(s: Float) =
    copy(fontSize = (fontSize.value * s).sp, lineHeight = (lineHeight.value * s).sp)

fun Typography.scaledBy(s: Float): Typography = copy(
    displayLarge = displayLarge.scaled(s), headlineLarge = headlineLarge.scaled(s),
    headlineMedium = headlineMedium.scaled(s), titleLarge = titleLarge.scaled(s),
    titleMedium = titleMedium.scaled(s), bodyLarge = bodyLarge.scaled(s),
    bodyMedium = bodyMedium.scaled(s), labelLarge = labelLarge.scaled(s),
    labelMedium = labelMedium.scaled(s),
)
