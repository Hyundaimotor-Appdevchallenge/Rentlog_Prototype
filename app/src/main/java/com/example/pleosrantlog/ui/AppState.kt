package com.example.pleosrantlog.ui

import com.example.pleosrantlog.data.backend.FakeBackend
import com.example.pleosrantlog.data.model.*
import com.example.pleosrantlog.data.navi.NaviRouter
import com.example.pleosrantlog.data.net.AlwaysOnline
import com.example.pleosrantlog.data.net.NetworkStatus
import com.example.pleosrantlog.data.vehicle.VehicleDataSource
import com.example.pleosrantlog.data.voice.Speaker

/** Dependencies of [AppViewModel] (manual DI, built in RentLogApp). */
class AppDeps(
    val backend: FakeBackend,
    val vehicle: VehicleDataSource,
    val speaker: Speaker,
    val navi: NaviRouter,
    val clock: () -> Long = { System.currentTimeMillis() },
    val network: NetworkStatus = AlwaysOnline,
)

data class AppConfig(
    val vehicleNo: String = "12가3456",
    val requestTimeoutMs: Long = 5_000,
    val tripLogIntervalMs: Long = 30_000,
    val refreshIntervalMs: Long = 2_000,
    /** F-02: vehicle re-read period so closing the trunk/doors in the console updates the screen live. */
    val inspectionRefreshMs: Long = 1_000,
    val eventPollMs: Long = 2_000,
    val returnNoticeLeadMs: Long = 2 * 3_600_000L,
    val returnNoticeSnoozeMs: Long = 30 * 60_000L,
    /** On departure the backend sets returnAt = now + this (2h01m), so F-06 (lead 2h) shows ~1 min later. */
    val demoReturnAfterDepartureMs: Long = FakeBackend.DEMO_RETURN_AFTER_DEPARTURE_MS,
)

enum class ErrorKind { NO_RESERVATION, LOCKED, DISCONNECTED }

/** One value per screen of the plan. */
sealed interface Screen {
    data object StartVerify : Screen                          // F-01
    data class ErrorState(val kind: ErrorKind) : Screen       // F-08
    data object PickupInspection : Screen                     // F-02
    data object Driving : Screen                              // F-03
    data object CautionWarning : Screen                       // F-04
    data object StoppedResponse : Screen                      // F-05
    data object ReturnNotice : Screen                         // F-06
    data object ReturnInspection : Screen                     // F-07
    data object Emergency : Screen                            // F-10
    data object ReturnComplete : Screen                       // F-11
    data object Statement : Screen
}

data class UiState(
    val screen: Screen = Screen.StartVerify,
    val loading: Boolean = false,
    val message: String? = null,
    val showDemoControl: Boolean = false,

    // F-01 / F-08
    val vehicleNo: String = "12가3456",
    val rentalId: String? = null,
    val rental: Rental? = null,
    val codeInput: String = "",
    val verifyError: String? = null,        // e.g. "번호가 달라요 (남은 횟수 4회)"
    val remainingAttempts: Int? = null,
    val contactRequested: Boolean = false,  // "업체에 전화 요청" sent (F-01/F-02/F-08)
    val demoNetworkDown: Boolean = false,
    val demoNoReservation: Boolean = false,

    // F-02 / F-07 (live reading)
    val inspection: VehicleSnapshot? = null,
    val pickup: VehicleSnapshot? = null,

    // F-03 (refreshed every refreshIntervalMs)
    val live: VehicleSnapshot? = null,
    val drivenKm: Int = 0,
    val returnRemainingMs: Long = 0,
    val tripLogCount: Int = 0,

    // F-04 / F-05 / F-10
    val warning: VehicleWarning? = null,
    val currentEvent: EventPosted? = null,
    val currentEventType: EventType? = null,
    val currentEventLevel: EventLevel? = null,
    val eventOccurredAt: String? = null,
    val eventOdometerKm: Int = 0,
    val timeline: List<TimelineItem> = emptyList(),
    val repairShop: ActionResult? = null,
    val callRequested: Boolean = false,
    val eventStatus: EventStatus? = null,       // polled every 5s on Emergency
    val emergencyCallRequested: Boolean = false,

    // F-06 / F-07 / F-11 / statement
    val returnPreview: ReturnPreview? = null,
    val returnResult: ReturnInspectionResult? = null,
    val completeResult: CompleteResult? = null,
    val statement: Statement? = null,
)
