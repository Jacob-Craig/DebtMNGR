package com.jacobcraig.debtmngr.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import kotlin.test.assertNotNull

class TransactionTest {

    @Test
    fun `default transaction initialization`() {
        val group = Group(name = "Apartment")
        val payer = Participant(group = group, name = "Alice")
        val tx = Transaction(
            group = group,
            description = "Groceries",
            amount = 1000L,
            payer = payer
        )

        assertNull(tx.id)
        assertSame(group, tx.group)
        assertEquals("Groceries", tx.description)
        assertEquals(1000L, tx.amount)
        assertSame(payer, tx.payer)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertFalse(tx.isLocked)
        assertFalse(tx.isDeleted)
        assertNull(tx.originalTransaction)
        assertNotNull(tx.createdAt)
        assertTrue(tx.entries.isEmpty())
    }

    @Test
    fun `blank description throws IllegalArgumentException`() {
        val group = Group(name = "Apartment")
        val payer = Participant(group = group, name = "Alice")

        val ex = assertThrows<IllegalArgumentException> {
            Transaction(
                group = group,
                description = "   ",
                amount = 1000L,
                payer = payer
            )
        }
        assertEquals("Transaction description cannot be blank", ex.message)
    }

    @Test
    fun `zero or negative amount throws IllegalArgumentException`() {
        val group = Group(name = "Apartment")
        val payer = Participant(group = group, name = "Alice")

        assertThrows<IllegalArgumentException> {
            Transaction(
                group = group,
                description = "Dinner",
                amount = 0L,
                payer = payer
            )
        }

        assertThrows<IllegalArgumentException> {
            Transaction(
                group = group,
                description = "Dinner",
                amount = -100L,
                payer = payer
            )
        }
    }

    @Test
    fun `balanced entries pass double-entry validation`() {
        val group = Group(name = "Apartment")
        val alice = Participant(group = group, name = "Alice")
        val bob = Participant(group = group, name = "Bob")

        val tx = Transaction(
            group = group,
            description = "Groceries",
            amount = 1000L,
            payer = alice
        )

        val creditEntry = Entry(transaction = tx, account = alice.account, type = EntryType.CREDIT, amount = 1000L)
        val debitAlice = Entry(transaction = tx, account = alice.account, type = EntryType.DEBIT, amount = 500L)
        val debitBob = Entry(transaction = tx, account = bob.account, type = EntryType.DEBIT, amount = 500L)

        tx.addEntry(creditEntry)
        tx.addEntry(debitAlice)
        tx.addEntry(debitBob)

        assertEquals(1000L, tx.totalCredits())
        assertEquals(1000L, tx.totalDebits())
        assertTrue(tx.isBalanced())
        assertDoesNotThrow { tx.validateDoubleEntry() }
    }

    @Test
    fun `unbalanced entries throw IllegalStateException on validation`() {
        val group = Group(name = "Apartment")
        val alice = Participant(group = group, name = "Alice")
        val bob = Participant(group = group, name = "Bob")

        val tx = Transaction(
            group = group,
            description = "Groceries",
            amount = 1000L,
            payer = alice
        )

        // Only 900 debited against 1000 credited
        tx.addEntry(Entry(transaction = tx, account = alice.account, type = EntryType.CREDIT, amount = 1000L))
        tx.addEntry(Entry(transaction = tx, account = bob.account, type = EntryType.DEBIT, amount = 900L))

        assertEquals(1000L, tx.totalCredits())
        assertEquals(900L, tx.totalDebits())
        assertFalse(tx.isBalanced())

        val ex = assertThrows<IllegalStateException> {
            tx.validateDoubleEntry()
        }
        val msg = ex.message
        assertNotNull(msg)
        assertTrue(msg.contains("Double-entry violation"))
    }

    @Test
    fun `entries matching each other but not matching transaction amount throw IllegalStateException`() {
        val group = Group(name = "Apartment")
        val alice = Participant(group = group, name = "Alice")
        val bob = Participant(group = group, name = "Bob")

        val tx = Transaction(
            group = group,
            description = "Groceries",
            amount = 1000L,
            payer = alice
        )

        // Debits = credits = 800, but transaction amount is 1000
        tx.addEntry(Entry(transaction = tx, account = alice.account, type = EntryType.CREDIT, amount = 800L))
        tx.addEntry(Entry(transaction = tx, account = bob.account, type = EntryType.DEBIT, amount = 800L))

        assertFalse(tx.isBalanced())
        val ex = assertThrows<IllegalStateException> {
            tx.validateDoubleEntry()
        }
        val msg = ex.message
        assertNotNull(msg)
        assertTrue(msg.contains("Transaction amount"))
    }

    @Test
    fun `custom createdAt instant can be specified`() {
        val group = Group(name = "Apartment")
        val payer = Participant(group = group, name = "Alice")
        val pastInstant = Instant.parse("2026-01-01T10:00:00Z")
        val tx = Transaction(
            group = group,
            description = "Old expense",
            amount = 500L,
            payer = payer,
            createdAt = pastInstant
        )
        assertEquals(pastInstant, tx.createdAt)
    }

    @Test
    fun `can assign system category to transaction`() {
        val group = Group(name = "Apartment")
        val payer = Participant(group = group, name = "Alice")
        val category = Category(name = "Groceries", systemKey = "GROCERIES")
        val tx = Transaction(
            group = group,
            description = "Groceries run",
            amount = 500L,
            payer = payer,
            category = category
        )

        assertSame(category, tx.category)
    }

    @Test
    fun `can assign group custom category to transaction`() {
        val group = Group(name = "Apartment")
        val payer = Participant(group = group, name = "Alice")
        val category = Category(name = "Cleaning Supplies", group = group)
        val tx = Transaction(
            group = group,
            description = "Mop and bucket",
            amount = 200L,
            payer = payer,
            category = category
        )

        assertSame(category, tx.category)
    }

    @Test
    fun `assigning custom category from different group throws IllegalArgumentException`() {
        val group1 = Group(id = 1L, name = "Apartment 1")
        val group2 = Group(id = 2L, name = "Apartment 2")
        val payer = Participant(group = group1, name = "Alice")
        val categoryGroup2 = Category(id = 10L, name = "Custom in Group 2", group = group2)

        val ex = assertThrows<IllegalArgumentException> {
            Transaction(
                group = group1,
                description = "Supplies",
                amount = 200L,
                payer = payer,
                category = categoryGroup2
            )
        }
        val msg = checkNotNull(ex.message)
        assertTrue(msg.contains("does not belong to group"))
    }

    @Test
    fun `settlement transaction initialization and balanced entries`() {
        val group = Group(name = "Apartment")
        val alice = Participant(group = group, name = "Alice")
        val bob = Participant(group = group, name = "Bob")

        val settlement = Transaction(
            group = group,
            description = "Settlement: Bob paid Alice",
            amount = 1500L,
            payer = bob,
            type = TransactionType.SETTLEMENT
        )

        val creditBob = Entry(transaction = settlement, account = bob.account, type = EntryType.CREDIT, amount = 1500L)
        val debitAlice = Entry(transaction = settlement, account = alice.account, type = EntryType.DEBIT, amount = 1500L)
        settlement.addEntry(creditBob)
        settlement.addEntry(debitAlice)

        assertEquals(TransactionType.SETTLEMENT, settlement.type)
        assertFalse(settlement.isLocked)
        assertTrue(settlement.isBalanced())
        assertDoesNotThrow { settlement.validateDoubleEntry() }
    }

    @Test
    fun `transaction can be locked`() {
        val group = Group(name = "Apartment")
        val alice = Participant(group = group, name = "Alice")
        val tx = Transaction(
            group = group,
            description = "Dinner",
            amount = 2000L,
            payer = alice
        )

        assertFalse(tx.isLocked)
        tx.isLocked = true
        assertTrue(tx.isLocked)
    }
}
