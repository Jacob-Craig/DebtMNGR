package com.jacobcraig.debtmngr

import com.jacobcraig.debtmngr.domain.Group
import com.jacobcraig.debtmngr.domain.Participant
import com.jacobcraig.debtmngr.domain.TransactionType
import com.jacobcraig.debtmngr.repository.*
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
class GlobalNettingIntegrationTest {

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
    fun `full flow - cross-group expenses, consolidated dashboard, proportional lump-sum settlement, and zeroed balances`() {
        // Setup: Group 1 (Flatmates) with Alice (operator) and Bob
        val group1 = groupRepository.save(Group(name = "Apartment Flatmates", description = "Home living"))
        val g1Id = checkNotNull(group1.id)
        val alice1 = participantRepository.save(Participant(group = group1, name = "Alice", isSelf = true))
        val bob1 = participantRepository.save(Participant(group = group1, name = "Bob", isSelf = false))
        val alice1Id = checkNotNull(alice1.id)
        val bob1Id = checkNotNull(bob1.id)

        // Setup: Group 2 (Holiday) with Alice (operator), Bob, and Charlie
        val group2 = groupRepository.save(Group(name = "Spain Trip", description = "Summer vacation"))
        val g2Id = checkNotNull(group2.id)
        val alice2 = participantRepository.save(Participant(group = group2, name = "Alice", isSelf = true))
        val bob2 = participantRepository.save(Participant(group = group2, name = "Bob", isSelf = false))
        val charlie2 = participantRepository.save(Participant(group = group2, name = "Charlie", isSelf = false))
        val alice2Id = checkNotNull(alice2.id)
        val bob2Id = checkNotNull(bob2.id)
        val charlie2Id = checkNotNull(charlie2.id)

        // 1. Group 1: Alice pays £20.00 for Groceries split equally with Bob (£10.00 each)
        mockMvc.perform(
            post("/groups/$g1Id/transactions")
                .param("description", "Weekly Groceries")
                .param("amount", "20.00")
                .param("payerId", alice1Id.toString())
                .param("consumerIds", alice1Id.toString(), bob1Id.toString())
                .param("date", "2026-09-17")
        ).andExpect(status().is3xxRedirection)

        // 2. Group 2: Alice pays £60.00 for Hotel split equally among Alice, Bob, and Charlie (£20.00 each)
        mockMvc.perform(
            post("/groups/$g2Id/transactions")
                .param("description", "Hotel Room")
                .param("amount", "60.00")
                .param("payerId", alice2Id.toString())
                .param("consumerIds", alice2Id.toString(), bob2Id.toString(), charlie2Id.toString())
                .param("date", "2026-09-17")
        ).andExpect(status().is3xxRedirection)

        // 3. Verify Dashboard shows:
        // - Operator global net balance: +£10 (group 1) + +£40 (group 2) = +£50.00
        // - Bob owes Alice: £10 in Group 1 + £20 in Group 2 = £30.00 total
        // - Charlie owes Alice: £20.00 in Group 2
        mockMvc.perform(get("/"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("+£50.00")))
            .andExpect(content().string(containsString("Bob")))
            .andExpect(content().string(containsString("+£30.00")))
            .andExpect(content().string(containsString("Spain Trip:")))
            .andExpect(content().string(containsString("Apartment Flatmates:")))
            .andExpect(content().string(containsString("Global Settle Up")))

        // 4. View Global Settle Up page pre-selecting Bob
        mockMvc.perform(get("/settle").param("contactName", "Bob"))
            .andExpect(status().isOk)
            .andExpect(view().name("settle"))
            .andExpect(content().string(containsString("Global Settle Up")))
            .andExpect(content().string(containsString("Bob")))

        // 5. Submit Partial Lump-Sum Settlement: Bob pays Alice £15.00
        // Debt distribution: Group 1 has 1000 debt (1/3), Group 2 has 2000 debt (2/3)
        // £15.00 allocated: £5.00 to Group 1, £10.00 to Group 2
        mockMvc.perform(
            post("/settle")
                .param("contactName", "Bob")
                .param("payerIsSelf", "false")
                .param("amount", "15.00")
                .param("date", "2026-09-17")
                .param("notes", "Partial lump-sum bank transfer")
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/"))

        // 6. Verify Database Settlement Transactions
        val g1Txs = transactionRepository.findByGroupIdAndIsDeletedFalseOrderByCreatedAtDescIdDesc(g1Id)
        val g2Txs = transactionRepository.findByGroupIdAndIsDeletedFalseOrderByCreatedAtDescIdDesc(g2Id)

        val g1Settlement = g1Txs.find { it.type == TransactionType.SETTLEMENT }
        assertNotNull(g1Settlement, "Group 1 must contain a settlement transaction")
        assertEquals(500L, g1Settlement!!.amount, "Group 1 should receive £5.00 (500L) settlement")
        assertEquals(bob1Id, g1Settlement.payer.id)

        val g2Settlement = g2Txs.find { it.type == TransactionType.SETTLEMENT }
        assertNotNull(g2Settlement, "Group 2 must contain a settlement transaction")
        assertEquals(1000L, g2Settlement!!.amount, "Group 2 should receive £10.00 (1000L) settlement")
        assertEquals(bob2Id, g2Settlement.payer.id)

        // Invariant: Prior transactions involving Alice and Bob in both groups are locked
        val g1Groceries = g1Txs.first { it.description == "Weekly Groceries" }
        assertTrue(g1Groceries.isLocked, "Group 1 groceries transaction must be locked")

        val g2Hotel = g2Txs.first { it.description == "Hotel Room" }
        assertTrue(g2Hotel.isLocked, "Group 2 hotel transaction must be locked")

        // 7. Verify Dashboard reflects reduced operator balance:
        // Group 1: Alice has +£5.00
        // Group 2: Alice has +£30.00
        // Total global net: +£35.00
        // Bob owes Alice: £15.00 total (£5.00 in Group 1, £10.00 in Group 2)
        mockMvc.perform(get("/"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("+£35.00")))
            .andExpect(content().string(containsString("+£15.00")))

        // 8. Verify individual Group show pages:
        // Group 1: Bob balance is -£5.00, Alice is +£5.00
        mockMvc.perform(get("/groups/$g1Id"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("+£5.00")))
            .andExpect(content().string(containsString("-£5.00")))

        // Group 2: Bob balance is -£10.00, Charlie is -£20.00, Alice is +£30.00
        mockMvc.perform(get("/groups/$g2Id"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("+£30.00")))
            .andExpect(content().string(containsString("-£10.00")))
            .andExpect(content().string(containsString("-£20.00")))

        // 9. Full Settlement: Bob pays Alice the remaining £15.00
        mockMvc.perform(
            post("/settle")
                .param("contactName", "Bob")
                .param("payerIsSelf", "false")
                .param("amount", "15.00")
                .param("date", "2026-09-17")
                .param("notes", "Final balance")
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/"))

        // 10. Verify Group 1 Bob balance is £0.00 (zeroed!)
        mockMvc.perform(get("/groups/$g1Id"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("£0.00")))

        // Verify Group 2 Bob balance is £0.00 (zeroed!)
        mockMvc.perform(get("/groups/$g2Id"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("£0.00")))

        // Verify Dashboard operator net balance is now +£20.00 (only Charlie owes Alice)
        mockMvc.perform(get("/"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("+£20.00")))
    }
}
