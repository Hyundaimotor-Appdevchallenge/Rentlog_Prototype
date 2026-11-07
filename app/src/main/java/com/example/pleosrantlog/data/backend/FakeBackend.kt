package com.example.pleosrantlog.data.backend

import android.util.Log
import com.example.pleosrantlog.data.DemoConstants
import com.example.pleosrantlog.data.grouped
import com.example.pleosrantlog.data.hm
import com.example.pleosrantlog.data.model.*
import com.example.pleosrantlog.data.vehicle.SnapshotBlockers
import kotlinx.coroutines.delay

/**
 * In-memory implementation of the B-01~B-10 contracts. All state is lost on process restart.
 * Calls are suspend + artificially delayed; when [networkDown] is set they wait [timeoutMs] and fail with NETWORK.
 */
class FakeBackend(
    private val clock: () -> Long = { System.currentTimeMillis() },
    var latencyMs: Long = 300,
    var timeoutMs: Long = 5000,
    /** Return time shown on F-01 before departure (now + this). */
    private val initialReturnInMs: Long = INITIAL_RETURN_IN_MS,
    /** Demo timing: on PICKUP success returnAt = now + this, so F-06 appears ~1 min after departure. */
    var returnAfterDepartureMs: Long = DEMO_RETURN_AFTER_DEPARTURE_MS,
    /** EMERGENCY events: GET /events/{id} reports ack CONFIRMED / dispatch ACCEPTED after these delays. */
    var emergencyAckAfterMs: Long = EMERGENCY_ACK_AFTER_MS,
    var emergencyDispatchAfterMs: Long = EMERGENCY_DISPATCH_AFTER_MS,
) {
    companion object {
        const val VERIFY_CODE = "1234"
        const val MAX_FAILS = 5
        const val SECRET = "rentlog-demo-secret"
        const val SHOP_NAME = "강남 제일정비소"
        const val SHOP_DISTANCE_KM = 3.2
        val RETURN_PLACE = ReturnPlace("렌트로그 강남 반납센터", 37.4979, 127.0276)
        const val DEMO_RETURN_AFTER_DEPARTURE_MS = 2 * 3_600_000L + 60_000L   // 2h01m
        const val INITIAL_RETURN_IN_MS = 2 * 3_600_000L + 4 * 60_000L        // 2h04m
        const val EMERGENCY_ACK_AFTER_MS = 6_000L
        const val EMERGENCY_DISPATCH_AFTER_MS = 12_000L
        private const val TAG = "FakeBackend"
    }

    // ---- demo switches -------------------------------------------------------------------
    @Volatile var networkDown = false
    @Volatile var noReservation = false
    fun forceNetworkDown(down: Boolean) { networkDown = down }
    fun forceNoReservation(on: Boolean) { noReservation = on }

    // ---- state ---------------------------------------------------------------------------
    private class EventState(var record: EventRecord, val createdAt: Long)

    private var status = RentalStatus.RESERVED
    private var failCount = 0
    private var startAt = clock() - 10 * 60_000L
    private var returnAt = clock() + initialReturnInMs
    private var pickup: Pair<VehicleSnapshot, Long>? = null
    private var returnResult: ReturnInspectionResult? = null
    // idempotency: a request that timed out client-side may already be applied; a repeat returns the stored result
    private var pickupSaved: InspectionSaved? = null
    private var returnSaved: InspectionSaved? = null
    private var lastTripOdo: Int? = null
    private var eventSeq = 0
    private var inspectionSeq = 0
    private val events = mutableListOf<EventState>()
    private val timelineLog = mutableListOf<TimelineItem>()
    private var complete: CompleteResult? = null
    private val _contactLog = mutableListOf<ContactRequest>()
    val contactLog: List<ContactRequest> get() = _contactLog
    val tripLogs = mutableListOf<Triple<Long, Int, Int>>()
    val currentStatus: RentalStatus get() = status
    val isLocked: Boolean get() = failCount >= MAX_FAILS

    fun reset() {
        status = RentalStatus.RESERVED; failCount = 0
        startAt = clock() - 10 * 60_000L
        returnAt = clock() + initialReturnInMs
        pickup = null; returnResult = null; lastTripOdo = null; pickupSaved = null; returnSaved = null
        events.clear(); timelineLog.clear(); _contactLog.clear(); tripLogs.clear()
        eventSeq = 0; inspectionSeq = 0; complete = null; stopLoggedFor = null
        chainReset()
        networkDown = false; noReservation = false
    }

    private var chainRef = HashChain()
    private fun chainReset() { chainRef = HashChain() }

    /** Demo: set the return time relative to now. */
    fun setReturnInMinutes(minutes: Long) { returnAt = clock() + minutes * 60_000L }

    // ---- helpers -------------------------------------------------------------------------
    private suspend fun <T> call(block: () -> ApiResult<T>): ApiResult<T> {
        if (networkDown) {
            delay(timeoutMs)
            return ApiResult.Error(ErrorCodes.NETWORK, 0, "연결 끊김")
        }
        delay(latencyMs)
        return block()
    }

    private fun notFound(msg: String = "not found") = ApiResult.Error(ErrorCodes.NOT_FOUND, 404, msg)
    private fun log(msg: String) = try { Log.d(TAG, msg) } catch (_: Throwable) { println("[$TAG] $msg") }
    private fun rentalOk(id: String) = id == DemoConstants.RENTAL_ID

    private fun rental() = Rental(
        rentalId = DemoConstants.RENTAL_ID,
        customerName = "김*준",
        vehicle = "아반떼 · 12가 3456",
        company = Company("[렌터카 업체명]", "02-1234-5678"),
        startAt = startAt,
        returnAt = returnAt,
    )

    private fun addTimeline(text: String) { timelineLog += TimelineItem(clock().hm(), text) }

    private fun lightLabel(t: EventType) = when (t) { EventType.TPMS -> "TPMS"; EventType.BRAKE -> "ABS" }
    private var stopLoggedFor: String? = null

    /** Emergency auto progression (replaces the admin API in demos), based on the injected clock. */
    private fun progress(e: EventState) {
        if (e.record.level != EventLevel.EMERGENCY) return
        val age = clock() - e.createdAt
        if (e.record.ackStatus == "PENDING" && age >= emergencyAckAfterMs) {
            e.record = e.record.copy(ackStatus = "CONFIRMED"); addTimeline("업체가 긴급 알림을 확인했어요")
        }
        if (e.record.dispatchStatus == "PENDING" && age >= emergencyDispatchAfterMs) {
            e.record = e.record.copy(dispatchStatus = "ACCEPTED"); addTimeline("업체가 출동을 수락했어요")
        }
    }

    // ---- B-01 ----------------------------------------------------------------------------
    suspend fun getTodayRental(vehicleNo: String): ApiResult<Rental> = call {
        if (noReservation || vehicleNo.replace(" ", "") != DemoConstants.VEHICLE_NO)
            ApiResult.Error(ErrorCodes.NO_RESERVATION, 404, "예약 없음")
        else ApiResult.Success(rental())
    }

    suspend fun verify(rentalId: String, code: String): ApiResult<Boolean> = call {
        if (!rentalOk(rentalId)) return@call notFound()
        if (failCount >= MAX_FAILS) {
            return@call ApiResult.Error(ErrorCodes.LOCKED, 423, "확인 잠김")
        }
        if (code == VERIFY_CODE) {
            failCount = 0
            if (status == RentalStatus.RESERVED) status = RentalStatus.VERIFIED
            log("verify '$code' OK")
            ApiResult.Success(true)
        } else {
            failCount++
            log("verify '$code' FAIL ($failCount/$MAX_FAILS)")
            if (failCount >= MAX_FAILS) ApiResult.Error(ErrorCodes.LOCKED, 423, "확인 잠김")
            else ApiResult.Error(ErrorCodes.VERIFY_FAILED, 401, "번호가 달라요", remaining = MAX_FAILS - failCount)
        }
    }

    // ---- B-02 / B-07 ---------------------------------------------------------------------
    suspend fun postInspection(rentalId: String, type: InspectionType, v: VehicleSnapshot): ApiResult<InspectionSaved> =
        postInspectionFull(rentalId, type, v).let {
            when (it) {
                is ApiResult.Success -> ApiResult.Success(it.data.first)
                is ApiResult.Error -> it
            }
        }

    /** Returns saved info plus (for RETURN) the compare/settlement result. */
    suspend fun postInspectionFull(
        rentalId: String, type: InspectionType, v: VehicleSnapshot,
    ): ApiResult<Pair<InspectionSaved, ReturnInspectionResult?>> = call {
        if (!rentalOk(rentalId)) return@call notFound()
        when (type) {
            InspectionType.PICKUP -> pickupSaved?.let { return@call ApiResult.Success(it to null) }
            InspectionType.RETURN -> returnSaved?.let { sv -> returnResult?.let { return@call ApiResult.Success(sv to it) } }
        }
        val content = "INSPECTION|$type|${v.odometerKm}|${v.fuelPercent}|${v.warningLights}|${v.tpmsOk}|${v.doorsClosed}|${v.lightsOk}" +
            (if (v.tirePressures.isEmpty() && v.warningNames.isEmpty() && v.lampFaults.isEmpty()) ""
            else "|${v.tirePressures.entries.joinToString(",") { "${it.key}=${it.value.toInt()}" }}|${v.warningNames.joinToString(",")}|${v.lampFaults.joinToString(",")}")
        val hash = chainRef.append(content)
        val saved = InspectionSaved("I-%03d".format(++inspectionSeq), hash)
        log("inspection $type $v hash=$hash")
        when (type) {
            InspectionType.PICKUP -> {
                pickupSaved = saved
                pickup = v to clock()
                lastTripOdo = v.odometerKm
                status = RentalStatus.DRIVING
                returnAt = clock() + returnAfterDepartureMs
                addTimeline("출발 전 검수 완료 (주행거리 ${v.odometerKm.grouped()} km)")
                ApiResult.Success(saved to null)
            }
            InspectionType.RETURN -> {
                val p = pickup?.first ?: return@call ApiResult.Error(ErrorCodes.CONFLICT, 409, "인수 검수 없음")
                val res = buildReturnResult(p, v)
                returnResult = res; returnSaved = saved
                lastTripOdo = v.odometerKm
                addTimeline("반납 검수 완료 (정산 ${res.settlement.total.grouped()}원)")
                ApiResult.Success(saved to res)
            }
        }
    }

    private fun isOpen(v: VehicleSnapshot) = !v.doorsClosed || v.openItems.isNotEmpty()
    private fun openText(v: VehicleSnapshot) = if (isOpen(v)) "열림 있음" else "닫힘"

    private fun warnText(v: VehicleSnapshot) = if (v.warningNames.isEmpty()) "${v.warningLights}개" else v.warningNames.joinToString(" · ")
    private fun tireText(v: VehicleSnapshot) =
        if (v.tirePressures.isEmpty()) (if (v.tpmsOk) "정상" else "점검 필요") else "${v.tirePressures.values.min().toInt()} kPa"
    private fun lampText(v: VehicleSnapshot) = if (v.lightsOk) "정상" else "이상 있음"

    private fun buildReturnResult(p: VehicleSnapshot, r: VehicleSnapshot): ReturnInspectionResult {
        val driven = r.odometerKm - p.odometerKm
        val fee = FeeCalculator.fee(driven)
        fun ok(b: Boolean) = if (b) "정상" else "점검 필요"
        fun same(a: Any, b: Any) = if (a == b) "동일" else "변경됨"
        val compare = listOf(
            CompareRow("주행거리", "${p.odometerKm.grouped()} km", "${r.odometerKm.grouped()} km", "+${driven.grouped()} km"),
            CompareRow("연료", "${p.fuelPercent}%", "${r.fuelPercent}%", "기록만"),
            CompareRow("경고등", warnText(p), warnText(r), same(p.warningLights, r.warningLights)),
            CompareRow("타이어 공기압(TPMS)", tireText(p), tireText(r),
                if (r.tirePressures.values.any { it < SnapshotBlockers.LOW_TIRE_KPA }) "공기압 낮음" else same(p.tpmsOk, r.tpmsOk)),
            CompareRow("문·창문", openText(p), openText(r), if (isOpen(r)) "열림 있음" else same(isOpen(p), isOpen(r))),
            CompareRow("등화", lampText(p), lampText(r), same(p.lightsOk, r.lightsOk)),
        )
        val lines = listOf(
            SettlementLine("주행 요금", fee, "(${driven}km − ${FeeCalculator.FREE_KM}km) × ${FeeCalculator.PRICE_PER_KM}원"),
            SettlementLine("연료 정산", 0, "정산 없음"),
            SettlementLine("경고 대응 비용", 0, "업체 부담"),
        )
        return ReturnInspectionResult(compare, Settlement(lines, fee))
    }

    suspend fun postContactRequest(
        vehicleNo: String? = null, rentalId: String? = null, eventId: String? = null, reason: ContactReason,
    ): ApiResult<Unit> = call {
        val req = ContactRequest(vehicleNo, rentalId, eventId, reason)
        _contactLog += req
        log("contact-request $req")
        if (rentalId != null || eventId != null)
            addTimeline("업체에 전화 요청 (${reason.name})")
        ApiResult.Success(Unit)
    }

    // ---- B-03 ----------------------------------------------------------------------------
    suspend fun postTripLog(rentalId: String, recordedAt: Long, odometerKm: Int, fuelPercent: Int): ApiResult<Unit> = call {
        if (!rentalOk(rentalId)) return@call notFound()
        lastTripOdo = odometerKm
        tripLogs += Triple(recordedAt, odometerKm, fuelPercent)
        log("[TRIP] $rentalId ${recordedAt.hm()} odo=${odometerKm}km fuel=$fuelPercent%")
        ApiResult.Success(Unit)
    }

    // ---- B-04 / B-09 ---------------------------------------------------------------------
    suspend fun postEvent(
        rentalId: String, type: EventType, level: EventLevel, occurredAt: String, odometerKm: Int,
    ): ApiResult<EventPosted> = call {
        if (!rentalOk(rentalId)) return@call notFound()
        val content = "$rentalId|$type|$level|$occurredAt|$odometerKm"
        val signature = Crypto.hmacSha256Hex(content, SECRET)
        val p = pickup?.first
        val newSince = when (type) {
            EventType.TPMS -> p?.tpmsOk ?: true
            EventType.BRAKE -> (p?.warningLights ?: 0) == 0
        }
        val id = "E-%03d".format(++eventSeq)
        chainRef.append("EVENT|$id|$content")
        val emergency = level == EventLevel.EMERGENCY
        events += EventState(
            EventRecord(id, type, level, occurredAt, odometerKm, signature, newSince,
                notifyStatus = if (emergency) "SENT" else "NONE", ackStatus = "PENDING", dispatchStatus = "PENDING"),
            createdAt = clock(),
        )
        addTimeline("${lightLabel(type)} 경고등 켜짐 · 주행거리 ${odometerKm.grouped()} km")
        addTimeline("업체에 자동 알림 전송 · 카카오톡으로 고객님께도 기록 전송")
        if (!emergency) addTimeline("업체 응답: 지정 정비소 방문 시 수리비는 업체 부담")
        log("event $id $content sig=$signature")
        ApiResult.Success(EventPosted(id, signature, newSince))
    }

    suspend fun getEvents(rentalId: String): ApiResult<List<TimelineItem>> = call {
        if (!rentalOk(rentalId)) notFound() else { events.forEach(::progress); ApiResult.Success(timelineLog.toList()) }
    }

    /** F-05 entry: the vehicle stopped after a warning. Logged once per event. */
    suspend fun postStopDetected(rentalId: String): ApiResult<Unit> = call {
        if (!rentalOk(rentalId)) return@call notFound()
        val last = events.lastOrNull()?.record?.eventId
        if (last != null && stopLoggedFor != last) {
            stopLoggedFor = last
            addTimeline("정차 감지 · 대응 방법 안내 시작")
        }
        ApiResult.Success(Unit)
    }

    suspend fun getEventRecords(rentalId: String): ApiResult<List<EventRecord>> = call {
        if (!rentalOk(rentalId)) notFound() else { events.forEach(::progress); ApiResult.Success(events.map { it.record }) }
    }

    suspend fun getEvent(eventId: String): ApiResult<EventStatus> = call {
        val e = events.firstOrNull { it.record.eventId == eventId } ?: return@call notFound()
        progress(e)
        ApiResult.Success(EventStatus(e.record.notifyStatus, e.record.ackStatus, e.record.dispatchStatus))
    }

    // ---- B-05 ----------------------------------------------------------------------------
    suspend fun postAction(eventId: String, action: ActionType): ApiResult<ActionResult> = call {
        val e = events.firstOrNull { it.record.eventId == eventId } ?: return@call notFound()
        val hash = chainRef.append("ACTION|$eventId|$action")
        log("action $eventId $action hash=$hash")
        when (action) {
            ActionType.REPAIR_SHOP -> {
                addTimeline("정비소 안내 요청 · $SHOP_NAME · 업체 부담")
                ApiResult.Success(ActionResult(action, SHOP_NAME, SHOP_DISTANCE_KM, "COMPANY"))
            }
            ActionType.CALL_REQUEST -> {
                _contactLog += ContactRequest(null, DemoConstants.RENTAL_ID, eventId, ContactReason.CALL_REQUEST)
                addTimeline("업체에 전화 요청")
                ApiResult.Success(ActionResult(action))
            }
            ActionType.CONTINUE -> {
                addTimeline("계속 운행 선택")
                ApiResult.Success(ActionResult(action))
            }
        }
    }

    // ---- B-06 ----------------------------------------------------------------------------
    suspend fun getReturnPreview(rentalId: String, odometerKm: Int, fuelPercent: Int = 0): ApiResult<ReturnPreview> = call {
        if (!rentalOk(rentalId)) return@call notFound()
        val base = pickup?.first?.odometerKm ?: odometerKm
        val driven = maxOf(0, odometerKm - base)
        ApiResult.Success(
            ReturnPreview(
                drivenKm = driven, freeKm = FeeCalculator.FREE_KM, pricePerKm = FeeCalculator.PRICE_PER_KM,
                estimatedFee = FeeCalculator.fee(driven), returnPlace = RETURN_PLACE, returnAt = returnAt,
                fuelPercent = fuelPercent,
            )
        )
    }

    // ---- B-08 ----------------------------------------------------------------------------
    suspend fun health(): ApiResult<String> = call { ApiResult.Success("OK") }

    // ---- B-10 ----------------------------------------------------------------------------
    suspend fun complete(rentalId: String): ApiResult<CompleteResult> = call {
        if (!rentalOk(rentalId)) return@call notFound()
        complete?.let { return@call ApiResult.Success(it) } // already completed (repeat after timeout)
        if (status != RentalStatus.DRIVING) return@call ApiResult.Error(ErrorCodes.CONFLICT, 409, "대여 상태가 DRIVING이 아님")
        val p = pickup?.first
        val res = returnResult
        val driven = if (res != null && p != null) {
            // derive from compare of last RETURN inspection
            (lastTripOdo ?: p.odometerKm) - p.odometerKm
        } else maxOf(0, (lastTripOdo ?: 0) - (p?.odometerKm ?: 0))
        val total = res?.settlement?.total ?: FeeCalculator.fee(driven)
        status = RentalStatus.RETURNED
        val out = CompleteResult(driven, total, chainRef.verifyCode())
        complete = out
        addTimeline("반납 완료")
        log("complete $out")
        ApiResult.Success(out)
    }

    suspend fun getStatement(rentalId: String): ApiResult<Statement> = call {
        if (!rentalOk(rentalId)) return@call notFound()
        val c = complete ?: return@call ApiResult.Error(ErrorCodes.CONFLICT, 409, "아직 반납 전")
        val r = returnResult ?: ReturnInspectionResult(emptyList(), Settlement(emptyList(), c.totalFee))
        val rt = rental()
        ApiResult.Success(Statement(rt.rentalId, rt.customerName, rt.vehicle, rt.company, r.compare, r.settlement,
            timelineLog.toList(), c.drivenKm, c.totalFee, c.verifyCode))
    }

    // ---- admin / demo helpers ------------------------------------------------------------
    suspend fun ack(eventId: String): ApiResult<Unit> = call {
        val e = events.firstOrNull { it.record.eventId == eventId } ?: return@call notFound()
        if (e.record.ackStatus != "CONFIRMED") {
            e.record = e.record.copy(ackStatus = "CONFIRMED")
            addTimeline("업체가 긴급 알림을 확인했어요")
        }
        ApiResult.Success(Unit)
    }

    suspend fun dispatch(eventId: String): ApiResult<Unit> = call {
        val e = events.firstOrNull { it.record.eventId == eventId } ?: return@call notFound()
        if (e.record.dispatchStatus != "ACCEPTED") {
            e.record = e.record.copy(dispatchStatus = "ACCEPTED")
            addTimeline("업체가 출동을 수락했어요")
        }
        ApiResult.Success(Unit)
    }

    suspend fun unlock(rentalId: String): ApiResult<Unit> = call {
        failCount = 0
        ApiResult.Success(Unit)
    }

    /** Test/demo accessor for the hash chain tip. */
    val lastHash: String get() = chainRef.last
}
