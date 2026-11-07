package com.example.pleosrantlog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.sp
import com.example.pleosrantlog.ui.theme.RentLogColors

/** Text sized from a Figma pixel value (px * LocalFigmaScale as sp). */
@Composable
fun FigmaText(
    text: String,
    px: Int,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Normal,
    color: Color = RentLogColors.TextPrimary,
    align: TextAlign? = null,
) {
    val sp = px * LocalFigmaScale.current
    Text(text, modifier, color = color, textAlign = align,
        style = TextStyle(fontSize = sp.sp, lineHeight = (sp * 1.3f).sp, fontWeight = weight))
}

/** Figma "ghost" button: white @5% fill. */
@Composable
fun SoftButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier.heightIn(min = maxOf(100.fdp, 48.dp)).clip(RoundedCornerShape(24.fdp))
            .background(RentLogColors.SurfaceHigh)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 40.fdp),
        contentAlignment = Alignment.Center,
    ) {
        FigmaText(text, 30, weight = FontWeight.SemiBold,
            color = if (enabled) RentLogColors.TextPrimary else RentLogColors.TextSecondary, align = TextAlign.Center)
    }
}

/** Figma light primary button. */
@Composable
fun LightButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier.heightIn(min = maxOf(100.fdp, 48.dp)).clip(RoundedCornerShape(24.fdp))
            .background(if (enabled) RentLogColors.PrimaryButton else RentLogColors.SurfaceHigh)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 40.fdp),
        contentAlignment = Alignment.Center,
    ) {
        FigmaText(text, 32, weight = FontWeight.Bold,
            color = if (enabled) RentLogColors.OnPrimaryButton else RentLogColors.TextSecondary, align = TextAlign.Center)
    }
}

/** Dependency-free glyph "icon" (material-icons is not a dependency). */
@Composable
fun GlyphIcon(glyph: String, color: Color, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Text(glyph, color = color, style = TextStyle(fontSize = (size.value * 0.85f).sp, fontWeight = FontWeight.Bold))
    }
}
