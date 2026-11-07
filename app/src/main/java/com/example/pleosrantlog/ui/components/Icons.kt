package com.example.pleosrantlog.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

/** Canvas-drawn icons (no icon library dependency). All are drawn in a square of [size]. */

@Composable
fun ShieldCheckIcon(color: Color, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val sw = w * 0.07f
        val st = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val p = Path().apply {
            moveTo(w * .5f, w * .08f)
            lineTo(w * .82f, w * .2f)
            lineTo(w * .82f, w * .48f)
            cubicTo(w * .82f, w * .72f, w * .66f, w * .86f, w * .5f, w * .93f)
            cubicTo(w * .34f, w * .86f, w * .18f, w * .72f, w * .18f, w * .48f)
            lineTo(w * .18f, w * .2f)
            close()
        }
        drawPath(p, color, style = st)
        val c = Path().apply {
            moveTo(w * .35f, w * .5f); lineTo(w * .46f, w * .61f); lineTo(w * .66f, w * .38f)
        }
        drawPath(c, color, style = st)
    }
}

@Composable
fun CheckIcon(color: Color, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val c = Path().apply {
            moveTo(w * .2f, w * .52f); lineTo(w * .42f, w * .74f); lineTo(w * .8f, w * .28f)
        }
        drawPath(c, color, style = Stroke(w * .11f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun WarningTriangleIcon(color: Color, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val sw = w * 0.075f
        val p = Path().apply {
            moveTo(w * .5f, w * .12f); lineTo(w * .95f, w * .88f); lineTo(w * .05f, w * .88f); close()
        }
        drawPath(p, color, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawLine(color, Offset(w * .5f, w * .38f), Offset(w * .5f, w * .6f), sw, StrokeCap.Round)
        drawCircle(color, sw * .62f, Offset(w * .5f, w * .74f))
    }
}

/** Circle outline with "!" (F-08 no-reservation / disconnected). */
@Composable
fun ExclamationCircleIcon(color: Color, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val sw = w * 0.07f
        drawCircle(color, w * .42f, Offset(w / 2, w / 2), style = Stroke(sw))
        drawLine(color, Offset(w * .5f, w * .28f), Offset(w * .5f, w * .54f), sw, StrokeCap.Round)
        drawCircle(color, sw * .62f, Offset(w * .5f, w * .69f))
    }
}

@Composable
fun LockIcon(color: Color, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val sw = w * 0.07f
        val st = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val arc = Path().apply {
            moveTo(w * .32f, w * .45f); lineTo(w * .32f, w * .32f)
            cubicTo(w * .32f, w * .1f, w * .68f, w * .1f, w * .68f, w * .32f)
            lineTo(w * .68f, w * .45f)
        }
        drawPath(arc, color, style = st)
        drawRoundRect(color, Offset(w * .2f, w * .45f), androidx.compose.ui.geometry.Size(w * .6f, w * .42f),
            androidx.compose.ui.geometry.CornerRadius(w * .08f), style = st)
        drawCircle(color, sw * .7f, Offset(w * .5f, w * .66f))
    }
}

@Composable
fun SpeakerIcon(color: Color, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val sw = w * 0.08f
        val st = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val body = Path().apply {
            moveTo(w * .12f, w * .4f); lineTo(w * .3f, w * .4f); lineTo(w * .5f, w * .22f)
            lineTo(w * .5f, w * .78f); lineTo(w * .3f, w * .6f); lineTo(w * .12f, w * .6f); close()
        }
        drawPath(body, color, style = st)
        val a1 = Path().apply { moveTo(w * .65f, w * .38f); quadraticBezierTo(w * .76f, w * .5f, w * .65f, w * .62f) }
        val a2 = Path().apply { moveTo(w * .75f, w * .27f); quadraticBezierTo(w * .98f, w * .5f, w * .75f, w * .73f) }
        drawPath(a1, color, style = st)
        drawPath(a2, color, style = st)
    }
}

/** Multi-line body text at Figma px size with a 1.5 line height (FigmaText uses 1.3). */
@Composable
fun FigmaLines(
    text: String,
    px: Int,
    modifier: Modifier = Modifier,
    color: Color = com.example.pleosrantlog.ui.theme.RentLogColors.TextBody,
    align: androidx.compose.ui.text.style.TextAlign? = null,
    weight: androidx.compose.ui.text.font.FontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
) {
    androidx.compose.material3.Text(
        text, modifier, color = color, textAlign = align,
        style = androidx.compose.ui.text.TextStyle(fontSize = px.fsp, lineHeight = (px * 1.5f).fsp, fontWeight = weight),
    )
}
