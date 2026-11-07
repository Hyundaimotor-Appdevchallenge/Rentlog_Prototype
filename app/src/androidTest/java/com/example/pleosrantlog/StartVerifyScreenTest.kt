package com.example.pleosrantlog

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onAllNodesWithText
import com.example.pleosrantlog.data.backend.FakeBackend
import com.example.pleosrantlog.data.model.*
import com.example.pleosrantlog.data.navi.NaviRouter
import com.example.pleosrantlog.data.vehicle.SimulatedVehicleDataSource
import com.example.pleosrantlog.ui.*
import com.example.pleosrantlog.ui.screens.ReturnInspectionScreen
import com.example.pleosrantlog.ui.screens.StartVerifyScreen
import com.example.pleosrantlog.ui.theme.PleosRantLogTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StartVerifyScreenTest {
    @get:Rule val rule = createComposeRule()

    private fun newVm(): AppViewModel {
        val backend = FakeBackend(latencyMs = 0)
        return AppViewModel(AppDeps(backend, SimulatedVehicleDataSource(), { }, NaviRouter { _, _, _ -> true }))
    }

    private fun key(label: String) = hasText(label) and hasClickAction()

    @Test fun keypadTyping_updatesInput_andClearWorks() {
        val vm = newVm()
        rule.setContent { PleosRantLogTheme { val s by vm.state.collectAsState(); StartVerifyScreen(s, vm) } }
        rule.waitUntil(5_000) { vm.state.value.rental != null && !vm.state.value.loading }
        listOf("1", "2", "3").forEach { rule.onNode(key(it)).performClick() }
        rule.waitForIdle()
        assertEquals("123", vm.state.value.codeInput)
        rule.onNodeWithText("1  2  3").assertExists()
        rule.onNode(key("지움")).performClick()
        rule.waitForIdle()
        assertEquals("", vm.state.value.codeInput)
        rule.onNodeWithText("• • • •").assertExists()
    }

    @Test fun wrongCode_showsRemainingMessage() {
        val vm = newVm()
        rule.setContent { PleosRantLogTheme { val s by vm.state.collectAsState(); StartVerifyScreen(s, vm) } }
        rule.waitUntil(5_000) { vm.state.value.rental != null && !vm.state.value.loading }
        listOf("0", "0", "0", "0").forEach { rule.onNode(key(it)).performClick() }
        rule.waitUntil(5_000) { vm.state.value.verifyError != null }
        rule.onNodeWithText("번호가 달라요 (남은 횟수 4회)").assertExists()
    }

    @Test fun returnInspection_compareTableTexts() {
        val vm = newVm()
        val result = runBlocking {
            val b = FakeBackend(latencyMs = 0)
            b.verify("R-001", "1234")
            b.postInspection("R-001", InspectionType.PICKUP, VehicleSnapshot(12340, 80, 0, true, true, true))
            b.postInspectionFull("R-001", InspectionType.RETURN, VehicleSnapshot(12612, 41, 0, true, true, true))
                .let { (it as ApiResult.Success).data.second!! }
        }
        val state = UiState(screen = Screen.ReturnInspection, returnResult = result)
        rule.setContent { PleosRantLogTheme { ReturnInspectionScreen(state, vm) } }
        listOf("항목", "인수", "반납", "결과", "주행거리", "12,340 km", "12,612 km", "+272 km", "67,760원", "정산 미리보기")
            .forEach { t ->
                assertTrue("missing '$t'", rule.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty())
            }
    }
}
