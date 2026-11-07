package com.example.pleosrantlog.ui.demo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.pleosrantlog.ui.AppViewModel
import com.example.pleosrantlog.ui.UiState
import com.example.pleosrantlog.ui.theme.RentLogColors

/** Hidden demo panel (opened by long-pressing a screen title). Wired to every vm.demo* function. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoControlSheet(state: UiState, vm: AppViewModel) {
    ModalBottomSheet(
        onDismissRequest = { vm.showDemoControl(false) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = RentLogColors.Surface,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("시연 컨트롤", style = MaterialTheme.typography.titleLarge, color = RentLogColors.TextPrimary)
            Text("현재 화면: ${state.screen}", style = MaterialTheme.typography.bodyMedium, color = RentLogColors.TextSecondary)

            Section("경고")
            Pair2("TPMS 주의 경고", vm::demoInjectTpmsCaution, "브레이크 긴급 경고", vm::demoInjectBrakeEmergency)
            Section("차량 상태")
            Pair2("정차", { vm.demoSetStopped(true) }, "출발", { vm.demoSetStopped(false) })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn("주행거리 +10 km", { vm.demoAddKm(10) }, Modifier.weight(1f))
                Btn("+100 km", { vm.demoAddKm(100) }, Modifier.weight(1f))
                Btn("+272 km", { vm.demoAddKm(272) }, Modifier.weight(1f))
            }
            Btn("반납 2시간 전 강제", vm::demoForceReturnNotice, Modifier.fillMaxWidth())
            Btn("반납 장소 도착 (검수 시작)", vm::demoArriveAtReturnPlace, Modifier.fillMaxWidth())
            Section("업체 응답 / 잠금")
            Pair2("업체 확인", vm::demoAck, "업체 출동", vm::demoDispatch)
            Btn("잠금 해제", vm::demoUnlock, Modifier.fillMaxWidth())
            Section("오류 시나리오")
            Pair2(
                if (state.demoNetworkDown) "연결 복구" else "연결 끊김",
                { vm.demoNetworkDown(!state.demoNetworkDown) },
                if (state.demoNoReservation) "예약 있음으로" else "예약 없음으로",
                { vm.demoNoReservation(!state.demoNoReservation) },
            )
            Spacer(Modifier.height(4.dp))
            Btn("닫기", { vm.showDemoControl(false) }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(t: String) {
    Text(t, style = MaterialTheme.typography.labelMedium, color = RentLogColors.TextSecondary, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Pair2(a: String, ao: () -> Unit, b: String, bo: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Btn(a, ao, Modifier.weight(1f)); Btn(b, bo, Modifier.weight(1f))
    }
}

@Composable
private fun Btn(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 52.dp)) { Text(text, color = RentLogColors.TextPrimary) }
}
