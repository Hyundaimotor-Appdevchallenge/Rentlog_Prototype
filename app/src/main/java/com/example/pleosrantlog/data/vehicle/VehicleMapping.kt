package com.example.pleosrantlog.data.vehicle

import com.example.pleosrantlog.data.model.VehicleSnapshot

/** Departure-blocking thresholds in one place (also referenced by tests/scripts). */
object SnapshotBlockers {
    const val LOW_TIRE_KPA = 180f
    const val LOW_BATTERY_PERCENT = 20
}

/** Pure mapping helpers between Pleos Vehicle SDK values and app values (unit-tested). Telltales/gears use enum names. */
object VehicleMapping {
    /** Telltales that count as a "warning light". Indicator-only ones (turn signals, headlamps, auto hold, parking brake...) are excluded. */
    val WARNING_TELLTALES = setOf(
        "TPMS", "ABS", "ESC", "AIRBAG", "MASTER_WARNING", "MDPS", "EV_SYSTEM", "ENGINE",
        "ENGINE_OIL_PRESSURE", "LOW_VOLT_BATTERY",
    )

    /** telltales: (type name, isOn). */
    fun warningLightCount(telltales: List<Pair<String, Boolean>>): Int =
        telltales.count { it.second && it.first in WARNING_TELLTALES }

    /** Fuel column: fuel percent -> battery SOC -> simulation. Fuel 0/null/failed falls back to SOC. */
    fun fuelOrSoc(fuel: Int?, soc: Int?, simulated: Int): Int =
        (fuel?.takeIf { it > 0 } ?: soc ?: simulated).coerceIn(0, 100)

    /** Door positions (0 closed, non-zero open). Failed reads (null) are ignored; null when nothing could be read. */
    fun doorsClosed(positions: List<Int?>): Boolean? {
        val known = positions.filterNotNull()
        return if (known.isEmpty()) null else known.all { it == 0 }
    }

    /** Labels in [DoorValue] read order: ROW_1_LEFT, ROW_1_RIGHT, ROW_2_LEFT, ROW_2_RIGHT, HOOD, REAR. */
    val DOOR_LABELS = listOf("앞 왼쪽 문", "앞 오른쪽 문", "뒤 왼쪽 문", "뒤 오른쪽 문", "프렁크", "트렁크")
    /** Labels in [WindowValue] read order: ROW_1_LEFT, ROW_1_RIGHT, ROW_2_LEFT, ROW_2_RIGHT. */
    val WINDOW_LABELS = listOf("앞 왼쪽 창문", "앞 오른쪽 창문", "뒤 왼쪽 창문", "뒤 오른쪽 창문")
    val OPENABLE_LABELS = DOOR_LABELS + WINDOW_LABELS

    /**
     * Open items from door positions (0 closed, non-zero open; order of [DOOR_LABELS]) and window positions
     * (0 closed, >0 open; order of [WINDOW_LABELS]). Failed/unsupported reads (null) are ignored, never "open".
     * Returns null when nothing at all could be read.
     */
    fun openItems(doors: List<Int?>, windows: List<Int?>): List<String>? {
        if (doors.all { it == null } && windows.all { it == null }) return null
        val d = doors.zip(DOOR_LABELS).filter { (p, _) -> p != null && p != 0 }.map { it.second }
        val w = windows.zip(WINDOW_LABELS).filter { (p, _) -> p != null && p > 0 }.map { it.second }
        return d + w
    }

    /** Union of two open-item lists in canonical order (doors, frunk, trunk, windows; unknown labels last). */
    fun mergeOpen(a: Collection<String>, b: Collection<String>): List<String> {
        val all = (a + b).toSet()
        return OPENABLE_LABELS.filter { it in all } + all.filter { it !in OPENABLE_LABELS }
    }

    val TIRE_LABELS = listOf("앞 왼쪽", "앞 오른쪽", "뒤 왼쪽", "뒤 오른쪽")

    /** SDK telltale enum name -> Korean display name for safety warning lights (TPMS is handled as a tire problem). */
    val WARNING_NAMES = linkedMapOf(
        "ABS" to "ABS", "ESC" to "ESC", "AIRBAG" to "에어백", "MASTER_WARNING" to "경고등", "MDPS" to "MDPS",
        "EV_SYSTEM" to "EV 시스템", "ENGINE" to "엔진", "ENGINE_OIL_PRESSURE" to "엔진 오일 압력", "LOW_VOLT_BATTERY" to "보조 배터리",
    )
    /** Lamp telltales used as the lamp-fault proxy; kept out of the warning-light count. */
    val LAMP_NAMES = linkedMapOf("LED_HEADLAMP" to "LED 헤드램프", "AFS" to "AFS")

    /** Lit safety warning lights as Korean names, in canonical order. */
    fun warningNames(telltales: List<Pair<String, Boolean>>): List<String> {
        val on = telltales.filter { it.second }.map { it.first }.toSet()
        return WARNING_NAMES.filterKeys { it in on }.values.toList()
    }

    /** Lit lamp-fault telltales (LED_HEADLAMP, AFS) as Korean names. */
    fun lampFaults(telltales: List<Pair<String, Boolean>>): List<String> {
        val on = telltales.filter { it.second }.map { it.first }.toSet()
        return LAMP_NAMES.filterKeys { it in on }.values.toList()
    }

    /** Low wheels ("앞 왼쪽 타이어 공기압 낮음 (150 kPa)"); TPMS telltale on without a low reading -> "타이어 공기압 경고등". */
    fun lowTires(pressures: Map<String, Float>, tpmsOn: Boolean, thresholdKpa: Float = SnapshotBlockers.LOW_TIRE_KPA): List<String> {
        val low = (TIRE_LABELS.filter { it in pressures } + pressures.keys.filter { it !in TIRE_LABELS })
            .filter { pressures.getValue(it) < thresholdKpa }
            .map { "$it 타이어 공기압 낮음 (${pressures.getValue(it).toInt()} kPa)" }
        return if (low.isEmpty() && tpmsOn) listOf("타이어 공기압 경고등") else low
    }

    /** LightState enum name -> text: OFF 꺼짐, ON 켜짐, DAYTIME_RUNNING 자동, else "" (unknown). */
    fun headlampText(stateName: String?): String = when (stateName) {
        "OFF" -> "꺼짐"; "ON" -> "켜짐"; "DAYTIME_RUNNING" -> "자동"; else -> ""
    }

    fun batteryLow(percent: Int, threshold: Int = SnapshotBlockers.LOW_BATTERY_PERCENT): Boolean = percent < threshold

    /** All reasons departure is blocked, in order: open items, tires, warning lights, lamps, battery. */
    fun blockers(s: VehicleSnapshot): List<String> =
        s.openItems.map { "$it 열림" } +
            lowTires(s.tirePressures, !s.tpmsOk) +
            s.warningNames.map { "$it 경고등" } +
            s.lampFaults.map { "$it 경고" } +
            (if (batteryLow(s.fuelPercent)) listOf("배터리 ${s.fuelPercent}%") else emptyList())

    /** Gear -> stopped flag: PARK = true, DRIVE/REVERSE = false (moving), NEUTRAL/UNKNOWN/others = null (no change). */
    fun gearStopped(gearName: String): Boolean? = when (gearName) {
        "GEAR_PARK", "PARK" -> true
        "GEAR_DRIVE", "DRIVE", "GEAR_REVERSE", "REVERSE" -> false
        else -> null
    }

    /**
     * Edge detector for a rising telltale: emits once when it turns on, re-arms when it turns off
     * (so TPMS/ABS can fire again after the console clears them).
     */
    class Latch {
        private var wasOn = false
        /** @return true when [on] is a fresh off->on transition. */
        fun rising(on: Boolean): Boolean { val fire = on && !wasOn; wasOn = on; return fire }
    }
}
