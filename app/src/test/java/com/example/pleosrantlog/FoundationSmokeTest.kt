package com.example.pleosrantlog

import com.example.pleosrantlog.data.backend.FakeBackend
import com.example.pleosrantlog.data.backend.FeeCalculator
import com.example.pleosrantlog.data.model.ApiResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoundationSmokeTest {
    @Test fun fee() { assertEquals(67760, FeeCalculator.fee(272)); assertEquals(0, FeeCalculator.fee(20)) }

    @Test fun lockAfterFiveFails() = runTest {
        val b = FakeBackend(latencyMs = 0)
        repeat(4) {
            val r = b.verify("R-001", "0000") as ApiResult.Error
            assertEquals(401, r.http); assertEquals(4 - it, r.remaining)
        }
        assertEquals(423, (b.verify("R-001", "0000") as ApiResult.Error).http)
        assertEquals(423, (b.verify("R-001", "1234") as ApiResult.Error).http)
        b.unlock("R-001")
        assertTrue(b.verify("R-001", "1234") is ApiResult.Success)
    }
}
