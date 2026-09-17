package com.jacobcraig.debtmngr.service

import com.jacobcraig.debtmngr.domain.*
import com.jacobcraig.debtmngr.repository.EntryRepository
import com.jacobcraig.debtmngr.repository.GroupRepository
import com.jacobcraig.debtmngr.repository.ParticipantRepository
import com.jacobcraig.debtmngr.repository.TransactionRepository
import jakarta.persistence.EntityNotFoundException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import java.util.Optional

class TransactionServiceTest {

    private lateinit var groupRepository: GroupRepository
    private lateinit var participantRepository: ParticipantRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var entryRepository: EntryRepository
    private lateinit var transactionService: TransactionService

    private val group = Group(id = 1L, name = "Apartment")
    private val aliceAccount = Account(id = 100L)
    private val bobAccount = Account(id = 200L)
    private val charlieAccount = Account(id = 300L)
    private val alice = Participant(id = 10L, group = group, name = "Alice", isSelf = true, account = aliceAccount)
    private val bob = Participant(id = 20L, group = group, name = "Bob", isSelf = false, account = bobAccount)
    private val charlie = Participant(id = 30L, group = group, name = "Charlie", isSelf = false, account = charlieAccount)

    @BeforeEach
    fun setUp() {
        groupRepository = mock(GroupRepository::class.java)
        participantRepository = mock(ParticipantRepository::class.java)
        transactionRepository = mock(TransactionRepository::class.java)
        entryRepository = mock(EntryRepository::class.java)

        transactionService = TransactionService(
            groupRepository = groupRepository,
            participantRepository = participantRepository,
            transactionRepository = transactionRepository,
            entryRepository = entryRepository
        )

        `when`(groupRepository.findById(1L)).thenReturn(Optional.of(group))
        `when`(groupRepository.existsById(1L)).thenReturn(true)
        `when`(participantRepository.findById(10L)).thenReturn(Optional.of(alice))
        `when`(participantRepository.findById(20L)).thenReturn(Optional.of(bob))
        `when`(participantRepository.findById(30L)).thenReturn(Optional.of(charlie))
        `when`(participantRepository.findByGroupIdOrderByIdAsc(1L)).thenReturn(listOf(alice, bob, charlie))
    }

    @Test
    fun `createExpense with payer in consumers creates balanced transaction with odd penny to payer`() {
        // total 1000 pence (£10.00), split among Alice (payer), Bob, Charlie (N=3)
        // Alice gets credited 1000, debited 334.
        // Bob debited 333.
        // Charlie debited 333.
        val captor = ArgumentCaptor.forClass(Transaction::class.java)
        `when`(transactionRepository.save(captor.capture())).thenAnswer { it.arguments[0] }

        val result = transactionService.createExpense(
            groupId = 1L,
            payerId = 10L,
            amount = 1000L,
            description = "Groceries",
            consumerIds = listOf(10L, 20L, 30L)
        )

        assertNotNull(result)
        val captured = captor.value
        assertEquals("Groceries", captured.description)
        assertEquals(1000L, captured.amount)
        assertSame(alice, captured.payer)
        assertSame(group, captured.group)
        assertEquals(TransactionType.EXPENSE, captured.type)

        // 1 credit entry for Alice + 3 debit entries
        assertEquals(4, captured.entries.size)
        assertEquals(1000L, captured.totalCredits())
        assertEquals(1000L, captured.totalDebits())
        assertTrue(captured.isBalanced())

        val creditEntry = captured.entries.first { it.type == EntryType.CREDIT }
        assertEquals(1000L, creditEntry.amount)
        assertSame(alice.account, creditEntry.account)

        val debitEntries = captured.entries.filter { it.type == EntryType.DEBIT }
        assertEquals(3, debitEntries.size)
        val aliceDebit = debitEntries.first { it.account == alice.account }
        val bobDebit = debitEntries.first { it.account == bob.account }
        val charlieDebit = debitEntries.first { it.account == charlie.account }

        assertEquals(334L, aliceDebit.amount)
        assertEquals(333L, bobDebit.amount)
        assertEquals(333L, charlieDebit.amount)
    }

    @Test
    fun `createExpense with payer NOT in consumers creates balanced transaction with odd penny to first consumer`() {
        // Alice pays 1000 pence (£10.00), split between Bob and Charlie (N=2, payer not in consumers)
        // Alice credited 1000.
        // Bob debited 500, Charlie debited 500.
        val captor = ArgumentCaptor.forClass(Transaction::class.java)
        `when`(transactionRepository.save(captor.capture())).thenAnswer { it.arguments[0] }

        val result = transactionService.createExpense(
            groupId = 1L,
            payerId = 10L,
            amount = 1001L,
            description = "Treat",
            consumerIds = listOf(20L, 30L)
        )

        assertNotNull(result)
        val captured = captor.value
        assertEquals(1001L, captured.totalCredits())
        assertEquals(1001L, captured.totalDebits())
        assertTrue(captured.isBalanced())

        val debitEntries = captured.entries.filter { it.type == EntryType.DEBIT }
        val bobDebit = debitEntries.first { it.account == bob.account }
        val charlieDebit = debitEntries.first { it.account == charlie.account }

        // Remainder 1 goes to first consumer (Bob): 500 + 1 = 501
        assertEquals(501L, bobDebit.amount)
        assertEquals(500L, charlieDebit.amount)
    }

    @Test
    fun `createExpense with custom date sets createdAt on transaction`() {
        val captor = ArgumentCaptor.forClass(Transaction::class.java)
        `when`(transactionRepository.save(captor.capture())).thenAnswer { it.arguments[0] }

        val customDate = java.time.Instant.parse("2026-05-15T10:30:00Z")
        val result = transactionService.createExpense(
            groupId = 1L,
            payerId = 10L,
            amount = 1000L,
            description = "Museum",
            consumerIds = listOf(10L, 20L),
            date = customDate
        )

        assertNotNull(result)
        val captured = captor.value
        assertEquals(customDate, captured.createdAt)
    }

    @Test
    fun `createExpense deduplicates consumerIds list`() {
        val captor = ArgumentCaptor.forClass(Transaction::class.java)
        `when`(transactionRepository.save(captor.capture())).thenAnswer { it.arguments[0] }

        transactionService.createExpense(
            groupId = 1L,
            payerId = 10L,
            amount = 1000L,
            description = "Groceries",
            consumerIds = listOf(10L, 20L, 20L, 30L, 30L) // duplicates
        )

        val captured = captor.value
        val debitEntries = captured.entries.filter { it.type == EntryType.DEBIT }
        assertEquals(3, debitEntries.size)
    }

    @Test
    fun `createExpense with blank description throws IllegalArgumentException`() {
        val ex = assertThrows<IllegalArgumentException> {
            transactionService.createExpense(
                groupId = 1L,
                payerId = 10L,
                amount = 1000L,
                description = "   ",
                consumerIds = listOf(10L, 20L)
            )
        }
        assertEquals("Expense description cannot be blank", ex.message)
        verify(transactionRepository, never()).save(any())
    }

    @Test
    fun `createExpense with non-positive amount throws IllegalArgumentException`() {
        val ex = assertThrows<IllegalArgumentException> {
            transactionService.createExpense(
                groupId = 1L,
                payerId = 10L,
                amount = 0L,
                description = "Dinner",
                consumerIds = listOf(10L, 20L)
            )
        }
        assertEquals("Expense amount must be positive", ex.message)
        verify(transactionRepository, never()).save(any())
    }

    @Test
    fun `createExpense with empty consumers throws IllegalArgumentException`() {
        val ex = assertThrows<IllegalArgumentException> {
            transactionService.createExpense(
                groupId = 1L,
                payerId = 10L,
                amount = 1000L,
                description = "Dinner",
                consumerIds = emptyList()
            )
        }
        assertEquals("At least one consumer must be selected", ex.message)
        verify(transactionRepository, never()).save(any())
    }

    @Test
    fun `createExpense with nonexistent group throws EntityNotFoundException`() {
        `when`(groupRepository.findById(99L)).thenReturn(Optional.empty())

        val ex = assertThrows<EntityNotFoundException> {
            transactionService.createExpense(
                groupId = 99L,
                payerId = 10L,
                amount = 1000L,
                description = "Dinner",
                consumerIds = listOf(10L, 20L)
            )
        }
        assertEquals("Group not found with id: 99", ex.message)
    }

    @Test
    fun `createExpense with nonexistent payer throws EntityNotFoundException`() {
        `when`(participantRepository.findById(99L)).thenReturn(Optional.empty())

        val ex = assertThrows<EntityNotFoundException> {
            transactionService.createExpense(
                groupId = 1L,
                payerId = 99L,
                amount = 1000L,
                description = "Dinner",
                consumerIds = listOf(10L, 20L)
            )
        }
        assertEquals("Participant not found with id: 99", ex.message)
    }

    @Test
    fun `createExpense with payer belonging to another group throws IllegalArgumentException`() {
        val otherGroup = Group(id = 2L, name = "Other")
        val otherPayer = Participant(id = 99L, group = otherGroup, name = "Dave")
        `when`(participantRepository.findById(99L)).thenReturn(Optional.of(otherPayer))

        val ex = assertThrows<IllegalArgumentException> {
            transactionService.createExpense(
                groupId = 1L,
                payerId = 99L,
                amount = 1000L,
                description = "Dinner",
                consumerIds = listOf(10L, 20L)
            )
        }
        assertEquals("Payer does not belong to group 1", ex.message)
    }

    @Test
    fun `createExpense with consumer belonging to another group throws IllegalArgumentException`() {
        val otherGroup = Group(id = 2L, name = "Other")
        val otherConsumer = Participant(id = 99L, group = otherGroup, name = "Dave")
        `when`(participantRepository.findById(99L)).thenReturn(Optional.of(otherConsumer))

        val ex = assertThrows<IllegalArgumentException> {
            transactionService.createExpense(
                groupId = 1L,
                payerId = 10L,
                amount = 1000L,
                description = "Dinner",
                consumerIds = listOf(10L, 99L)
            )
        }
        assertEquals("Consumer 99 does not belong to group 1", ex.message)
    }

    @Test
    fun `getTransactionsForGroup returns active transactions for valid group`() {
        val tx1 = Transaction(id = 1L, group = group, description = "Groceries", amount = 1000L, payer = alice)
        val tx2 = Transaction(id = 2L, group = group, description = "Utilities", amount = 5000L, payer = bob)
        `when`(transactionRepository.findByGroupIdAndIsDeletedFalseOrderByCreatedAtDescIdDesc(1L))
            .thenReturn(listOf(tx2, tx1))

        val result = transactionService.getTransactionsForGroup(1L)

        assertEquals(2, result.size)
        assertEquals(tx2, result[0])
        assertEquals(tx1, result[1])
        verify(transactionRepository).findByGroupIdAndIsDeletedFalseOrderByCreatedAtDescIdDesc(1L)
    }

    @Test
    fun `getTransactionsForGroup with nonexistent group throws EntityNotFoundException`() {
        `when`(groupRepository.existsById(99L)).thenReturn(false)

        val ex = assertThrows<EntityNotFoundException> {
            transactionService.getTransactionsForGroup(99L)
        }
        assertEquals("Group not found with id: 99", ex.message)
    }

    @Test
    fun `getParticipantBalances calculates correct net balances for all group participants`() {
        // Alice paid 3000 split among Alice, Bob, Charlie (1000 each)
        // Alice: CREDIT 3000, DEBIT 1000 -> net +2000
        // Bob: DEBIT 1000 -> net -1000
        // Charlie: DEBIT 1000 -> net -1000
        val tx = Transaction(id = 1L, group = group, description = "Dinner", amount = 3000L, payer = alice)
        val entries = listOf(
            Entry(id = 1L, transaction = tx, account = alice.account, type = EntryType.CREDIT, amount = 3000L),
            Entry(id = 2L, transaction = tx, account = alice.account, type = EntryType.DEBIT, amount = 1000L),
            Entry(id = 3L, transaction = tx, account = bob.account, type = EntryType.DEBIT, amount = 1000L),
            Entry(id = 4L, transaction = tx, account = charlie.account, type = EntryType.DEBIT, amount = 1000L)
        )
        `when`(entryRepository.findActiveByGroupId(1L)).thenReturn(entries)

        val balances = transactionService.getParticipantBalances(1L)

        assertEquals(3, balances.size)
        assertEquals(2000L, balances[10L]) // Alice
        assertEquals(-1000L, balances[20L]) // Bob
        assertEquals(-1000L, balances[30L]) // Charlie
        // Sum of all balances is 0 (double-entry zero-sum invariant)
        assertEquals(0L, balances.values.sum())
    }

    @Test
    fun `getParticipantBalances returns zero for participants with no entries`() {
        `when`(entryRepository.findActiveByGroupId(1L)).thenReturn(emptyList())

        val balances = transactionService.getParticipantBalances(1L)

        assertEquals(3, balances.size)
        assertEquals(0L, balances[10L])
        assertEquals(0L, balances[20L])
        assertEquals(0L, balances[30L])
    }

    @Test
    fun `getParticipantBalances with nonexistent group throws EntityNotFoundException`() {
        `when`(groupRepository.existsById(99L)).thenReturn(false)

        val ex = assertThrows<EntityNotFoundException> {
            transactionService.getParticipantBalances(99L)
        }
        assertEquals("Group not found with id: 99", ex.message)
    }
}
