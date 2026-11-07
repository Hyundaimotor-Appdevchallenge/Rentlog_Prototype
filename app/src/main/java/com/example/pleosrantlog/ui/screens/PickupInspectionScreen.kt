package com.example.pleosrantlog.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.ui.text.TextStyle
import com.example.pleosrantlog.data.model.ContactReason
import com.example.pleosrantlog.data.model.VehicleSnapshot
import com.example.pleosrantlog.data.vehicle.SnapshotBlockers
import com.example.pleosrantlog.data.vehicle.VehicleMapping
import com.example.pleosrantlog.ui.AppViewModel
import com.example.pleosrantlog.ui.UiState
import com.example.pleosrantlog.ui.components.*
import com.example.pleosrantlog.ui.theme.PleosRantLogTheme
import com.example.pleosrantlog.ui.theme.RentLogColors
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

internal fun fmtKm(km: Int): String = NumberFormat.getIntegerInstance(Locale.US).format(km)

/** F-02 출발 전 검수 (Figma 11:48). */
@Composable
fun PickupInspectionScreen(state: UiState, vm: AppViewModel) {
    PickupContent(
        state,
        onContact = { vm.requestCompanyCall(ContactReason.PICKUP) },
        onConfirm = vm::confirmDeparture,
        onTitleLongPress = { vm.showDemoControl(true) },
    )
}

@Composable
private fun PickupContent(state: UiState, onContact: () -> Unit, onConfirm: () -> Unit, onTitleLongPress: () -> Unit) {
    val s = state.inspection
    val open = s?.openItems.orEmpty()
    val blockers = s?.let { VehicleMapping.blockers(it) }.orEmpty()
    val now = SimpleDateFormat("MM.dd (E) HH:mm", Locale.KOREAN).format(System.currentTimeMillis())
    val tiles = listOf(
        Tile("주행거리", s?.let { "${fmtKm(it.odometerKm)} km" } ?: "-", "30 km 무료, 이후 1 km당 280원", "기록", true),
        InspectionTexts.fuel(s),
        InspectionTexts.warnings(s),
        InspectionTexts.tires(s),
        if (open.isEmpty()) Tile("문 · 창문", "모두 닫힘", "트렁크 포함 5곳", "정상", true)
        else Tile("문 · 창문", "열림 있음", OpenItemsText.summary(open), "주의", false),
        InspectionTexts.lamps(s),
    )
    val cfg = LocalConfiguration.current
    val landscape = cfg.screenWidthDp > cfg.screenHeightDp
    RentLogScaffold(
        title = "차량 상태를 자동으로 기록했어요",
        eyebrow = "출발 전 검수 · $now",
        onTitleLongPress = onTitleLongPress,
        left = {
            Column(if (landscape) Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxWidth()) {
                val cols = if (landscape) 3 else 2
                Column(verticalArrangement = Arrangement.spacedBy(24.fdp)) {
                    tiles.chunked(cols).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(24.fdp)) {
                            row.forEach { t -> TileCard(t, Modifier.weight(1f)) }
                            repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                if (landscape) Spacer(Modifier.weight(1f)) else Spacer(Modifier.height(24.fdp))
                val note = buildAnnotatedString {
                    append("이 기록이 ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = RentLogColors.TextPrimary)) { append("인수 기준") }
                    append("이 돼요. 고객님과 업체에 함께 보내요.\n외관 흠집은 메시지 링크로 사진을 남겨 주세요.")
                }
                val noteText: @Composable (Modifier) -> Unit = { m ->
                    Column(m) {
                        if (blockers.isNotEmpty()) {
                            FigmaText(InspectionTexts.notice(blockers), 26, weight = FontWeight.Bold, color = RentLogColors.Caution)
                            Spacer(Modifier.height(8.fdp))
                        }
                        Text(note, color = RentLogColors.TextBody, style = TextStyle(fontSize = 26.fsp, lineHeight = 39.fsp))
                        if (state.contactRequested) FigmaText("업체에 문의 요청을 보냈어요.", 24, color = RentLogColors.Normal)
                    }
                }
                val buttons: @Composable RowScope.() -> Unit = {
                    if (state.loading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp, color = RentLogColors.TextPrimary)
                    SoftButton("업체에 문의", onContact, enabled = !state.contactRequested)
                    LightButton("확인, 출발하기", onConfirm, enabled = !state.loading && blockers.isEmpty())
                }
                Box(Modifier.fillMaxWidth().heightIn(min = 164.fdp).clip(RoundedCornerShape(32.fdp)).background(RentLogColors.Surface)) {
                    if (landscape) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 40.fdp, vertical = 32.fdp), verticalAlignment = Alignment.CenterVertically) {
                            noteText(Modifier.weight(1f))
                            Row(horizontalArrangement = Arrangement.spacedBy(32.fdp), verticalAlignment = Alignment.CenterVertically, content = buttons)
                        }
                    } else {
                        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            noteText(Modifier)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically, content = buttons)
                        }
                    }
                }
            }
        },
    )
}

/** Korean texts for open doors/frunk/trunk/windows on F-02 (unit-tested). */
internal object OpenItemsText {
    /** Tile sub-text: "트렁크 · 앞 왼쪽 문 열림", more than 2 -> "트렁크 · 앞 왼쪽 문 외 1곳 열림". */
    fun summary(items: List<String>, max: Int = 2): String {
        val shown = items.take(max).joinToString(" · ")
        return if (items.size > max) "$shown 외 ${items.size - max}곳 열림" else "$shown 열림"
    }

    /** Bottom notice: "트렁크가 열려 있어요. 닫으면 자동으로 다시 확인해요." */
    fun notice(items: List<String>): String {
        val subject = if (items.size > 1) "${items.first()} 외 ${items.size - 1}곳" else items.first()
        return "$subject${subjectParticle(subject)} 열려 있어요. 닫으면 자동으로 다시 확인해요."
    }

    /** 이/가 by the final consonant (받침) of the last Hangul syllable; digits/others default to "이". */
    fun subjectParticle(word: String): String {
        val c = word.lastOrNull() ?: return "이"
        return if (c in '\uAC00'..'\uD7A3') { if ((c - '\uAC00') % 28 == 0) "가" else "이" } else "이"
    }
}

internal data class Tile(val label: String, val value: String, val note: String, val badge: String, val ok: Boolean)

/** Tile / notice texts of F-02 built from real snapshot values (unit-tested). */
internal object InspectionTexts {
    fun fuel(s: VehicleSnapshot?): Tile = when {
        s == null -> Tile("연료", "-", "연료는 정산하지 않아요", "기록", true)
        VehicleMapping.batteryLow(s.fuelPercent) -> Tile("연료", "${s.fuelPercent} %", "충전이 필요해요", "주의", false)
        else -> Tile("연료", "${s.fuelPercent} %", "연료는 정산하지 않아요", "기록", true)
    }

    fun warnings(s: VehicleSnapshot?): Tile = when {
        s == null -> Tile("경고등", "-", "켜진 경고등 없음", "정상", true)
        s.warningNames.isNotEmpty() -> Tile("경고등", "${s.warningNames.size}개", s.warningNames.joinToString(" · "), "주의", false)
        s.warningLights > 0 -> Tile("경고등", "${s.warningLights}개", "켜진 경고등이 있어요", "주의", false)
        else -> Tile("경고등", "0개", "켜진 경고등 없음", "정상", true)
    }

    fun tires(s: VehicleSnapshot?): Tile {
        val label = "타이어 (TPMS)"
        if (s == null) return Tile(label, "경고 없음", "공기압 경고등 꺼짐", "정상", true)
        val low = VehicleMapping.lowTires(s.tirePressures, !s.tpmsOk)
        if (low.isNotEmpty()) {
            val lowWheels = s.tirePressures.filterValues { it < SnapshotBlockers.LOW_TIRE_KPA }
            val note = if (lowWheels.isEmpty()) "공기압 경고등 켜짐" else {
                val first = VehicleMapping.TIRE_LABELS.firstOrNull { it in lowWheels } ?: lowWheels.keys.first()
                "$first ${lowWheels.getValue(first).toInt()} kPa" + if (lowWheels.size > 1) " 외 ${lowWheels.size - 1}곳" else ""
            }
            return Tile(label, "공기압 낮음", note, "주의", false)
        }
        val kpa = s.tirePressures.values.map { it.toInt() }
        val note = when {
            kpa.isEmpty() -> "공기압 경고등 꺼짐"
            kpa.min() == kpa.max() -> "4륜 ${kpa.first()} kPa"
            else -> "${kpa.min()}–${kpa.max()} kPa"
        }
        return Tile(label, "경고 없음", note, "정상", true)
    }

    fun lamps(s: VehicleSnapshot?): Tile = when {
        s == null || (s.lightsOk && s.lampFaults.isEmpty()) ->
            Tile("등화", "이상 없음", if (s?.headlamp.isNullOrEmpty()) "전조등 · 미등 · 방향지시등" else "전조등 ${s?.headlamp}", "정상", true)
        else -> Tile("등화", "이상 있음", s.lampFaults.joinToString(" · ").ifEmpty { "등화 경고" }, "주의", false)
    }

    /** First blocker, " 외 N건" when more. */
    fun notice(blockers: List<String>): String =
        blockers.first() + (if (blockers.size > 1) " 외 ${blockers.size - 1}건" else "") + ". 해결되면 자동으로 다시 확인해요."
}

@Composable
private fun TileCard(t: Tile, modifier: Modifier) {
    Column(modifier.heightIn(min = 223.fdp).clip(RoundedCornerShape(32.fdp)).background(RentLogColors.Surface)
        .padding(horizontal = 36.fdp, vertical = 32.fdp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FigmaText(t.label, 26, color = RentLogColors.TextSecondary, modifier = Modifier.weight(1f))
            val bg = if (t.ok) RentLogColors.Normal else RentLogColors.Caution
            Box(Modifier.height(36.fdp).clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 16.fdp), contentAlignment = Alignment.Center) {
                FigmaText(t.badge, 20, weight = FontWeight.Bold, color = RentLogColors.OnPrimaryButton)
            }
        }
        Spacer(Modifier.height(18.fdp))
        FigmaText(t.value, 48, weight = FontWeight.ExtraBold)
        Spacer(Modifier.height(14.fdp))
        FigmaText(t.note, 24, color = RentLogColors.TextBody)
    }
}

internal val previewSnapshot = VehicleSnapshot(12_340, 80, 0, true, true, true)

@Preview(widthDp = 1700, heightDp = 1184, showBackground = true)
@Composable
private fun PickupPreview() = PleosRantLogTheme {
    PickupContent(UiState(inspection = previewSnapshot), {}, {}, {})
}

@Preview(widthDp = 1700, heightDp = 1184, showBackground = true)
@Composable
private fun PickupTrunkOpenPreview() = PleosRantLogTheme {
    PickupContent(UiState(inspection = previewSnapshot.copy(doorsClosed = false, openItems = listOf("트렁크"))), {}, {}, {})
}

@Preview(widthDp = 600, heightDp = 1000, showBackground = true)
@Composable
private fun PickupPortraitPreview() = PleosRantLogTheme {
    PickupContent(UiState(inspection = previewSnapshot.copy(tpmsOk = false, warningLights = 1), contactRequested = true), {}, {}, {})
}

@Preview(widthDp = 1700, heightDp = 1184, showBackground = true)
@Composable
private fun PickupLowTirePreview() = PleosRantLogTheme {
    PickupContent(UiState(inspection = previewSnapshot.copy(
        tpmsOk = false, warningLights = 2, warningNames = listOf("ABS", "에어백"),
        tirePressures = mapOf("앞 왼쪽" to 150f, "앞 오른쪽" to 230f, "뒤 왼쪽" to 230f, "뒤 오른쪽" to 230f),
    )), {}, {}, {})
}
