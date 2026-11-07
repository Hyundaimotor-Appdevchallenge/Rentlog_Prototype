package com.example.pleosrantlog.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.example.pleosrantlog.ui.AppViewModel
import com.example.pleosrantlog.ui.UiState
import com.example.pleosrantlog.ui.components.*
import com.example.pleosrantlog.ui.theme.PleosRantLogTheme
import com.example.pleosrantlog.ui.theme.RentLogColors
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** F-11 반납 완료 (Figma 13:36). */
@Composable
fun ReturnCompleteScreen(state: UiState, vm: AppViewModel) {
    ReturnCompleteContent(state, { vm.showDemoControl(true) }, vm::openStatement, vm::finishAndRestart)
}

@Composable
private fun CheckRing(modifier: Modifier = Modifier) {
    val ringW = maxOf(8.fdp, 4.dp)
    Box(modifier.size(200.fdp).border(ringW, RentLogColors.Normal, CircleShape), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(96.fdp)) {
            val w = size.width
            val path = Path().apply {
                moveTo(w * 0.14f, w * 0.52f)
                lineTo(w * 0.40f, w * 0.76f)
                lineTo(w * 0.86f, w * 0.26f)
            }
            drawPath(path, RentLogColors.Normal, style = Stroke(width = w * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
private fun CompleteTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(28.fdp)).background(RentLogColors.Surface).padding(horizontal = 48.fdp, vertical = 28.fdp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.fdp),
    ) {
        FigmaText(label, 24, color = RentLogColors.TextSecondary)
        FigmaText(value, 40, weight = FontWeight.ExtraBold)
    }
}

@Composable
internal fun ReturnCompleteContent(state: UiState, onLongPress: () -> Unit, onPreview: () -> Unit, onClose: () -> Unit) {
    val r = state.completeResult
    RentLogScaffold(
        title = "렌트로그",
        onTitleLongPress = onLongPress,
        left = {
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(48.fdp))
                CheckRing()
                Spacer(Modifier.height(32.fdp))
                FigmaText("반납이 완료됐어요", 60, weight = FontWeight.ExtraBold, align = TextAlign.Center)
                Spacer(Modifier.height(32.fdp))
                FigmaText("정산 명세서를 카카오톡과 이메일로 보냈어요.\n업체에도 같은 명세서가 저장됐어요.", 30,
                    color = RentLogColors.TextBody, align = TextAlign.Center)
                Spacer(Modifier.height(48.fdp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.fdp)) {
                    CompleteTile("주행거리", r?.let { km(it.drivenKm) } ?: "-")
                    CompleteTile("청구 합계", r?.let { won(it.totalFee) } ?: "-")
                    CompleteTile("기록 검증 코드", r?.verifyCode ?: "-")
                }
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.height(24.fdp))
                Row(Modifier.widthIn(max = 700.fdp), horizontalArrangement = Arrangement.spacedBy(20.fdp)) {
                    PrimaryButton("명세서 미리 보기", onPreview, Modifier.weight(2f), kind = ButtonKind.Secondary)
                    PrimaryButton("닫기", onClose, Modifier.weight(1f))
                }
            }
        },
    )
}

@Preview(widthDp = 1700, heightDp = 1184)
@Composable
private fun ReturnCompletePreview() = PleosRantLogTheme { ReturnCompleteContent(sampleState(), {}, {}, {}) }
