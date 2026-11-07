package com.example.pleosrantlog.data.backend

object FeeCalculator {
    const val FREE_KM = 30
    const val PRICE_PER_KM = 280

    /** max(0, driven - 30) * 280. 272 km -> 67,760. */
    fun fee(drivenKm: Int): Int = maxOf(0, drivenKm - FREE_KM) * PRICE_PER_KM
}
