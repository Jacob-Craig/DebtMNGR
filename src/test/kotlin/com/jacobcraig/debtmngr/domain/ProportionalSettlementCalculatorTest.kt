package com.jacobcraig.debtmngr.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ProportionalSettlementCalculatorTest {

    @Test
    fun `exact proportional split divides amounts accurately`() {
        val debts = mapOf(1L to 1000L, 2L to 2000L)
        val allocations = ProportionalSettlementCalculator.calculate(
            totalAmount = 1500L,
            debtsByGroup = debts
        )

        assertEquals(500L, allocations[1L])
        assertEquals(1000L, allocations[2L])
        assertEquals(1500L, allocations.values.sum())
    }

    @Test
    fun `full settlement clears all group debts completely`() {
        val debts = mapOf(1L to 1050L, 2L to 2450L)
        val allocations = ProportionalSettlementCalculator.calculate(
            totalAmount = 3500L,
            debtsByGroup = debts
        )

        assertEquals(1050L, allocations[1L])
        assertEquals(2450L, allocations[2L])
        assertEquals(3500L, allocations.values.sum())
    }

    @Test
    fun `proportional split with rounding remainder preserves total amount exactly`() {
        val debts = mapOf(1L to 1000L, 2L to 1000L, 3L to 1000L)
        val allocations = ProportionalSettlementCalculator.calculate(
            totalAmount = 1000L,
            debtsByGroup = debts
        )

        assertEquals(1000L, allocations.values.sum())
        assertTrue(allocations.values.all { it in 333L..334L })
    }

    @Test
    fun `allocates across multiple groups with uneven debts and strictly conserves sum`() {
        val debts = mapOf(1L to 1234L, 2L to 5678L, 3L to 9101L)
        val totalDebt = debts.values.sum()
        val settleAmount = 7777L

        val allocations = ProportionalSettlementCalculator.calculate(
            totalAmount = settleAmount,
            debtsByGroup = debts
        )

        assertEquals(settleAmount, allocations.values.sum())
        for ((groupId, allocated) in allocations) {
            val debt = debts.getValue(groupId)
            assertTrue(allocated in 0L..debt, "Allocated $allocated must be <= debt $debt for group $groupId")
        }
    }

    @Test
    fun `throws exception when settlement amount is zero or negative`() {
        val debts = mapOf(1L to 1000L)
        val exZero = assertThrows<IllegalArgumentException> {
            ProportionalSettlementCalculator.calculate(totalAmount = 0L, debtsByGroup = debts)
        }
        assertEquals("Settlement amount must be positive", exZero.message)

        val exNeg = assertThrows<IllegalArgumentException> {
            ProportionalSettlementCalculator.calculate(totalAmount = -500L, debtsByGroup = debts)
        }
        assertEquals("Settlement amount must be positive", exNeg.message)
    }

    @Test
    fun `throws exception when debts map is empty`() {
        val ex = assertThrows<IllegalArgumentException> {
            ProportionalSettlementCalculator.calculate(totalAmount = 500L, debtsByGroup = emptyMap())
        }
        assertEquals("Debts map cannot be empty", ex.message)
    }

    @Test
    fun `throws exception when any group debt is non-positive`() {
        val ex = assertThrows<IllegalArgumentException> {
            ProportionalSettlementCalculator.calculate(totalAmount = 500L, debtsByGroup = mapOf(1L to 0L, 2L to 1000L))
        }
        assertEquals("Each group debt must be positive", ex.message)
    }

    @Test
    fun `throws exception when settlement amount exceeds total debt`() {
        val debts = mapOf(1L to 1000L, 2L to 1000L)
        val ex = assertThrows<IllegalArgumentException> {
            ProportionalSettlementCalculator.calculate(totalAmount = 2500L, debtsByGroup = debts)
        }
        assertEquals("Settlement amount cannot exceed total debt", ex.message)
    }
}
