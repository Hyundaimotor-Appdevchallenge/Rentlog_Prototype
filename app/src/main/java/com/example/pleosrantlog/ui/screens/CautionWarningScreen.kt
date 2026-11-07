package com.example.pleosrantlog.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.pleosrantlog.data.model.EventLevel
import com.example.pleosrantlog.data.model.EventPosted
import com.example.pleosrantlog.data.model.EventType
import com.example.pleosrantlog.data.model.VehicleWarning
import com.example.pleosrantlog.ui.AppViewModel
import com.example.pleosrantlog.ui.UiState
import com.example.pleosrantlog.ui.components.*
import com.example.pleosrantlog.ui.theme.PleosRantLogTheme
import com.example.pleosrantlog.ui.theme.RentLogColors

/** F-04 주행 중 경고 발생 (CAUTION) — landscape 11:113, portrait 13:73. Tapping the card returns to driving. */
@Composable
fun CautionWarningScreen(state: UiState, vm: AppViewModel) {
    CautionContent(state, onDismiss = vm::dismissCaution, onTitleLongPress = { vm.showDemoControl(true) })
}

@Composable
private fun CautionContent(state: UiState, onDismiss: () -> Unit, onTitleLongPress: () -> Unit) {
    val cfg = LocalConfiguration.current
    val landscape = cfg.screenWidthDp > cfg.screenHeightDp
    val type = state.currentEventType ?: state.warning?.type ?: EventType.TPMS
    val title = if (type == EventType.TPMS) "타이어 공기압 경고등이 켜졌어요." else "경고등이 켜졌어요."
    val titlePortrait = if (type == EventType.TPMS) "타이어 공기압\n경고등이\n켜졌어요." else "경고등이\n켜졌어요."
    val newSince = state.currentEvent?.newSincePickup
    RentLogScaffold(
        title = "렌트로그",
        onTitleLongPress = onTitleLongPress,
        left = {
            Column(
                (if (landscape) Modifier.weight(1f) else Modifier).fillMaxWidth()
                    .clip(RoundedCornerShape(32.fdp)).background(RentLogColors.Surface)
                    .border(6.fdp, RentLogColors.Caution, RoundedCornerShape(32.fdp))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = if (landscape) 72.fdp else 32.dp, vertical = if (landscape) 64.fdp else 36.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.fdp)) {
                    WarningTriangleIcon(RentLogColors.Caution, if (landscape) 80.fdp else 64.dp)
                    if (landscape) CautionPill()
                }
                if (!landscape) { Spacer(Modifier.height(12.dp)); CautionPill() }
                Spacer(Modifier.height(if (landscape) 30.fdp else 20.dp))
                FigmaText(if (landscape) title else titlePortrait, if (landscape) 60 else 56, weight = FontWeight.ExtraBold)
                Spacer(Modifier.height(if (landscape) 24.fdp else 16.dp))
                if (landscape) {
                    FigmaLines("발생 시각 ${state.eventOccurredAt ?: "-"}, 주행거리 ${fmtKm(state.eventOdometerKm)} km로 기록했어요. 업체에도 알렸어요.\n계속 달려도 되지만, 다음에 정차하면 대응 방법을 안내해 드릴게요.", 30)
                } else {
                    FigmaLines("기록하고 업체에 알렸어요.\n정차하면 대응 방법을 안내해요.", 28)
                    Spacer(Modifier.height(12.dp))
                    FigmaText("발생 시각 ${state.eventOccurredAt ?: "-"} · ${fmtKm(state.eventOdometerKm)} km", 24, color = RentLogColors.TextSecondary)
                }
                if (newSince != null) {
                    Spacer(Modifier.height(if (landscape) 28.fdp else 16.dp))
                    StatusBadge(
                        if (newSince) "인수 시 없음 · 이번 대여 중 새로 발생" else "인수 시 이미 있던 경고",
                        if (newSince) BadgeKind.Caution else BadgeKind.Neutral,
                    )
                }
                if (landscape) Spacer(Modifier.weight(1f)) else Spacer(Modifier.height(48.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.fdp)) {
                    SpeakerIcon(RentLogColors.TextSecondary, 36.fdp)
                    FigmaText(if (landscape) "위 내용을 음성으로 안내 중 · 화면 조작 불필요" else "음성으로 안내 중 · 화면 조작 불필요",
                        26, color = RentLogColors.TextSecondary)
                }
            }
        },
    )
}

@Composable
private fun CautionPill() {
    Box(Modifier.height(maxOf(56.fdp, 36.dp)).clip(RoundedCornerShape(50)).background(RentLogColors.Caution).padding(horizontal = 24.fdp),
        contentAlignment = Alignment.Center) {
        FigmaText("주의 · 운행 가능", 30, weight = FontWeight.Bold, color = RentLogColors.OnPrimaryButton)
    }
}

private val previewCaution = UiState(
    warning = VehicleWarning(EventType.TPMS, EventLevel.CAUTION),
    currentEvent = EventPosted("E-1", "sig", true),
    currentEventType = EventType.TPMS, currentEventLevel = EventLevel.CAUTION,
    eventOccurredAt = "14:32", eventOdometerKm = 12_488,
)

@Preview(widthDp = 1700, heightDp = 1184, showBackground = true)
@Composable
private fun CautionLandscapePreview() = PleosRantLogTheme { CautionContent(previewCaution, {}, {}) }

@Preview(widthDp = 520, heightDp = 730, showBackground = true)
@Composable
private fun CautionPortraitPreview() = PleosRantLogTheme { CautionContent(previewCaution, {}, {}) }
