package com.jacobcraig.debtmngr

import com.jacobcraig.debtmngr.domain.EntryType
import com.jacobcraig.debtmngr.domain.Group
import com.jacobcraig.debtmngr.domain.Participant
import com.jacobcraig.debtmngr.repository.*
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@AutoConfigureMockMvc
class ExactSplitExpenseIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var groupRepository: GroupRepository

    @Autowired
    private lateinit var participantRepository: ParticipantRepository

    @Autowired
    private lateinit var accountRepository: AccountRepository

    @Autowired
    private lateinit var transactionRepository: TransactionRepository

    @Autowired
    private lateinit var entryRepository: EntryRepository

    @BeforeEach
    fun cleanDatabase() {
        entryRepository.deleteAll()
        transactionRepository.deleteAll()
        participantRepository.deleteAll()
        accountRepository.deleteAll()
        groupRepository.deleteAll()
    }

    @Test
    fun `full flow - record exact-split expenses, verify double-entry invariants, zero shares, and net balances`() {
        // Setup: Create Group and 4 Participants
        val group = groupRepository.save(Group(name = "Road Trip to Highlands", description = "Vacation rental"))
        val groupId = checkNotNull(group.id)

        val alice = participantRepository.save(Participant(group = group, name = "Alice", isSelf = true))
        val bob = participantRepository.save(Participant(group = group, name = "Bob", isSelf = false))
        val charlie = participantRepository.save(Participant(group = group, name = "Charlie", isSelf = false))
        val dana = participantRepository.save(Participant(group = group, name = "Dana", isSelf = false))

        val aliceId = checkNotNull(alice.id)
        val bobId = checkNotNull(bob.id)
        val charlieId = checkNotNull(charlie.id)
        val danaId = checkNotNull(dana.id)

        // 1. Initial Group view has zero balances and empty transactions
        mockMvc.perform(get("/groups/$groupId"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Road Trip to Highlands")))
            .andExpect(content().string(containsString("Record Expense")))
            .andExpect(content().string(containsString("No transactions yet")))

        // 2. Open GET /groups/{id}/transactions/new
        mockMvc.perform(get("/groups/$groupId/transactions/new"))
            .andExpect(status().isOk)
            .andExpect(view().name("groups/transactions/new"))
            .andExpect(content().string(containsString("Record Expense")))
            .andExpect(content().string(containsString("Split Mode")))
            .andExpect(content().string(containsString("Exact Amounts")))
            .andExpect(content().string(containsString("Split by Exact Amounts")))
            .andExpect(content().string(containsString("Alice")))
            .andExpect(content().string(containsString("Bob")))
            .andExpect(content().string(containsString("Charlie")))
            .andExpect(content().string(containsString("Dana")))

        // 3. Alice pays £100.00 (10000 pence) for Cabin Rental with uneven exact split:
        // Alice: £40.00, Bob: £35.00, Charlie: £25.00, Dana: £0.00 (Dana didn't stay)
        mockMvc.perform(
            post("/groups/$groupId/transactions")
                .param("description", "Cabin Rental")
                .param("amount", "100.00")
                .param("payerId", aliceId.toString())
                .param("splitMode", "EXACT")
                .param("exactAmounts[$aliceId]", "40.00")
                .param("exactAmounts[$bobId]", "35.00")
                .param("exactAmounts[$charlieId]", "25.00")
                .param("exactAmounts[$danaId]", "0.00")
                .param("date", "2026-09-17")
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/groups/$groupId"))

        // 4. Verify DB state for Transaction 1
        val txsAfterFirst = transactionRepository.findByGroupIdAndIsDeletedFalseOrderByCreatedAtDescIdDesc(groupId)
        assertEquals(1, txsAfterFirst.size)
        val tx1 = txsAfterFirst[0]
        assertEquals("Cabin Rental", tx1.description)
        assertEquals(10000L, tx1.amount)
        assertEquals(aliceId, tx1.payer.id)

        // Verify Entries and Invariants for Tx 1
        val entries1 = entryRepository.findActiveByGroupId(groupId)
        // 1 CREDIT (Alice) + 3 DEBITS (Alice 4000, Bob 3500, Charlie 2500) -> Dana (0) has no entry
        assertEquals(4, entries1.size)
        val credits1 = entries1.filter { it.type == EntryType.CREDIT }.sumOf { it.amount }
        val debits1 = entries1.filter { it.type == EntryType.DEBIT }.sumOf { it.amount }
        assertEquals(10000L, credits1)
        assertEquals(10000L, debits1)
        assertEquals(10000L, tx1.amount)

        val creditEntry1 = entries1.first { it.type == EntryType.CREDIT }
        assertEquals(10000L, creditEntry1.amount)
        assertEquals(alice.account.id, creditEntry1.account.id)

        val debitAlice1 = entries1.first { it.account.id == alice.account.id && it.type == EntryType.DEBIT }
        val debitBob1 = entries1.first { it.account.id == bob.account.id && it.type == EntryType.DEBIT }
        val debitCharlie1 = entries1.first { it.account.id == charlie.account.id && it.type == EntryType.DEBIT }

        assertEquals(4000L, debitAlice1.amount)
        assertEquals(3500L, debitBob1.amount)
        assertEquals(2500L, debitCharlie1.amount)
        assertFalse(entries1.any { it.account.id == dana.account.id })

        // 5. Check Group page after Tx 1
        // Alice: +100.00 - 40.00 = +£60.00
        // Bob: -£35.00
        // Charlie: -£25.00
        // Dana: £0.00
        mockMvc.perform(get("/groups/$groupId"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("+£60.00")))
            .andExpect(content().string(containsString("-£35.00")))
            .andExpect(content().string(containsString("-£25.00")))
            .andExpect(content().string(containsString("Cabin Rental")))
            .andExpect(content().string(containsString("£100.00")))
            .andExpect(content().string(not(containsString("No transactions yet"))))

        // 6. Bob pays £30.00 (3000 pence) for "Special Groceries" split only between Charlie (£18.00) and Dana (£12.00)
        // Payer Bob has no share in this expense
        mockMvc.perform(
            post("/groups/$groupId/transactions")
                .param("description", "Special Groceries")
                .param("amount", "30.00")
                .param("payerId", bobId.toString())
                .param("splitMode", "EXACT")
                .param("exactAmounts[$charlieId]", "18.00")
                .param("exactAmounts[$danaId]", "12.00")
                .param("date", "2026-09-17")
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/groups/$groupId"))

        // Verify DB state for Transaction 2
        val txsAfterSecond = transactionRepository.findByGroupIdAndIsDeletedFalseOrderByCreatedAtDescIdDesc(groupId)
        assertEquals(2, txsAfterSecond.size)
        val tx2 = txsAfterSecond.first { it.description == "Special Groceries" }
        assertEquals(3000L, tx2.amount)
        assertEquals(bobId, tx2.payer.id)

        val entries2 = entryRepository.findActiveByGroupId(groupId).filter { it.transaction.id == tx2.id }
        assertEquals(3, entries2.size) // 1 CREDIT (Bob 3000) + 2 DEBITS (Charlie 1800, Dana 1200)
        val credits2 = entries2.filter { it.type == EntryType.CREDIT }.sumOf { it.amount }
        val debits2 = entries2.filter { it.type == EntryType.DEBIT }.sumOf { it.amount }
        assertEquals(3000L, credits2)
        assertEquals(3000L, debits2)
        assertEquals(3000L, tx2.amount)

        // 7. Check updated balances on Group page
        // Alice: +£60.00
        // Bob: -35.00 + 30.00 = -£5.00
        // Charlie: -25.00 - 18.00 = -£43.00
        // Dana: 0.00 - 12.00 = -£12.00
        // Sum of balances: +60.00 - 5.00 - 43.00 - 12.00 = 0.00!
        mockMvc.perform(get("/groups/$groupId"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("+£60.00")))
            .andExpect(content().string(containsString("-£5.00")))
            .andExpect(content().string(containsString("-£43.00")))
            .andExpect(content().string(containsString("-£12.00")))
            .andExpect(content().string(containsString("Cabin Rental")))
            .andExpect(content().string(containsString("Special Groceries")))

        // 8. Validation rejection test: sum mismatch
        mockMvc.perform(
            post("/groups/$groupId/transactions")
                .param("description", "Fuel")
                .param("amount", "50.00")
                .param("payerId", aliceId.toString())
                .param("splitMode", "EXACT")
                .param("exactAmounts[$aliceId]", "20.00")
                .param("exactAmounts[$bobId]", "20.00") // total = 40.00 != 50.00
        )
            .andExpect(status().isOk)
            .andExpect(view().name("groups/transactions/new"))
            .andExpect(model().attributeHasFieldErrors("expenseForm", "exactAmounts"))
            .andExpect(content().string(containsString("The sum of exact split amounts (£40.00) must equal the total expense amount (£50.00)")))

        // 9. Validation rejection test: negative amount
        mockMvc.perform(
            post("/groups/$groupId/transactions")
                .param("description", "Fuel")
                .param("amount", "50.00")
                .param("payerId", aliceId.toString())
                .param("splitMode", "EXACT")
                .param("exactAmounts[$aliceId]", "60.00")
                .param("exactAmounts[$bobId]", "-10.00")
        )
            .andExpect(status().isOk)
            .andExpect(view().name("groups/transactions/new"))
            .andExpect(model().attributeHasFieldErrors("expenseForm", "exactAmounts"))
            .andExpect(content().string(containsString("Individual split amounts cannot be negative")))

        // 10. Verify transaction count remains 2 after failed validation attempts
        assertEquals(2, transactionRepository.findByGroupIdAndIsDeletedFalseOrderByCreatedAtDescIdDesc(groupId).size)
    }
}
