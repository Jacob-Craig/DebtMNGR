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
class EqualSplitExpenseIntegrationTest {

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
    fun `full flow - record equal-split expenses, odd-penny allocation, verify ledger invariants and group balances`() {
        // Setup: Create Group and 3 Participants
        val group = groupRepository.save(Group(name = "Holiday in Rome", description = "Vacation trip"))
        val groupId = checkNotNull(group.id)

        val alice = participantRepository.save(Participant(group = group, name = "Alice", isSelf = true))
        val bob = participantRepository.save(Participant(group = group, name = "Bob", isSelf = false))
        val charlie = participantRepository.save(Participant(group = group, name = "Charlie", isSelf = false))

        val aliceId = checkNotNull(alice.id)
        val bobId = checkNotNull(bob.id)
        val charlieId = checkNotNull(charlie.id)

        // 1. Initial Group view has zero balances and empty transactions
        mockMvc.perform(get("/groups/$groupId"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Holiday in Rome")))
            .andExpect(content().string(containsString("Record Expense")))
            .andExpect(content().string(containsString("No transactions yet")))

        // 2. Open GET /groups/{id}/transactions/new
        mockMvc.perform(get("/groups/$groupId/transactions/new"))
            .andExpect(status().isOk)
            .andExpect(view().name("groups/transactions/new"))
            .andExpect(content().string(containsString("Record Expense")))
            .andExpect(content().string(containsString("Alice")))
            .andExpect(content().string(containsString("Bob")))
            .andExpect(content().string(containsString("Charlie")))

        // 3. Alice pays £10.00 (1000 pence) split among Alice, Bob, Charlie (N=3)
        // Remainder 1000 % 3 = 1 penny allocated to payer Alice
        mockMvc.perform(
            post("/groups/$groupId/transactions")
                .param("description", "Piazza Dinner")
                .param("amount", "10.00")
                .param("payerId", aliceId.toString())
                .param("consumerIds", aliceId.toString(), bobId.toString(), charlieId.toString())
                .param("date", "2026-09-17")
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/groups/$groupId"))

        // Verify DB state for Transaction 1
        val transactionsAfterFirst = transactionRepository.findByGroupIdAndIsDeletedFalseOrderByCreatedAtDescIdDesc(groupId)
        assertEquals(1, transactionsAfterFirst.size)
        val tx1 = transactionsAfterFirst[0]
        assertEquals("Piazza Dinner", tx1.description)
        assertEquals(1000L, tx1.amount)
        assertEquals(aliceId, tx1.payer.id)

        // Verify Entries and Invariant for Tx 1
        val entries1 = entryRepository.findActiveByGroupId(groupId)
        assertEquals(4, entries1.size)
        val credits1 = entries1.filter { it.type == EntryType.CREDIT }.sumOf { it.amount }
        val debits1 = entries1.filter { it.type == EntryType.DEBIT }.sumOf { it.amount }
        assertEquals(1000L, credits1)
        assertEquals(1000L, debits1)
        assertEquals(1000L, tx1.amount)

        val creditEntry1 = entries1.first { it.type == EntryType.CREDIT }
        assertEquals(1000L, creditEntry1.amount)
        assertEquals(alice.account.id, creditEntry1.account.id)

        val debitAlice1 = entries1.first { it.account.id == alice.account.id && it.type == EntryType.DEBIT }
        val debitBob1 = entries1.first { it.account.id == bob.account.id && it.type == EntryType.DEBIT }
        val debitCharlie1 = entries1.first { it.account.id == charlie.account.id && it.type == EntryType.DEBIT }

        // Alice gets 333 + 1 = 334. Bob gets 333. Charlie gets 333.
        assertEquals(334L, debitAlice1.amount)
        assertEquals(333L, debitBob1.amount)
        assertEquals(333L, debitCharlie1.amount)

        // 4. Check Group page after Tx 1
        // Alice net balance: +1000 - 334 = +666 (+£6.66)
        // Bob net balance: -333 (-£3.33)
        // Charlie net balance: -333 (-£3.33)
        mockMvc.perform(get("/groups/$groupId"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("+£6.66")))
            .andExpect(content().string(containsString("-£3.33")))
            .andExpect(content().string(containsString("Piazza Dinner")))
            .andExpect(content().string(containsString("£10.00")))
            .andExpect(content().string(containsString("17 Sep 2026")))
            .andExpect(content().string(not(containsString("No transactions yet"))))

        // 5. Bob pays £7.01 (701 pence) for "Museum Tickets" split between Alice and Charlie (N=2, payer Bob NOT in split)
        // Remainder 701 % 2 = 1 penny allocated to first consumer (Alice)
        mockMvc.perform(
            post("/groups/$groupId/transactions")
                .param("description", "Museum Tickets")
                .param("amount", "7.01")
                .param("payerId", bobId.toString())
                .param("consumerIds", aliceId.toString(), charlieId.toString())
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/groups/$groupId"))

        // Verify DB state for Transaction 2
        val transactionsAfterSecond = transactionRepository.findByGroupIdAndIsDeletedFalseOrderByCreatedAtDescIdDesc(groupId)
        assertEquals(2, transactionsAfterSecond.size)
        val tx2 = transactionsAfterSecond.first { it.description == "Museum Tickets" }
        assertEquals(701L, tx2.amount)
        assertEquals(bobId, tx2.payer.id)
        val entries2 = entryRepository.findActiveByGroupId(groupId).filter { it.transaction.id == tx2.id }
        assertEquals(3, entries2.size)
        val credits2 = entries2.filter { it.type == EntryType.CREDIT }.sumOf { it.amount }
        val debits2 = entries2.filter { it.type == EntryType.DEBIT }.sumOf { it.amount }
        assertEquals(701L, credits2)
        assertEquals(701L, debits2)
        assertEquals(701L, tx2.amount)

        // 6. Check updated balances on Group page
        // Alice: +666 - (350 + 1) = +666 - 351 = +315 (+£3.15)
        // Bob: -333 + 701 = +368 (+£3.68)
        // Charlie: -333 - 350 = -683 (-£6.83)
        // Invariant sum: +315 + 368 - 683 = 0
        mockMvc.perform(get("/groups/$groupId"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("+£3.15")))
            .andExpect(content().string(containsString("+£3.68")))
            .andExpect(content().string(containsString("-£6.83")))
            .andExpect(content().string(containsString("Piazza Dinner")))
            .andExpect(content().string(containsString("Museum Tickets")))

        // 7. Form validation rejection
        mockMvc.perform(
            post("/groups/$groupId/transactions")
                .param("description", "")
                .param("amount", "-5.00")
                .param("payerId", "")
        )
            .andExpect(status().isOk)
            .andExpect(view().name("groups/transactions/new"))
            .andExpect(model().attributeHasFieldErrors("expenseForm", "description", "amount", "payerId", "consumerIds"))

        // Transactions count unchanged
        assertEquals(2, transactionRepository.findByGroupIdAndIsDeletedFalseOrderByCreatedAtDescIdDesc(groupId).size)
    }
}
