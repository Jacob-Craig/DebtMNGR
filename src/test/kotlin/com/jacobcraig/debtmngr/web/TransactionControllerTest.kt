package com.jacobcraig.debtmngr.web

import com.jacobcraig.debtmngr.domain.Group
import com.jacobcraig.debtmngr.domain.Participant
import com.jacobcraig.debtmngr.domain.Transaction
import com.jacobcraig.debtmngr.service.GroupService
import com.jacobcraig.debtmngr.service.TransactionService
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest(TransactionController::class)
class TransactionControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var groupService: GroupService

    @MockitoBean
    private lateinit var transactionService: TransactionService

    private val group = Group(id = 1L, name = "Trip to Spain")
    private val alice = Participant(id = 10L, group = group, name = "Alice", isSelf = true)
    private val bob = Participant(id = 20L, group = group, name = "Bob", isSelf = false)

    @Test
    fun `GET new expense form displays form view with group and participants`() {
        `when`(groupService.getGroup(1L)).thenReturn(group)
        `when`(groupService.getParticipants(1L)).thenReturn(listOf(alice, bob))

        mockMvc.perform(get("/groups/1/transactions/new"))
            .andExpect(status().isOk)
            .andExpect(view().name("groups/transactions/new"))
            .andExpect(model().attribute("group", group))
            .andExpect(model().attribute("participants", listOf(alice, bob)))
            .andExpect(model().attributeExists("expenseForm"))
            .andExpect(content().string(containsString("Record Expense")))
            .andExpect(content().string(containsString("Alice")))
            .andExpect(content().string(containsString("Bob")))
    }

    @Test
    fun `POST create expense with valid inputs creates expense and redirects`() {
        val testDate = java.time.LocalDate.of(2026, 9, 17)
        val expectedInstant = testDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant()
        val createdTx = Transaction(
            id = 100L,
            group = group,
            description = "Tapas Dinner",
            amount = 4550L,
            payer = alice,
            createdAt = expectedInstant
        )
        `when`(
            transactionService.createExpense(
                groupId = 1L,
                payerId = 10L,
                amount = 4550L,
                description = "Tapas Dinner",
                consumerIds = listOf(10L, 20L),
                date = expectedInstant
            )
        ).thenReturn(createdTx)

        mockMvc.perform(
            post("/groups/1/transactions")
                .param("description", "Tapas Dinner")
                .param("amount", "45.50")
                .param("payerId", "10")
                .param("consumerIds", "10", "20")
                .param("date", "2026-09-17")
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/groups/1"))

        verify(transactionService).createExpense(
            groupId = 1L,
            payerId = 10L,
            amount = 4550L,
            description = "Tapas Dinner",
            consumerIds = listOf(10L, 20L),
            date = expectedInstant
        )
    }

    @Test
    fun `POST create expense with blank description returns form with errors`() {
        `when`(groupService.getGroup(1L)).thenReturn(group)
        `when`(groupService.getParticipants(1L)).thenReturn(listOf(alice, bob))

        mockMvc.perform(
            post("/groups/1/transactions")
                .param("description", "   ")
                .param("amount", "20.00")
                .param("payerId", "10")
                .param("consumerIds", "10", "20")
        )
            .andExpect(status().isOk)
            .andExpect(view().name("groups/transactions/new"))
            .andExpect(model().attributeHasFieldErrors("expenseForm", "description"))

        verifyNoInteractions(transactionService)
    }

    @Test
    fun `POST create expense with negative or zero amount returns form with errors`() {
        `when`(groupService.getGroup(1L)).thenReturn(group)
        `when`(groupService.getParticipants(1L)).thenReturn(listOf(alice, bob))

        mockMvc.perform(
            post("/groups/1/transactions")
                .param("description", "Drinks")
                .param("amount", "0.00")
                .param("payerId", "10")
                .param("consumerIds", "10", "20")
        )
            .andExpect(status().isOk)
            .andExpect(view().name("groups/transactions/new"))
            .andExpect(model().attributeHasFieldErrors("expenseForm", "amount"))

        verifyNoInteractions(transactionService)
    }

    @Test
    fun `POST create expense with missing payer returns form with errors`() {
        `when`(groupService.getGroup(1L)).thenReturn(group)
        `when`(groupService.getParticipants(1L)).thenReturn(listOf(alice, bob))

        mockMvc.perform(
            post("/groups/1/transactions")
                .param("description", "Drinks")
                .param("amount", "15.00")
                .param("consumerIds", "10", "20")
        )
            .andExpect(status().isOk)
            .andExpect(view().name("groups/transactions/new"))
            .andExpect(model().attributeHasFieldErrors("expenseForm", "payerId"))

        verifyNoInteractions(transactionService)
    }

    @Test
    fun `POST create expense with empty consumers returns form with errors`() {
        `when`(groupService.getGroup(1L)).thenReturn(group)
        `when`(groupService.getParticipants(1L)).thenReturn(listOf(alice, bob))

        mockMvc.perform(
            post("/groups/1/transactions")
                .param("description", "Drinks")
                .param("amount", "15.00")
                .param("payerId", "10")
        )
            .andExpect(status().isOk)
            .andExpect(view().name("groups/transactions/new"))
            .andExpect(model().attributeHasFieldErrors("expenseForm", "consumerIds"))

        verifyNoInteractions(transactionService)
    }
}
