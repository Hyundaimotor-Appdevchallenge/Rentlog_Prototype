package com.example.pleosrantlog.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pleosrantlog.ui.theme.RentLogColors
import com.example.pleosrantlog.ui.theme.Typography
import com.example.pleosrantlog.ui.theme.scaledBy

/** Window size / Figma frame (1700x1184), clamped. Figma px * scale = dp. */
val LocalFigmaScale = compositionLocalOf { 1f }

/** Figma px -> dp (px treated 1:1 as dp, times [LocalFigmaScale]). */
val Int.fdp: Dp @Composable get() = (this * LocalFigmaScale.current).dp
val Float.fdp: Dp @Composable get() = (this * LocalFigmaScale.current).dp
/** Figma px -> sp. */
val Int.fsp: TextUnit @Composable get() = (this * LocalFigmaScale.current).sp
val Float.fsp: TextUnit @Composable get() = (this * LocalFigmaScale.current).sp

private const val FRAME_W = 1700f
private const val FRAME_H = 1184f

/**
 * Landscape (width >= height): header + two panes side by side ([left] | [right]); NO scrolling,
 * panes are bounded (fillMaxHeight) so children may use Modifier.weight(1f) / Spacer(weight).
 * Portrait: header + one scrolling column ([left] then [right]).
 * If [right] is null, [left] fills the whole body.
 * [eyebrow] (small line) is drawn above the big [title]. Long-press on title -> [onTitleLongPress].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RentLogScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onTitleLongPress: (() -> Unit)? = null,
    headerAction: (@Composable RowScope.() -> Unit)? = null,
    eyebrow: String? = null,
    eyebrowColor: Color = RentLogColors.TextSecondary,
    left: @Composable ColumnScope.() -> Unit,
    right: (@Composable ColumnScope.() -> Unit)? = null,
    leftWeight: Float = 1f,
    rightWeight: Float = 1f,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(RentLogColors.Background)
            .systemBarsPadding(),
    ) {
        val landscape = maxWidth >= maxHeight
        val scale = minOf(maxWidth.value / FRAME_W, maxHeight.value / FRAME_H).coerceIn(0.5f, 1.6f)
        val typo = remember(scale) { Typography.scaledBy(scale) }
        CompositionLocalProvider(LocalFigmaScale provides scale) {
            MaterialTheme(colorScheme = MaterialTheme.colorScheme, shapes = MaterialTheme.shapes, typography = typo) {
                // Figma: side margin 80, top/bottom 64, pane gap 40 (1700x1184 frame).
                val padH = if (landscape) (80 * scale).dp else 20.dp
                val padV = if (landscape) (64 * scale).dp else 20.dp
                val gap = (24 * scale).dp
                val paneGap = (40 * scale).dp
                Column(Modifier.fillMaxSize().padding(horizontal = padH, vertical = padV)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            Modifier.weight(1f).combinedClickable(onClick = {}, onLongClick = onTitleLongPress)
                        ) {
                            if (eyebrow != null) {
                                Text(eyebrow, style = MaterialTheme.typography.labelMedium, color = eyebrowColor)
                                Spacer(Modifier.height((6 * scale).dp))
                            }
                            Text(title, style = MaterialTheme.typography.headlineMedium, color = RentLogColors.TextPrimary)
                            if (subtitle != null)
                                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = RentLogColors.TextSecondary)
                        }
                        headerAction?.invoke(this)
                    }
                    Spacer(Modifier.height(if (landscape) (if (eyebrow != null) 36.5f else 40f).times(scale).dp else 20.dp))
                    if (landscape && right != null) {
                        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(paneGap)) {
                            Column(Modifier.weight(leftWeight).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) { left() }
                            Column(Modifier.weight(rightWeight).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) { right() }
                        }
                    } else if (landscape) {
                        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) { left() }
                    } else {
                        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            left()
                            right?.invoke(this)
                        }
                    }
                }
            }
        }
    }
}
