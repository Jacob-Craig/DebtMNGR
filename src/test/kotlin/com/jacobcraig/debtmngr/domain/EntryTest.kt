package com.jacobcraig.debtmngr.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EntryTest {

    @Test
    fun `valid entry initialization`() {
        val group = Group(name = "Apartment")
        val payer = Participant(group = group, name = "Alice")
        val transaction = Transaction(
            group = group,
            description = "Groceries",
            amount = 1000L,
            payer = payer
        )
        val entry = Entry(
            transaction = transaction,
            account = payer.account,
            type = EntryType.CREDIT,
            amount = 1000L
        )

        assertNull(entry.id)
        assertSame(transaction, entry.transaction)
        assertSame(payer.account, entry.account)
        assertEquals(EntryType.CREDIT, entry.type)
        assertEquals(1000L, entry.amount)
        assertNotNull(entry.createdAt)
    }

    @Test
    fun `entry with zero amount throws IllegalArgumentException`() {
        val group = Group(name = "Apartment")
        val payer = Participant(group = group, name = "Alice")
        val transaction = Transaction(
            group = group,
            description = "Groceries",
            amount = 1000L,
            payer = payer
        )

        val ex = assertThrows<IllegalArgumentException> {
            Entry(
                transaction = transaction,
                account = payer.account,
                type = EntryType.DEBIT,
                amount = 0L
            )
        }
        assertEquals("Entry amount must be positive", ex.message)
    }

    @Test
    fun `entry with negative amount throws IllegalArgumentException`() {
        val group = Group(name = "Apartment")
        val payer = Participant(group = group, name = "Alice")
        val transaction = Transaction(
            group = group,
            description = "Groceries",
            amount = 1000L,
            payer = payer
        )

        val ex = assertThrows<IllegalArgumentException> {
            Entry(
                transaction = transaction,
                account = payer.account,
                type = EntryType.DEBIT,
                amount = -50L
            )
        }
        assertEquals("Entry amount must be positive", ex.message)
    }
}
