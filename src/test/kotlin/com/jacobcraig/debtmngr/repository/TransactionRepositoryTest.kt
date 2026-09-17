package com.jacobcraig.debtmngr.repository

import com.jacobcraig.debtmngr.domain.Category
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

    @Test
    fun `findByGroupIdAndCategoryIdAndIsDeletedFalseOrderByCreatedAtDescIdDesc filters by category`() {
        val group = entityManager.persist(Group(name = "Trip to Spain"))
        val alice = entityManager.persist(Participant(group = group, name = "Alice"))
        val groceriesCat = entityManager.persist(Category(name = "Groceries", systemKey = "GROCERIES"))
        val transportCat = entityManager.persist(Category(name = "Transport", systemKey = "TRANSPORT"))

        val tx1 = Transaction(group = group, description = "Supermarket", amount = 5000L, payer = alice, category = groceriesCat)
        tx1.addEntry(Entry(transaction = tx1, account = alice.account, type = EntryType.CREDIT, amount = 5000L))
        tx1.addEntry(Entry(transaction = tx1, account = alice.account, type = EntryType.DEBIT, amount = 5000L))

        val tx2 = Transaction(group = group, description = "Metro", amount = 1000L, payer = alice, category = transportCat)
        tx2.addEntry(Entry(transaction = tx2, account = alice.account, type = EntryType.CREDIT, amount = 1000L))
        tx2.addEntry(Entry(transaction = tx2, account = alice.account, type = EntryType.DEBIT, amount = 1000L))

        transactionRepository.save(tx1)
        transactionRepository.save(tx2)
        entityManager.flush()
        entityManager.clear()

        val filtered = transactionRepository.findByGroupIdAndCategoryIdAndIsDeletedFalseOrderByCreatedAtDescIdDesc(
            groupId = checkNotNull(group.id),
            categoryId = checkNotNull(groceriesCat.id)
        )
        assertEquals(1, filtered.size)
        assertEquals("Supermarket", filtered[0].description)
        assertEquals("Groceries", filtered[0].category?.name)
    }

    @Test
    fun `findUnlockedTransactionsBetweenAccounts returns only transactions between two accounts prior to cutoff`() {
        val group = entityManager.persist(Group(name = "Apartment"))
        val alice = entityManager.persist(Participant(group = group, name = "Alice"))
        val bob = entityManager.persist(Participant(group = group, name = "Bob"))
        val charlie = entityManager.persist(Participant(group = group, name = "Charlie"))

        val aliceAccId = checkNotNull(alice.account.id)
        val bobAccId = checkNotNull(bob.account.id)

        val t0 = java.time.Instant.parse("2026-09-01T10:00:00Z")
        val t1 = java.time.Instant.parse("2026-09-02T10:00:00Z")
        val t2 = java.time.Instant.parse("2026-09-03T10:00:00Z")
        val settlementTime = java.time.Instant.parse("2026-09-04T10:00:00Z")
        val futureTime = java.time.Instant.parse("2026-09-05T10:00:00Z")

        // tx1: Alice paid, Alice and Bob consumed, unlocked, non-deleted, prior -> INCLUDED
        val tx1 = Transaction(group = group, description = "Groceries", amount = 2000L, payer = alice, createdAt = t0)
        tx1.addEntry(Entry(transaction = tx1, account = alice.account, type = EntryType.CREDIT, amount = 2000L))
        tx1.addEntry(Entry(transaction = tx1, account = alice.account, type = EntryType.DEBIT, amount = 1000L))
        tx1.addEntry(Entry(transaction = tx1, account = bob.account, type = EntryType.DEBIT, amount = 1000L))

        // tx2: Charlie paid, Alice and Bob both consumed (both DEBIT entries, no debt between Alice & Bob) -> EXCLUDED
        val tx2 = Transaction(group = group, description = "Charlie Treats", amount = 2000L, payer = charlie, createdAt = t1)
        tx2.addEntry(Entry(transaction = tx2, account = charlie.account, type = EntryType.CREDIT, amount = 2000L))
        tx2.addEntry(Entry(transaction = tx2, account = alice.account, type = EntryType.DEBIT, amount = 1000L))
        tx2.addEntry(Entry(transaction = tx2, account = bob.account, type = EntryType.DEBIT, amount = 1000L))

        // tx3: Bob paid, Bob and Alice consumed, but already locked -> EXCLUDED
        val tx3 = Transaction(group = group, description = "Electricity", amount = 1000L, payer = bob, isLocked = true, createdAt = t2)
        tx3.addEntry(Entry(transaction = tx3, account = bob.account, type = EntryType.CREDIT, amount = 1000L))
        tx3.addEntry(Entry(transaction = tx3, account = alice.account, type = EntryType.DEBIT, amount = 1000L))

        // tx4: Alice paid, Bob consumed, but future timestamp -> EXCLUDED
        val tx4 = Transaction(group = group, description = "Future Purchase", amount = 1000L, payer = alice, createdAt = futureTime)
        tx4.addEntry(Entry(transaction = tx4, account = alice.account, type = EntryType.CREDIT, amount = 1000L))
        tx4.addEntry(Entry(transaction = tx4, account = bob.account, type = EntryType.DEBIT, amount = 1000L))

        val saved1 = transactionRepository.save(tx1)
        transactionRepository.save(tx2)
        transactionRepository.save(tx3)
        transactionRepository.save(tx4)
        entityManager.flush()
        entityManager.clear()

        val results = transactionRepository.findUnlockedTransactionsBetweenAccounts(
            groupId = checkNotNull(group.id),
            account1Id = aliceAccId,
            account2Id = bobAccId,
            beforeInstant = settlementTime,
            excludeId = -1L
        )

        assertEquals(1, results.size)
        assertEquals(saved1.id, results[0].id)
        assertEquals("Groceries", results[0].description)
        assertFalse(results[0].isLocked)
        assertFalse(results[0].isDeleted)
    }
}
