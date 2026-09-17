package com.jacobcraig.debtmngr.repository

import com.jacobcraig.debtmngr.domain.Entry
import com.jacobcraig.debtmngr.domain.EntryType
import com.jacobcraig.debtmngr.domain.Group
import com.jacobcraig.debtmngr.domain.Participant
import com.jacobcraig.debtmngr.domain.Transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TransactionRepositoryTest @Autowired constructor(
    private val entityManager: TestEntityManager,
    private val transactionRepository: TransactionRepository
) {

    @Test
    fun `save transaction with cascade-persisted entries and retrieve by id`() {
        val group = entityManager.persist(Group(name = "Apartment"))
        val alice = entityManager.persist(Participant(group = group, name = "Alice"))
        val bob = entityManager.persist(Participant(group = group, name = "Bob"))

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

        val saved = transactionRepository.save(tx)
        entityManager.flush()
        entityManager.clear()

        val found = transactionRepository.findById(checkNotNull(saved.id)).orElse(null)
        assertNotNull(found)
        assertEquals("Groceries", found.description)
        assertEquals(1000L, found.amount)
        assertEquals(3, found.entries.size)
        assertEquals(1000L, found.totalCredits())
        assertEquals(1000L, found.totalDebits())
    }

    @Test
    fun `findByGroupIdAndIsDeletedFalseOrderByCreatedAtDescIdDesc returns only active transactions ordered`() {
        val group = entityManager.persist(Group(name = "Trip to Spain"))
        val alice = entityManager.persist(Participant(group = group, name = "Alice"))

        val tx1 = Transaction(group = group, description = "Flight", amount = 20000L, payer = alice)
        tx1.addEntry(Entry(transaction = tx1, account = alice.account, type = EntryType.CREDIT, amount = 20000L))
        tx1.addEntry(Entry(transaction = tx1, account = alice.account, type = EntryType.DEBIT, amount = 20000L))

        val tx2 = Transaction(group = group, description = "Hotel", amount = 15000L, payer = alice)
        tx2.addEntry(Entry(transaction = tx2, account = alice.account, type = EntryType.CREDIT, amount = 15000L))
        tx2.addEntry(Entry(transaction = tx2, account = alice.account, type = EntryType.DEBIT, amount = 15000L))

        val tx3Deleted = Transaction(group = group, description = "Cancelled Taxi", amount = 3000L, payer = alice, isDeleted = true)
        tx3Deleted.addEntry(Entry(transaction = tx3Deleted, account = alice.account, type = EntryType.CREDIT, amount = 3000L))
        tx3Deleted.addEntry(Entry(transaction = tx3Deleted, account = alice.account, type = EntryType.DEBIT, amount = 3000L))

        transactionRepository.save(tx1)
        transactionRepository.save(tx2)
        transactionRepository.save(tx3Deleted)
        entityManager.flush()
        entityManager.clear()

        val active = transactionRepository.findByGroupIdAndIsDeletedFalseOrderByCreatedAtDescIdDesc(checkNotNull(group.id))
        assertEquals(2, active.size)
        // Should contain Hotel and Flight, but not Cancelled Taxi
        val descriptions = active.map { it.description }
        assertTrue(descriptions.contains("Hotel"))
        assertTrue(descriptions.contains("Flight"))
        assertFalse(descriptions.contains("Cancelled Taxi"))
    }
}
