package com.example.pleosrantlog

import com.example.pleosrantlog.data.backend.FakeBackend
import com.example.pleosrantlog.data.backend.FeeCalculator
import com.example.pleosrantlog.data.model.*
import com.example.pleosrantlog.data.navi.NaviRouter
import com.example.pleosrantlog.data.net.NetworkStatus
import com.example.pleosrantlog.data.vehicle.SimulatedVehicleDataSource
import com.example.pleosrantlog.data.voice.Speaker
import com.example.pleosrantlog.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelFlowTest {
    private val spoken = mutableListOf<String>()
    private val navigated = mutableListOf<Triple<String, Double, Double>>()
    private lateinit var scheduler: TestCoroutineScheduler
    private lateinit var backend: FakeBackend
    private lateinit var vehicle: SimulatedVehicleDataSource
    private lateinit var vm: AppViewModel
    private val base = 1_762_000_000_000L
    private var online = true

    @Before fun setUp() {
        val d = StandardTestDispatcher(); scheduler = d.scheduler
        Dispatchers.setMain(d)
    }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun clock() = base + scheduler.currentTime

    private fun TestScope.newVm() {
        backend = FakeBackend(clock = ::clock, latencyMs = 100, timeoutMs = 5000)
        vehicle = SimulatedVehicleDataSource(clock = ::clock)
        vm = AppViewModel(AppDeps(backend, vehicle, { spoken += it }, NaviRouter { n, la, lo -> navigated += Triple(n, la, lo); true }, ::clock, NetworkStatus { online }))
        runCurrent(); advanceTimeBy(500); runCurrent()
    }

    /** viewModelScope loops never go idle; cancel them so runTest can finish. */
    private fun vmTest(body: suspend TestScope.() -> Unit) = runTest {
        try { body() } finally { if (::vm.isInitialized) vm.viewModelScope.cancel() }
    }

    private val s get() = vm.state.value
    private fun TestScope.step(ms: Long = 1000) { advanceTimeBy(ms); runCurrent() }
    private fun TestScope.type(code: String) { code.forEach { vm.onDigit(it) }; step(600) }

    private fun TestScope.toDriving() {
        type("1234"); assertEquals(Screen.PickupInspection, s.screen)
        vm.confirmDeparture(); step(600)
        assertEquals(Screen.Driving, s.screen)
    }

    @Test fun initialLoad_showsF01_withRental() = vmTest {
        newVm()
        assertEquals(Screen.StartVerify, s.screen)
        assertEquals("김*준", s.rental?.customerName); assertEquals("R-001", s.rentalId)
    }

    @Test fun happyPath_F01_to_statement_and_restart() = vmTest {
        newVm()
        toDriving()
        assertEquals(RentalStatus.DRIVING, backend.currentStatus)
        assertEquals(s.pickup?.odometerKm, s.live?.odometerKm)

        // F-04 TPMS caution + voice
        vm.demoInjectTpmsCaution(); step(2500)
        assertEquals(Screen.CautionWarning, s.screen)
        assertEquals(EventLevel.CAUTION, s.currentEventLevel); assertNotNull(s.currentEvent)
        assertEquals(1, spoken.size); assertTrue(spoken[0].contains("타이어"))
        assertTrue(s.currentEvent!!.newSincePickup)

        // stop -> F-05 with timeline
        vm.demoSetStopped(true); step(1000)
        assertEquals(Screen.StoppedResponse, s.screen)
        assertTrue(s.timeline.isNotEmpty())
        vm.chooseRepairShop(); step(1000)
        assertEquals("COMPANY", s.repairShop?.costBy)
        vm.chooseCallRequest(); step(1000)
        assertTrue(s.callRequested)
        vm.chooseContinue(); step(1000)
        assertEquals(Screen.Driving, s.screen)
        vm.demoSetStopped(false)

        // 272 km driven, return notice 2h before
        vm.demoAddKm(272); vm.demoForceReturnNotice(); step(3000)
        assertEquals(Screen.ReturnNotice, s.screen)
        assertEquals(30, s.returnPreview?.freeKm); assertEquals(280, s.returnPreview?.pricePerKm)
        assertEquals(FeeCalculator.fee(s.returnPreview!!.drivenKm), s.returnPreview?.estimatedFee)
        assertTrue(s.returnPreview!!.drivenKm >= 272)
        vm.navigateToReturnPlace()
        assertEquals(1, navigated.size); assertEquals(FakeBackend.RETURN_PLACE.name, navigated[0].first)
        assertEquals(FakeBackend.RETURN_PLACE.lat, navigated[0].second, 0.0)

        // F-07
        vm.startReturnInspection(); step(1000)
        assertEquals(Screen.ReturnInspection, s.screen)
        val res = s.returnResult!!
        val driven = s.inspection!!.odometerKm - s.pickup!!.odometerKm
        assertTrue(driven >= 272)
        assertEquals(FeeCalculator.fee(driven), res.settlement.total)

        // F-11
        vm.confirmReturn(); step(1000)
        assertEquals(Screen.ReturnComplete, s.screen)
        assertEquals(driven, s.completeResult!!.drivenKm); assertEquals(res.settlement.total, s.completeResult!!.totalFee)
        assertTrue(Regex("^[0-9a-f]{4}·[0-9a-f]{4}$").matches(s.completeResult!!.verifyCode))
        assertEquals(RentalStatus.RETURNED, backend.currentStatus)

        vm.openStatement(); step(1000)
        assertEquals(Screen.Statement, s.screen); assertEquals(res.settlement.total, s.statement!!.totalFee)
        vm.closeStatement(); assertEquals(Screen.ReturnComplete, s.screen)

        vm.finishAndRestart(); step(1000)
        assertEquals(Screen.StartVerify, s.screen); assertEquals("", s.codeInput); assertNull(s.completeResult)
        assertEquals(RentalStatus.RESERVED, backend.currentStatus)
    }

    @Test fun returnNotice_later_snoozes30Minutes() = vmTest {
        newVm(); toDriving()
        vm.demoForceReturnNotice(); step(3000)
        assertEquals(Screen.ReturnNotice, s.screen)
        vm.returnNoticeLater()
        assertEquals(Screen.Driving, s.screen)
        step(29 * 60_000L)
        assertEquals(Screen.Driving, s.screen)
        step(3 * 60_000L)
        assertEquals(Screen.ReturnNotice, s.screen)
    }

    @Test fun returnNotice_notShownWhenMoreThan2hAway() = vmTest {
        newVm(); toDriving(); step(10_000)
        assertEquals(Screen.Driving, s.screen)
    }

    @Test fun wrongCode_showsRemaining() = vmTest {
        newVm()
        type("0000")
        assertEquals("번호가 달라요 (남은 횟수 4회)", s.verifyError); assertEquals(Screen.StartVerify, s.screen)
        type("1111")
        assertEquals("번호가 달라요 (남은 횟수 3회)", s.verifyError)
        assertEquals("", s.codeInput)
    }

    @Test fun keypad_limitedTo4Digits_backspace() = vmTest {
        newVm()
        vm.onDigit('1'); vm.onDigit('2'); vm.onBackspace()
        assertEquals("1", s.codeInput)
    }

    @Test fun lockout_after5Wrong_showsLockedScreen_untilUnlock() = vmTest {
        newVm()
        repeat(5) { type("0000") }
        assertEquals(Screen.ErrorState(ErrorKind.LOCKED), s.screen)
        vm.retry(); step(500)
        assertEquals(Screen.ErrorState(ErrorKind.LOCKED), s.screen)
        vm.demoUnlock(); step(500); vm.retry(); step(500)
        assertEquals(Screen.StartVerify, s.screen)
        type("1234"); assertEquals(Screen.PickupInspection, s.screen)
    }

    @Test fun lockedScreen_companyCallRequest_loggedWithLockedReason() = vmTest {
        newVm()
        repeat(5) { type("0000") }
        vm.requestCompanyCall(ContactReason.LOCKED); step(500)
        assertTrue(backend.contactLog.any { it.reason == ContactReason.LOCKED && it.vehicleNo != null })
        assertTrue(s.contactRequested)
    }

    @Test fun noReservation_screen_thenRetry() = vmTest {
        newVm()
        vm.demoNoReservation(true); vm.loadToday(); step(500)
        assertEquals(Screen.ErrorState(ErrorKind.NO_RESERVATION), s.screen)
        vm.requestCompanyCall(ContactReason.NO_RESERVATION); step(500)
        assertTrue(backend.contactLog.any { it.reason == ContactReason.NO_RESERVATION })
        vm.retry(); step(500) // still no reservation
        assertEquals(Screen.ErrorState(ErrorKind.NO_RESERVATION), s.screen)
        vm.demoNoReservation(false); vm.retry(); step(500)
        assertEquals(Screen.StartVerify, s.screen)
    }

    @Test fun disconnected_thenRetryReturnsToPreviousScreen() = vmTest {
        newVm()
        type("1234"); assertEquals(Screen.PickupInspection, s.screen)
        vm.demoNetworkDown(true)
        vm.confirmDeparture(); step(6000)
        assertEquals(Screen.ErrorState(ErrorKind.DISCONNECTED), s.screen)
        vm.retry(); step(6000) // still down: stays
        assertEquals(Screen.ErrorState(ErrorKind.DISCONNECTED), s.screen)
        vm.demoNetworkDown(false); vm.retry(); step(1000)
        assertEquals(Screen.Driving, s.screen) // interrupted departure is re-run
    }

    @Test fun disconnected_onF01_retryRerunsVerify() = vmTest {
        newVm()
        vm.demoNetworkDown(true); type("1234"); step(6000)
        assertEquals(Screen.ErrorState(ErrorKind.DISCONNECTED), s.screen)
        vm.demoNetworkDown(false); vm.retry(); step(1000)
        assertEquals(Screen.PickupInspection, s.screen)
    }

    @Test fun tripLog_every30Seconds() = vmTest {
        newVm(); toDriving()
        val start = backend.tripLogs.size
        step(95_000)
        assertEquals(3, backend.tripLogs.size - start)
        val times = backend.tripLogs.map { it.first }
        assertTrue((times[1] - times[0]) in 30_000L..30_500L); assertTrue((times[2] - times[1]) in 30_000L..30_500L) // drift = API latency
        assertEquals(3, s.tripLogCount)
    }

    @Test fun tripLog_failureNotRetransmitted_noDisconnectScreen() = vmTest {
        newVm(); toDriving()
        vm.demoNetworkDown(true); step(31_000)
        assertEquals(0, backend.tripLogs.size)
        vm.demoNetworkDown(false); step(35_000)
        assertTrue(backend.tripLogs.size in 1..2)
    }

    @Test fun emergencyBrake_F10_pollsStatus() = vmTest {
        newVm(); toDriving()
        vm.demoInjectBrakeEmergency(); step(2500)
        assertEquals(Screen.Emergency, s.screen)
        assertEquals(EventLevel.EMERGENCY, s.currentEventLevel); assertEquals(EventType.BRAKE, s.currentEventType)
        assertTrue(spoken.single().contains("브레이크"))
        step(1000)
        assertEquals("SENT", s.eventStatus?.notifyStatus); assertEquals("PENDING", s.eventStatus?.ackStatus)
        step(6000) // auto ack after 6 s (no admin API)
        assertEquals("CONFIRMED", s.eventStatus?.ackStatus); assertEquals("PENDING", s.eventStatus?.dispatchStatus)
        step(6000) // auto dispatch after 12 s
        assertEquals("ACCEPTED", s.eventStatus?.dispatchStatus)
        vm.requestEmergencyCall(); step(500)
        assertTrue(s.emergencyCallRequested)
        val c = backend.contactLog.last()
        assertEquals(ContactReason.EMERGENCY, c.reason); assertEquals(s.currentEvent!!.eventId, c.eventId); assertEquals("R-001", c.rentalId)
    }

    @Test fun warningBeforeDeparture_isIgnored() = vmTest {
        newVm()
        vm.demoInjectTpmsCaution(); step(2000)
        assertEquals(Screen.StartVerify, s.screen); assertTrue(spoken.isEmpty())
    }

    @Test fun contactRequestOnPickup_usesRentalId() = vmTest {
        newVm(); type("1234")
        vm.requestCompanyCall(ContactReason.PICKUP); step(500)
        assertTrue(backend.contactLog.any { it.reason == ContactReason.PICKUP && it.rentalId == "R-001" })
    }

    private fun TestScope.toReturnNotice() {
        toDriving()
        vm.demoAddKm(272); vm.demoForceReturnNotice(); step(3000)
        assertEquals(Screen.ReturnNotice, s.screen)
    }

    @Test fun returnNotice_navigateThenStop_startsReturnInspection() = vmTest {
        newVm(); toReturnNotice()
        vm.navigateToReturnPlace(); step(500)
        assertEquals(Screen.ReturnNotice, s.screen)
        vm.demoSetStopped(true); step(1500)
        assertEquals(Screen.ReturnInspection, s.screen)
        assertNotNull(s.returnResult)
        step(10_000) // loops stopped: no notice re-pop
        assertEquals(Screen.ReturnInspection, s.screen)
    }

    @Test fun stopWithoutNavigation_doesNotStartInspection() = vmTest {
        newVm(); toReturnNotice()
        vm.demoSetStopped(true); step(1500)
        assertEquals(Screen.ReturnNotice, s.screen)
    }

    @Test fun snoozeThenNavigateThenStop_fromDriving() = vmTest {
        newVm(); toReturnNotice()
        vm.navigateToReturnPlace(); vm.returnNoticeLater(); step(500)
        assertEquals(Screen.Driving, s.screen)
        vm.demoSetStopped(true); step(1500)
        assertEquals(Screen.ReturnInspection, s.screen)
    }

    @Test fun arrivedButtonPath_startsReturnInspection() = vmTest {
        newVm(); toReturnNotice()
        vm.startReturnInspection(); step(1000)
        assertEquals(Screen.ReturnInspection, s.screen)
        assertNotNull(s.returnResult)
    }

    @Test fun happyPath_F06_to_F07_to_F11_viaDemoTrigger() = vmTest {
        newVm(); toReturnNotice()
        vm.demoArriveAtReturnPlace(); step(1000)
        assertEquals(Screen.ReturnInspection, s.screen)
        vm.confirmReturn(); step(1000)
        assertEquals(Screen.ReturnComplete, s.screen)
        step(10_000)
        assertEquals(Screen.ReturnComplete, s.screen)
    }

    @Test fun returnNotice_appearsAbout1MinuteAfterDeparture() = vmTest {
        newVm(); toDriving()
        assertEquals(2.0 * 3_600_000 + 60_000, (s.rental!!.returnAt - clock()).toDouble(), 5_000.0)
        step(40_000)
        assertEquals(Screen.Driving, s.screen)
        step(30_000)
        assertEquals(Screen.ReturnNotice, s.screen)
    }

    @Test fun offline_showsDisconnectedAfter5s_thenRetryRecovers() = vmTest {
        newVm()
        online = false
        type("1234")
        assertEquals(Screen.StartVerify, s.screen)
        step(6000)
        assertEquals(Screen.ErrorState(ErrorKind.DISCONNECTED), s.screen)
        vm.retry(); step(6000) // still offline
        assertEquals(Screen.ErrorState(ErrorKind.DISCONNECTED), s.screen)
        online = true; vm.retry(); step(1000)
        assertEquals(Screen.PickupInspection, s.screen) // interrupted verify is re-sent
    }

    @Test fun disconnect_onCodeSubmit_retryReRunsVerify() = vmTest {
        newVm(); online = false
        type("1234"); step(6000)
        assertEquals(Screen.ErrorState(ErrorKind.DISCONNECTED), s.screen)
        online = true; vm.retry(); step(1000)
        assertEquals(Screen.PickupInspection, s.screen)
        assertEquals(RentalStatus.VERIFIED, backend.currentStatus)
    }

    @Test fun disconnect_onWrongCodeSubmit_retryShowsVerifyResult() = vmTest {
        newVm(); online = false
        type("9999"); step(6000)
        online = true; vm.retry(); step(1000)
        assertEquals(Screen.StartVerify, s.screen)
        assertEquals(4, s.remainingAttempts)
    }

    @Test fun disconnect_onConfirmDeparture_retryEndsDriving() = vmTest {
        newVm(); type("1234")
        online = false
        vm.confirmDeparture(); step(6000)
        assertEquals(Screen.ErrorState(ErrorKind.DISCONNECTED), s.screen)
        online = true; vm.retry(); step(1000)
        assertEquals(Screen.Driving, s.screen)
        assertEquals(RentalStatus.DRIVING, backend.currentStatus)
        assertNotNull(s.pickup)
    }

    @Test fun confirmDeparture_repeatedPickup_isIdempotent() = vmTest {
        newVm(); type("1234")
        val snap = vehicle.snapshot()
        val a = backend.postInspection("R-001", InspectionType.PICKUP, snap) as ApiResult.Success
        val b = backend.postInspection("R-001", InspectionType.PICKUP, snap) as ApiResult.Success
        assertEquals(a.data, b.data)
    }

    @Test fun disconnect_onArrived_retryLoadsReturnResult() = vmTest {
        newVm(); toReturnNotice()
        online = false
        vm.startReturnInspection(); step(6000)
        assertEquals(Screen.ErrorState(ErrorKind.DISCONNECTED), s.screen)
        online = true; vm.retry(); step(1000)
        assertEquals(Screen.ReturnInspection, s.screen)
        assertNotNull(s.returnResult)
        assertFalse(s.loading)
    }

    @Test fun disconnect_onConfirmReturn_retryEndsReturnComplete() = vmTest {
        newVm(); toReturnNotice()
        vm.startReturnInspection(); step(1000)
        online = false
        vm.confirmReturn(); step(6000)
        assertEquals(Screen.ErrorState(ErrorKind.DISCONNECTED), s.screen)
        online = true; vm.retry(); step(1000)
        assertEquals(Screen.ReturnComplete, s.screen)
        assertEquals(RentalStatus.RETURNED, backend.currentStatus)
    }

    @Test fun disconnect_onStoppedAction_retryRerunsAction() = vmTest {
        newVm(); toDriving()
        vm.demoInjectTpmsCaution(); step(2500)
        vm.demoSetStopped(true); step(1000)
        assertEquals(Screen.StoppedResponse, s.screen)
        online = false
        vm.chooseRepairShop(); step(6000)
        assertEquals(Screen.ErrorState(ErrorKind.DISCONNECTED), s.screen)
        online = true; vm.retry(); step(1000)
        assertEquals(Screen.StoppedResponse, s.screen)
        assertEquals("COMPANY", s.repairShop?.costBy)
    }

    @Test fun tpmsRepeats_afterContinue_andStopWhileDrivingWithPendingWarning() = vmTest {
        newVm(); toDriving()
        vm.demoInjectTpmsCaution(); step(2500)
        assertEquals(Screen.CautionWarning, s.screen)
        vm.dismissCaution(); assertEquals(Screen.Driving, s.screen)
        vehicle.setStopped(true); step(2500) // stopped while on F-03 with a pending warning -> F-05
        assertEquals(Screen.StoppedResponse, s.screen)
        assertTrue(s.timeline.any { it.text.contains("정차 감지") })
        vm.chooseContinue(); step(1000); vehicle.setStopped(false); step(500)
        assertEquals(Screen.Driving, s.screen)
        vm.demoInjectTpmsCaution(); step(2500) // fires again
        assertEquals(Screen.CautionWarning, s.screen); assertEquals(2, spoken.size)
    }

    @Test fun openTrunk_blocksDeparture_untilClosed_liveRefresh() = vmTest {
        newVm()
        vehicle.setOpen("트렁크", true)
        type("1234"); assertEquals(Screen.PickupInspection, s.screen)
        assertEquals(listOf("트렁크"), s.inspection?.openItems); assertFalse(s.inspection!!.doorsClosed)
        vm.confirmDeparture(); step(600)
        assertEquals(Screen.PickupInspection, s.screen)
        assertNotNull(s.message)
        assertNotEquals(RentalStatus.DRIVING, backend.currentStatus)

        vehicle.setOpen("트렁크", false); step(1000) // 1 s inspection refresh picks up the closed trunk
        assertEquals(emptyList<String>(), s.inspection?.openItems); assertTrue(s.inspection!!.doorsClosed)
        vm.confirmDeparture(); step(600)
        assertEquals(Screen.Driving, s.screen)
        assertEquals(RentalStatus.DRIVING, backend.currentStatus)
    }

    @Test fun openedOnF02_afterEntering_isDetectedByRefresh_andRaceBlocked() = vmTest {
        newVm()
        type("1234"); assertTrue(s.inspection!!.openItems.isEmpty())
        vehicle.setOpen("앞 왼쪽 창문", true)
        vm.confirmDeparture(); step(600) // fresh snapshot inside confirmDeparture also blocks
        assertEquals(Screen.PickupInspection, s.screen)
        assertEquals(listOf("앞 왼쪽 창문"), s.inspection?.openItems)
    }

    private fun TestScope.assertBlockedThenCleared(inject: () -> Unit, clear: () -> Unit, expected: String) {
        inject()
        type("1234"); assertEquals(Screen.PickupInspection, s.screen)
        assertTrue(s.inspection!!.let { com.example.pleosrantlog.data.vehicle.VehicleMapping.blockers(it) }.contains(expected))
        vm.confirmDeparture(); step(600)
        assertEquals(Screen.PickupInspection, s.screen); assertNotNull(s.message)
        assertNotEquals(RentalStatus.DRIVING, backend.currentStatus)
        clear(); step(1000)
        assertTrue(com.example.pleosrantlog.data.vehicle.VehicleMapping.blockers(s.inspection!!).isEmpty())
        vm.confirmDeparture(); step(600)
        assertEquals(Screen.Driving, s.screen)
    }

    @Test fun lowTire_blocksDeparture_untilRecovered() = vmTest {
        newVm()
        assertBlockedThenCleared({ vehicle.setTirePressure("앞 왼쪽", 150f) }, { vehicle.setTirePressure("앞 왼쪽", 230f) },
            "앞 왼쪽 타이어 공기압 낮음 (150 kPa)")
    }

    @Test fun warningLight_blocksDeparture_untilCleared() = vmTest {
        newVm()
        assertBlockedThenCleared({ vehicle.setWarningName("ABS", true) }, { vehicle.setWarningName("ABS", false) }, "ABS 경고등")
    }

    @Test fun lampFault_blocksDeparture_untilCleared() = vmTest {
        newVm()
        assertBlockedThenCleared({ vehicle.setLampFault("LED 헤드램프", true) }, { vehicle.setLampFault("LED 헤드램프", false) }, "LED 헤드램프 경고")
    }

    @Test fun lowBattery_blocksDeparture_untilCharged() = vmTest {
        newVm()
        assertBlockedThenCleared({ vehicle.setFuelPercent(15) }, { vehicle.setFuelPercent(20) }, "배터리 15%")
    }

    @Test fun multipleBlockers_listedAndNoticeCountsRest() = vmTest {
        newVm()
        vehicle.setOpen("트렁크", true); vehicle.setTirePressure("뒤 왼쪽", 100f); vehicle.setWarningName("에어백", true)
        type("1234")
        val b = com.example.pleosrantlog.data.vehicle.VehicleMapping.blockers(s.inspection!!)
        assertEquals(3, b.size); assertEquals("트렁크 열림", b.first())
        assertEquals("트렁크 열림 외 2건. 해결되면 자동으로 다시 확인해요.", com.example.pleosrantlog.ui.screens.InspectionTexts.notice(b))
    }
}
