package com.example.pleosrantlog.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.pleosrantlog.data.model.CompareRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.pleosrantlog.ui.AppViewModel
import com.example.pleosrantlog.ui.UiState
import com.example.pleosrantlog.ui.components.*
import com.example.pleosrantlog.ui.theme.PleosRantLogTheme
import com.example.pleosrantlog.ui.theme.RentLogColors

private fun stmtResultColor(r: String) = if (r.contains("주행")) RentLogColors.Caution else RentLogColors.Normal

/** Compact compare table: wide label column, rows grow with content (no fixed height -> no overlap). */
@Composable
private fun StatementCompare(rows: List<CompareRow>) {
    val cols = listOf(300f, 150f, 150f, 200f)
    Column(verticalArrangement = Arrangement.spacedBy(10.fdp)) {
        Row(Modifier.fillMaxWidth()) {
            listOf("항목", "인수", "반납", "결과").forEachIndexed { i, t ->
                Text(t, Modifier.weight(cols[i]), style = MaterialTheme.typography.bodyMedium, color = RentLogColors.TextSecondary)
            }
        }
        rows.forEach { r ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(r.item, Modifier.weight(cols[0]).padding(end = 8.fdp), style = MaterialTheme.typography.bodyLarge, color = RentLogColors.TextBody)
                Text(r.pickup, Modifier.weight(cols[1]), style = MaterialTheme.typography.bodyLarge, color = RentLogColors.TextPrimary)
                Text(r.ret, Modifier.weight(cols[2]), style = MaterialTheme.typography.bodyLarge, color = RentLogColors.TextPrimary, fontWeight = FontWeight.Bold)
                Text(r.result, Modifier.weight(cols[3]), style = MaterialTheme.typography.bodyLarge, color = stmtResultColor(r.result), fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** 명세서 미리보기 (content reference Figma 4:109), built from state.statement. */
@Composable
fun StatementScreen(state: UiState, vm: AppViewModel) {
    StatementContent(state, { vm.showDemoControl(true) }, vm::closeStatement)
}

@Composable
internal fun StatementContent(state: UiState, onLongPress: () -> Unit, onClose: () -> Unit) {
    val s = state.statement
    RentLogScaffold(
        title = "반납 정산 명세서",
        subtitle = s?.let { "렌트로그 · ${it.company.name}" },
        onTitleLongPress = onLongPress,
        headerAction = { PrimaryButton("닫기", onClose, kind = ButtonKind.Secondary) },
        left = {
            if (s == null) {
                Text("명세서를 불러오는 중이에요…", color = RentLogColors.TextSecondary)
                return@RentLogScaffold
            }
            InfoCard {
                Text("${s.customerName} 고객님, 반납이 완료됐어요.", style = MaterialTheme.typography.bodyLarge, color = RentLogColors.TextPrimary)
                Text("${s.vehicle} · ${s.rentalId}", style = MaterialTheme.typography.bodyLarge, color = RentLogColors.TextPrimary)
                Divider()
                StatementCompare(s.compare)
            }
            InfoCard(title = "운행 중 이벤트", modifier = Modifier.weight(1f, fill = false)) {
                if (s.events.isEmpty()) Text("기록된 이벤트가 없어요.", color = RentLogColors.TextSecondary)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.fdp)) {
                    s.events.forEach {
                        Row {
                            Text(it.time, Modifier.width(100.fdp), style = MaterialTheme.typography.bodyMedium, color = RentLogColors.TextSecondary)
                            Text(it.text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = RentLogColors.TextPrimary)
                        }
                    }
                }
            }
        },
        right = {
            if (s == null) return@RentLogScaffold
            InfoCard(title = "정산 내역") {
                SettlementLines(s.settlement)
                Divider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("청구 합계", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = RentLogColors.TextPrimary)
                    Text(won(s.totalFee), style = MaterialTheme.typography.headlineLarge, color = RentLogColors.TextPrimary)
                }
                Text("주행거리 ${km(s.drivenKm)}", style = MaterialTheme.typography.bodyMedium, color = RentLogColors.TextSecondary)
            }
            InfoCard {
                Text("이 명세서는 차량이 직접 기록한 데이터로 만들었어요.", style = MaterialTheme.typography.bodyMedium, color = RentLogColors.TextSecondary)
                Text("기록 검증 코드 ${s.verifyCode}", style = MaterialTheme.typography.titleMedium, color = RentLogColors.TextPrimary)
            }
            PrimaryButton("닫기", onClose, Modifier.fillMaxWidth())
        },
    )
}

@Preview(widthDp = 1700, heightDp = 1184)
@Composable
private fun StatementPreview() = PleosRantLogTheme { StatementContent(sampleState(), {}, {}) }
