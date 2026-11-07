package com.example.pleosrantlog.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.example.pleosrantlog.data.model.ActionResult
import com.example.pleosrantlog.data.model.ActionType
import com.example.pleosrantlog.data.model.EventLevel
import com.example.pleosrantlog.data.model.EventType
import com.example.pleosrantlog.ui.AppViewModel
import com.example.pleosrantlog.ui.UiState
import com.example.pleosrantlog.ui.components.FigmaText
import com.example.pleosrantlog.ui.components.fdp
import com.example.pleosrantlog.ui.components.RentLogScaffold
import com.example.pleosrantlog.ui.theme.PleosRantLogTheme
import com.example.pleosrantlog.ui.theme.RentLogColors

/** F-05 정차 후 문제 대응 (Figma 12:2). */
@Composable
fun StoppedResponseScreen(state: UiState, vm: AppViewModel) {
    StoppedResponseContent(
        state,
        onLongPress = { vm.showDemoControl(true) },
        onRepairShop = vm::chooseRepairShop,
        onCall = vm::chooseCallRequest,
        onContinue = vm::chooseContinue,
    )
}

@Composable
internal fun StoppedResponseContent(
    state: UiState,
    onLongPress: () -> Unit,
    onRepairShop: () -> Unit,
    onCall: () -> Unit,
    onContinue: () -> Unit,
) {
    val emergency = state.currentEventLevel == EventLevel.EMERGENCY
    val title = when (state.currentEventType) {
        EventType.BRAKE -> "브레이크 경고"
        else -> "타이어 공기압 경고 (TPMS)"
    }
    RentLogScaffold(
        leftWeight = 860f, rightWeight = 640f,
        title = title,
        eyebrow = "운행 중 발생 · " + if (emergency) "긴급" else "주의",
        eyebrowColor = if (emergency) Color(0xFFFF4339) else RentLogColors.Caution,
        onTitleLongPress = onLongPress,
        left = {
            PaneCard(Modifier.weight(1f)) {
                FigmaText("기록된 내용", 32, weight = FontWeight.Bold)
                Spacer(Modifier.height(16.fdp))
                Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(26.fdp)) {
                    state.timeline.forEach { item ->
                        Row {
                            FigmaText(item.time, 28, Modifier.width(124.fdp), color = RentLogColors.TextSecondary)
                            FigmaText(item.text, 28, Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.fdp)).background(RentLogColors.SurfaceHigh)
                        .padding(horizontal = 28.fdp, vertical = 24.fdp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SmallChip("인수 시 없음", RentLogColors.Caution, px = 22)
                    Spacer(Modifier.width(14.fdp))
                    FigmaText("운행 중 새로 생긴 경고예요. 책임 판단은 업체가 기록을 보고 해요.", 26, Modifier.weight(1f))
                }
            }
        },
        right = {
            FigmaText("어떻게 할까요?", 32, weight = FontWeight.Bold)
            ChoiceCard("업체 지정 정비소로 안내", "5.2 km · 업체가 비용 부담 승인", primary = true, onClick = onRepairShop)
            state.repairShop?.let { shop ->
                PaneCard(accent = RentLogColors.Normal, padding = 32, radius = 28) {
                    FigmaText(shop.shopName ?: "업체 지정 정비소", 30, weight = FontWeight.Medium)
                    val dist = shop.distanceKm?.let { "$it km · " } ?: ""
                    FigmaText(dist + (shop.costBy ?: "업체") + " 부담", 26, color = RentLogColors.Normal)
                }
            }
            ChoiceCard("업체에 전화 요청", "업체가 고객님 휴대폰으로 연락해요", primary = false, onClick = onCall)
            if (state.callRequested) {
                PaneCard(accent = RentLogColors.Normal, padding = 32, radius = 28) {
                    FigmaText("업체가 연락해요", 30, weight = FontWeight.Medium, color = RentLogColors.Normal)
                }
            }
            ChoiceCard("계속 운행", "기록은 유지되고, 경고등 변화를 계속 지켜봐요", primary = false, onClick = onContinue)
        },
    )
}

@Preview(widthDp = 1700, heightDp = 1184)
@Composable
private fun StoppedPreview() = PleosRantLogTheme {
    StoppedResponseContent(
        sampleState().copy(repairShop = ActionResult(ActionType.REPAIR_SHOP, "OO정비소", 5.2, "업체")),
        {}, {}, {}, {},
    )
}
