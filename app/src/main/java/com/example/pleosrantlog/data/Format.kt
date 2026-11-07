package com.example.pleosrantlog.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Long.hm(): String = SimpleDateFormat("HH:mm", Locale.KOREA).format(Date(this))
fun Int.grouped(): String = "%,d".format(this)
fun Int.won(): String = "${grouped()}원"
fun Int.km(): String = "${grouped()} km"

/** "1시간 25분" style, for the "반납까지" tile. */
fun Long.hoursMinutes(): String {
    val totalMin = (maxOf(0L, this) / 60_000L)
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "${h}시간 ${m}분" else "${m}분"
}

object DemoConstants {
    const val RENTAL_ID = "R-001"
    const val VEHICLE_NO = "12가3456"
    const val ROUTE_SUMMARY = "8.4 km · 18분"
}
