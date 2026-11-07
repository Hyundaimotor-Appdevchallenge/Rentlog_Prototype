package com.example.pleosrantlog.data.vehicle

import com.example.pleosrantlog.data.model.VehicleSnapshot
import com.example.pleosrantlog.data.model.VehicleWarning
import kotlinx.coroutines.flow.Flow

interface VehicleDataSource {
    /** true when values are simulated (no real Pleos vehicle service). */
    val isSimulated: Boolean

    suspend fun snapshot(): VehicleSnapshot

    /** Emits each newly appearing warning (TPMS caution / brake emergency). Cold flow: collect to start polling. */
    val warnings: Flow<VehicleWarning>

    /** Emits true when the vehicle has just stopped (speed 0 / gear P), false when it moves again. */
    val stopped: Flow<Boolean>

    /** App tells the source that the rental trip started/ended (simulated odometer only advances while moving). */
    fun setMoving(moving: Boolean)

    // ---- demo injection (works on top of real data too) ----
    fun injectWarning(warning: VehicleWarning)
    fun clearWarnings()
    fun setStopped(stopped: Boolean)
    fun addDistance(km: Int)
    /** Marks a door/frunk/trunk/window (label from [VehicleMapping.OPENABLE_LABELS]) as open/closed on top of real data. */
    fun setOpen(label: String, open: Boolean) {}
    /** Test/demo injections on top of real data. [label] from [VehicleMapping.TIRE_LABELS], names from [VehicleMapping.WARNING_NAMES]/[VehicleMapping.LAMP_NAMES] values. */
    fun setTirePressure(label: String, kpa: Float) {}
    fun setLampFault(name: String, on: Boolean) {}
    fun setWarningName(name: String, on: Boolean) {}
    fun setFuelPercent(percent: Int) {}
    fun reset()
    fun release()
}
