package com.example.pleosrantlog.data.model

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    /** B-08 error format { code, message }. remaining only for 401 verify failures. */
    data class Error(
        val code: String,
        val http: Int,
        val message: String = "",
        val remaining: Int? = null,
    ) : ApiResult<Nothing>
}

val ApiResult.Error.isNetwork: Boolean get() = code == ErrorCodes.NETWORK

object ErrorCodes {
    const val NETWORK = "NETWORK"
    const val NO_RESERVATION = "NO_RESERVATION"
    const val LOCKED = "LOCKED"
    const val VERIFY_FAILED = "VERIFY_FAILED"
    const val NOT_FOUND = "NOT_FOUND"
    const val CONFLICT = "CONFLICT"
}

enum class EventType { TPMS, BRAKE }
enum class EventLevel { CAUTION, EMERGENCY }
enum class InspectionType { PICKUP, RETURN }
enum class ActionType { REPAIR_SHOP, CALL_REQUEST, CONTINUE }
enum class ContactReason { PICKUP, NO_RESERVATION, LOCKED, EMERGENCY, CALL_REQUEST }
enum class RentalStatus { RESERVED, VERIFIED, DRIVING, RETURNED }

data class Company(val name: String, val phone: String)

/** B-01 response. startAt/returnAt are epoch millis. */
data class Rental(
    val rentalId: String,
    val customerName: String,
    val vehicle: String,
    val company: Company,
    val startAt: Long,
    val returnAt: Long,
)

data class VehicleWarning(val type: EventType, val level: EventLevel)

/** Values read from the Vehicle SDK (or simulated). Same shape as B-02 request body. */
data class VehicleSnapshot(
    val odometerKm: Int,
    val fuelPercent: Int,
    val warningLights: Int,
    val tpmsOk: Boolean,
    val doorsClosed: Boolean,
    val lightsOk: Boolean,
    /** Open doors/frunk/trunk/windows as Korean labels (e.g. "트렁크"). Empty = all closed (or unknown). */
    val openItems: List<String> = emptyList(),
    /** Tire pressure per wheel in kPa (labels "앞 왼쪽"...). Empty = unknown. */
    val tirePressures: Map<String, Float> = emptyMap(),
    /** Korean names of lit safety warning lights (ABS, 에어백...). Lamp telltales and TPMS are not included. */
    val warningNames: List<String> = emptyList(),
    /** Lamp faults (LED 헤드램프 / AFS telltales are the proxy: the SDK has no bulb-failure getter). */
    val lampFaults: List<String> = emptyList(),
    /** Low-beam state text: 꺼짐 / 켜짐 / 자동, "" = unknown. */
    val headlamp: String = "",
)

data class InspectionSaved(val inspectionId: String, val hash: String)

data class CompareRow(val item: String, val pickup: String, val ret: String, val result: String)
data class SettlementLine(val label: String, val amount: Int, val note: String = "")
data class Settlement(val lines: List<SettlementLine>, val total: Int)
data class ReturnInspectionResult(val compare: List<CompareRow>, val settlement: Settlement)

data class EventPosted(val eventId: String, val signature: String, val newSincePickup: Boolean)

data class EventRecord(
    val eventId: String,
    val type: EventType,
    val level: EventLevel,
    val occurredAt: String,
    val odometerKm: Int,
    val signature: String,
    val newSincePickup: Boolean,
    val notifyStatus: String,   // NONE | SENT
    val ackStatus: String,      // PENDING | CONFIRMED
    val dispatchStatus: String, // PENDING | ACCEPTED
)

data class EventStatus(val notifyStatus: String, val ackStatus: String, val dispatchStatus: String)

data class TimelineItem(val time: String, val text: String)

data class ActionResult(
    val action: ActionType,
    val shopName: String? = null,
    val distanceKm: Double? = null,
    val costBy: String? = null,
)

data class ReturnPlace(val name: String, val lat: Double, val lng: Double)

data class ReturnPreview(
    val drivenKm: Int,
    val freeKm: Int,
    val pricePerKm: Int,
    val estimatedFee: Int,
    val returnPlace: ReturnPlace,
    val returnAt: Long,
    val fuelPercent: Int,
)

data class CompleteResult(val drivenKm: Int, val totalFee: Int, val verifyCode: String)

data class Statement(
    val rentalId: String,
    val customerName: String,
    val vehicle: String,
    val company: Company,
    val compare: List<CompareRow>,
    val settlement: Settlement,
    val events: List<TimelineItem>,
    val drivenKm: Int,
    val totalFee: Int,
    val verifyCode: String,
)

data class ContactRequest(
    val vehicleNo: String?,
    val rentalId: String?,
    val eventId: String?,
    val reason: ContactReason,
)
