package com.jacobcraig.debtmngr

import com.jacobcraig.debtmngr.domain.EntryType
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
class IntraGroupSettlementIntegrationTest {

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
    fun `full flow - record expense, settle debt, lock prior transactions, verify updated balances on group and dashboard`() {
        // Setup: Create Group and 3 Participants
        val group = groupRepository.save(Group(name = "Apartment Flatmates", description = "Shared living"))
        val groupId = checkNotNull(group.id)

        val alice = participantRepository.save(Participant(group = group, name = "Alice", isSelf = true))
        val bob = participantRepository.save(Participant(group = group, name = "Bob", isSelf = false))
        val charlie = participantRepository.save(Participant(group = group, name = "Charlie", isSelf = false))

        val aliceId = checkNotNull(alice.id)
        val bobId = checkNotNull(bob.id)
        val charlieId = checkNotNull(charlie.id)

        // 1. Alice pays £20.00 for Groceries split equally between Alice and Bob (£10.00 each)
        mockMvc.perform(
            post("/groups/$groupId/transactions")
                .param("description", "Weekly Groceries")
                .param("amount", "20.00")
                .param("payerId", aliceId.toString())
                .param("consumerIds", aliceId.toString(), bobId.toString())
                .param("date", "2026-09-17")
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/groups/$groupId"))

        // 2. Charlie pays £15.00 for WiFi split between Charlie and Alice (£7.50 each, Bob NOT involved)
        mockMvc.perform(
            post("/groups/$groupId/transactions")
                .param("description", "High-speed WiFi")
                .param("amount", "15.00")
                .param("payerId", charlieId.toString())
                .param("consumerIds", charlieId.toString(), aliceId.toString())
                .param("date", "2026-09-17")
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/groups/$groupId"))

        // Verify initial transactions state: both unlocked
        val initialTxs = transactionRepository.findByGroupIdAndIsDeletedFalseOrderByCreatedAtDescIdDesc(groupId)
        assertEquals(2, initialTxs.size)
        val groceriesTx = initialTxs.first { it.description == "Weekly Groceries" }
        val wifiTx = initialTxs.first { it.description == "High-speed WiFi" }
        assertFalse(groceriesTx.isLocked)
        assertFalse(wifiTx.isLocked)

        // 3. Verify Group show page balances:
        // Alice: +2000 - 1000 (groceries) - 750 (wifi) = +250 (+£2.50)
        // Bob: -1000 (-£10.00)
        // Charlie: +1500 - 750 = +750 (+£7.50)
        mockMvc.perform(get("/groups/$groupId"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("+£2.50")))
            .andExpect(content().string(containsString("-£10.00")))
            .andExpect(content().string(containsString("+£7.50")))
            .andExpect(content().string(containsString("Settle Up")))

        // 4. Verify Dashboard shows operator's current balance (+£2.50)
        mockMvc.perform(get("/"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Apartment Flatmates")))
            .andExpect(content().string(containsString("+£2.50")))

        // 5. Open Settle Up page pre-selecting Bob as Payer and Alice as Receiver
        mockMvc.perform(get("/groups/$groupId/settle").param("payerId", bobId.toString()).param("receiverId", aliceId.toString()))
            .andExpect(status().isOk)
            .andExpect(view().name("groups/settle"))
            .andExpect(content().string(containsString("Settle Up")))

        // 6. Submit Settlement: Bob pays Alice £10.00
        mockMvc.perform(
            post("/groups/$groupId/settle")
                .param("payerId", bobId.toString())
                .param("receiverId", aliceId.toString())
                .param("amount", "10.00")
                .param("date", "2026-09-17")
                .param("notes", "Bank transfer")
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/groups/$groupId"))

        // 7. Verify DB state after Settlement
        val allTxs = transactionRepository.findByGroupIdAndIsDeletedFalseOrderByCreatedAtDescIdDesc(groupId)
        assertEquals(3, allTxs.size)

        val settlementTx = allTxs.first { it.type == TransactionType.SETTLEMENT }
        assertEquals(1000L, settlementTx.amount)
        assertEquals(bobId, settlementTx.payer.id)
        assertEquals("Settlement: Bob paid Alice - Bank transfer", settlementTx.description)
        val settlementEntries = entryRepository.findActiveByGroupId(groupId).filter { it.transaction.id == settlementTx.id }
        assertEquals(2, settlementEntries.size)

        val creditEntry = settlementEntries.first { it.type == EntryType.CREDIT }
        val debitEntry = settlementEntries.first { it.type == EntryType.DEBIT }
        assertEquals(bob.account.id, creditEntry.account.id)
        assertEquals(1000L, creditEntry.amount)
        assertEquals(alice.account.id, debitEntry.account.id)
        assertEquals(1000L, debitEntry.amount)

        // Invariant check: Groceries involved Alice & Bob -> MUST BE LOCKED
        val reloadedGroceries = transactionRepository.findById(checkNotNull(groceriesTx.id)).orElseThrow()
        assertTrue(reloadedGroceries.isLocked, "Groceries transaction should be locked after settlement")

        // Invariant check: WiFi involved Charlie & Alice only -> MUST REMAIN UNLOCKED
        val reloadedWifi = transactionRepository.findById(checkNotNull(wifiTx.id)).orElseThrow()
        assertFalse(reloadedWifi.isLocked, "WiFi transaction should remain unlocked")

        // 8. Verify Group show page reflects zeroed/reduced balances:
        // Bob: -1000 + 1000 = 0 (£0.00 - zeroed!)
        // Alice: +250 - 1000 = -750 (-£7.50 - Alice now only owes Charlie)
        // Charlie: +750 (+£7.50)
        mockMvc.perform(get("/groups/$groupId"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("£0.00")))
            .andExpect(content().string(containsString("-£7.50")))
            .andExpect(content().string(containsString("+£7.50")))
            .andExpect(content().string(containsString("Settlement: Bob paid Alice - Bank transfer")))
            .andExpect(content().string(containsString("Settlement")))
            .andExpect(content().string(containsString("Locked")))

        // 9. Verify Dashboard reflects new operator balance (-£7.50)
        mockMvc.perform(get("/"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Apartment Flatmates")))
            .andExpect(content().string(containsString("-£7.50")))

        // 10. Subsequent Partial Settlement: Alice pays Charlie £5.00
        mockMvc.perform(
            post("/groups/$groupId/settle")
                .param("payerId", aliceId.toString())
                .param("receiverId", charlieId.toString())
                .param("amount", "5.00")
                .param("date", "2026-09-17")
                .param("notes", "Partial cash")
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/groups/$groupId"))

        // Now WiFi transaction was involved between Alice & Charlie -> MUST BE LOCKED
        val reloadedWifiAfterSecondSettlement = transactionRepository.findById(checkNotNull(wifiTx.id)).orElseThrow()
        assertTrue(reloadedWifiAfterSecondSettlement.isLocked, "WiFi transaction should be locked after Alice-Charlie settlement")

        // Balances after partial settlement:
        // Alice: -750 + 500 = -250 (-£2.50)
        // Charlie: +750 - 500 = +250 (+£2.50)
        // Bob: £0.00
        mockMvc.perform(get("/groups/$groupId"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("-£2.50")))
            .andExpect(content().string(containsString("+£2.50")))
            .andExpect(content().string(containsString("£0.00")))

        // Dashboard reflects reduced balance (-£2.50)
        mockMvc.perform(get("/"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("-£2.50")))
    }
}
