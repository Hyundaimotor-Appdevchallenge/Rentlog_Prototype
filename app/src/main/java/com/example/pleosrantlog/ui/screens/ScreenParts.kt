package com.example.pleosrantlog.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.pleosrantlog.data.model.*
import com.example.pleosrantlog.ui.UiState
import com.example.pleosrantlog.ui.components.FigmaText
import com.example.pleosrantlog.ui.components.fdp
import com.example.pleosrantlog.ui.theme.RentLogColors
import java.util.Locale

internal fun won(v: Int) = String.format(Locale.KOREA, "%,d원", v)
internal fun km(v: Int) = String.format(Locale.KOREA, "%,d km", v)

/** Label on the left, value on the right (Figma 28px rows). */
@Composable
internal fun LabelValueRow(
    label: String, value: String, modifier: Modifier = Modifier,
    valueColor: Color = RentLogColors.TextPrimary,
) {
    Row(modifier.fillMaxWidth().heightIn(min = 54.fdp), verticalAlignment = Alignment.CenterVertically) {
        FigmaText(label, 28, Modifier.weight(1f), color = RentLogColors.TextBody)
        Spacer(Modifier.width(12.fdp))
        FigmaText(value, 28, color = valueColor, align = TextAlign.End)
    }
}

@Composable
internal fun Divider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(maxOf(2.fdp, 1.dp)).background(RentLogColors.Outline))
}

/** Card with Figma padding/radius that can be given weight(1f) to fill its pane. */
@Composable
internal fun PaneCard(
    modifier: Modifier = Modifier,
    padding: Int = 48,
    radius: Int = 32,
    accent: Color? = null,
    spacing: Int = 10,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius.fdp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(RentLogColors.Surface)
            .then(if (accent != null) Modifier.border(1.dp, accent, shape) else Modifier)
            .padding(padding.fdp),
        verticalArrangement = Arrangement.spacedBy(spacing.fdp),
        content = content,
    )
}

/** Tappable choice card (F-05): 140 high, primary = light fill. */
@Composable
internal fun ChoiceCard(
    title: String, subtitle: String, primary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
) {
    val fg = if (primary) RentLogColors.OnPrimaryButton else RentLogColors.TextPrimary
    Column(
        modifier.fillMaxWidth().heightIn(min = maxOf(140.fdp, 72.dp))
            .clip(RoundedCornerShape((if (primary) 32 else 28).fdp))
            .background(if (primary) RentLogColors.PrimaryButton else RentLogColors.SurfaceHigh)
            .clickable(onClick = onClick).padding(horizontal = 32.fdp, vertical = 28.fdp),
        verticalArrangement = Arrangement.Center,
    ) {
        FigmaText(title, 32, weight = FontWeight.Bold, color = fg)
        Spacer(Modifier.height(8.fdp))
        FigmaText(subtitle, 24, color = if (primary) fg else RentLogColors.TextBody)
    }
}

/** Solid pill (Figma chips/badges): dark text on colored fill. */
@Composable
internal fun SmallChip(text: String, bg: Color, fg: Color = RentLogColors.OnPrimaryButton, px: Int = 20) {
    FigmaText(
        text, px, weight = FontWeight.Bold, color = fg,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 16.fdp, vertical = 6.fdp),
    )
}

// ---------------------------------------------------------------- sample data for previews
internal fun sampleCompare() = listOf(
    CompareRow("주행거리", "12,340 km", "12,612 km", "272 km 주행"),
    CompareRow("연료", "80 %", "62 %", "기록만"),
    CompareRow("경고등", "0개", "0개", "운행 중 1건 해결"),
    CompareRow("타이어 (TPMS)", "경고 없음", "경고 없음", "정상"),
    CompareRow("문 · 창문", "모두 닫힘", "모두 닫힘", "정상"),
)

internal fun sampleSettlement() = Settlement(
    listOf(
        SettlementLine("주행 242 km × 280원", 67760),
        SettlementLine("연료", 0, "정산 없음"),
        SettlementLine("타이어 경고 대응", 0, "업체 부담"),
    ), 67760,
)

internal fun sampleTimeline() = listOf(
    TimelineItem("14:32", "TPMS 경고등 켜짐 · 주행거리 12,488 km"),
    TimelineItem("14:32", "업체에 자동 알림 전송 · 카카오톡으로 고객님께도 기록 전송"),
    TimelineItem("14:36", "업체 응답: 지정 정비소 방문 시 수리비는 업체 부담"),
    TimelineItem("14:41", "정차 감지 · 대응 방법 안내 시작"),
)

internal fun sampleState() = UiState(
    timeline = sampleTimeline(),
    currentEventType = EventType.TPMS,
    currentEventLevel = EventLevel.CAUTION,
    eventOccurredAt = "15:08",
    eventOdometerKm = 12530,
    eventStatus = EventStatus("SENT", "PENDING", "PENDING"),
    rental = Rental("R-001", "김*준", "아반떼 · 12가 3456", Company("[렌터카 업체명]", "02-000-0000"), 0L, 0L),
    returnPreview = ReturnPreview(272, 30, 280, 67760, ReturnPlace("[렌터카 업체명] 주차장", 0.0, 0.0), 0L, 62),
    returnResult = ReturnInspectionResult(sampleCompare(), sampleSettlement()),
    completeResult = CompleteResult(272, 67760, "7f3a·c21e"),
    statement = Statement(
        "R-001", "김*준", "아반떼 · 12가 3456", Company("[렌터카 업체명]", "02-000-0000"),
        sampleCompare(), sampleSettlement(), sampleTimeline(), 272, 67760, "7f3a·c21e",
    ),
)
