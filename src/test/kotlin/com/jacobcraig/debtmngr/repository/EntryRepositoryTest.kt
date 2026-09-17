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
class EntryRepositoryTest @Autowired constructor(
    private val entityManager: TestEntityManager,
    private val entryRepository: EntryRepository
) {

    @Test
    fun `findByAccountId and findActiveByGroupId retrieve active entries`() {
        val group = entityManager.persist(Group(name = "Apartment"))
        val alice = entityManager.persist(Participant(group = group, name = "Alice"))
        val bob = entityManager.persist(Participant(group = group, name = "Bob"))

        val tx1 = Transaction(group = group, description = "Groceries", amount = 1000L, payer = alice)
        val creditAlice = Entry(transaction = tx1, account = alice.account, type = EntryType.CREDIT, amount = 1000L)
        val debitAlice = Entry(transaction = tx1, account = alice.account, type = EntryType.DEBIT, amount = 500L)
        val debitBob = Entry(transaction = tx1, account = bob.account, type = EntryType.DEBIT, amount = 500L)
        tx1.addEntry(creditAlice)
        tx1.addEntry(debitAlice)
        tx1.addEntry(debitBob)
        entityManager.persist(tx1)

        val txDeleted = Transaction(group = group, description = "Mistake", amount = 200L, payer = alice, isDeleted = true)
        val creditAliceDeleted = Entry(transaction = txDeleted, account = alice.account, type = EntryType.CREDIT, amount = 200L)
        val debitBobDeleted = Entry(transaction = txDeleted, account = bob.account, type = EntryType.DEBIT, amount = 200L)
        txDeleted.addEntry(creditAliceDeleted)
        txDeleted.addEntry(debitBobDeleted)
        entityManager.persist(txDeleted)

        entityManager.flush()
        entityManager.clear()

        // 1. findByAccountId returns all entries for an account (including deleted transaction entries)
        val aliceEntries = entryRepository.findByAccountId(checkNotNull(alice.account.id))
        assertEquals(3, aliceEntries.size)

        // 2. findActiveByGroupId returns entries only from non-deleted transactions for the group
        val activeGroupEntries = entryRepository.findActiveByGroupId(checkNotNull(group.id))
        assertEquals(3, activeGroupEntries.size)
        assertTrue(activeGroupEntries.all { it.transaction.description == "Groceries" })
    }
}
