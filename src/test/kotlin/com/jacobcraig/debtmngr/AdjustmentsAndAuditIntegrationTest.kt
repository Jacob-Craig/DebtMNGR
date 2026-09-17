package com.jacobcraig.debtmngr

import com.jacobcraig.debtmngr.domain.*
import com.jacobcraig.debtmngr.repository.*
import com.jacobcraig.debtmngr.service.TransactionService
import org.hamcrest.Matchers.containsString
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
class AdjustmentsAndAuditIntegrationTest {

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

    @Autowired
    private lateinit var auditLogRepository: AuditLogRepository

    @Autowired
    private lateinit var transactionService: TransactionService

    @BeforeEach
    @org.junit.jupiter.api.AfterEach
    fun cleanDatabase() {
        auditLogRepository.deleteAll()
        entryRepository.deleteAll()
        transactionRepository.deleteAll()
        participantRepository.deleteAll()
        accountRepository.deleteAll()
        groupRepository.deleteAll()
    }

    @Test
    fun `full flow - view detail, edit unlocked, settle and lock, reject edit on locked, adjust locked transaction`() {
        // Step 1: Create Group and Participants (Alice isSelf, Bob)
        val group = groupRepository.save(Group(name = "Holiday Trip", description = "Vacation expenses"))
        val groupId = checkNotNull(group.id)

        val alice = participantRepository.save(Participant(group = group, name = "Alice", isSelf = true))
        val bob = participantRepository.save(Participant(group = group, name = "Bob", isSelf = false))
        val aliceId = checkNotNull(alice.id)
        val bobId = checkNotNull(bob.id)

        // Step 2: Record expense "Hotel" £100.00 split equally
        val tx1 = transactionService.createExpense(
            groupId = groupId,
            payerId = aliceId,
            amount = 10000L,
            description = "Hotel",
            consumerIds = listOf(aliceId, bobId)
        )
        val tx1Id = checkNotNull(tx1.id)

        var balances = transactionService.getParticipantBalances(groupId)
        assertEquals(5000L, balances[aliceId])
        assertEquals(-5000L, balances[bobId])

        // Step 3: View transaction details (HTTP GET /transactions/{id})
        mockMvc.perform(get("/transactions/$tx1Id"))
            .andExpect(status().isOk)
            .andExpect(view().name("transactions/show"))
            .andExpect(content().string(containsString("Hotel")))
            .andExpect(content().string(containsString("£100.00")))
            .andExpect(content().string(containsString("CREDIT")))
            .andExpect(content().string(containsString("DEBIT")))
            .andExpect(content().string(containsString("Edit Transaction")))
            .andExpect(content().string(containsString("Delete Transaction")))

        // Step 4: Edit unlocked transaction (HTTP POST /transactions/{id}/edit)
        // Correct amount to £120.00
        val editResult = mockMvc.perform(
            post("/transactions/$tx1Id/edit")
                .param("description", "Luxury Hotel")
                .param("amount", "120.00")
                .param("payerId", aliceId.toString())
                .param("consumerIds", aliceId.toString(), bobId.toString())
                .param("date", "2026-09-17")
                .param("splitMode", "EQUAL")
                .param("reason", "Included resort fee")
        )
            .andExpect(status().is3xxRedirection)
            .andReturn()

        val redirectedUrl = editResult.response.redirectedUrl
        assertNotNull(redirectedUrl)
        val newTxId = redirectedUrl!!.removePrefix("/transactions/").toLong()

        // Verify old transaction is soft-deleted
        val oldTxAfterEdit = transactionRepository.findById(tx1Id).orElseThrow()
        assertTrue(oldTxAfterEdit.isDeleted)

        // Verify AuditLog was recorded on old transaction
        val auditLogs = auditLogRepository.findByTransactionIdOrderByCreatedAtAscIdAsc(tx1Id)
        assertEquals(1, auditLogs.size)
        assertEquals(AuditAction.EDIT, auditLogs[0].action)
        assertEquals("Included resort fee", auditLogs[0].reason)
        assertTrue(auditLogs[0].serializedPriorState.contains("Hotel"))

        // Verify new transaction is active and linked to old transaction
        val newTx = transactionRepository.findById(newTxId).orElseThrow()
        assertEquals("Luxury Hotel", newTx.description)
        assertEquals(12000L, newTx.amount)
        assertEquals(tx1Id, newTx.originalTransaction?.id)
        assertFalse(newTx.isDeleted)
        assertFalse(newTx.isLocked)

        // Verify balances updated with new amount (£60 each)
        balances = transactionService.getParticipantBalances(groupId)
        assertEquals(6000L, balances[aliceId])
        assertEquals(-6000L, balances[bobId])

        // Step 5: Settle up - Bob pays Alice £60.00
        val settlement = transactionService.createSettlement(
            groupId = groupId,
            payerId = bobId,
            receiverId = aliceId,
            amount = 6000L
        )
        val settlementId = checkNotNull(settlement.id)

        // Verify newTx is now locked after settlement
        val newTxAfterSettlement = transactionRepository.findById(newTxId).orElseThrow()
        assertTrue(newTxAfterSettlement.isLocked)

        balances = transactionService.getParticipantBalances(groupId)
        assertEquals(0L, balances[aliceId])
        assertEquals(0L, balances[bobId])

        // Step 6: View locked transaction details
        mockMvc.perform(get("/transactions/$newTxId"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Locked")))
            .andExpect(content().string(containsString("Adjust")))

        // Step 7: Attempting to edit or delete locked transaction fails/redirects
        mockMvc.perform(get("/transactions/$newTxId/edit"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/transactions/$newTxId"))

        // Step 8: Adjust the locked transaction (HTTP POST /transactions/{id}/adjust)
        // Add delta of £20.00 split equally (£10 each)
        val adjustResult = mockMvc.perform(
            post("/transactions/$newTxId/adjust")
                .param("description", "Adjustment: Minibar charges")
                .param("amount", "20.00")
                .param("payerId", aliceId.toString())
                .param("consumerIds", aliceId.toString(), bobId.toString())
                .param("date", "2026-09-17")
                .param("splitMode", "EQUAL")
        )
            .andExpect(status().is3xxRedirection)
            .andReturn()

        val adjustRedirectedUrl = adjustResult.response.redirectedUrl
        assertNotNull(adjustRedirectedUrl)
        val adjustmentId = adjustRedirectedUrl!!.removePrefix("/transactions/").toLong()

        val adjustmentTx = transactionRepository.findById(adjustmentId).orElseThrow()
        assertEquals(TransactionType.ADJUSTMENT, adjustmentTx.type)
        assertEquals(newTxId, adjustmentTx.originalTransaction?.id)
        assertEquals(2000L, adjustmentTx.amount)
        assertFalse(adjustmentTx.isLocked)
        assertFalse(adjustmentTx.isDeleted)

        // Verify balances now reflect the £20 delta (£10 each: Alice +£10, Bob -£10)
        balances = transactionService.getParticipantBalances(groupId)
        assertEquals(1000L, balances[aliceId])
        assertEquals(-1000L, balances[bobId])

        // Step 9: Inspect original transaction again; verify linked adjustments are listed
        mockMvc.perform(get("/transactions/$newTxId"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Adjustment: Minibar charges")))
            .andExpect(content().string(containsString("£20.00")))

        // Step 10: Soft-delete an unlocked transaction (e.g. the adjustment)
        mockMvc.perform(
            post("/transactions/$adjustmentId/delete")
                .param("reason", "Cancelled charge")
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/transactions/$adjustmentId"))

        val adjustmentAfterDelete = transactionRepository.findById(adjustmentId).orElseThrow()
        assertTrue(adjustmentAfterDelete.isDeleted)

        val deleteLogs = auditLogRepository.findByTransactionIdOrderByCreatedAtAscIdAsc(adjustmentId)
        assertEquals(1, deleteLogs.size)
        assertEquals(AuditAction.DELETE, deleteLogs[0].action)
        assertEquals("Cancelled charge", deleteLogs[0].reason)

        // Balances return to 0
        balances = transactionService.getParticipantBalances(groupId)
        assertEquals(0L, balances[aliceId])
        assertEquals(0L, balances[bobId])
    }
}
