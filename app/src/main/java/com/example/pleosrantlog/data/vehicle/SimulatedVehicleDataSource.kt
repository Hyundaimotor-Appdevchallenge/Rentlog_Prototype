package com.example.pleosrantlog.data.vehicle

import com.example.pleosrantlog.data.model.EventType
import com.example.pleosrantlog.data.model.VehicleSnapshot
import com.example.pleosrantlog.data.model.VehicleWarning
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Works on any emulator: odometer rises and fuel drops while "moving". */
class SimulatedVehicleDataSource(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val startOdometerKm: Double = 12340.0,
    private val startFuelPercent: Double = 80.0,
    /** simulated speed while moving, km/h */
    private val speedKmh: Double = 60.0,
    private val fuelPercentPerKm: Double = 0.05,
) : VehicleDataSource {
    override val isSimulated = true

    private var odo = startOdometerKm
    private var fuel = startFuelPercent
    private var moving = false
    private var lastTick = clock()
    private val active = linkedSetOf<VehicleWarning>()
    var injectedKm = 0
        private set
    /** Demo/test-injected open items (labels), merged with real readings by [PleosVehicleDataSource]. */
    @Volatile var openItems: List<String> = emptyList()
        private set

    /** Injected tire pressures (kPa) over the 230 default; lamp faults and warning names are demo/test-injected. */
    @Volatile var tireOverrides: Map<String, Float> = emptyMap()
        private set
    @Volatile var lampFaultNames: List<String> = emptyList()
        private set
    @Volatile var warningNameList: List<String> = emptyList()
        private set

    /** false while a real source drives odometer/fuel: the 60 km/h drift is then switched off. */
    @Volatile var autoDrift = true

    private val _warnings = MutableSharedFlow<VehicleWarning>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val warnings: Flow<VehicleWarning> = _warnings.asSharedFlow()
    private val _stopped = MutableStateFlow(false)
    override val stopped: Flow<Boolean> = _stopped.asStateFlow()
    val isStopped: Boolean get() = _stopped.value

    private fun integrate() {
        val now = clock()
        if (autoDrift && moving && !_stopped.value) advance((now - lastTick) / 3_600_000.0 * speedKmh)
        lastTick = now
    }

    private fun advance(km: Double) {
        odo += km
        fuel = (fuel - km * fuelPercentPerKm).coerceAtLeast(5.0)
    }

    override suspend fun snapshot(): VehicleSnapshot {
        integrate()
        val pressures = VehicleMapping.TIRE_LABELS.associateWith { DEFAULT_TIRE_KPA } + tireOverrides
        val names = (warningNameList + (if (active.any { it.type == EventType.BRAKE }) listOf("ABS") else emptyList())).distinct()
        return VehicleSnapshot(
            odometerKm = odo.toInt(),
            fuelPercent = fuel.toInt(),
            warningLights = names.size,
            tpmsOk = active.none { it.type == EventType.TPMS } && VehicleMapping.lowTires(pressures, false).isEmpty(),
            doorsClosed = openItems.isEmpty(),
            lightsOk = lampFaultNames.isEmpty(),
            openItems = openItems,
            tirePressures = pressures,
            warningNames = names,
            lampFaults = lampFaultNames,
            headlamp = "꺼짐",
        )
    }

    override fun setMoving(moving: Boolean) { integrate(); this.moving = moving }

    override fun injectWarning(warning: VehicleWarning) { active += warning; _warnings.tryEmit(warning) }
    override fun clearWarnings() { active.clear() }
    override fun setStopped(stopped: Boolean) { integrate(); _stopped.value = stopped }
    override fun addDistance(km: Int) { integrate(); injectedKm += km; advance(km.toDouble()) }

    override fun setOpen(label: String, open: Boolean) {
        openItems = if (open) VehicleMapping.mergeOpen(openItems, listOf(label)) else openItems - label
    }

    override fun setTirePressure(label: String, kpa: Float) { tireOverrides = tireOverrides + (label to kpa) }
    override fun setLampFault(name: String, on: Boolean) { lampFaultNames = if (on) (lampFaultNames + name).distinct() else lampFaultNames - name }
    override fun setWarningName(name: String, on: Boolean) { warningNameList = if (on) (warningNameList + name).distinct() else warningNameList - name }
    override fun setFuelPercent(percent: Int) { fuel = percent.toDouble() }

    override fun reset() {
        openItems = emptyList(); tireOverrides = emptyMap(); lampFaultNames = emptyList(); warningNameList = emptyList()
        odo = startOdometerKm; fuel = startFuelPercent; moving = false; active.clear(); injectedKm = 0
        _stopped.value = false; lastTick = clock()
    }

    override fun release() {}

    companion object { const val DEFAULT_TIRE_KPA = 230f }
}
