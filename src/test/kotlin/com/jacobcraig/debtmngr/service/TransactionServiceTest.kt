package com.jacobcraig.debtmngr.service

import com.jacobcraig.debtmngr.domain.*
import com.jacobcraig.debtmngr.repository.AuditLogRepository
import com.jacobcraig.debtmngr.repository.CategoryRepository
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
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var auditLogRepository: AuditLogRepository
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
        categoryRepository = mock(CategoryRepository::class.java)
        auditLogRepository = mock(AuditLogRepository::class.java)

        transactionService = TransactionService(
            groupRepository = groupRepository,
            participantRepository = participantRepository,
            transactionRepository = transactionRepository,
            entryRepository = entryRepository,
            categoryRepository = categoryRepository,
            auditLogRepository = auditLogRepository,
            objectMapper = tools.jackson.databind.json.JsonMapper.builder().build()
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
    fun `createExpense with splitMode EXACT creates balanced transaction with exact split entries`() {
        val captor = ArgumentCaptor.forClass(Transaction::class.java)
        `when`(transactionRepository.save(captor.capture())).thenAnswer { it.arguments[0] }

        val result = transactionService.createExpense(
            groupId = 1L,
            payerId = 10L,
            amount = 1000L,
            description = "Concert tickets",
            splitMode = SplitMode.EXACT,
            exactAmounts = mapOf(10L to 300L, 20L to 700L)
        )

        assertNotNull(result)
        val captured = captor.value
        assertEquals("Concert tickets", captured.description)
        assertEquals(1000L, captured.amount)
        assertSame(alice, captured.payer)
        assertTrue(captured.isBalanced())

        val creditEntry = captured.entries.first { it.type == EntryType.CREDIT }
        assertEquals(1000L, creditEntry.amount)
        assertSame(alice.account, creditEntry.account)

        val debitEntries = captured.entries.filter { it.type == EntryType.DEBIT }
        assertEquals(2, debitEntries.size)
        val aliceDebit = debitEntries.first { it.account == alice.account }
        val bobDebit = debitEntries.first { it.account == bob.account }
        assertEquals(300L, aliceDebit.amount)
        assertEquals(700L, bobDebit.amount)
    }

    @Test
    fun `createExpense with splitMode EXACT creates debit entries only for positive shares`() {
        val captor = ArgumentCaptor.forClass(Transaction::class.java)
        `when`(transactionRepository.save(captor.capture())).thenAnswer { it.arguments[0] }

        val result = transactionService.createExpense(
            groupId = 1L,
            payerId = 10L,
            amount = 1000L,
            description = "Solo purchase",
            splitMode = SplitMode.EXACT,
            exactAmounts = mapOf(10L to 1000L, 20L to 0L)
        )

        assertNotNull(result)
        val captured = captor.value
        val debitEntries = captured.entries.filter { it.type == EntryType.DEBIT }
        assertEquals(1, debitEntries.size)
        assertEquals(1000L, debitEntries.first().amount)
        assertSame(alice.account, debitEntries.first().account)
    }

    @Test
    fun `createExpense with splitMode EXACT and sum mismatch throws IllegalArgumentException`() {
        val ex = assertThrows<IllegalArgumentException> {
            transactionService.createExpense(
                groupId = 1L,
                payerId = 10L,
                amount = 1000L,
                description = "Uneven",
                splitMode = SplitMode.EXACT,
                exactAmounts = mapOf(10L to 400L, 20L to 500L)
            )
        }
        val msg = checkNotNull(ex.message)
        assertTrue(msg.contains("The sum of exact split amounts (900) must equal the total expense amount (1000)"))
        verify(transactionRepository, never()).save(any())
    }

    @Test
    fun `createExpense with splitMode EXACT and empty exact amounts throws IllegalArgumentException`() {
        val ex = assertThrows<IllegalArgumentException> {
            transactionService.createExpense(
                groupId = 1L,
                payerId = 10L,
                amount = 1000L,
                description = "Empty",
                splitMode = SplitMode.EXACT,
                exactAmounts = emptyMap<Long, Long>()
            )
        }
        val msg = checkNotNull(ex.message)
        assertEquals("At least one consumer must be assigned an amount", msg)
        verify(transactionRepository, never()).save(any())
    }

    @Test
    fun `createExpense with splitMode EXACT and participant from another group throws IllegalArgumentException`() {
        val otherGroup = Group(id = 2L, name = "Other")
        val otherParticipant = Participant(id = 99L, group = otherGroup, name = "Dave")
        `when`(participantRepository.findById(99L)).thenReturn(Optional.of(otherParticipant))

        val ex = assertThrows<IllegalArgumentException> {
            transactionService.createExpense(
                groupId = 1L,
                payerId = 10L,
                amount = 1000L,
                description = "Cross-group exact",
                splitMode = SplitMode.EXACT,
                exactAmounts = mapOf(10L to 500L, 99L to 500L)
            )
        }
        assertEquals("Participant 99 does not belong to group 1", ex.message)
        verify(transactionRepository, never()).save(any())
    }

    @Test
    fun `createExpense with splitMode EXACT and nonexistent participant throws EntityNotFoundException`() {
        `when`(participantRepository.findById(99L)).thenReturn(Optional.empty())

        val ex = assertThrows<EntityNotFoundException> {
            transactionService.createExpense(
                groupId = 1L,
                payerId = 10L,
                amount = 1000L,
                description = "Nonexistent participant",
                splitMode = SplitMode.EXACT,
                exactAmounts = mapOf(10L to 500L, 99L to 500L)
            )
        }
        assertEquals("Participant not found with id: 99", ex.message)
        verify(transactionRepository, never()).save(any())
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
    fun `createExpense with valid system categoryId sets category on transaction`() {
        val sysCat = Category(id = 50L, name = "Groceries", systemKey = "GROCERIES")
        `when`(categoryRepository.findById(50L)).thenReturn(Optional.of(sysCat))

        val captor = ArgumentCaptor.forClass(Transaction::class.java)
        `when`(transactionRepository.save(captor.capture())).thenAnswer { it.arguments[0] }

        val result = transactionService.createExpense(
            groupId = 1L,
            payerId = 10L,
            amount = 1000L,
            description = "Groceries run",
            consumerIds = listOf(10L, 20L),
            categoryId = 50L
        )

        assertNotNull(result)
        val captured = captor.value
        assertSame(sysCat, captured.category)
    }

    @Test
    fun `createExpense with valid group custom categoryId sets category on transaction`() {
        val customCat = Category(id = 60L, name = "Cleaning", group = group)
        `when`(categoryRepository.findById(60L)).thenReturn(Optional.of(customCat))

        val captor = ArgumentCaptor.forClass(Transaction::class.java)
        `when`(transactionRepository.save(captor.capture())).thenAnswer { it.arguments[0] }

        val result = transactionService.createExpense(
            groupId = 1L,
            payerId = 10L,
            amount = 1000L,
            description = "Cleaning supplies",
            consumerIds = listOf(10L, 20L),
            categoryId = 60L
        )

        assertNotNull(result)
        val captured = captor.value
        assertSame(customCat, captured.category)
    }

    @Test
    fun `createExpense with categoryId from different group throws IllegalArgumentException`() {
        val otherGroup = Group(id = 2L, name = "Other")
        val otherCat = Category(id = 70L, name = "Other Group Cat", group = otherGroup)
        `when`(categoryRepository.findById(70L)).thenReturn(Optional.of(otherCat))

        val ex = assertThrows<IllegalArgumentException> {
            transactionService.createExpense(
                groupId = 1L,
                payerId = 10L,
                amount = 1000L,
                description = "Supplies",
                consumerIds = listOf(10L, 20L),
                categoryId = 70L
            )
        }
        assertTrue(checkNotNull(ex.message).contains("does not belong to group 1"))
        verify(transactionRepository, never()).save(any())
    }

    @Test
    fun `createExpense with nonexistent categoryId throws EntityNotFoundException`() {
        `when`(categoryRepository.findById(99L)).thenReturn(Optional.empty())

        val ex = assertThrows<EntityNotFoundException> {
            transactionService.createExpense(
                groupId = 1L,
                payerId = 10L,
                amount = 1000L,
                description = "Supplies",
                consumerIds = listOf(10L, 20L),
                categoryId = 99L
            )
        }
        assertEquals("Category not found with id: 99", ex.message)
        verify(transactionRepository, never()).save(any())
    }

    @Test
    fun `getTransactionsForGroup with categoryId filters by category`() {
        val tx1 = Transaction(id = 1L, group = group, description = "Groceries", amount = 1000L, payer = alice)
        `when`(transactionRepository.findByGroupIdAndCategoryIdAndIsDeletedFalseOrderByCreatedAtDescIdDesc(1L, 50L))
            .thenReturn(listOf(tx1))

        val result = transactionService.getTransactionsForGroup(1L, categoryId = 50L)

        assertEquals(1, result.size)
        assertEquals(tx1, result[0])
        verify(transactionRepository).findByGroupIdAndCategoryIdAndIsDeletedFalseOrderByCreatedAtDescIdDesc(1L, 50L)
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

    @Test
    fun `createSettlement creates balanced SETTLEMENT transaction and locks prior unlocked transactions involving both participants`() {
        val priorTx = Transaction(
            id = 101L,
            group = group,
            description = "Dinner",
            amount = 2000L,
            payer = alice,
            isLocked = false
        )
        `when`(
            transactionRepository.findUnlockedTransactionsBetweenAccounts(
                eq(1L),
                eq(checkNotNull(bobAccount.id)),
                eq(checkNotNull(aliceAccount.id)),
                any(java.time.Instant::class.java) ?: java.time.Instant.now(),
                anyLong()
            )
        ).thenReturn(listOf(priorTx))

        val captor = ArgumentCaptor.forClass(Transaction::class.java)
        `when`(transactionRepository.save(captor.capture())).thenAnswer { it.arguments[0] }

        val result = transactionService.createSettlement(
            groupId = 1L,
            payerId = 20L, // Bob
            receiverId = 10L, // Alice
            amount = 1000L
        )

        assertNotNull(result)
        // Verify prior transaction was locked
        assertTrue(priorTx.isLocked)
        verify(transactionRepository).save(priorTx)

        // Verify the created settlement transaction
        val captured = captor.allValues.first { it.type == TransactionType.SETTLEMENT }
        assertEquals("Settlement: Bob paid Alice", captured.description)
        assertEquals(1000L, captured.amount)
        assertSame(bob, captured.payer)
        assertSame(group, captured.group)
        assertEquals(TransactionType.SETTLEMENT, captured.type)
        assertFalse(captured.isLocked)
        assertNull(captured.category)

        assertEquals(2, captured.entries.size)
        assertEquals(1000L, captured.totalCredits())
        assertEquals(1000L, captured.totalDebits())
        assertTrue(captured.isBalanced())

        val creditEntry = captured.entries.first { it.type == EntryType.CREDIT }
        assertEquals(1000L, creditEntry.amount)
        assertSame(bob.account, creditEntry.account)

        val debitEntry = captured.entries.first { it.type == EntryType.DEBIT }
        assertEquals(1000L, debitEntry.amount)
        assertSame(alice.account, debitEntry.account)
    }

    @Test
    fun `createSettlement with custom notes appends notes to description`() {
        `when`(
            transactionRepository.findUnlockedTransactionsBetweenAccounts(
                eq(1L),
                eq(checkNotNull(bobAccount.id)),
                eq(checkNotNull(aliceAccount.id)),
                any(java.time.Instant::class.java) ?: java.time.Instant.now(),
                anyLong()
            )
        ).thenReturn(emptyList())

        val captor = ArgumentCaptor.forClass(Transaction::class.java)
        `when`(transactionRepository.save(captor.capture())).thenAnswer { it.arguments[0] }

        val result = transactionService.createSettlement(
            groupId = 1L,
            payerId = 20L,
            receiverId = 10L,
            amount = 500L,
            notes = "Monzo transfer"
        )

        assertNotNull(result)
        val captured = captor.allValues.first { it.type == TransactionType.SETTLEMENT }
        assertEquals("Settlement: Bob paid Alice - Monzo transfer", captured.description)
    }

    @Test
    fun `createSettlement with custom date sets createdAt on transaction`() {
        `when`(
            transactionRepository.findUnlockedTransactionsBetweenAccounts(
                eq(1L),
                eq(checkNotNull(bobAccount.id)),
                eq(checkNotNull(aliceAccount.id)),
                any(java.time.Instant::class.java) ?: java.time.Instant.now(),
                anyLong()
            )
        ).thenReturn(emptyList())

        val captor = ArgumentCaptor.forClass(Transaction::class.java)
        `when`(transactionRepository.save(captor.capture())).thenAnswer { it.arguments[0] }

        val customDate = java.time.Instant.parse("2026-06-01T12:00:00Z")
        val result = transactionService.createSettlement(
            groupId = 1L,
            payerId = 20L,
            receiverId = 10L,
            amount = 500L,
            date = customDate
        )

        assertNotNull(result)
        val captured = captor.allValues.first { it.type == TransactionType.SETTLEMENT }
        assertEquals(customDate, captured.createdAt)
    }

    @Test
    fun `createSettlement with same payer and receiver throws IllegalArgumentException`() {
        val ex = assertThrows<IllegalArgumentException> {
            transactionService.createSettlement(
                groupId = 1L,
                payerId = 10L,
                receiverId = 10L,
                amount = 500L
            )
        }
        assertEquals("Payer and receiver cannot be the same participant", ex.message)
        verify(transactionRepository, never()).save(any())
    }

    @Test
    fun `createSettlement with non-positive amount throws IllegalArgumentException`() {
        val ex = assertThrows<IllegalArgumentException> {
            transactionService.createSettlement(
                groupId = 1L,
                payerId = 20L,
                receiverId = 10L,
                amount = 0L
            )
        }
        assertEquals("Settlement amount must be positive", ex.message)
        verify(transactionRepository, never()).save(any())
    }

    @Test
    fun `createSettlement with nonexistent group throws EntityNotFoundException`() {
        `when`(groupRepository.findById(99L)).thenReturn(Optional.empty())

        val ex = assertThrows<EntityNotFoundException> {
            transactionService.createSettlement(
                groupId = 99L,
                payerId = 20L,
                receiverId = 10L,
                amount = 500L
            )
        }
        assertEquals("Group not found with id: 99", ex.message)
    }

    @Test
    fun `createSettlement with nonexistent payer throws EntityNotFoundException`() {
        `when`(participantRepository.findById(99L)).thenReturn(Optional.empty())

        val ex = assertThrows<EntityNotFoundException> {
            transactionService.createSettlement(
                groupId = 1L,
                payerId = 99L,
                receiverId = 10L,
                amount = 500L
            )
        }
        assertEquals("Participant not found with id: 99", ex.message)
    }

    @Test
    fun `createSettlement with nonexistent receiver throws EntityNotFoundException`() {
        `when`(participantRepository.findById(99L)).thenReturn(Optional.empty())

        val ex = assertThrows<EntityNotFoundException> {
            transactionService.createSettlement(
                groupId = 1L,
                payerId = 20L,
                receiverId = 99L,
                amount = 500L
            )
        }
        assertEquals("Participant not found with id: 99", ex.message)
    }

    @Test
    fun `createSettlement with payer from another group throws IllegalArgumentException`() {
        val otherGroup = Group(id = 2L, name = "Other")
        val otherPayer = Participant(id = 99L, group = otherGroup, name = "Dave")
        `when`(participantRepository.findById(99L)).thenReturn(Optional.of(otherPayer))

        val ex = assertThrows<IllegalArgumentException> {
            transactionService.createSettlement(
                groupId = 1L,
                payerId = 99L,
                receiverId = 10L,
                amount = 500L
            )
        }
        assertEquals("Payer does not belong to group 1", ex.message)
    }

    @Test
    fun `createSettlement with receiver from another group throws IllegalArgumentException`() {
        val otherGroup = Group(id = 2L, name = "Other")
        val otherReceiver = Participant(id = 99L, group = otherGroup, name = "Dave")
        `when`(participantRepository.findById(99L)).thenReturn(Optional.of(otherReceiver))

        val ex = assertThrows<IllegalArgumentException> {
            transactionService.createSettlement(
                groupId = 1L,
                payerId = 20L,
                receiverId = 99L,
                amount = 500L
            )
        }
        assertEquals("Receiver does not belong to group 1", ex.message)
    }

    @Test
    fun `getTransaction returns transaction when found`() {
        val tx = Transaction(id = 500L, group = group, description = "Lunch", amount = 1500L, payer = alice)
        `when`(transactionRepository.findById(500L)).thenReturn(Optional.of(tx))

        val result = transactionService.getTransaction(500L)
        assertSame(tx, result)
        verify(transactionRepository).findById(500L)
    }

    @Test
    fun `getTransaction throws EntityNotFoundException when not found`() {
        `when`(transactionRepository.findById(999L)).thenReturn(Optional.empty())

        val ex = assertThrows<EntityNotFoundException> {
            transactionService.getTransaction(999L)
        }
        assertEquals("Transaction not found with id: 999", ex.message)
    }

    @Test
    fun `getAuditLogs delegates to auditLogRepository`() {
        val log = AuditLog(
            transaction = Transaction(id = 100L, group = group, description = "Old", amount = 500L, payer = alice),
            action = AuditAction.EDIT,
            serializedPriorState = "{}"
        )
        `when`(auditLogRepository.findByTransactionIdOrderByCreatedAtAscIdAsc(100L)).thenReturn(listOf(log))

        val logs = transactionService.getAuditLogs(100L)
        assertEquals(1, logs.size)
        assertSame(log, logs[0])
    }

    @Test
    fun `getAdjustments delegates to transactionRepository`() {
        val adj = Transaction(
            id = 200L,
            group = group,
            description = "Adjustment",
            amount = 300L,
            payer = alice,
            type = TransactionType.ADJUSTMENT,
            originalTransaction = Transaction(id = 100L, group = group, description = "Original", amount = 1000L, payer = alice, isLocked = true)
        )
        `when`(transactionRepository.findByOriginalTransactionIdAndIsDeletedFalseOrderByCreatedAtAscIdAsc(100L))
            .thenReturn(listOf(adj))

        val adjustments = transactionService.getAdjustments(100L)
        assertEquals(1, adjustments.size)
        assertSame(adj, adjustments[0])
    }

    @Test
    fun `deleteTransaction on unlocked transaction soft-deletes and creates DELETE AuditLog`() {
        val tx = Transaction(id = 500L, group = group, description = "Lunch", amount = 1500L, payer = alice)
        tx.addEntry(Entry(transaction = tx, account = aliceAccount, type = EntryType.CREDIT, amount = 1500L))
        tx.addEntry(Entry(transaction = tx, account = bobAccount, type = EntryType.DEBIT, amount = 1500L))
        `when`(transactionRepository.findById(500L)).thenReturn(Optional.of(tx))
        `when`(transactionRepository.save(any(Transaction::class.java))).thenAnswer { it.arguments[0] }

        val deleted = transactionService.deleteTransaction(500L, reason = "Mistake")
        assertTrue(deleted.isDeleted)

        val logCaptor = ArgumentCaptor.forClass(AuditLog::class.java)
        verify(auditLogRepository).save(logCaptor.capture())
        val savedLog = logCaptor.value
        assertEquals(AuditAction.DELETE, savedLog.action)
        assertEquals("Mistake", savedLog.reason)
        assertSame(tx, savedLog.transaction)
        assertTrue(savedLog.serializedPriorState.contains("Lunch"))
    }

    @Test
    fun `deleteTransaction on locked transaction throws IllegalStateException`() {
        val tx = Transaction(id = 500L, group = group, description = "Settled Lunch", amount = 1500L, payer = alice, isLocked = true)
        `when`(transactionRepository.findById(500L)).thenReturn(Optional.of(tx))

        val ex = assertThrows<IllegalStateException> {
            transactionService.deleteTransaction(500L)
        }
        assertEquals("Cannot delete a locked transaction", ex.message)
        verify(auditLogRepository, never()).save(any())
    }

    @Test
    fun `deleteTransaction on already deleted transaction throws IllegalStateException`() {
        val tx = Transaction(id = 500L, group = group, description = "Deleted Lunch", amount = 1500L, payer = alice, isDeleted = true)
        `when`(transactionRepository.findById(500L)).thenReturn(Optional.of(tx))

        val ex = assertThrows<IllegalStateException> {
            transactionService.deleteTransaction(500L)
        }
        assertEquals("Transaction is already deleted", ex.message)
        verify(auditLogRepository, never()).save(any())
    }

    @Test
    fun `editExpense on unlocked transaction soft-deletes old transaction, saves EDIT AuditLog, and creates replacement transaction`() {
        val oldTx = Transaction(id = 500L, group = group, description = "Dinner typo", amount = 2000L, payer = alice)
        oldTx.addEntry(Entry(transaction = oldTx, account = aliceAccount, type = EntryType.CREDIT, amount = 2000L))
        oldTx.addEntry(Entry(transaction = oldTx, account = bobAccount, type = EntryType.DEBIT, amount = 2000L))
        `when`(transactionRepository.findById(500L)).thenReturn(Optional.of(oldTx))
        `when`(transactionRepository.save(any(Transaction::class.java))).thenAnswer { it.arguments[0] }

        val newTx = transactionService.editExpense(
            transactionId = 500L,
            description = "Dinner corrected",
            amount = 3000L,
            payerId = 10L,
            consumerIds = listOf(10L, 20L),
            reason = "Fixed typo and added Bob share"
        )

        assertTrue(oldTx.isDeleted)
        verify(transactionRepository).save(oldTx)

        val logCaptor = ArgumentCaptor.forClass(AuditLog::class.java)
        verify(auditLogRepository).save(logCaptor.capture())
        val savedLog = logCaptor.value
        assertEquals(AuditAction.EDIT, savedLog.action)
        assertEquals("Fixed typo and added Bob share", savedLog.reason)
        assertSame(oldTx, savedLog.transaction)
        assertTrue(savedLog.serializedPriorState.contains("Dinner typo"))

        assertEquals("Dinner corrected", newTx.description)
        assertEquals(3000L, newTx.amount)
        assertSame(oldTx, newTx.originalTransaction)
        assertFalse(newTx.isDeleted)
        assertFalse(newTx.isLocked)
        assertEquals(3, newTx.entries.size)
        assertTrue(newTx.isBalanced())
    }

    @Test
    fun `editExpense on locked transaction throws IllegalStateException`() {
        val lockedTx = Transaction(id = 500L, group = group, description = "Settled", amount = 2000L, payer = alice, isLocked = true)
        `when`(transactionRepository.findById(500L)).thenReturn(Optional.of(lockedTx))

        val ex = assertThrows<IllegalStateException> {
            transactionService.editExpense(
                transactionId = 500L,
                description = "New description",
                amount = 2000L,
                payerId = 10L,
                consumerIds = listOf(20L)
            )
        }
        assertEquals("Cannot edit a locked transaction", ex.message)
    }

    @Test
    fun `createAdjustment on locked transaction creates balanced ADJUSTMENT transaction linked to original`() {
        val originalTx = Transaction(
            id = 500L,
            group = group,
            description = "Original Hotel",
            amount = 10000L,
            payer = alice,
            isLocked = true
        )
        `when`(transactionRepository.findById(500L)).thenReturn(Optional.of(originalTx))
        `when`(transactionRepository.save(any(Transaction::class.java))).thenAnswer { it.arguments[0] }

        val adjustment = transactionService.createAdjustment(
            originalTransactionId = 500L,
            description = "City tax adjustment",
            amount = 2000L,
            payerId = 10L,
            consumerIds = listOf(10L, 20L)
        )

        assertEquals("City tax adjustment", adjustment.description)
        assertEquals(2000L, adjustment.amount)
        assertEquals(TransactionType.ADJUSTMENT, adjustment.type)
        assertSame(originalTx, adjustment.originalTransaction)
        assertFalse(adjustment.isLocked)
        assertFalse(adjustment.isDeleted)
        assertTrue(adjustment.isBalanced())
        verify(transactionRepository).save(adjustment)
    }

    @Test
    fun `createAdjustment on unlocked original transaction throws IllegalArgumentException`() {
        val unlockedTx = Transaction(
            id = 500L,
            group = group,
            description = "Unlocked Hotel",
            amount = 10000L,
            payer = alice,
            isLocked = false
        )
        `when`(transactionRepository.findById(500L)).thenReturn(Optional.of(unlockedTx))

        val ex = assertThrows<IllegalArgumentException> {
            transactionService.createAdjustment(
                originalTransactionId = 500L,
                description = "Delta",
                amount = 1000L,
                payerId = 10L,
                consumerIds = listOf(20L)
            )
        }
        assertEquals("Adjustment can only be made to a locked transaction", ex.message)
    }

    @Test
    fun `createAdjustment with exact split mode creates balanced ADJUSTMENT transaction`() {
        val originalTx = Transaction(
            id = 500L,
            group = group,
            description = "Locked Dinner",
            amount = 5000L,
            payer = alice,
            isLocked = true
        )
        `when`(transactionRepository.findById(500L)).thenReturn(Optional.of(originalTx))
        `when`(transactionRepository.save(any(Transaction::class.java))).thenAnswer { it.arguments[0] }

        val adjustment = transactionService.createAdjustment(
            originalTransactionId = 500L,
            description = "Late tip adjustment",
            amount = 1000L,
            payerId = 10L,
            splitMode = SplitMode.EXACT,
            exactAmounts = mapOf(10L to 400L, 20L to 600L)
        )

        assertEquals("Late tip adjustment", adjustment.description)
        assertEquals(1000L, adjustment.amount)
        assertEquals(TransactionType.ADJUSTMENT, adjustment.type)
        assertTrue(adjustment.isBalanced())
    }
}
