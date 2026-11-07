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
import androidx.compose.foundation.shape.CircleShape

/** F-10 긴급 경고 후 정차 (Figma 13:2). */
@Composable
fun EmergencyScreen(state: UiState, vm: AppViewModel) {
    EmergencyContent(state, { vm.showDemoControl(true) }, vm::requestEmergencyCall, vm::dismissEmergency)
}

@Composable
internal fun EmergencyContent(state: UiState, onLongPress: () -> Unit, onCallRequest: () -> Unit, onDismiss: () -> Unit) {
    val st = state.eventStatus
    val steps = listOf(
        "비상등을 켜고 갓길이나 안전한 곳에 세워 주세요",
        "고속도로라면 가드레일 밖으로 대피해 주세요",
        "업체 안내가 오기 전까지 다시 출발하지 마세요",
    )
    RentLogScaffold(
        leftWeight = 820f, rightWeight = 680f,
        title = "브레이크 경고등이 켜졌어요. 운행을 멈춰 주세요",
        eyebrow = "운행 중 발생 · 긴급",
        eyebrowColor = Color(0xFFFF4339),
        onTitleLongPress = onLongPress,
        left = {
            PaneCard(Modifier.weight(1f)) {
                FigmaText("지금 해 주세요", 32, weight = FontWeight.Bold)
                Spacer(Modifier.height(10.fdp))
                Column(verticalArrangement = Arrangement.spacedBy(28.fdp)) {
                    steps.forEachIndexed { i, s ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(56.fdp).clip(CircleShape).background(Color(0xFF313236)),
                                contentAlignment = Alignment.Center,
                            ) { FigmaText("${i + 1}", 28, weight = FontWeight.Bold) }
                            Spacer(Modifier.width(24.fdp))
                            FigmaText(s, 32, Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.fdp)).background(Color(0xFF313236)).padding(28.fdp),
                    verticalArrangement = Arrangement.spacedBy(11.fdp),
                ) {
                    val at = state.eventOccurredAt ?: "--:--"
                    FigmaText("발생 시각 $at · 주행거리 ${km(state.eventOdometerKm)} 로 기록했어요.", 26, color = RentLogColors.TextBody)
                    FigmaText("인수 때는 켜져 있지 않던 경고예요.", 26, color = RentLogColors.TextBody)
                }
            }
        },
        right = {
            PaneCard(padding = 40, radius = 28, spacing = 22) {
                FigmaText("연락 상태", 32, weight = FontWeight.Bold)
                StatusRow("업체 자동 알림",
                    if (st?.notifyStatus == "SENT") "전송 완료" else "전송 중",
                    if (st?.notifyStatus == "SENT") RentLogColors.Normal else RentLogColors.TextSecondary)
                StatusRow("업체 확인",
                    if (st?.ackStatus == "CONFIRMED") "확인 완료" else "확인 중",
                    if (st?.ackStatus == "CONFIRMED") RentLogColors.Normal else RentLogColors.Caution)
                StatusRow("긴급출동 요청",
                    if (st?.dispatchStatus == "ACCEPTED") "출동 접수됨" else "업체가 접수 예정",
                    if (st?.dispatchStatus == "ACCEPTED") RentLogColors.Normal else RentLogColors.TextSecondary)
            }
            PaneCard(padding = 40, radius = 28, spacing = 8) {
                FigmaText("업체 긴급 연락처", 26, color = RentLogColors.TextSecondary)
                FigmaText(state.rental?.company?.phone ?: "[업체 전화번호]", 44, weight = FontWeight.ExtraBold)
                FigmaText("휴대폰으로 직접 걸어 주세요", 24, color = RentLogColors.TextBody)
            }
            if (state.emergencyCallRequested) {
                FigmaText("업체에 긴급 전화를 요청했어요. 곧 연락이 와요.", 26, color = RentLogColors.Normal)
            }
            Spacer(Modifier.weight(1f))
            PrimaryButton("확인했어요 · 운행 화면으로", onDismiss, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
            PrimaryButton("업체에 긴급 전화 요청", onCallRequest, Modifier.fillMaxWidth(), enabled = !state.emergencyCallRequested)
        },
    )
}

@Composable
private fun StatusRow(label: String, badge: String, color: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        FigmaText(label, 28, Modifier.weight(1f))
        SmallChip(badge, color)
    }
}

@Preview(widthDp = 1700, heightDp = 1184)
@Composable
private fun EmergencyPreview() = PleosRantLogTheme { EmergencyContent(sampleState(), {}, {}, {}) }
