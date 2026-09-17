package com.jacobcraig.debtmngr.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ExactSplitCalculatorTest {

    @Test
    fun `exact split with valid amounts matching total returns correct shares`() {
        val splits = ExactSplitCalculator.calculate(
            totalAmount = 1000L,
            exactAmounts = mapOf(1L to 600L, 2L to 400L)
        )

        assertEquals(2, splits.size)
        assertEquals(600L, splits.first { it.participantId == 1L }.amount)
        assertEquals(400L, splits.first { it.participantId == 2L }.amount)
        assertEquals(1000L, splits.sumOf { it.amount })
    }

    @Test
    fun `exact split with participant having zero amount excludes zero share`() {
        val splits = ExactSplitCalculator.calculate(
            totalAmount = 1000L,
            exactAmounts = mapOf(1L to 1000L, 2L to 0L)
        )

        assertEquals(1, splits.size)
        assertEquals(1000L, splits.first().amount)
        assertEquals(1L, splits.first().participantId)
        assertEquals(1000L, splits.sumOf { it.amount })
    }

    @Test
    fun `throws exception when sum of exact amounts is less than total amount`() {
        val ex = assertThrows<IllegalArgumentException> {
            ExactSplitCalculator.calculate(
                totalAmount = 1000L,
                exactAmounts = mapOf(1L to 500L, 2L to 300L)
            )
        }
        val msg = checkNotNull(ex.message)
        assertTrue(msg.contains("The sum of exact split amounts (800) must equal the total expense amount (1000)"))
    }

    @Test
    fun `throws exception when sum of exact amounts is greater than total amount`() {
        val ex = assertThrows<IllegalArgumentException> {
            ExactSplitCalculator.calculate(
                totalAmount = 1000L,
                exactAmounts = mapOf(1L to 700L, 2L to 400L)
            )
        }
        val msg = checkNotNull(ex.message)
        assertTrue(msg.contains("The sum of exact split amounts (1100) must equal the total expense amount (1000)"))
    }

    @Test
    fun `throws exception when any exact amount is negative`() {
        val ex = assertThrows<IllegalArgumentException> {
            ExactSplitCalculator.calculate(
                totalAmount = 1000L,
                exactAmounts = mapOf(1L to 1200L, 2L to -200L)
            )
        }
        val msg = ex.message
        assertNotNull(msg)
        assertEquals("Individual split amounts cannot be negative", msg)
    }

    @Test
    fun `throws exception when total amount is not positive`() {
        val ex = assertThrows<IllegalArgumentException> {
            ExactSplitCalculator.calculate(
                totalAmount = 0L,
                exactAmounts = mapOf(1L to 0L)
            )
        }
        val msg = ex.message
        assertNotNull(msg)
        assertEquals("Total amount must be greater than zero", msg)
    }

    @Test
    fun `throws exception when exact amounts map is empty`() {
        val ex = assertThrows<IllegalArgumentException> {
            ExactSplitCalculator.calculate(
                totalAmount = 1000L,
                exactAmounts = emptyMap<Long, Long>()
            )
        }
        val msg = ex.message
        assertNotNull(msg)
        assertEquals("At least one consumer must be assigned an amount", msg)
    }

    @Test
    fun `boundary - 1 penny exact split`() {
        val splits = ExactSplitCalculator.calculate(
            totalAmount = 1L,
            exactAmounts = mapOf(1L to 1L, 2L to 0L)
        )
        assertEquals(1, splits.size)
        assertEquals(1L, splits.first().amount)
        assertEquals(1L, splits.first().participantId)
    }

    @Test
    fun `total conservation is strictly maintained across uneven splits`() {
        val testCases = listOf(
            Pair(1000L, mapOf(1L to 333L, 2L to 333L, 3L to 334L)),
            Pair(701L, mapOf(1L to 500L, 2L to 201L)),
            Pair(1L, mapOf(10L to 1L)),
            Pair(999999L, mapOf(1L to 100000L, 2L to 200000L, 3L to 699999L))
        )

        for ((amount, exactMap) in testCases) {
            val splits = ExactSplitCalculator.calculate(amount, exactMap)
            assertEquals(amount, splits.sumOf { it.amount }, "Total sum must be conserved for amount=$amount")
        }
    }
}
