package com.example.pleosrantlog.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.pleosrantlog.ui.theme.RentLogColors

enum class ButtonKind { Primary, Secondary, Danger }

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    kind: ButtonKind = ButtonKind.Primary,
) {
    val shape = RoundedCornerShape(24.fdp)
    val m = modifier.heightIn(min = maxOf(100.fdp, 48.dp))
    when (kind) {
        ButtonKind.Primary -> Button(
            onClick = onClick, enabled = enabled, modifier = m, shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = RentLogColors.PrimaryButton, contentColor = RentLogColors.OnPrimaryButton,
                disabledContainerColor = RentLogColors.SurfaceHigh, disabledContentColor = RentLogColors.TextSecondary,
            ),
        ) { Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center) }
        ButtonKind.Danger -> Button(
            onClick = onClick, enabled = enabled, modifier = m, shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = RentLogColors.Emergency, contentColor = Color.White,
                disabledContainerColor = RentLogColors.SurfaceHigh, disabledContentColor = RentLogColors.TextSecondary,
            ),
        ) { Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center) }
        ButtonKind.Secondary -> OutlinedButton(
            onClick = onClick, enabled = enabled, modifier = m, shape = shape,
            border = BorderStroke(2.dp, RentLogColors.Outline),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = RentLogColors.TextPrimary),
        ) { Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center) }
    }
}

@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    accent: Color? = null,
    container: Color = RentLogColors.Surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.fdp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container)
            .then(if (accent != null) Modifier.border(1.dp, accent, shape) else Modifier)
            .padding(32.fdp),
        verticalArrangement = Arrangement.spacedBy(14.fdp),
    ) {
        if (title != null) Text(title, style = MaterialTheme.typography.labelMedium, color = RentLogColors.TextSecondary)
        content()
    }
}

enum class BadgeKind { Normal, Caution, Emergency, Neutral }

@Composable
fun StatusBadge(text: String, kind: BadgeKind = BadgeKind.Neutral, modifier: Modifier = Modifier) {
    val (fg, bg) = when (kind) {
        BadgeKind.Normal -> RentLogColors.Normal to RentLogColors.NormalContainer
        BadgeKind.Caution -> RentLogColors.Caution to RentLogColors.CautionContainer
        BadgeKind.Emergency -> RentLogColors.Emergency to RentLogColors.EmergencyContainer
        BadgeKind.Neutral -> RentLogColors.TextSecondary to RentLogColors.SurfaceHigh
    }
    Row(
        modifier.clip(CircleShape).background(bg).padding(horizontal = 20.fdp, vertical = 10.fdp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.fdp).clip(CircleShape).background(fg))
        Spacer(Modifier.width(10.fdp))
        Text(text, color = fg, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    valueColor: Color = RentLogColors.TextPrimary,
) {
    Column(
        modifier.clip(RoundedCornerShape(24.fdp)).background(RentLogColors.Surface).padding(28.fdp),
        verticalArrangement = Arrangement.spacedBy(8.fdp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = RentLogColors.TextSecondary)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.headlineLarge, color = valueColor)
            if (unit != null) {
                Spacer(Modifier.width(8.fdp))
                Text(unit, style = MaterialTheme.typography.bodyMedium, color = RentLogColors.TextSecondary,
                    modifier = Modifier.padding(bottom = 6.fdp))
            }
        }
    }
}

/** 4 dots showing how many digits are entered. */
@Composable
fun CodeDots(length: Int, modifier: Modifier = Modifier, max: Int = 4, error: Boolean = false) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(20.fdp)) {
        repeat(max) { i ->
            val filled = i < length
            Box(
                Modifier.size(30.fdp).clip(CircleShape)
                    .background(if (filled) (if (error) RentLogColors.Emergency else RentLogColors.TextPrimary) else Color.Transparent)
                    .border(2.dp, if (error) RentLogColors.Emergency else RentLogColors.Outline, CircleShape)
            )
        }
    }
}

/** Numeric keypad 1-9, then [clear/"지움"], 0, backspace. */
@Composable
fun KeyPad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val rows = listOf("123", "456", "789")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.fdp)) {
        rows.forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.fdp)) {
                r.forEach { d -> Key(d.toString(), enabled, Modifier.weight(1f)) { onDigit(d) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.fdp)) {
            Spacer(Modifier.weight(1f))
            Key("0", enabled, Modifier.weight(1f)) { onDigit('0') }
            Key("⌫", enabled, Modifier.weight(1f)) { onBackspace() }
        }
    }
}

@Composable
private fun Key(label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(maxOf(68.fdp, 48.dp)).clip(RoundedCornerShape(20.fdp)).background(RentLogColors.Surface)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = MaterialTheme.typography.headlineMedium, color = RentLogColors.TextPrimary) }
}
