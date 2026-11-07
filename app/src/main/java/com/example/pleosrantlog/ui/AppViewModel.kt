package com.example.pleosrantlog.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pleosrantlog.data.hm
import com.example.pleosrantlog.data.model.*
import com.example.pleosrantlog.data.vehicle.VehicleMapping
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class AppViewModel(
    private val deps: AppDeps,
    private val config: AppConfig = AppConfig(),
) : ViewModel() {

    private val _state = MutableStateFlow(UiState(vehicleNo = config.vehicleNo))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val backend get() = deps.backend
    private val vehicle get() = deps.vehicle
    private val now get() = deps.clock()

    /** rentalId kept in memory only; deleted on [finishAndRestart]. */
    private var rentalId: String? = null
    private var departed = false
    private var stoppedNow = false
    private var headingToReturn = false
    private var snoozeUntil = 0L
    private var screenBeforeDisconnect: Screen = Screen.StartVerify
    /** The interrupted API-calling action; re-run by [retry] after the health check succeeds. */
    private var pendingAction: (() -> Unit)? = null

    private var loopJob: Job? = null
    private var tripJob: Job? = null
    private var pollJob: Job? = null

    private val screen get() = _state.value.screen

    init {
        backend.returnAfterDepartureMs = config.demoReturnAfterDepartureMs
        viewModelScope.launch { vehicle.warnings.collect { onWarning(it) } }
        viewModelScope.launch {
            vehicle.stopped.collect { s ->
                val was = stoppedNow
                stoppedNow = s
                if (s && !was && (screen == Screen.CautionWarning || (screen == Screen.Driving && _state.value.warning != null))) goStopped()
                else if (s && !was && headingToReturn && (screen == Screen.ReturnNotice || screen == Screen.Driving)) startReturnInspection()
            }
        }
        // F-02: re-read the vehicle every second while the pickup inspection is shown (cancelled on leaving it)
        viewModelScope.launch {
            _state.map { it.screen == Screen.PickupInspection }.distinctUntilChanged().collectLatest { onF02 ->
                while (onF02) {
                    delay(config.inspectionRefreshMs)
                    val snap = vehicle.snapshot()
                    _state.update { if (it.screen == Screen.PickupInspection) it.copy(inspection = snap) else it }
                }
            }
        }
        loadToday()
    }

    // ------------------------------------------------------------------ helpers
    private suspend fun <T> api(block: suspend () -> ApiResult<T>): ApiResult<T> {
        // Real connectivity loss (airplane mode / no network): behave exactly like a request timeout.
        if (!deps.network.isOnline()) {
            delay(config.requestTimeoutMs)
            return ApiResult.Error(ErrorCodes.NETWORK, 0, "offline")
        }
        return withTimeoutOrNull(config.requestTimeoutMs) { block() }
            ?: ApiResult.Error(ErrorCodes.NETWORK, 0, "timeout")
    }

    /** Network failure -> F-08 DISCONNECTED; [again] re-invokes the interrupted action once connectivity is back. */
    private fun ApiResult.Error.handleNetwork(again: (() -> Unit)? = null): Boolean {
        if (!isNetwork) return false
        showDisconnected(again)
        return true
    }

    private fun showDisconnected(again: (() -> Unit)?) {
        val cur = screen
        if (!(cur is Screen.ErrorState && cur.kind == ErrorKind.DISCONNECTED)) screenBeforeDisconnect = cur
        pendingAction = again
        _state.update { it.copy(screen = Screen.ErrorState(ErrorKind.DISCONNECTED), loading = false) }
    }

    private fun setScreen(s: Screen) = _state.update { it.copy(screen = s, loading = false) }
    private fun toast(msg: String) = _state.update { it.copy(message = msg) }
    fun consumeMessage() = _state.update { it.copy(message = null) }

    private val drivingPhase get() = screen is Screen.Driving || screen is Screen.CautionWarning ||
        screen is Screen.StoppedResponse || screen is Screen.ReturnNotice || screen is Screen.Emergency

    // ------------------------------------------------------------------ F-01 / F-08
    /** GET /api/rentals/today. Also the "다시 시도" of the NO_RESERVATION screen. */
    fun loadToday() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            when (val r = api { backend.getTodayRental(config.vehicleNo) }) {
                is ApiResult.Success -> {
                    rentalId = r.data.rentalId
                    _state.update {
                        it.copy(rental = r.data, rentalId = r.data.rentalId, loading = false, screen = Screen.StartVerify,
                            codeInput = "", verifyError = null, contactRequested = false)
                    }
                }
                is ApiResult.Error -> {
                    if (r.handleNetwork(::loadToday)) return@launch
                    _state.update { it.copy(loading = false, screen = Screen.ErrorState(ErrorKind.NO_RESERVATION), contactRequested = false) }
                }
            }
        }
    }

    fun onDigit(d: Char) {
        val s = _state.value
        if (s.screen != Screen.StartVerify || s.loading || s.codeInput.length >= 4) return
        _state.update { it.copy(codeInput = it.codeInput + d, verifyError = null) }
        if (_state.value.codeInput.length == 4) submitCode()
    }

    fun onBackspace() = _state.update { it.copy(codeInput = it.codeInput.dropLast(1), verifyError = null) }

    /** POST /verify with the 4 entered digits. Auto-called on the 4th digit. */
    fun submitCode() {
        val id = rentalId ?: return
        val code = _state.value.codeInput
        if (code.length != 4 || _state.value.loading) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            when (val r = api { backend.verify(id, code) }) {
                is ApiResult.Success -> {
                    val snap = vehicle.snapshot()
                    _state.update { it.copy(loading = false, screen = Screen.PickupInspection, inspection = snap, verifyError = null, contactRequested = false) }
                }
                is ApiResult.Error -> {
                    if (r.handleNetwork(::submitCode)) return@launch
                    if (r.http == 423) {
                        _state.update { it.copy(loading = false, codeInput = "", screen = Screen.ErrorState(ErrorKind.LOCKED), contactRequested = false) }
                    } else {
                        _state.update {
                            it.copy(loading = false, codeInput = "", remainingAttempts = r.remaining,
                                verifyError = "번호가 달라요 (남은 횟수 ${r.remaining}회)")
                        }
                    }
                }
            }
        }
    }

    /** "업체에 전화 요청" on F-01 / F-02 / F-08. */
    fun requestCompanyCall(reason: ContactReason) {
        viewModelScope.launch {
            val r = api {
                if (reason == ContactReason.PICKUP) backend.postContactRequest(rentalId = rentalId, reason = reason)
                else backend.postContactRequest(vehicleNo = config.vehicleNo, reason = reason)
            }
            if (r is ApiResult.Error && r.handleNetwork { requestCompanyCall(reason) }) return@launch
            _state.update { it.copy(contactRequested = true) }
        }
    }

    /** F-08 '다시 시도'. NO_RESERVATION -> reload; DISCONNECTED -> health check then previous screen; LOCKED -> nothing. */
    fun retry() {
        val kind = (screen as? Screen.ErrorState)?.kind ?: return
        when (kind) {
            ErrorKind.NO_RESERVATION -> loadToday()
            ErrorKind.LOCKED -> viewModelScope.launch {
                _state.update { it.copy(loading = true) }
                // locked until admin unlock; retry simply re-opens F-01 if unlocked
                if (!backend.isLocked) _state.update { it.copy(loading = false, screen = Screen.StartVerify, codeInput = "") }
                else _state.update { it.copy(loading = false) }
            }
            ErrorKind.DISCONNECTED -> if (!_state.value.loading) viewModelScope.launch {
                _state.update { it.copy(loading = true) }
                val r = api { backend.health() }
                if (r is ApiResult.Success) {
                    _state.update { it.copy(loading = false) }
                    val again = pendingAction
                    pendingAction = null
                    if (again != null) { setScreen(screenBeforeDisconnect); again() }
                    else if (screenBeforeDisconnect == Screen.StartVerify || rentalId == null) loadToday()
                    else setScreen(screenBeforeDisconnect)
                } else _state.update { it.copy(loading = false) }
            }
        }
    }

    // ------------------------------------------------------------------ F-02
    fun refreshInspection() {
        viewModelScope.launch { val s = vehicle.snapshot(); _state.update { it.copy(inspection = s) } }
    }

    /** '확인, 출발하기': POST PICKUP; success -> F-03. Blocked while [VehicleMapping.blockers] is non-empty. */
    fun confirmDeparture() {
        val id = rentalId ?: return
        if (_state.value.inspection?.let { VehicleMapping.blockers(it).isNotEmpty() } == true) return toast(OPEN_BLOCK_MESSAGE)
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val snap = vehicle.snapshot()
            if (VehicleMapping.blockers(snap).isNotEmpty()) {
                _state.update { it.copy(inspection = snap, loading = false) }
                toast(OPEN_BLOCK_MESSAGE)
                return@launch
            }
            when (val r = api { backend.postInspection(id, InspectionType.PICKUP, snap) }) {
                is ApiResult.Success -> {
                    departed = true
                    vehicle.setMoving(true)
                    // the backend re-based returnAt on departure (demo: now + 2h01m)
                    (api { backend.getTodayRental(config.vehicleNo) } as? ApiResult.Success)?.let { fresh ->
                        _state.update { it.copy(rental = fresh.data) }
                    }
                    val rental = _state.value.rental
                    _state.update {
                        it.copy(pickup = snap, inspection = snap, live = snap, drivenKm = 0, loading = false,
                            returnRemainingMs = (rental?.returnAt ?: now) - now, screen = Screen.Driving)
                    }
                    startLoops()
                }
                is ApiResult.Error -> {
                    if (r.handleNetwork(::confirmDeparture)) return@launch
                    _state.update { it.copy(loading = false) }
                    toast("검수 저장에 실패했어요. 다시 시도해 주세요")
                }
            }
        }
    }

    // ------------------------------------------------------------------ F-03 timers
    private fun startLoops() {
        loopJob?.cancel(); tripJob?.cancel()
        loopJob = viewModelScope.launch {
            while (true) {
                refreshLive()
                checkReturnNotice()
                delay(config.refreshIntervalMs)
            }
        }
        tripJob = viewModelScope.launch {
            while (true) {
                delay(config.tripLogIntervalMs)
                val id = rentalId ?: continue
                val snap = vehicle.snapshot()
                // failure just waits for the next tick (no retransmit)
                val r = api { backend.postTripLog(id, now, snap.odometerKm, snap.fuelPercent) }
                if (r is ApiResult.Success) _state.update { it.copy(tripLogCount = it.tripLogCount + 1) }
            }
        }
    }

    private fun stopLoops() { loopJob?.cancel(); tripJob?.cancel(); pollJob?.cancel() }

    private suspend fun refreshLive() {
        val snap = vehicle.snapshot()
        val s = _state.value
        val driven = snap.odometerKm - (s.pickup?.odometerKm ?: snap.odometerKm)
        val remaining = (s.rental?.returnAt ?: now) - now
        _state.update { it.copy(live = snap, drivenKm = driven, returnRemainingMs = remaining) }
    }

    private suspend fun checkReturnNotice() {
        val s = _state.value
        if (s.screen != Screen.Driving) return
        val returnAt = s.rental?.returnAt ?: return
        if (returnAt - now <= config.returnNoticeLeadMs && now >= snoozeUntil) showReturnNotice()
    }

    // ------------------------------------------------------------------ F-04 / F-10
    private fun onWarning(w: VehicleWarning) {
        if (!departed || !drivingPhase || screen == Screen.Emergency) return
        val id = rentalId ?: return
        viewModelScope.launch {
            val snap = vehicle.snapshot()
            val at = now.hm()
            when (val r = api { backend.postEvent(id, w.type, w.level, at, snap.odometerKm) }) {
                is ApiResult.Success -> {
                    val emergency = w.level == EventLevel.EMERGENCY
                    _state.update {
                        it.copy(
                            warning = w, currentEvent = r.data, currentEventType = w.type, currentEventLevel = w.level,
                            eventOccurredAt = at, eventOdometerKm = snap.odometerKm, repairShop = null, callRequested = false,
                            eventStatus = null, emergencyCallRequested = false,
                            screen = if (emergency) Screen.Emergency else Screen.CautionWarning, loading = false,
                        )
                    }
                    deps.speaker.speak(VoiceTexts.forWarning(w))
                    if (emergency) startEventPolling()
                }
                is ApiResult.Error -> r.handleNetwork { onWarning(w) }
            }
        }
    }

    private fun startEventPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                val e = _state.value.currentEvent
                if (e != null) {
                    val r = api { backend.getEvent(e.eventId) }
                    if (r is ApiResult.Success) _state.update { it.copy(eventStatus = r.data) }
                }
                delay(config.eventPollMs)
            }
        }
    }

    /** F-10 '업체에 긴급 전화 요청'. */
    fun requestEmergencyCall() {
        val e = _state.value.currentEvent ?: return
        viewModelScope.launch {
            val r = api { backend.postContactRequest(rentalId = rentalId, eventId = e.eventId, reason = ContactReason.EMERGENCY) }
            if (r is ApiResult.Error && r.handleNetwork(::requestEmergencyCall)) return@launch
            _state.update { it.copy(emergencyCallRequested = true) }
        }
    }

    /** Leave F-10 and go on (demo). */
    fun dismissEmergency() {
        pollJob?.cancel()
        _state.update { it.copy(warning = null) }
        vehicle.clearWarnings()
        setScreen(Screen.Driving)
    }

    /** F-04 -> back to driving screen (warning stays recorded). */
    fun dismissCaution() = setScreen(Screen.Driving)

    // ------------------------------------------------------------------ F-05
    private fun goStopped() {
        val id = rentalId ?: return
        viewModelScope.launch {
            api { backend.postStopDetected(id) }
            val r = api { backend.getEvents(id) }
            if (r is ApiResult.Success) _state.update { it.copy(timeline = r.data, screen = Screen.StoppedResponse, repairShop = null, callRequested = false) }
            else if (r is ApiResult.Error) r.handleNetwork(::goStopped)
        }
    }

    /** Manually open F-05 from F-04 (button or stop detection). */
    fun openStoppedResponse() = goStopped()

    private fun refreshTimeline() {
        val id = rentalId ?: return
        viewModelScope.launch {
            val r = api { backend.getEvents(id) }
            if (r is ApiResult.Success) _state.update { it.copy(timeline = r.data) }
        }
    }

    fun chooseRepairShop() = doAction(ActionType.REPAIR_SHOP)
    fun chooseCallRequest() = doAction(ActionType.CALL_REQUEST)
    fun chooseContinue() = doAction(ActionType.CONTINUE)

    private fun doAction(a: ActionType) {
        val e = _state.value.currentEvent ?: return
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            when (val r = api { backend.postAction(e.eventId, a) }) {
                is ApiResult.Success -> {
                    when (a) {
                        ActionType.REPAIR_SHOP -> _state.update { it.copy(repairShop = r.data, loading = false) }
                        ActionType.CALL_REQUEST -> _state.update { it.copy(callRequested = true, loading = false) }
                        ActionType.CONTINUE -> {
                            vehicle.clearWarnings()
                            _state.update { it.copy(warning = null, loading = false) }
                            setScreen(Screen.Driving)
                        }
                    }
                    if (a != ActionType.CONTINUE) refreshTimeline()
                }
                is ApiResult.Error -> { if (!r.handleNetwork { doAction(a) }) { _state.update { it.copy(loading = false) }; toast("요청에 실패했어요") } }
            }
        }
    }

    // ------------------------------------------------------------------ F-06
    private suspend fun showReturnNotice() {
        val id = rentalId ?: return
        val snap = vehicle.snapshot()
        when (val r = api { backend.getReturnPreview(id, snap.odometerKm, snap.fuelPercent) }) {
            is ApiResult.Success -> _state.update { it.copy(returnPreview = r.data, screen = Screen.ReturnNotice) }
            is ApiResult.Error -> { r.handleNetwork() } // loop re-checks after retry restores the driving screen
        }
    }

    /** '나중에 안내' -> shown again 30 min later. */
    fun returnNoticeLater() {
        snoozeUntil = now + config.returnNoticeSnoozeMs
        setScreen(Screen.Driving)
    }

    /** '반납 장소로 안내' -> navigation app. */
    fun navigateToReturnPlace() {
        val p = _state.value.returnPreview?.returnPlace ?: return
        headingToReturn = true
        val ok = deps.navi.navigate(p.name, p.lat, p.lng)
        if (!ok) toast("내비 앱을 열 수 없어 안내만 표시해요")
    }

    // ------------------------------------------------------------------ F-07
    /** Go to return inspection: re-read vehicle, POST RETURN, show compare + settlement. */
    fun startReturnInspection() {
        val id = rentalId ?: return
        if (screen == Screen.ReturnInspection || screen == Screen.ReturnComplete || screen == Screen.Statement) return
        headingToReturn = false
        snoozeUntil = Long.MAX_VALUE // never re-pop the return notice
        stopLoops()
        runReturnInspection(id)
    }

    private fun runReturnInspection(id: String) {
        viewModelScope.launch {
            vehicle.setMoving(false)
            _state.update { it.copy(screen = Screen.ReturnInspection, loading = true, returnResult = null) }
            val snap = vehicle.snapshot()
            _state.update { it.copy(inspection = snap) }
            when (val r = api { backend.postInspectionFull(id, InspectionType.RETURN, snap) }) {
                is ApiResult.Success -> _state.update { it.copy(returnResult = r.data.second, loading = false) }
                is ApiResult.Error -> { _state.update { it.copy(loading = false) }; if (!r.handleNetwork { runReturnInspection(id) }) toast("반납 검수에 실패했어요") }
            }
        }
    }

    /** '반납 확정 · 명세서 받기' -> POST complete -> F-11. */
    fun confirmReturn() {
        val id = rentalId ?: return
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            when (val r = api { backend.complete(id) }) {
                is ApiResult.Success -> _state.update { it.copy(completeResult = r.data, loading = false, screen = Screen.ReturnComplete) }
                is ApiResult.Error -> { _state.update { it.copy(loading = false) }; if (!r.handleNetwork(::confirmReturn)) toast("반납 확정에 실패했어요") }
            }
        }
    }

    // ------------------------------------------------------------------ F-11 / Statement
    fun openStatement() {
        val id = rentalId ?: return
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            when (val r = api { backend.getStatement(id) }) {
                is ApiResult.Success -> _state.update { it.copy(statement = r.data, loading = false, screen = Screen.Statement) }
                is ApiResult.Error -> { _state.update { it.copy(loading = false) }; if (!r.handleNetwork(::openStatement)) toast("명세서를 불러오지 못했어요") }
            }
        }
    }

    fun closeStatement() = setScreen(Screen.ReturnComplete)

    /** '닫기' on F-11: delete stored rentalId, reset, back to F-01 (a fresh demo rental is created). */
    fun finishAndRestart() {
        stopLoops()
        pendingAction = null
        rentalId = null; departed = false; stoppedNow = false; snoozeUntil = 0; headingToReturn = false
        vehicle.reset(); backend.reset()
        _state.value = UiState(vehicleNo = config.vehicleNo)
        loadToday()
    }

    // ------------------------------------------------------------------ demo controls (DemoControlSheet)
    fun showDemoControl(show: Boolean) = _state.update { it.copy(showDemoControl = show) }

    fun demoInjectTpmsCaution() = vehicle.injectWarning(VehicleWarning(EventType.TPMS, EventLevel.CAUTION))
    fun demoInjectBrakeEmergency() = vehicle.injectWarning(VehicleWarning(EventType.BRAKE, EventLevel.EMERGENCY))
    fun demoSetStopped(stopped: Boolean) = vehicle.setStopped(stopped)
    fun demoAddKm(km: Int) = vehicle.addDistance(km)
    fun demoArriveAtReturnPlace() = startReturnInspection()

    /** Return time becomes now+110 min (inside the 2 h window) and snooze is cleared. */
    fun demoForceReturnNotice() {
        backend.setReturnInMinutes(110)
        snoozeUntil = 0
        _state.update { s -> s.copy(rental = s.rental?.copy(returnAt = now + 110 * 60_000L)) }
    }

    fun demoAck() = demoEvent { backend.ack(it) }
    fun demoDispatch() = demoEvent { backend.dispatch(it) }
    private fun demoEvent(f: suspend (String) -> ApiResult<Unit>) {
        val e = _state.value.currentEvent ?: return toast("긴급 이벤트가 없어요")
        viewModelScope.launch { f(e.eventId) }
    }

    fun demoUnlock() { viewModelScope.launch { backend.unlock(rentalId ?: "R-001") ; toast("잠금을 해제했어요") } }
    fun demoNetworkDown(down: Boolean) { backend.forceNetworkDown(down); _state.update { it.copy(demoNetworkDown = down) } }
    fun demoNoReservation(on: Boolean) { backend.forceNoReservation(on); _state.update { it.copy(demoNoReservation = on) } }

    private companion object { const val OPEN_BLOCK_MESSAGE = "문제를 해결하면 출발할 수 있어요" }

    override fun onCleared() {
        stopLoops()
        vehicle.release()
    }
}

object VoiceTexts {
    fun forWarning(w: VehicleWarning): String = when {
        w.level == EventLevel.EMERGENCY -> "브레이크 긴급 경고가 발생했어요. 안전한 곳에 즉시 정차해 주세요. 업체에 알렸어요."
        w.type == EventType.TPMS -> "타이어 공기압 경고가 켜졌어요. 운행은 가능하지만 가까운 곳에서 확인해 주세요."
        else -> "경고가 발생했어요. 차량 상태를 확인해 주세요."
    }
}
