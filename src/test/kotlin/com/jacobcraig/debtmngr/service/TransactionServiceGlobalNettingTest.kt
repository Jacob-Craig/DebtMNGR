package com.jacobcraig.debtmngr.service

import com.jacobcraig.debtmngr.domain.*
import com.jacobcraig.debtmngr.repository.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import java.time.Instant
import java.util.Optional

class TransactionServiceGlobalNettingTest {

    private lateinit var groupRepository: GroupRepository
    private lateinit var participantRepository: ParticipantRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var entryRepository: EntryRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var auditLogRepository: AuditLogRepository
    private lateinit var transactionService: TransactionService

    private val group1 = Group(id = 1L, name = "Flatmates")
    private val group2 = Group(id = 2L, name = "Holiday")

    private val aliceAccount1 = Account(id = 101L)
    private val bobAccount1 = Account(id = 102L)
    private val aliceAccount2 = Account(id = 201L)
    private val bobAccount2 = Account(id = 202L)

    private val alice1 = Participant(id = 1L, group = group1, name = "Alice", isSelf = true, account = aliceAccount1)
    private val bob1 = Participant(id = 2L, group = group1, name = "Bob", isSelf = false, account = bobAccount1)

    private val alice2 = Participant(id = 3L, group = group2, name = "Alice", isSelf = true, account = aliceAccount2)
    private val bob2 = Participant(id = 4L, group = group2, name = "Bob", isSelf = false, account = bobAccount2)

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

        `when`(groupRepository.findAll()).thenReturn(listOf(group1, group2))
        `when`(groupRepository.findById(1L)).thenReturn(Optional.of(group1))
        `when`(groupRepository.findById(2L)).thenReturn(Optional.of(group2))
        `when`(groupRepository.existsById(1L)).thenReturn(true)
        `when`(groupRepository.existsById(2L)).thenReturn(true)

        `when`(participantRepository.findByGroupIdOrderByIdAsc(1L)).thenReturn(listOf(alice1, bob1))
        `when`(participantRepository.findByGroupIdOrderByIdAsc(2L)).thenReturn(listOf(alice2, bob2))
        `when`(participantRepository.findById(1L)).thenReturn(Optional.of(alice1))
        `when`(participantRepository.findById(2L)).thenReturn(Optional.of(bob1))
        `when`(participantRepository.findById(3L)).thenReturn(Optional.of(alice2))
        `when`(participantRepository.findById(4L)).thenReturn(Optional.of(bob2))
    }

    @Test
    fun `getGlobalNetSummary aggregates operator net balance and computes cross-group contact breakdown`() {
        // In Group 1: Alice paid £20 for Alice & Bob (£10 each) -> Bob owes Alice £10 (1000)
        val tx1 = Transaction(
            id = 10L,
            group = group1,
            description = "Groceries",
            amount = 2000L,
            payer = alice1,
            type = TransactionType.EXPENSE
        )
        tx1.addEntry(Entry(transaction = tx1, account = aliceAccount1, type = EntryType.CREDIT, amount = 2000L))
        tx1.addEntry(Entry(transaction = tx1, account = aliceAccount1, type = EntryType.DEBIT, amount = 1000L))
        tx1.addEntry(Entry(transaction = tx1, account = bobAccount1, type = EntryType.DEBIT, amount = 1000L))

        // In Group 2: Alice paid £60 for Alice & Bob (£30 each) -> Bob owes Alice £30 (3000)
        val tx2 = Transaction(
            id = 20L,
            group = group2,
            description = "Hotel",
            amount = 6000L,
            payer = alice2,
            type = TransactionType.EXPENSE
        )
        tx2.addEntry(Entry(transaction = tx2, account = aliceAccount2, type = EntryType.CREDIT, amount = 6000L))
        tx2.addEntry(Entry(transaction = tx2, account = aliceAccount2, type = EntryType.DEBIT, amount = 3000L))
        tx2.addEntry(Entry(transaction = tx2, account = bobAccount2, type = EntryType.DEBIT, amount = 3000L))

        `when`(transactionRepository.findByGroupIdAndIsDeletedFalseOrderByCreatedAtDescIdDesc(1L)).thenReturn(listOf(tx1))
        `when`(transactionRepository.findByGroupIdAndIsDeletedFalseOrderByCreatedAtDescIdDesc(2L)).thenReturn(listOf(tx2))
        `when`(entryRepository.findActiveByGroupId(1L)).thenReturn(tx1.entries)
        `when`(entryRepository.findActiveByGroupId(2L)).thenReturn(tx2.entries)

        val summary = transactionService.getGlobalNetSummary()

        // Total operator balance across groups = 1000 + 3000 = 4000
        assertEquals(4000L, summary.operatorNetTotal)

        // Cross-group breakdown for Bob = 4000 total (1000 in Group 1, 3000 in Group 2)
        assertEquals(1, summary.contacts.size)
        val bobBreakdown = summary.contacts.first()
        assertEquals("Bob", bobBreakdown.contactName)
        assertEquals(4000L, bobBreakdown.totalNet)
        assertEquals(2, bobBreakdown.groupDebts.size)

        val g1Debt = bobBreakdown.groupDebts.first { it.groupId == 1L }
        assertEquals(1000L, g1Debt.amount)
        assertEquals("Flatmates", g1Debt.groupName)

        val g2Debt = bobBreakdown.groupDebts.first { it.groupId == 2L }
        assertEquals(3000L, g2Debt.amount)
        assertEquals("Holiday", g2Debt.groupName)
    }

    @Test
    fun `createGlobalSettlement allocates lump-sum proportionally into settlement transactions in each group`() {
        // In Group 1: Bob owes Alice £10 (1000)
        val tx1 = Transaction(id = 10L, group = group1, description = "Groceries", amount = 2000L, payer = alice1)
        tx1.addEntry(Entry(transaction = tx1, account = aliceAccount1, type = EntryType.CREDIT, amount = 2000L))
        tx1.addEntry(Entry(transaction = tx1, account = aliceAccount1, type = EntryType.DEBIT, amount = 1000L))
        tx1.addEntry(Entry(transaction = tx1, account = bobAccount1, type = EntryType.DEBIT, amount = 1000L))

        // In Group 2: Bob owes Alice £30 (3000)
        val tx2 = Transaction(id = 20L, group = group2, description = "Hotel", amount = 6000L, payer = alice2)
        tx2.addEntry(Entry(transaction = tx2, account = aliceAccount2, type = EntryType.CREDIT, amount = 6000L))
        tx2.addEntry(Entry(transaction = tx2, account = aliceAccount2, type = EntryType.DEBIT, amount = 3000L))
        tx2.addEntry(Entry(transaction = tx2, account = bobAccount2, type = EntryType.DEBIT, amount = 3000L))

        `when`(transactionRepository.findByGroupIdAndIsDeletedFalseOrderByCreatedAtDescIdDesc(1L)).thenReturn(listOf(tx1))
        `when`(transactionRepository.findByGroupIdAndIsDeletedFalseOrderByCreatedAtDescIdDesc(2L)).thenReturn(listOf(tx2))

        `when`(transactionRepository.save(any(Transaction::class.java))).thenAnswer { invocation ->
            val tx = invocation.getArgument<Transaction>(0)
            tx
        }

        // Bob pays Alice £20.00 (2000 minor units) lump-sum across groups
        // Total debt = 4000. Group 1 proportion: 1000/4000 * 2000 = 500. Group 2: 3000/4000 * 2000 = 1500.
        val settlements = transactionService.createGlobalSettlement(
            contactName = "Bob",
            payerIsSelf = false,
            amount = 2000L,
            date = Instant.now(),
            notes = "Consolidated lump sum"
        )

        assertEquals(2, settlements.size)
        val s1 = settlements.first { it.group.id == 1L }
        val s2 = settlements.first { it.group.id == 2L }

        assertEquals(500L, s1.amount)
        assertEquals(TransactionType.SETTLEMENT, s1.type)
        assertEquals(bob1.id, s1.payer.id)

        assertEquals(1500L, s2.amount)
        assertEquals(TransactionType.SETTLEMENT, s2.type)
        assertEquals(bob2.id, s2.payer.id)
    }

    @Test
    fun `createGlobalSettlement throws when amount exceeds total debt`() {
        val tx1 = Transaction(id = 10L, group = group1, description = "Groceries", amount = 2000L, payer = alice1)
        tx1.addEntry(Entry(transaction = tx1, account = aliceAccount1, type = EntryType.CREDIT, amount = 2000L))
        tx1.addEntry(Entry(transaction = tx1, account = aliceAccount1, type = EntryType.DEBIT, amount = 1000L))
        tx1.addEntry(Entry(transaction = tx1, account = bobAccount1, type = EntryType.DEBIT, amount = 1000L))

        `when`(transactionRepository.findByGroupIdAndIsDeletedFalseOrderByCreatedAtDescIdDesc(1L)).thenReturn(listOf(tx1))
        `when`(transactionRepository.findByGroupIdAndIsDeletedFalseOrderByCreatedAtDescIdDesc(2L)).thenReturn(emptyList())

        val ex = assertThrows<IllegalArgumentException> {
            transactionService.createGlobalSettlement(
                contactName = "Bob",
                payerIsSelf = false,
                amount = 1500L // Exceeds 1000L debt
            )
        }
        assertTrue(ex.message!!.contains("cannot exceed total debt"))
    }
}
