package com.example.pleosrantlog.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.pleosrantlog.data.model.Company
import com.example.pleosrantlog.data.model.ContactReason
import com.example.pleosrantlog.data.model.Rental
import com.example.pleosrantlog.ui.AppViewModel
import com.example.pleosrantlog.ui.UiState
import com.example.pleosrantlog.ui.components.*
import com.example.pleosrantlog.ui.theme.PleosRantLogTheme
import com.example.pleosrantlog.ui.theme.RentLogColors
import java.text.SimpleDateFormat
import java.util.Locale

internal fun fmtRentalTime(ms: Long): String =
    SimpleDateFormat("MM.dd (E) HH:mm", Locale.KOREAN).format(ms)

/** F-01 대여 시작 · 본인 확인 (Figma 11:2). */
@Composable
fun StartVerifyScreen(state: UiState, vm: AppViewModel) {
    StartVerifyContent(
        state = state,
        onDigit = vm::onDigit,
        onBackspace = vm::onBackspace,
        onClear = { repeat(state.codeInput.length) { vm.onBackspace() } },
        onSubmit = vm::submitCode,
        onCall = { vm.requestCompanyCall(ContactReason.NO_RESERVATION) },
        onTitleLongPress = { vm.showDemoControl(true) },
    )
}

@Composable
private fun StartVerifyContent(
    state: UiState,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    onCall: () -> Unit,
    onTitleLongPress: () -> Unit,
) {
    val cfg = LocalConfiguration.current
    val landscape = cfg.screenWidthDp > cfg.screenHeightDp
    RentLogScaffold(
        title = "렌트로그",
        onTitleLongPress = onTitleLongPress,
        left = {
            if (landscape) {
                // Figma: left card 820 wide, right card 680 wide, 40 gap, both full height.
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(40.fdp)) {
                    InfoPanel(state, true, Modifier.weight(820f).fillMaxHeight())
                    VerifyPanel(state, true, onDigit, onClear, onSubmit, onCall, Modifier.weight(680f).fillMaxHeight())
                }
            } else {
                InfoPanel(state, false, Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                VerifyPanel(state, false, onDigit, onClear, onSubmit, onCall, Modifier.fillMaxWidth())
            }
        },
    )
}

@Composable
private fun InfoPanel(state: UiState, landscape: Boolean, modifier: Modifier) {
    val rental = state.rental
    Column(modifier.clip(RoundedCornerShape(32.fdp)).background(RentLogColors.Surface).padding(56.fdp)) {
        FigmaText("오늘 시작하는 대여", 26, color = RentLogColors.TextSecondary)
        Spacer(Modifier.height(24.fdp))
        FigmaText("${rental?.customerName ?: "고객"} 고객님,", 56, weight = FontWeight.ExtraBold)
        FigmaText("반가워요", 56, weight = FontWeight.ExtraBold)
        Spacer(Modifier.height(36.fdp))
        Row(Modifier.fillMaxWidth()) {
            Info("차량", rental?.vehicle ?: "-", Modifier.weight(1f))
            Info("업체", rental?.company?.name ?: "-", Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.fdp))
        Row(Modifier.fillMaxWidth()) {
            Info("대여 시작", rental?.let { fmtRentalTime(it.startAt) } ?: "-", Modifier.weight(1f))
            Info("반납 예정", rental?.let { fmtRentalTime(it.returnAt) } ?: "-", Modifier.weight(1f))
        }
        if (landscape) Spacer(Modifier.weight(1f)) else Spacer(Modifier.height(32.fdp))
        FigmaLines("검수 기록과 정산 명세서는 예약할 때 등록한\n카카오톡과 이메일로 보내 드려요.", 26)
    }
}

@Composable
private fun VerifyPanel(
    state: UiState, landscape: Boolean,
    onDigit: (Char) -> Unit, onClear: () -> Unit, onSubmit: () -> Unit, onCall: () -> Unit,
    modifier: Modifier,
) {
    val err = state.verifyError != null
    val en = !state.loading
    val gap = 16.fdp
    Column(modifier.clip(RoundedCornerShape(32.fdp)).background(RentLogColors.Surface).padding(48.fdp)) {
        FigmaText("본인 확인 · 휴대폰 번호 뒤 4자리", 32, weight = FontWeight.Bold)
        Spacer(Modifier.height(20.fdp))
        Box(
            Modifier.fillMaxWidth().height(100.fdp).clip(RoundedCornerShape(24.fdp))
                .background(RentLogColors.Background)
                .border(3.fdp, if (err) RentLogColors.Emergency else RentLogColors.Accent, RoundedCornerShape(24.fdp)),
            contentAlignment = Alignment.Center,
        ) {
            if (state.codeInput.isEmpty()) FigmaText("• • • •", 48, color = RentLogColors.Outline)
            else FigmaText(state.codeInput.toList().joinToString("  "), 48, align = TextAlign.Center)
        }
        // Fixed-height error line: always reserved so the keypad never jumps.
        Box(Modifier.fillMaxWidth().height(44.fdp), contentAlignment = Alignment.CenterStart) {
            if (state.verifyError != null) FigmaText(state.verifyError, 26, color = RentLogColors.Emergency)
        }
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9")).forEach { r ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                r.forEach { d -> PadKey(d, en, Modifier.weight(1f)) { onDigit(d[0]) } }
            }
            Spacer(Modifier.height(gap))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            PadKey("지움", en, Modifier.weight(1f)) { onClear() }
            PadKey("0", en, Modifier.weight(1f)) { onDigit('0') }
            PadKey("완료", en, Modifier.weight(1f)) { onSubmit() }
        }
        Spacer(Modifier.height(gap))
        Box(contentAlignment = Alignment.Center) {
            LightButton(
                if (state.loading) "확인 중…" else "확인하고 출발 전 검수 시작",
                onSubmit, Modifier.fillMaxWidth().height(maxOf(100.fdp, 48.dp)),
                enabled = state.codeInput.length == 4 && !state.loading,
            )
            if (state.loading) CircularProgressIndicator(Modifier.size(24.dp).align(Alignment.CenterEnd).padding(end = 0.dp), strokeWidth = 3.dp, color = RentLogColors.OnPrimaryButton)
        }
        if (landscape) Spacer(Modifier.weight(1f)) else Spacer(Modifier.height(gap))
        SoftButton(
            if (state.contactRequested) "업체에 전화 요청했어요" else "업체에 전화 요청",
            onCall, Modifier.fillMaxWidth().height(maxOf(84.fdp, 48.dp)), enabled = !state.contactRequested,
        )
        // Fixed-height message line (reserved even when empty).
        Box(Modifier.fillMaxWidth().height(44.fdp), contentAlignment = Alignment.BottomStart) {
            if (state.contactRequested) FigmaText("업체에 전화 요청을 보냈어요. 곧 연락드릴게요.", 24, color = RentLogColors.Normal)
        }
    }
}

@Composable
private fun Info(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        FigmaText(label, 24, color = RentLogColors.TextSecondary)
        Spacer(Modifier.height(6.fdp))
        FigmaText(value, 30)
    }
}

@Composable
private fun PadKey(label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(maxOf(80.fdp, 48.dp)).clip(RoundedCornerShape(24.fdp)).background(RentLogColors.SurfaceHigh)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { FigmaText(label, 32, weight = FontWeight.SemiBold) }
}

internal val previewRental = Rental(
    "R-001", "김*준", "아반떼 · 12가 3456", Company("[렌터카 업체명]", "02-000-0000"),
    1_762_000_000_000, 1_762_150_000_000,
)

@Preview(widthDp = 1700, heightDp = 1184, showBackground = true)
@Composable
private fun StartVerifyLandscapePreview() = PleosRantLogTheme {
    StartVerifyContent(UiState(rental = previewRental, codeInput = "27"), {}, {}, {}, {}, {}, {})
}

@Preview(widthDp = 600, heightDp = 1000, showBackground = true)
@Composable
private fun StartVerifyPortraitErrorPreview() = PleosRantLogTheme {
    StartVerifyContent(UiState(rental = previewRental, verifyError = "번호가 달라요 (남은 횟수 4회)", contactRequested = true), {}, {}, {}, {}, {}, {})
}
