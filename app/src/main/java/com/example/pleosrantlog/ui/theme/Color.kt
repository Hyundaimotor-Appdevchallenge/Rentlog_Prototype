package com.example.pleosrantlog.ui.theme

import androidx.compose.ui.graphics.Color

/** Dark tokens taken from the Figma "렌트로그 화면" file. */
object RentLogColors {
    val Background = Color(0xFF131417)
    val Surface = Color(0xFF1D1E21)
    val SurfaceHigh = Color(0xFF28292C)     // white @5% over Surface (buttons, keys)
    val Outline = Color(0xFF3A3B3F)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xA3FFFFFF)   // white @64%
    val TextBody = Color(0xD6FFFFFF)        // white @84%
    val Caution = Color(0xFFFFC224)         // amber, 주의
    val CautionContainer = Color(0xFF3A2E10)
    val Normal = Color(0xFF00E074)          // green, 정상/기록
    val NormalContainer = Color(0xFF0F3322)
    val Emergency = Color(0xFFFF5A5F)       // red (not in checked frames)
    val EmergencyContainer = Color(0xFF3B1517)
    val PrimaryButton = Color(0xFFE0E1E6)
    val OnPrimaryButton = Color(0xFF131417)
    val Accent = Color(0xFF0082FF)          // focused input border
}
