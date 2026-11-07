package com.example.pleosrantlog.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.pleosrantlog.ui.AppViewModel
import com.example.pleosrantlog.ui.UiState
import com.example.pleosrantlog.ui.components.*
import com.example.pleosrantlog.ui.theme.PleosRantLogTheme
import com.example.pleosrantlog.ui.theme.RentLogColors

internal fun fmtRemaining(ms: Long): String {
    val totalMin = (ms.coerceAtLeast(0) / 60_000).toInt()
    val d = totalMin / (24 * 60)
    val h = (totalMin / 60) % 24
    val m = totalMin % 60
    return when {
        d > 0 -> "${d}일 ${h}시간"
        h > 0 -> "${h}시간 ${m}분"
        else -> "${m}분"
    }
}

/** F-03 주행 중 (기록 중) — landscape 11:96, portrait 13:57. */
@Composable
fun DrivingScreen(state: UiState, vm: AppViewModel) {
    DrivingContent(state) { vm.showDemoControl(true) }
}

@Composable
private fun DrivingContent(state: UiState, onTitleLongPress: () -> Unit) {
    val cfg = LocalConfiguration.current
    val landscape = cfg.screenWidthDp > cfg.screenHeightDp
    val km = "${fmtKm(state.drivenKm)} km"
    val fuel = state.live?.let { "${it.fuelPercent} %" } ?: "-"
    val remain = fmtRemaining(state.returnRemainingMs)
    RentLogScaffold(
        title = "렌트로그",
        onTitleLongPress = onTitleLongPress,
        left = {
            Column(
                if (landscape) Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Figma: ring top 180 below the body top, stat tiles pinned to the bottom (gap 219).
                if (landscape) Spacer(Modifier.weight(180f)) else Spacer(Modifier.height(32.dp))
                val ring = if (landscape) 220.fdp else 140.dp
                Box(Modifier.size(ring).border(if (landscape) 8.fdp else 6.dp, RentLogColors.Normal, CircleShape), contentAlignment = Alignment.Center) {
                    ShieldCheckIcon(RentLogColors.Normal, ring * (96f / 220f))
                }
                Spacer(Modifier.height(if (landscape) 36.fdp else 24.dp))
                FigmaText(if (landscape) "기록 중 · 이상 없음" else "기록 중\n이상 없음", if (landscape) 60 else 56,
                    weight = FontWeight.ExtraBold, align = TextAlign.Center)
                Spacer(Modifier.height(if (landscape) 24.fdp else 16.dp))
                FigmaLines(if (landscape) "문제가 생기면 음성으로 알려 드려요. 화면은 만지지 않아도 돼요." else "문제가 생기면\n음성으로 알려 드려요",
                    if (landscape) 30 else 28, align = TextAlign.Center)
                if (landscape) Spacer(Modifier.weight(219f)) else Spacer(Modifier.height(32.dp))
                if (landscape) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.fdp)) {
                        Stat("이번 대여 주행", km, Modifier.weight(1f))
                        Stat("연료", fuel, Modifier.weight(1f))
                        Stat("반납까지", remain, Modifier.weight(1f))
                    }
                } else {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatRow("이번 대여 주행", km)
                        StatRow("연료", fuel)
                        StatRow("반납까지", remain)
                    }
                }
            }
        },
    )
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier.heightIn(min = 161.fdp).clip(RoundedCornerShape(32.fdp)).background(RentLogColors.Surface)
        .padding(horizontal = 40.fdp, vertical = 32.fdp), verticalArrangement = Arrangement.spacedBy(10.fdp)) {
        FigmaText(label, 26, color = RentLogColors.TextSecondary)
        FigmaText(value, 48, weight = FontWeight.ExtraBold)
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).clip(RoundedCornerShape(20.dp)).background(RentLogColors.Surface).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        FigmaText(label, 26, color = RentLogColors.TextSecondary, modifier = Modifier.weight(1f))
        FigmaText(value, 40, weight = FontWeight.ExtraBold)
    }
}

private val previewDriving = UiState(
    live = previewSnapshot.copy(fuelPercent = 71), drivenKm = 148, returnRemainingMs = (30L * 3_600_000),
)

@Preview(widthDp = 1700, heightDp = 1184, showBackground = true)
@Composable
private fun DrivingLandscapePreview() = PleosRantLogTheme { DrivingContent(previewDriving) {} }

@Preview(widthDp = 520, heightDp = 730, showBackground = true)
@Composable
private fun DrivingPortraitPreview() = PleosRantLogTheme { DrivingContent(previewDriving) {} }
