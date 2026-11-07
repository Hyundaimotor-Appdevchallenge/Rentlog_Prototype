package com.example.pleosrantlog

import com.example.pleosrantlog.data.net.decideOnline
import com.example.pleosrantlog.data.vehicle.SimulatedVehicleDataSource
import com.example.pleosrantlog.data.vehicle.SnapshotBlockers
import com.example.pleosrantlog.data.vehicle.VehicleMapping
import com.example.pleosrantlog.ui.screens.OpenItemsText
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class VehicleMappingTest {
    @Test fun fuel_fallsBackToSoc_thenSimulation() {
        assertEquals(55, VehicleMapping.fuelOrSoc(55, 80, 70))
        assertEquals(62, VehicleMapping.fuelOrSoc(null, 62, 70))
        assertEquals(62, VehicleMapping.fuelOrSoc(0, 62, 70))
        assertEquals(70, VehicleMapping.fuelOrSoc(null, null, 70))
        assertEquals(100, VehicleMapping.fuelOrSoc(null, 140, 70))
    }

    @Test fun warningCount_onlyWarningTelltalesThatAreOn() {
        val t = listOf("TPMS" to true, "ABS" to false, "TURN_SIGNAL_LEFT" to true, "AUTO_HOLD" to true, "PARKING_BRAKE" to true, "ESC" to true)
        assertEquals(2, VehicleMapping.warningLightCount(t))
        assertEquals(0, VehicleMapping.warningLightCount(emptyList()))
    }

    @Test fun doors_allZeroIsClosed_nullsIgnored() {
        assertEquals(true, VehicleMapping.doorsClosed(listOf(0, 0, 0, 0)))
        assertEquals(false, VehicleMapping.doorsClosed(listOf(0, 1, 0, 0)))
        assertEquals(true, VehicleMapping.doorsClosed(listOf(0, null, 0, null)))
        assertNull(VehicleMapping.doorsClosed(listOf(null, null)))
    }

    @Test fun openItems_doorsHoodRearWindows_failuresIgnored() {
        // doors: ROW_1_L, ROW_1_R, ROW_2_L, ROW_2_R, HOOD, REAR ; windows: ROW_1_L, ROW_1_R, ROW_2_L, ROW_2_R
        assertEquals(emptyList<String>(), VehicleMapping.openItems(listOf(0, 0, 0, 0, 0, 0), listOf(0, 0, 0, 0)))
        assertEquals(listOf("트렁크"), VehicleMapping.openItems(listOf(0, 0, 0, 0, 0, 1), listOf(0, 0, 0, 0)))
        assertEquals(listOf("프렁크"), VehicleMapping.openItems(listOf(0, 0, 0, 0, 100, 0), emptyList()))
        assertEquals(listOf("앞 왼쪽 문", "뒤 오른쪽 문"), VehicleMapping.openItems(listOf(1, 0, 0, -1, 0, 0), listOf(0, 0, 0, 0)))
        assertEquals(listOf("앞 오른쪽 창문", "뒤 왼쪽 창문"), VehicleMapping.openItems(listOf(0, 0, 0, 0, 0, 0), listOf(0, 30, 100, 0)))
        // failed/unsupported reads never count as open
        assertEquals(listOf("트렁크"), VehicleMapping.openItems(listOf(null, null, null, null, null, 1), listOf(null, null, null, null)))
        assertEquals(emptyList<String>(), VehicleMapping.openItems(listOf(null, 0), listOf(null, -1)))
        assertNull(VehicleMapping.openItems(listOf(null, null, null, null, null, null), listOf(null, null, null, null)))
    }

    @Test fun openItems_mergeKeepsCanonicalOrder() {
        assertEquals(listOf("앞 왼쪽 문", "트렁크", "앞 왼쪽 창문"),
            VehicleMapping.mergeOpen(listOf("앞 왼쪽 창문", "트렁크"), listOf("트렁크", "앞 왼쪽 문")))
    }

    @Test fun sim_setOpen_reflectsInSnapshot_andReset() = runTest {
        val sim = SimulatedVehicleDataSource(clock = { 0L })
        assertTrue(sim.snapshot().openItems.isEmpty()); assertTrue(sim.snapshot().doorsClosed)
        sim.setOpen("트렁크", true); sim.setOpen("프렁크", true)
        assertEquals(listOf("프렁크", "트렁크"), sim.snapshot().openItems); assertFalse(sim.snapshot().doorsClosed)
        sim.setOpen("프렁크", false)
        assertEquals(listOf("트렁크"), sim.snapshot().openItems)
        sim.reset()
        assertTrue(sim.snapshot().openItems.isEmpty())
    }

    @Test fun openItemsText_summaryAndParticle() {
        assertEquals("트렁크 열림", OpenItemsText.summary(listOf("트렁크")))
        assertEquals("트렁크 · 앞 왼쪽 문 열림", OpenItemsText.summary(listOf("트렁크", "앞 왼쪽 문")))
        assertEquals("앞 왼쪽 문 · 프렁크 외 2곳 열림", OpenItemsText.summary(listOf("앞 왼쪽 문", "프렁크", "트렁크", "앞 왼쪽 창문")))
        assertEquals("트렁크가 열려 있어요. 닫으면 자동으로 다시 확인해요.", OpenItemsText.notice(listOf("트렁크")))
        assertEquals("앞 왼쪽 문이 열려 있어요. 닫으면 자동으로 다시 확인해요.", OpenItemsText.notice(listOf("앞 왼쪽 문")))
        assertEquals("앞 왼쪽 창문이 열려 있어요. 닫으면 자동으로 다시 확인해요.", OpenItemsText.notice(listOf("앞 왼쪽 창문")))
        assertEquals("트렁크 외 1곳이 열려 있어요. 닫으면 자동으로 다시 확인해요.", OpenItemsText.notice(listOf("트렁크", "프렁크")))
    }

    @Test fun gear_toStopped() {
        assertEquals(true, VehicleMapping.gearStopped("GEAR_PARK"))
        assertEquals(false, VehicleMapping.gearStopped("GEAR_DRIVE"))
        assertEquals(false, VehicleMapping.gearStopped("GEAR_REVERSE"))
        assertNull(VehicleMapping.gearStopped("GEAR_NEUTRAL")); assertNull(VehicleMapping.gearStopped("GEAR_UNKNOWN"))
    }

    @Test fun latch_rearmsWhenTelltaleTurnsOff() {
        val l = VehicleMapping.Latch()
        assertFalse(l.rising(false)); assertTrue(l.rising(true)); assertFalse(l.rising(true))
        assertFalse(l.rising(false)); assertTrue(l.rising(true))
    }

    @Test fun network_airplaneIsPrimary_noNetworkOnlyAfterSeenOnce() {
        assertFalse(decideOnline(airplaneOn = true, hasActiveNetwork = true, everConnected = true))
        assertTrue(decideOnline(false, true, true))
        assertTrue(decideOnline(false, false, true)) // default: only airplane mode counts
        assertFalse(decideOnline(false, false, true, noNetworkMeansOffline = true))
        assertTrue(decideOnline(false, false, false, noNetworkMeansOffline = true)) // never had network: do not show F-08 forever
    }

    @Test fun sim_autoDriftOff_keepsOdometerFixed() = runTest {
        var now = 0L
        val sim = SimulatedVehicleDataSource(clock = { now })
        sim.setMoving(true); now += 3_600_000
        assertEquals(12400, sim.snapshot().odometerKm)
        sim.autoDrift = false; now += 3_600_000
        assertEquals(12400, sim.snapshot().odometerKm)
    }

    private fun snap(
        open: List<String> = emptyList(), tires: Map<String, Float> = emptyMap(), tpmsOk: Boolean = true,
        warns: List<String> = emptyList(), lamps: List<String> = emptyList(), fuel: Int = 80,
    ) = com.example.pleosrantlog.data.model.VehicleSnapshot(
        12340, fuel, warns.size, tpmsOk, open.isEmpty(), lamps.isEmpty(), open, tires, warns, lamps,
    )

    @Test fun lowTires_perWheel_andTpmsOnly() {
        val p = mapOf("앞 왼쪽" to 150f, "앞 오른쪽" to 230f, "뒤 왼쪽" to 179f, "뒤 오른쪽" to 180f)
        assertEquals(listOf("앞 왼쪽 타이어 공기압 낮음 (150 kPa)", "뒤 왼쪽 타이어 공기압 낮음 (179 kPa)"), VehicleMapping.lowTires(p, false))
        assertEquals(listOf("앞 왼쪽 타이어 공기압 낮음 (150 kPa)", "뒤 왼쪽 타이어 공기압 낮음 (179 kPa)"), VehicleMapping.lowTires(p, true))
        val ok = mapOf("앞 왼쪽" to 230f)
        assertEquals(listOf("타이어 공기압 경고등"), VehicleMapping.lowTires(ok, true))
        assertEquals(listOf("타이어 공기압 경고등"), VehicleMapping.lowTires(emptyMap(), true))
        assertTrue(VehicleMapping.lowTires(ok, false).isEmpty())
        assertTrue(VehicleMapping.lowTires(emptyMap(), false).isEmpty())
    }

    @Test fun warningNamesAndLampFaults_mapping_lampsExcludedFromWarnings() {
        val t = listOf("ABS" to true, "AIRBAG" to true, "LED_HEADLAMP" to true, "AFS" to false, "TPMS" to true,
            "PARKING_BRAKE" to true, "LOW_VOLT_BATTERY" to true, "ESC" to false, "TURN_SIGNAL_LEFT" to true)
        assertEquals(listOf("ABS", "에어백", "보조 배터리"), VehicleMapping.warningNames(t))
        assertEquals(listOf("LED 헤드램프"), VehicleMapping.lampFaults(t))
        assertEquals(listOf("LED 헤드램프", "AFS"), VehicleMapping.lampFaults(listOf("AFS" to true, "LED_HEADLAMP" to true)))
        assertEquals(listOf("경고등", "MDPS", "EV 시스템", "엔진", "엔진 오일 압력"),
            VehicleMapping.warningNames(listOf("ENGINE_OIL_PRESSURE", "ENGINE", "EV_SYSTEM", "MDPS", "MASTER_WARNING").map { it to true }))
        assertTrue(VehicleMapping.warningNames(emptyList()).isEmpty())
    }

    @Test fun batteryLow_boundary() {
        assertTrue(VehicleMapping.batteryLow(19)); assertFalse(VehicleMapping.batteryLow(20))
        assertEquals(180f, SnapshotBlockers.LOW_TIRE_KPA); assertEquals(20, SnapshotBlockers.LOW_BATTERY_PERCENT)
    }

    @Test fun headlampText() {
        assertEquals("꺼짐", VehicleMapping.headlampText("OFF")); assertEquals("켜짐", VehicleMapping.headlampText("ON"))
        assertEquals("자동", VehicleMapping.headlampText("DAYTIME_RUNNING")); assertEquals("", VehicleMapping.headlampText(null))
    }

    @Test fun blockers_orderAndEmpty() {
        assertTrue(VehicleMapping.blockers(snap()).isEmpty())
        val b = VehicleMapping.blockers(snap(
            open = listOf("트렁크"), tires = mapOf("앞 왼쪽" to 150f), tpmsOk = false, warns = listOf("ABS"), lamps = listOf("AFS"), fuel = 15,
        ))
        assertEquals(listOf("트렁크 열림", "앞 왼쪽 타이어 공기압 낮음 (150 kPa)", "ABS 경고등", "AFS 경고", "배터리 15%"), b)
        assertEquals(listOf("타이어 공기압 경고등"), VehicleMapping.blockers(snap(tpmsOk = false)))
        assertTrue(VehicleMapping.blockers(snap(fuel = 20)).isEmpty())
    }

    @Test fun inspectionTexts_tilesAndNotice() {
        val ok = snap(tires = VehicleMapping.TIRE_LABELS.associateWith { 230f })
        assertEquals("4륜 230 kPa", com.example.pleosrantlog.ui.screens.InspectionTexts.tires(ok).note)
        assertEquals("공기압 경고등 꺼짐", com.example.pleosrantlog.ui.screens.InspectionTexts.tires(snap()).note)
        val low = snap(tires = mapOf("앞 왼쪽" to 150f, "뒤 왼쪽" to 100f, "앞 오른쪽" to 230f), tpmsOk = false, warns = listOf("ABS", "에어백"), fuel = 15)
        val t = com.example.pleosrantlog.ui.screens.InspectionTexts.tires(low)
        assertEquals("공기압 낮음", t.value); assertEquals("앞 왼쪽 150 kPa 외 1곳", t.note); assertFalse(t.ok)
        assertEquals("2개", com.example.pleosrantlog.ui.screens.InspectionTexts.warnings(low).value)
        assertEquals("ABS · 에어백", com.example.pleosrantlog.ui.screens.InspectionTexts.warnings(low).note)
        assertEquals("충전이 필요해요", com.example.pleosrantlog.ui.screens.InspectionTexts.fuel(low).note)
        assertEquals("이상 있음", com.example.pleosrantlog.ui.screens.InspectionTexts.lamps(snap(lamps = listOf("AFS"))).value)
        assertEquals("전조등 꺼짐", com.example.pleosrantlog.ui.screens.InspectionTexts.lamps(snap().copy(headlamp = "꺼짐")).note)
        assertEquals("트렁크 열림 외 1건. 해결되면 자동으로 다시 확인해요.",
            com.example.pleosrantlog.ui.screens.InspectionTexts.notice(listOf("트렁크 열림", "ABS 경고등")))
    }
}
