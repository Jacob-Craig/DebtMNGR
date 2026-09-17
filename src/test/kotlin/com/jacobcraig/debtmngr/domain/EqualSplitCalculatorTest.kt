package com.jacobcraig.debtmngr.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EqualSplitCalculatorTest {

    @Test
    fun `equal split with no remainder divides amount evenly`() {
        val splits = EqualSplitCalculator.calculate(
            totalAmount = 900L,
            payerId = 1L,
            consumerIds = listOf(1L, 2L, 3L)
        )

        assertEquals(3, splits.size)
        assertEquals(300L, splits.first { it.participantId == 1L }.amount)
        assertEquals(300L, splits.first { it.participantId == 2L }.amount)
        assertEquals(300L, splits.first { it.participantId == 3L }.amount)
        assertEquals(900L, splits.sumOf { it.amount })
    }

    @Test
    fun `equal split with remainder allocates odd pennies to payer when payer is in consumers`() {
        // total 1000 pence (£10.00), 3 consumers. 1000 / 3 = 333, remainder = 1.
        // Payer (id = 2) is in consumers, so payer receives 333 + 1 = 334.
        val splits = EqualSplitCalculator.calculate(
            totalAmount = 1000L,
            payerId = 2L,
            consumerIds = listOf(1L, 2L, 3L)
        )

        assertEquals(3, splits.size)
        assertEquals(333L, splits.first { it.participantId == 1L }.amount)
        assertEquals(334L, splits.first { it.participantId == 2L }.amount)
        assertEquals(333L, splits.first { it.participantId == 3L }.amount)
        assertEquals(1000L, splits.sumOf { it.amount })
    }

    @Test
    fun `equal split with remainder allocates odd pennies to first consumer when payer is not in consumers`() {
        // total 1000 pence, 3 consumers. Payer (id = 99) is excluded.
        // First consumer (id = 1) receives 333 + 1 = 334.
        val splits = EqualSplitCalculator.calculate(
            totalAmount = 1000L,
            payerId = 99L,
            consumerIds = listOf(1L, 2L, 3L)
        )

        assertEquals(3, splits.size)
        assertEquals(334L, splits.first { it.participantId == 1L }.amount)
        assertEquals(333L, splits.first { it.participantId == 2L }.amount)
        assertEquals(333L, splits.first { it.participantId == 3L }.amount)
        assertEquals(1000L, splits.sumOf { it.amount })
    }

    @Test
    fun `boundary - 1 penny split among 3 consumers with payer in split`() {
        // total = 1 penny. base = 0, remainder = 1. Payer (id = 1) gets 1.
        val splits = EqualSplitCalculator.calculate(
            totalAmount = 1L,
            payerId = 1L,
            consumerIds = listOf(1L, 2L, 3L)
        )

        assertEquals(3, splits.size)
        assertEquals(1L, splits.first { it.participantId == 1L }.amount)
        assertEquals(0L, splits.first { it.participantId == 2L }.amount)
        assertEquals(0L, splits.first { it.participantId == 3L }.amount)
        assertEquals(1L, splits.sumOf { it.amount })
    }

    @Test
    fun `throws exception when total amount is not positive`() {
        val ex = assertThrows<IllegalArgumentException> {
            EqualSplitCalculator.calculate(
                totalAmount = 0L,
                payerId = 1L,
                consumerIds = listOf(1L, 2L)
            )
        }
        assertEquals("Total amount must be greater than zero", ex.message)
    }

    @Test
    fun `throws exception when consumer list is empty`() {
        val ex = assertThrows<IllegalArgumentException> {
            EqualSplitCalculator.calculate(
                totalAmount = 100L,
                payerId = 1L,
                consumerIds = emptyList()
            )
        }
        assertEquals("Consumer list cannot be empty", ex.message)
    }
}
