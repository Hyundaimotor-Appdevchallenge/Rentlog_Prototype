package com.example.pleosrantlog.data.vehicle

import android.content.Context
import android.util.Log
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.WheelArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.WheelValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.telltale.TelltaleState
import ai.pleos.playground.vehicle.constant.telltale.TelltaleType
import com.example.pleosrantlog.data.model.EventLevel
import com.example.pleosrantlog.data.model.EventType
import com.example.pleosrantlog.data.model.VehicleSnapshot
import com.example.pleosrantlog.data.model.VehicleWarning
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Wraps Pleos Vehicle SDK 2.3.2. Every SDK access is guarded: if the Pleos vehicle service is missing
 * (plain emulator/phone) values silently come from the [sim] fallback, which also carries the demo injections.
 * Telltale subscription is privileged, so warnings are polled (1 s). While the SDK works, odometer/fuel/doors/warnings
 * come only from the Virtual Console; the simulator's auto drift is off (demo injectedKm still adds to the odometer).
 */
class PleosVehicleDataSource(
    context: Context,
    private val sim: SimulatedVehicleDataSource = SimulatedVehicleDataSource(),
    private val pollMs: Long = 1000,
) : VehicleDataSource {

    private val vehicle: Vehicle? = try {
        Vehicle(context.applicationContext).also { it.initialize() }
    } catch (t: Throwable) {
        Log.w(TAG, "Vehicle SDK unavailable, using simulation", t); null
    }

    @Volatile private var lastTelltaleLog: String? = null
    @Volatile private var realWorking = false
    override val isSimulated: Boolean get() = !realWorking

    private val loggedErrors = HashSet<String>()
    private fun logOnce(key: String, e: Exception) {
        if (synchronized(loggedErrors) { loggedErrors.add(key) }) Log.w(TAG, "SDK read '$key' failed", e)
    }

    private suspend fun <T : Any> sdk(block: ((T) -> Unit, (Exception) -> Unit) -> Unit): T? =
        try {
            withTimeoutOrNull(1500) {
                suspendCancellableCoroutine<T?> { c ->
                    try {
                        block({ if (c.isActive) c.resume(it) }, { if (c.isActive) c.resume(null) })
                    } catch (t: Throwable) {
                        Log.d(TAG, "sdk call threw", t)
                        if (c.isActive) c.resume(null)
                    }
                }
            }
        } catch (t: Throwable) { Log.d(TAG, "sdk call failed", t); null }

    /** Same order as [VehicleMapping.DOOR_LABELS]. HOOD = 프렁크, REAR = 트렁크. */
    private val doorAreas = listOf(
        DoorValue.ROW_1_LEFT, DoorValue.ROW_1_RIGHT, DoorValue.ROW_2_LEFT, DoorValue.ROW_2_RIGHT, DoorValue.HOOD, DoorValue.REAR,
    )
    /** Same order as [VehicleMapping.WINDOW_LABELS]. */
    private val windowAreas = listOf(WindowValue.ROW_1_LEFT, WindowValue.ROW_1_RIGHT, WindowValue.ROW_2_LEFT, WindowValue.ROW_2_RIGHT)

    override suspend fun snapshot(): VehicleSnapshot {
        val v = vehicle ?: return sim.snapshot()
        // read everything in parallel so a slow/unsupported signal never stalls the others
        val r = coroutineScope {
            val odo = async { sdk<Float> { ok, err -> v.getOdometer().getCurrentDistanceDriven({ it?.let(ok) }, { err(it) }) } }
            val fuel = async { sdk<Int> { ok, err -> v.getFuel().getFuelPercent({ it?.let(ok) }, { err(it) }) } }
            val soc = async { sdk<Int> { ok, err -> v.getEvBatteryHelper(ok, err) } }
            val tell = async { telltales(v) }
            val doors = doorAreas.map { d ->
                async { sdk<Int> { ok, err -> v.getDoor().getCurrentDoorPosition(DoorArea(d), { it?.let(ok) }, { err(it) }) } }
            }
            val windows = windowAreas.map { w ->
                async { sdk<Int> { ok, err -> v.getWindow().getCurrentWindowPosition(WindowArea(w), { it?.let(ok) }, { err(it) }) } }
            }
            val tires = async { tirePressures(v) }
            val lamp = async { sdk<LightState> { ok, err -> v.getLight().getCurrentLowBeamLightState({ it?.let(ok) }, { logOnce("lamp", it); err(it) }) } }
            Readings(odo.await(), fuel.await(), soc.await(), tell.await(), doors.map { it.await() }, windows.map { it.await() },
                tires.await(), lamp.await())
        }
        realWorking = r.odo != null || r.fuel != null || r.soc != null || r.tell != null
        sim.autoDrift = !realWorking          // SDK works: odometer/fuel come only from the console
        val base = sim.snapshot()
        val names = r.tell?.map { it.first.name to it.second }
        val tpmsTell = r.tell?.any { it.first == TelltaleType.TPMS && it.second } == true
        // unread wheels are omitted (never "low"); injected sim values win over real ones
        val pressures = (if (realWorking) r.tires else base.tirePressures) + sim.tireOverrides
        val warnNames = (names?.let(VehicleMapping::warningNames).orEmpty() + base.warningNames).distinct()
        val lamps = (names?.let(VehicleMapping::lampFaults).orEmpty() + base.lampFaults).distinct()
        val tpmsOk = !tpmsTell && base.tpmsOk && VehicleMapping.lowTires(pressures, false).isEmpty()
        // unknown/failed reads never block: only positively-open items count; simulated opens are merged in
        val open = VehicleMapping.mergeOpen(VehicleMapping.openItems(r.doors, r.windows) ?: emptyList(), base.openItems)
        return base.copy(
            odometerKm = r.odo?.let { it.toInt() + sim.injectedKm } ?: base.odometerKm,
            fuelPercent = if (realWorking) VehicleMapping.fuelOrSoc(r.fuel, r.soc, base.fuelPercent) else base.fuelPercent,
            warningLights = warnNames.size,
            tpmsOk = tpmsOk,
            lightsOk = lamps.isEmpty(),
            tirePressures = pressures,
            warningNames = warnNames,
            lampFaults = lamps,
            headlamp = if (realWorking) VehicleMapping.headlampText(r.lamp?.name) else base.headlamp,
            doorsClosed = open.isEmpty(),
            openItems = open,
        )
    }

    private class Readings(
        val odo: Float?, val fuel: Int?, val soc: Int?, val tell: List<Pair<TelltaleType, Boolean>>?, val doors: List<Int?>,
        val windows: List<Int?>, val tires: Map<String, Float>, val lamp: LightState?,
    )

    private val wheelAreas = listOf(WheelValue.LEFT_FRONT, WheelValue.RIGHT_FRONT, WheelValue.LEFT_REAR, WheelValue.RIGHT_REAR)

    /** Pressure in kPa per wheel label; wheels whose read fails are omitted. */
    private suspend fun tirePressures(v: Vehicle): Map<String, Float> = coroutineScope {
        wheelAreas.map { w -> async { sdk<Float> { ok, err -> v.getTire().getTirePressure(WheelArea(w), { it?.let(ok) }, { logOnce("tire$w", it); err(it) }) } } }
            .map { it.await() }
            .let { reads -> VehicleMapping.TIRE_LABELS.zip(reads).mapNotNull { (l, p) -> p?.let { l to it } }.toMap() }
    }

    private fun Vehicle.getEvBatteryHelper(ok: (Int) -> Unit, err: (Exception) -> Unit) =
        getEvBattery().getEvBatteryStateOfCharge({ it?.let(ok) }, { err(it) })

    /** List of (type, isOn). BLINK counts as on. */
    private suspend fun telltales(v: Vehicle): List<Pair<TelltaleType, Boolean>>? =
        sdk<List<Pair<TelltaleType, Boolean>>> { ok, err ->
            v.getSafety().getTelltalesInfo(
                { t ->
                    val raw = t?.telltales?.map { "${it.type}/${it.state}/${it.color}" }.toString()
                    if (raw != lastTelltaleLog) { lastTelltaleLog = raw; Log.d(TAG, "telltales: $raw") }
                    ok((t?.telltales ?: emptyList()).map { it.type to (it.state != TelltaleState.OFF) })
                },
                { if (lastTelltaleLog != "err") { lastTelltaleLog = "err"; Log.w(TAG, "telltales error", it) }; err(it) },
            )
        }

    private val realWarnings: Flow<VehicleWarning> = flow {
        val v = vehicle ?: return@flow
        val tpms = VehicleMapping.Latch()
        val brake = VehicleMapping.Latch()
        while (true) {
            val kpa = tirePressures(v)
            telltales(v)?.let { list ->
                // latch re-arms when the telltale turns Off / pressure recovers, so the same warning can fire again later
                val tpmsOn = list.any { it.first == TelltaleType.TPMS && it.second } || kpa.values.any { it < SnapshotBlockers.LOW_TIRE_KPA }
                if (tpms.rising(tpmsOn)) emit(VehicleWarning(EventType.TPMS, EventLevel.CAUTION))
                if (brake.rising(list.any { it.first == TelltaleType.ABS && it.second })) emit(VehicleWarning(EventType.BRAKE, EventLevel.EMERGENCY))
            }
            delay(pollMs)
        }
    }

    /** true = gear P (stopped), false = D/R (moving). Emits the initial reading too, then every change. */
    private val realStopped: Flow<Boolean> = flow {
        val v = vehicle ?: return@flow
        var last: Boolean? = null
        while (true) {
            sdk<VehicleGear> { ok, err -> v.getGear().getCurrentGearSelectionState({ it?.let(ok) }, { err(it) }) }?.let {
                val p = VehicleMapping.gearStopped(it.name)
                if (p != null && p != last) { last = p; emit(p) }
            }
            delay(pollMs)
        }
    }

    override val warnings: Flow<VehicleWarning> = merge(sim.warnings, realWarnings)
    override val stopped: Flow<Boolean> = merge(sim.stopped, realStopped)

    override fun setMoving(moving: Boolean) = sim.setMoving(moving)
    override fun injectWarning(warning: VehicleWarning) = sim.injectWarning(warning)
    override fun clearWarnings() = sim.clearWarnings()
    override fun setStopped(stopped: Boolean) = sim.setStopped(stopped)
    override fun addDistance(km: Int) = sim.addDistance(km)
    override fun setOpen(label: String, open: Boolean) = sim.setOpen(label, open)
    override fun setTirePressure(label: String, kpa: Float) = sim.setTirePressure(label, kpa)
    override fun setLampFault(name: String, on: Boolean) = sim.setLampFault(name, on)
    override fun setWarningName(name: String, on: Boolean) = sim.setWarningName(name, on)
    override fun setFuelPercent(percent: Int) = sim.setFuelPercent(percent)
    override fun reset() = sim.reset()
    override fun release() {
        try { vehicle?.release() } catch (_: Throwable) {}
    }

    private companion object { const val TAG = "PleosVehicle" }
}
