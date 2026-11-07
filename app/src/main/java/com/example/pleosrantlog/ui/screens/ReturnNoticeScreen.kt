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
import androidx.compose.ui.unit.dp
import com.example.pleosrantlog.ui.AppViewModel
import com.example.pleosrantlog.ui.UiState
import com.example.pleosrantlog.ui.components.*
import com.example.pleosrantlog.ui.theme.PleosRantLogTheme
import com.example.pleosrantlog.ui.theme.RentLogColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** F-06 반납 전 안내 (Figma 12:29). */
@Composable
fun ReturnNoticeScreen(state: UiState, vm: AppViewModel) {
    ReturnNoticeContent(state, { vm.showDemoControl(true) }, vm::returnNoticeLater, vm::navigateToReturnPlace, vm::startReturnInspection)
}

@Composable
internal fun ReturnNoticeContent(state: UiState, onLongPress: () -> Unit, onLater: () -> Unit, onNavigate: () -> Unit, onArrived: () -> Unit = {}) {
    val p = state.returnPreview
    val returnTime = p?.let { SimpleDateFormat("HH:mm", Locale.KOREA).format(Date(it.returnAt)) } ?: "--:--"
    RentLogScaffold(
        leftWeight = 680f, rightWeight = 820f,
        title = "반납까지 2시간 남았어요",
        eyebrow = "반납 2시간 전 · 반납 시간 $returnTime",
        onTitleLongPress = onLongPress,
        left = {
            PaneCard(Modifier.weight(1f)) {
                FigmaText("지금 반납하면 예상 주행요금", 26, color = RentLogColors.TextSecondary)
                FigmaText(won(p?.estimatedFee ?: 0), 60, weight = FontWeight.ExtraBold)
                Spacer(Modifier.height(16.fdp))
                Divider()
                Spacer(Modifier.height(8.fdp))
                LabelValueRow("주행거리", km(p?.drivenKm ?: 0))
                LabelValueRow("무료 구간", "− ${p?.freeKm ?: 30} km")
                LabelValueRow("1 km당 요금", "× ${p?.pricePerKm ?: 280}원")
                Spacer(Modifier.weight(1f))
                FigmaText("반납 장소까지 남은 거리가 더해지면\n요금이 조금 늘어날 수 있어요.", 24, color = RentLogColors.TextBody)
            }
        },
        right = {
            PaneCard(padding = 40, radius = 28, spacing = 8) {
                FigmaText("반납 장소", 26, color = RentLogColors.TextSecondary)
                FigmaText(p?.returnPlace?.name ?: "[렌터카 업체명] 주차장", 36, weight = FontWeight.Bold)
                FigmaText("8.4 km · 약 18분 · 반납 시간 $returnTime", 26, color = RentLogColors.TextBody)
            }
            PaneCard(padding = 40, radius = 28, spacing = 8) {
                FigmaText("연료", 26, color = RentLogColors.TextSecondary)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    FigmaText("${p?.fuelPercent ?: 0} % · 정산하지 않아요", 36, Modifier.weight(1f), weight = FontWeight.Bold)
                    SmallChip("주유 필요 없음", RentLogColors.Normal)
                }
            }
            PaneCard(padding = 40, radius = 28, spacing = 8) {
                FigmaText("반납 전에 챙길 것", 26, color = RentLogColors.TextSecondary)
                FigmaText("개인 물품 · 트렁크 · 창문 닫기", 30)
            }
            Spacer(Modifier.weight(1f))
            PrimaryButton("반납 장소에 도착했어요", onArrived, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.fdp)) {
                PrimaryButton("나중에 안내", onLater, Modifier.weight(1f), kind = ButtonKind.Secondary)
                PrimaryButton("반납 장소로 안내", onNavigate, Modifier.weight(1f))
            }
        },
    )
}

@Preview(widthDp = 1700, heightDp = 1184)
@Composable
private fun ReturnNoticePreview() = PleosRantLogTheme { ReturnNoticeContent(sampleState(), {}, {}, {}) }
