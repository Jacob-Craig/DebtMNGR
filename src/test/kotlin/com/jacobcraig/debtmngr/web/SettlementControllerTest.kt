package com.jacobcraig.debtmngr.web

import com.jacobcraig.debtmngr.domain.Group
import com.jacobcraig.debtmngr.domain.Participant
import com.jacobcraig.debtmngr.domain.Transaction
import com.jacobcraig.debtmngr.domain.TransactionType
import com.jacobcraig.debtmngr.service.GroupService
import com.jacobcraig.debtmngr.service.TransactionService
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest(SettlementController::class)
class SettlementControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var groupService: GroupService

    @MockitoBean
    private lateinit var transactionService: TransactionService

    private val group = Group(id = 1L, name = "Apartment")
    private val alice = Participant(id = 10L, group = group, name = "Alice", isSelf = true)
    private val bob = Participant(id = 20L, group = group, name = "Bob", isSelf = false)

    @Test
    fun `GET settle returns 200 with settle view and model attributes`() {
        `when`(groupService.getGroup(1L)).thenReturn(group)
        `when`(groupService.getParticipants(1L)).thenReturn(listOf(alice, bob))
        `when`(transactionService.getParticipantBalances(1L)).thenReturn(mapOf(10L to 1000L, 20L to -1000L))

        mockMvc.perform(get("/groups/1/settle"))
            .andExpect(status().isOk)
            .andExpect(view().name("groups/settle"))
            .andExpect(model().attribute("group", group))
            .andExpect(model().attribute("participants", listOf(alice, bob)))
            .andExpect(model().attributeExists("balances"))
            .andExpect(model().attributeExists("settleForm"))
            .andExpect(content().string(containsString("Settle Up")))
    }


    @Test
    fun `GET settle with query parameters pre-selects payer and receiver`() {
        `when`(groupService.getGroup(1L)).thenReturn(group)
        `when`(groupService.getParticipants(1L)).thenReturn(listOf(alice, bob))
        `when`(transactionService.getParticipantBalances(1L)).thenReturn(emptyMap())

        mockMvc.perform(get("/groups/1/settle").param("payerId", "20").param("receiverId", "10"))
            .andExpect(status().isOk)
            .andExpect(view().name("groups/settle"))
            .andExpect(model().attribute("settleForm", org.hamcrest.Matchers.hasProperty<SettleUpForm>("payerId", org.hamcrest.Matchers.equalTo(20L))))
            .andExpect(model().attribute("settleForm", org.hamcrest.Matchers.hasProperty<SettleUpForm>("receiverId", org.hamcrest.Matchers.equalTo(10L))))
    }

    @Test
    fun `POST settle with valid parameters creates settlement and redirects to group show`() {
        val createdSettlement = Transaction(
            id = 100L,
            group = group,
            description = "Settlement: Bob paid Alice",
            amount = 1500L,
            payer = bob,
            type = TransactionType.SETTLEMENT
        )
        `when`(
            transactionService.createSettlement(
                groupId = eq(1L),
                payerId = eq(20L),
                receiverId = eq(10L),
                amount = eq(1500L),
                date = any(),
                notes = eq("Cash")
            )
        ).thenReturn(createdSettlement)

        mockMvc.perform(
            post("/groups/1/settle")
                .param("payerId", "20")
                .param("receiverId", "10")
                .param("amount", "15.00")
                .param("date", "2026-09-17")
                .param("notes", "Cash")
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/groups/1"))

        verify(transactionService).createSettlement(
            groupId = eq(1L),
            payerId = eq(20L),
            receiverId = eq(10L),
            amount = eq(1500L),
            date = any(),
            notes = eq("Cash")
        )
    }


    @Test
    fun `POST settle with same payer and receiver fails validation`() {
        `when`(groupService.getGroup(1L)).thenReturn(group)
        `when`(groupService.getParticipants(1L)).thenReturn(listOf(alice, bob))
        `when`(transactionService.getParticipantBalances(1L)).thenReturn(emptyMap())

        mockMvc.perform(
            post("/groups/1/settle")
                .param("payerId", "10")
                .param("receiverId", "10")
                .param("amount", "10.00")
                .param("date", "2026-09-17")
        )
            .andExpect(status().isOk)
            .andExpect(view().name("groups/settle"))
            .andExpect(model().attributeHasFieldErrors("settleForm", "receiverId"))
            .andExpect(content().string(containsString("Payer and receiver cannot be the same person")))

        verify(transactionService, never()).createSettlement(anyLong(), anyLong(), anyLong(), anyLong(), any(), any())
    }

    @Test
    fun `POST settle with missing amount fails validation`() {
        `when`(groupService.getGroup(1L)).thenReturn(group)
        `when`(groupService.getParticipants(1L)).thenReturn(listOf(alice, bob))
        `when`(transactionService.getParticipantBalances(1L)).thenReturn(emptyMap())

        mockMvc.perform(
            post("/groups/1/settle")
                .param("payerId", "20")
                .param("receiverId", "10")
                .param("date", "2026-09-17")
        )
            .andExpect(status().isOk)
            .andExpect(view().name("groups/settle"))
            .andExpect(model().attributeHasFieldErrors("settleForm", "amount"))

        verify(transactionService, never()).createSettlement(anyLong(), anyLong(), anyLong(), anyLong(), any(), any())
    }

    @Test
    fun `POST settle when service throws IllegalArgumentException displays global error`() {
        `when`(groupService.getGroup(1L)).thenReturn(group)
        `when`(groupService.getParticipants(1L)).thenReturn(listOf(alice, bob))
        `when`(transactionService.getParticipantBalances(1L)).thenReturn(emptyMap())

        `when`(
            transactionService.createSettlement(
                groupId = eq(1L),
                payerId = eq(20L),
                receiverId = eq(10L),
                amount = eq(1000L),
                date = any(),
                notes = any()
            )
        ).thenThrow(IllegalArgumentException("Custom validation failed"))

        mockMvc.perform(
            post("/groups/1/settle")
                .param("payerId", "20")
                .param("receiverId", "10")
                .param("amount", "10.00")
                .param("date", "2026-09-17")
        )
            .andExpect(status().isOk)
            .andExpect(view().name("groups/settle"))
            .andExpect(content().string(containsString("Custom validation failed")))
    }
}
