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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.pleosrantlog.ui.AppViewModel
import com.example.pleosrantlog.ui.UiState
import com.example.pleosrantlog.ui.components.*
import com.example.pleosrantlog.ui.theme.PleosRantLogTheme
import com.example.pleosrantlog.ui.theme.RentLogColors
import com.example.pleosrantlog.data.model.CompareRow
import com.example.pleosrantlog.data.model.Settlement

/** F-07 반납 검수 (Figma 12:60). */
@Composable
fun ReturnInspectionScreen(state: UiState, vm: AppViewModel) {
    ReturnInspectionContent(state, { vm.showDemoControl(true) }, vm::confirmReturn)
}

private fun resultColor(r: String): Color = when {
    r.contains("주행") || r.contains("열림") -> RentLogColors.Caution
    else -> RentLogColors.Normal
}

/** Compare table; Figma rows are 94 px apart (first row 83 px under the header line). */
@Composable
internal fun CompareTable(rows: List<CompareRow>, modifier: Modifier = Modifier, rowHeight: Dp = 94.fdp) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(rowHeight * 0.9f), verticalAlignment = Alignment.Top) {
            listOf("항목" to 216f, "인수" to 184f, "반납" to 184f, "결과" to 220f).forEach { (t, w) ->
                FigmaText(t, 24, Modifier.weight(w), color = RentLogColors.TextSecondary)
            }
        }
        rows.forEach { r ->
            Row(Modifier.fillMaxWidth().height(rowHeight), verticalAlignment = Alignment.Top) {
                FigmaText(r.item, 30, Modifier.weight(216f), color = RentLogColors.TextBody)
                FigmaText(r.pickup, 30, Modifier.weight(184f), color = if (r.pickup.contains("열림")) RentLogColors.Caution else RentLogColors.TextPrimary)
                FigmaText(r.ret, 30, Modifier.weight(184f), weight = FontWeight.Bold,
                    color = if (r.ret.contains("열림")) RentLogColors.Caution else RentLogColors.TextPrimary)
                FigmaText(r.result, 26, Modifier.weight(220f).padding(top = 2.fdp), weight = FontWeight.Bold, color = resultColor(r.result))
            }
        }
    }
}

@Composable
internal fun SettlementLines(s: Settlement) {
    s.lines.forEach { l ->
        LabelValueRow(l.label, if (l.note.isNotEmpty()) l.note else won(l.amount))
    }
}

@Composable
internal fun ReturnInspectionContent(state: UiState, onLongPress: () -> Unit, onConfirm: () -> Unit) {
    val res = state.returnResult
    RentLogScaffold(
        leftWeight = 900f, rightWeight = 600f,
        title = "인수 때와 비교했어요",
        eyebrow = "반납 검수",
        onTitleLongPress = onLongPress,
        left = {
            PaneCard(Modifier.weight(1f).fillMaxWidth(), padding = 48) {
                if (res == null) FigmaText("차량 상태를 확인하고 있어요…", 30, color = RentLogColors.TextSecondary)
                else CompareTable(res.compare)
            }
        },
        right = {
            PaneCard(Modifier.weight(1f), padding = 44) {
                FigmaText("정산 미리보기", 32, weight = FontWeight.Bold)
                Spacer(Modifier.height(8.fdp))
                if (res != null) SettlementLines(res.settlement)
                Spacer(Modifier.height(8.fdp))
                Divider()
                Row(Modifier.fillMaxWidth().padding(top = 16.fdp), verticalAlignment = Alignment.CenterVertically) {
                    FigmaText("합계", 28, Modifier.weight(1f), color = RentLogColors.TextSecondary)
                    FigmaText(won(res?.settlement?.total ?: 0), 56, weight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.height(8.fdp))
                FigmaText("명세서 발송: 카카오톡 · 이메일\n업체에도 같은 명세서가 저장돼요.", 24, color = RentLogColors.TextBody)
                Spacer(Modifier.weight(1f))
                PrimaryButton("반납 확정 · 명세서 받기", onConfirm, Modifier.fillMaxWidth(), enabled = res != null && !state.loading)
            }
        },
    )
}

@Preview(widthDp = 1700, heightDp = 1184)
@Composable
private fun ReturnInspectionPreview() = PleosRantLogTheme { ReturnInspectionContent(sampleState(), {}, {}) }
