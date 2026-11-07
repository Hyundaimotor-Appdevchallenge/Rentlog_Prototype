package com.example.pleosrantlog

import com.example.pleosrantlog.data.backend.Crypto
import com.example.pleosrantlog.data.backend.FakeBackend
import com.example.pleosrantlog.data.backend.HashChain
import com.example.pleosrantlog.data.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** Verifies FakeBackend against the B-01..B-10 spec (기능명세서.xlsx 백엔드 시트). */
@OptIn(ExperimentalCoroutinesApi::class)
class FakeBackendSpecTest {
    private val R = "R-001"
    private fun backend() = FakeBackend(latencyMs = 0)
    private fun snap(odo: Int = 12340, fuel: Int = 80, warn: Int = 0, tpms: Boolean = true) =
        VehicleSnapshot(odo, fuel, warn, tpms, true, true)

    private fun <T> ApiResult<T>.ok(): T = (this as ApiResult.Success).data
    private fun ApiResult<*>.err(): ApiResult.Error = this as ApiResult.Error

    // ---- B-01 ----
    @Test fun todayRental_returnsMaskedRental() = runTest {
        val r = backend().getTodayRental("12가3456").ok()
        assertEquals("R-001", r.rentalId); assertEquals("김*준", r.customerName)
        assertTrue(r.vehicle.contains("12가 3456")); assertTrue(r.company.name.isNotEmpty()); assertTrue(r.company.phone.isNotEmpty())
        assertTrue(r.returnAt > r.startAt)
    }

    @Test fun todayRental_unknownVehicle_404NoReservation() = runTest {
        val e = backend().getTodayRental("99나9999").err()
        assertEquals(404, e.http); assertEquals("NO_RESERVATION", e.code)
    }

    @Test fun todayRental_noReservationSwitch() = runTest {
        val b = backend(); b.forceNoReservation(true)
        assertEquals("NO_RESERVATION", b.getTodayRental("12가3456").err().code)
    }

    @Test fun verify_correct_setsVerified() = runTest {
        val b = backend()
        assertEquals(true, b.verify(R, "1234").ok())
        assertEquals(RentalStatus.VERIFIED, b.currentStatus)
    }

    @Test fun verify_wrong_decreasingRemaining_then423() = runTest {
        val b = backend()
        for (expected in listOf(4, 3, 2, 1)) {
            val e = b.verify(R, "0000").err()
            assertEquals(401, e.http); assertEquals(expected, e.remaining)
        }
        val fifth = b.verify(R, "0000").err()
        assertEquals(423, fifth.http); assertEquals("LOCKED", fifth.code)
    }

    @Test fun verify_locked_staysLockedEvenWithCorrectCode_untilAdminUnlock() = runTest {
        val b = backend()
        repeat(5) { b.verify(R, "0000") }
        assertEquals(423, b.verify(R, "1234").err().http)
        assertEquals(423, b.verify(R, "1234").err().http)
        b.unlock(R)
        assertTrue(b.verify(R, "1234") is ApiResult.Success)
    }

    @Test fun verify_unknownRental_404() = runTest {
        assertEquals(404, backend().verify("R-999", "1234").err().http)
    }

    // ---- B-02 / B-07 ----
    @Test fun pickupInspection_movesToDriving_andReturnsHash() = runTest {
        val b = backend(); b.verify(R, "1234")
        val saved = b.postInspection(R, InspectionType.PICKUP, snap()).ok()
        assertEquals(RentalStatus.DRIVING, b.currentStatus)
        assertTrue(saved.inspectionId.isNotEmpty())
        assertEquals(Crypto.sha256Hex(HashChain.GENESIS + "INSPECTION|PICKUP|12340|80|0|true|true|true"), saved.hash)
        assertTrue(Regex("[0-9a-f]{64}").matches(saved.hash))
    }

    @Test fun returnInspection_compareAndSettlement_272km() = runTest {
        val b = backend(); b.verify(R, "1234")
        b.postInspection(R, InspectionType.PICKUP, snap(12340, 80))
        val res = b.postInspectionFull(R, InspectionType.RETURN, snap(12612, 41)).ok().second!!
        assertEquals(67_760, res.settlement.total)
        val km = res.compare.first { it.item.contains("주행거리") }
        assertEquals("12,340 km", km.pickup); assertEquals("12,612 km", km.ret); assertTrue(km.result.contains("272"))
        val fuel = res.compare.first { it.item.contains("연료") }
        assertEquals("기록만", fuel.result)
        assertTrue(res.compare.any { it.item.contains("경고등") })
        assertTrue(res.compare.any { it.item.contains("TPMS") })
        assertTrue(res.compare.any { it.item.contains("문") })
        assertTrue(res.compare.any { it.item.contains("등화") })
        // fuel has no settlement, warning response cost is 0 (company pays)
        assertTrue(res.settlement.lines.filter { !it.label.contains("주행") }.all { it.amount == 0 })
        assertEquals(res.settlement.total, res.settlement.lines.sumOf { it.amount })
    }

    @Test fun returnInspection_freeKm_zeroFee() = runTest {
        for (driven in listOf(0, 20, 30)) {
            val b = backend(); b.verify(R, "1234")
            b.postInspection(R, InspectionType.PICKUP, snap(12340))
            val res = b.postInspectionFull(R, InspectionType.RETURN, snap(12340 + driven)).ok().second!!
            assertEquals("driven=$driven", 0, res.settlement.total)
        }
        val b = backend(); b.verify(R, "1234"); b.postInspection(R, InspectionType.PICKUP, snap(12340))
        assertEquals(280, b.postInspectionFull(R, InspectionType.RETURN, snap(12371)).ok().second!!.settlement.total)
    }

    @Test fun returnInspection_detectsChangedTpms() = runTest {
        val b = backend(); b.verify(R, "1234"); b.postInspection(R, InspectionType.PICKUP, snap())
        val res = b.postInspectionFull(R, InspectionType.RETURN, snap(12400, tpms = false)).ok().second!!
        val row = res.compare.first { it.item.contains("TPMS") }
        assertNotEquals("동일", row.result)
    }

    @Test fun returnInspection_openItems_showOpenRow_settlementUnchanged() = runTest {
        val b = backend(); b.verify(R, "1234"); b.postInspection(R, InspectionType.PICKUP, snap())
        val open = snap(12612, 41).copy(doorsClosed = false, openItems = listOf("트렁크"))
        val res = b.postInspectionFull(R, InspectionType.RETURN, open).ok().second!!
        val row = res.compare.first { it.item.contains("문") }
        assertEquals("닫힘", row.pickup); assertEquals("열림 있음", row.ret); assertEquals("열림 있음", row.result)
        assertEquals(67_760, res.settlement.total)

        val b2 = backend(); b2.verify(R, "1234"); b2.postInspection(R, InspectionType.PICKUP, snap())
        val closedRow = b2.postInspectionFull(R, InspectionType.RETURN, snap(12400)).ok().second!!.compare.first { it.item.contains("문") }
        assertEquals("닫힘", closedRow.ret); assertEquals("동일", closedRow.result)
    }

    // ---- B-03 ----
    @Test fun tripLog_keepsLastOdometer_usedForCompleteFee() = runTest {
        val b = backend(); b.verify(R, "1234"); b.postInspection(R, InspectionType.PICKUP, snap(12340))
        b.postTripLog(R, 1L, 12400, 70); b.postTripLog(R, 2L, 12612, 50)
        assertEquals(2, b.tripLogs.size)
        val c = b.complete(R).ok()
        assertEquals(272, c.drivenKm); assertEquals(67_760, c.totalFee)
    }

    @Test fun returnPreview_272km() = runTest {
        val b = backend(); b.verify(R, "1234"); b.postInspection(R, InspectionType.PICKUP, snap(12340))
        val p = b.getReturnPreview(R, 12612, 41).ok()
        assertEquals(272, p.drivenKm); assertEquals(30, p.freeKm); assertEquals(280, p.pricePerKm)
        assertEquals(67_760, p.estimatedFee); assertEquals(41, p.fuelPercent)
    }

    // ---- B-04 / B-09 ----
    @Test fun event_signature_deterministicHmac() = runTest {
        val b1 = backend(); val b2 = backend()
        for (b in listOf(b1, b2)) { b.verify(R, "1234"); b.postInspection(R, InspectionType.PICKUP, snap()) }
        val a = b1.postEvent(R, EventType.TPMS, EventLevel.CAUTION, "14:32", 12488).ok()
        val c = b2.postEvent(R, EventType.TPMS, EventLevel.CAUTION, "14:32", 12488).ok()
        assertEquals(a.signature, c.signature)
        assertTrue(Regex("[0-9a-f]{64}").matches(a.signature))
        val other = b1.postEvent(R, EventType.TPMS, EventLevel.CAUTION, "14:33", 12488).ok()
        assertNotEquals(a.signature, other.signature)
    }

    @Test fun event_newSincePickup() = runTest {
        suspend fun ev(p: VehicleSnapshot, t: EventType): Boolean {
            val b = backend(); b.verify(R, "1234"); b.postInspection(R, InspectionType.PICKUP, p)
            return b.postEvent(R, t, EventLevel.CAUTION, "14:32", 12488).ok().newSincePickup
        }
        assertTrue(ev(snap(), EventType.TPMS))
        assertFalse(ev(snap(tpms = false, warn = 1), EventType.TPMS))
        assertTrue(ev(snap(), EventType.BRAKE))
        assertFalse(ev(snap(warn = 1), EventType.BRAKE))
    }

    @Test fun emergency_notifySent_thenAckAndDispatchViaAdmin() = runTest {
        val b = backend(); b.verify(R, "1234"); b.postInspection(R, InspectionType.PICKUP, snap())
        val caution = b.postEvent(R, EventType.TPMS, EventLevel.CAUTION, "14:00", 12400).ok()
        assertEquals("NONE", b.getEvent(caution.eventId).ok().notifyStatus)
        val em = b.postEvent(R, EventType.BRAKE, EventLevel.EMERGENCY, "14:32", 12488).ok()
        var s = b.getEvent(em.eventId).ok()
        assertEquals("SENT", s.notifyStatus); assertEquals("PENDING", s.ackStatus); assertEquals("PENDING", s.dispatchStatus)
        b.ack(em.eventId)
        s = b.getEvent(em.eventId).ok()
        assertEquals("CONFIRMED", s.ackStatus); assertEquals("PENDING", s.dispatchStatus)
        b.dispatch(em.eventId)
        assertEquals("ACCEPTED", b.getEvent(em.eventId).ok().dispatchStatus)
    }

    @Test fun getEvent_unknown_404() = runTest { assertEquals(404, backend().getEvent("E-999").err().http) }

    // ---- B-05 ----
    @Test fun actions_repairShop_callRequest_continue() = runTest {
        val b = backend(); b.verify(R, "1234"); b.postInspection(R, InspectionType.PICKUP, snap())
        val id = b.postEvent(R, EventType.TPMS, EventLevel.CAUTION, "14:32", 12488).ok().eventId
        val shop = b.postAction(id, ActionType.REPAIR_SHOP).ok()
        assertFalse(shop.shopName.isNullOrEmpty()); assertNotNull(shop.distanceKm); assertEquals("COMPANY", shop.costBy)
        b.postAction(id, ActionType.CALL_REQUEST).ok()
        assertTrue(b.contactLog.any { it.eventId == id })
        assertTrue(b.postAction(id, ActionType.CONTINUE) is ApiResult.Success)
        assertEquals(404, b.postAction("E-999", ActionType.CONTINUE).err().http)
    }

    @Test fun events_timeline_chronologicalAndGrows() = runTest {
        val b = backend(); b.verify(R, "1234"); b.postInspection(R, InspectionType.PICKUP, snap())
        val before = b.getEvents(R).ok().size
        val id = b.postEvent(R, EventType.TPMS, EventLevel.CAUTION, "14:32", 12488).ok().eventId
        b.postAction(id, ActionType.CONTINUE)
        val tl = b.getEvents(R).ok()
        assertEquals(before + 4, tl.size) // 경고등 켜짐 + 자동 알림 + 업체 응답 + 계속 운행
        assertTrue(tl.last().text.isNotEmpty())
    }

    // ---- B-16 hash chain ----
    @Test fun hashChain_deterministic_and_chained() {
        val a = HashChain(); val b = HashChain()
        val h1 = a.append("x"); b.append("x")
        assertEquals(Crypto.sha256Hex(HashChain.GENESIS + "x"), h1)
        val h2 = a.append("y")
        assertEquals(Crypto.sha256Hex(h1 + "y"), h2)
        assertEquals(h1, b.last); assertEquals(h2, b.also { it.append("y") }.last)
    }

    @Test fun verifyCode_format() {
        val c = HashChain(); c.append("abc")
        val code = c.verifyCode()
        assertTrue(code, Regex("^[0-9a-f]{4}·[0-9a-f]{4}$").matches(code))
        assertEquals(c.last.take(4) + "·" + c.last.substring(4, 8), code)
        assertEquals("7f3a·c21e", HashChain.formatVerifyCode("7f3ac21e" + "0".repeat(56)))
    }

    // ---- B-10 ----
    @Test fun complete_returned_withStatementAndVerifyCode() = runTest {
        val b = backend(); b.verify(R, "1234"); b.postInspection(R, InspectionType.PICKUP, snap(12340))
        assertEquals(409, b.getStatement(R).err().http)
        b.postInspectionFull(R, InspectionType.RETURN, snap(12612))
        val c = b.complete(R).ok()
        assertEquals(RentalStatus.RETURNED, b.currentStatus)
        assertEquals(272, c.drivenKm); assertEquals(67_760, c.totalFee)
        assertEquals(HashChain.formatVerifyCode(b.lastHash), c.verifyCode)
        val st = b.getStatement(R).ok()
        assertEquals(67_760, st.totalFee); assertEquals(67_760, st.settlement.total)
        assertTrue(st.compare.isNotEmpty()); assertTrue(st.events.isNotEmpty()); assertEquals(c.verifyCode, st.verifyCode)
    }

    @Test fun complete_beforeDriving_conflict() = runTest {
        assertEquals(409, backend().complete(R).err().http)
    }

    @Test fun sameFlow_sameVerifyCode_deterministic() = runTest {
        suspend fun run(): String {
            val b = backend(); b.verify(R, "1234"); b.postInspection(R, InspectionType.PICKUP, snap())
            b.postInspectionFull(R, InspectionType.RETURN, snap(12612)); return b.complete(R).ok().verifyCode
        }
        assertEquals(run(), run())
    }

    // ---- B-08 ----
    @Test fun health_ok() = runTest { assertEquals("OK", backend().health().ok()) }

    @Test fun networkDown_timesOutAfter5s_withNetworkError() = runTest {
        val b = FakeBackend(latencyMs = 0, timeoutMs = 5000); b.forceNetworkDown(true)
        val d = async { b.health() }
        runCurrent(); advanceTimeBy(4_900); assertFalse(d.isCompleted)
        advanceTimeBy(200); runCurrent()
        val e = d.await().err()
        assertEquals("NETWORK", e.code); assertTrue(e.isNetwork)
        b.forceNetworkDown(false); assertTrue(b.health() is ApiResult.Success)
    }

    @Test fun contactRequest_logged() = runTest {
        val b = backend()
        b.postContactRequest(vehicleNo = "12가3456", reason = ContactReason.NO_RESERVATION).ok()
        b.postContactRequest(rentalId = R, eventId = "E-001", reason = ContactReason.EMERGENCY).ok()
        assertEquals(listOf(ContactReason.NO_RESERVATION, ContactReason.EMERGENCY), b.contactLog.map { it.reason })
    }

    // ---- demo timing / console integration ----
    @Test fun returnAt_initial2h04m_then2h01mAfterDeparture() = runTest {
        var now = 1_000_000_000_000L
        val b = FakeBackend(clock = { now }, latencyMs = 0)
        assertEquals(now + 2 * 3_600_000L + 4 * 60_000L, b.getTodayRental("12가3456").ok().returnAt)
        b.verify(R, "1234"); now += 30_000
        b.postInspection(R, InspectionType.PICKUP, snap())
        assertEquals(now + 2 * 3_600_000L + 60_000L, b.getTodayRental("12가3456").ok().returnAt)
    }

    @Test fun returnAfterDeparture_isConfigurable() = runTest {
        val now = 5_000L
        val b = FakeBackend(clock = { now }, latencyMs = 0, returnAfterDepartureMs = 7_000L)
        b.verify(R, "1234"); b.postInspection(R, InspectionType.PICKUP, snap())
        assertEquals(now + 7_000L, b.getTodayRental("12가3456").ok().returnAt)
    }

    @Test fun emergency_autoAcksAt6s_dispatchesAt12s() = runTest {
        var now = 0L
        val b = FakeBackend(clock = { now }, latencyMs = 0)
        b.verify(R, "1234"); b.postInspection(R, InspectionType.PICKUP, snap())
        val id = b.postEvent(R, EventType.BRAKE, EventLevel.EMERGENCY, "14:40", 12500).ok().eventId
        var st = b.getEvent(id).ok()
        assertEquals("SENT", st.notifyStatus); assertEquals("PENDING", st.ackStatus); assertEquals("PENDING", st.dispatchStatus)
        now = 5_999; assertEquals("PENDING", b.getEvent(id).ok().ackStatus)
        now = 6_000; st = b.getEvent(id).ok()
        assertEquals("CONFIRMED", st.ackStatus); assertEquals("PENDING", st.dispatchStatus)
        now = 12_000; st = b.getEvent(id).ok()
        assertEquals("CONFIRMED", st.ackStatus); assertEquals("ACCEPTED", st.dispatchStatus)
        assertEquals(1, b.getEvents(R).ok().count { it.text.contains("긴급 알림을 확인") })
    }

    @Test fun caution_doesNotAutoProgress() = runTest {
        var now = 0L
        val b = FakeBackend(clock = { now }, latencyMs = 0)
        b.verify(R, "1234"); b.postInspection(R, InspectionType.PICKUP, snap())
        val id = b.postEvent(R, EventType.TPMS, EventLevel.CAUTION, "14:32", 12488).ok().eventId
        now = 60_000; assertEquals("PENDING", b.getEvent(id).ok().ackStatus)
    }

    @Test fun f05Timeline_hasFigmaEntries() = runTest {
        val b = backend(); b.verify(R, "1234"); b.postInspection(R, InspectionType.PICKUP, snap())
        b.postEvent(R, EventType.TPMS, EventLevel.CAUTION, "14:32", 12488)
        b.postStopDetected(R); b.postStopDetected(R) // idempotent
        val t = b.getEvents(R).ok().map { it.text }
        assertTrue(t.contains("TPMS 경고등 켜짐 · 주행거리 12,488 km"))
        assertTrue(t.contains("업체에 자동 알림 전송 · 카카오톡으로 고객님께도 기록 전송"))
        assertTrue(t.contains("업체 응답: 지정 정비소 방문 시 수리비는 업체 부담"))
        assertEquals(1, t.count { it == "정차 감지 · 대응 방법 안내 시작" })
    }

    @Test fun returnInspection_newFields_compareRows_settlementUnchanged() = runTest {
        val pk = snap().copy(tirePressures = mapOf("앞 왼쪽" to 230f, "앞 오른쪽" to 230f))
        val b = backend(); b.verify(R, "1234"); b.postInspection(R, InspectionType.PICKUP, pk)
        val ret = snap(12612, 41, 1, false).copy(
            tirePressures = mapOf("앞 왼쪽" to 150f, "앞 오른쪽" to 230f), warningNames = listOf("ABS"),
            lampFaults = listOf("AFS"), lightsOk = false,
        )
        val res = b.postInspectionFull(R, InspectionType.RETURN, ret).ok().second!!
        val tire = res.compare.first { it.item.contains("TPMS") }
        assertEquals("230 kPa", tire.pickup); assertEquals("150 kPa", tire.ret); assertEquals("공기압 낮음", tire.result)
        val warn = res.compare.first { it.item.contains("경고등") }
        assertEquals("0개", warn.pickup); assertEquals("ABS", warn.ret)
        val lamp = res.compare.first { it.item.contains("등화") }
        assertEquals("정상", lamp.pickup); assertEquals("이상 있음", lamp.ret); assertEquals("변경됨", lamp.result)
        assertEquals(67_760, res.settlement.total)

        val b2 = backend(); b2.verify(R, "1234"); b2.postInspection(R, InspectionType.PICKUP, pk)
        val same = b2.postInspectionFull(R, InspectionType.RETURN, snap(12612, 41).copy(tirePressures = pk.tirePressures)).ok().second!!
        assertEquals("동일", same.compare.first { it.item.contains("TPMS") }.result)
    }
}
