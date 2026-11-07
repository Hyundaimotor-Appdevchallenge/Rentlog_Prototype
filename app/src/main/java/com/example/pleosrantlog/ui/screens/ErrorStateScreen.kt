package com.example.pleosrantlog.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.pleosrantlog.data.model.ContactReason
import com.example.pleosrantlog.ui.AppViewModel
import com.example.pleosrantlog.ui.ErrorKind
import com.example.pleosrantlog.ui.Screen
import com.example.pleosrantlog.ui.UiState
import com.example.pleosrantlog.ui.components.*
import com.example.pleosrantlog.ui.theme.PleosRantLogTheme
import com.example.pleosrantlog.ui.theme.RentLogColors

/** F-08 예약 없음 · 확인 잠김 · 연결 끊김 (Figma 12:103). */
@Composable
fun ErrorStateScreen(state: UiState, vm: AppViewModel) {
    val kind = (state.screen as? Screen.ErrorState)?.kind ?: ErrorKind.NO_RESERVATION
    ErrorContent(
        kind, state,
        onRetry = vm::retry,
        onCall = { vm.requestCompanyCall(if (kind == ErrorKind.LOCKED) ContactReason.LOCKED else ContactReason.NO_RESERVATION) },
        onTitleLongPress = { vm.showDemoControl(true) },
    )
}

@Composable
private fun ErrorContent(kind: ErrorKind, state: UiState, onRetry: () -> Unit, onCall: () -> Unit, onTitleLongPress: () -> Unit) {
    val cfg = LocalConfiguration.current
    val landscape = cfg.screenWidthDp > cfg.screenHeightDp
    val (title, body) = when (kind) {
        ErrorKind.NO_RESERVATION -> "이 차량에 예정된 대여가 없어요" to
            "예약 시간이 아니거나 다른 차량일 수 있어요.\n예약 문자에 적힌 차량 번호를 확인해 주세요."
        ErrorKind.LOCKED -> "본인 확인이 잠겼어요" to
            "번호를 5회 틀려서 잠시 잠겼어요.\n업체에 전화를 요청하면 잠금을 풀어 드려요."
        ErrorKind.DISCONNECTED -> "연결이 끊겼어요" to
            "네트워크 연결을 확인해 주세요.\n기록은 연결되면 이어서 전송해요."
    }
    RentLogScaffold(
        title = "렌트로그",
        onTitleLongPress = onTitleLongPress,
        left = {
            Column(
                if (landscape) Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxWidth().padding(top = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Figma: group top 214 / bottom gap 250 (to the buttons pinned at the bottom).
                if (landscape) Spacer(Modifier.weight(214f))
                Box(Modifier.size(180.fdp).clip(CircleShape).background(RentLogColors.Surface), contentAlignment = Alignment.Center) {
                    val c = RentLogColors.TextSecondary
                    val s = 88.fdp
                    if (kind == ErrorKind.LOCKED) LockIcon(c, s) else ExclamationCircleIcon(c, s)
                }
                Spacer(Modifier.height(40.fdp))
                FigmaText(title, 56, weight = FontWeight.ExtraBold, align = TextAlign.Center)
                Spacer(Modifier.height(28.fdp))
                FigmaLines(body, 30, align = TextAlign.Center)
                if (landscape) Spacer(Modifier.weight(250f)) else Spacer(Modifier.height(32.dp))
                // Fixed-height message line so the buttons never move.
                Box(Modifier.height(44.fdp), contentAlignment = Alignment.Center) {
                    if (state.contactRequested) FigmaText("업체에 전화 요청을 보냈어요.", 26, color = RentLogColors.Normal)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(20.fdp), verticalAlignment = Alignment.CenterVertically) {
                    if (kind != ErrorKind.DISCONNECTED)
                        SoftButton(if (state.contactRequested) "전화 요청 완료" else "업체에 전화 요청", onCall, enabled = !state.contactRequested)
                    LightButton(if (state.loading) "확인 중…" else "다시 시도", onRetry, enabled = !state.loading)
                    if (state.loading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp, color = RentLogColors.TextPrimary)
                }
            }
        },
    )
}

@Preview(widthDp = 1700, heightDp = 1184, showBackground = true)
@Composable
private fun ErrorNoReservationPreview() = PleosRantLogTheme {
    ErrorContent(ErrorKind.NO_RESERVATION, UiState(), {}, {}, {})
}

@Preview(widthDp = 1700, heightDp = 1184, showBackground = true)
@Composable
private fun ErrorLockedPreview() = PleosRantLogTheme {
    ErrorContent(ErrorKind.LOCKED, UiState(contactRequested = true), {}, {}, {})
}

@Preview(widthDp = 600, heightDp = 1000, showBackground = true)
@Composable
private fun ErrorDisconnectedPreview() = PleosRantLogTheme {
    ErrorContent(ErrorKind.DISCONNECTED, UiState(loading = true), {}, {}, {})
}
