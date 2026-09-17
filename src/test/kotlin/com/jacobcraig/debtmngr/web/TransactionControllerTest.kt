package com.jacobcraig.debtmngr.web

import com.jacobcraig.debtmngr.domain.Category
import com.jacobcraig.debtmngr.domain.Group
import com.jacobcraig.debtmngr.domain.Participant
import com.jacobcraig.debtmngr.domain.Transaction
import com.jacobcraig.debtmngr.service.CategoryService
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

    @MockitoBean
    private lateinit var categoryService: CategoryService

    private val group = Group(id = 1L, name = "Trip to Spain")
    private val alice = Participant(id = 10L, group = group, name = "Alice", isSelf = true)
    private val bob = Participant(id = 20L, group = group, name = "Bob", isSelf = false)
    private val groceriesCat = Category(id = 50L, name = "Groceries", systemKey = "GROCERIES")

    @Test
    fun `GET new expense form displays form view with group and participants`() {
        `when`(groupService.getGroup(1L)).thenReturn(group)
        `when`(groupService.getParticipants(1L)).thenReturn(listOf(alice, bob))
        `when`(categoryService.getCategoriesForGroup(1L)).thenReturn(listOf(groceriesCat))

        mockMvc.perform(get("/groups/1/transactions/new"))
            .andExpect(status().isOk)
            .andExpect(view().name("groups/transactions/new"))
            .andExpect(model().attribute("group", group))
            .andExpect(model().attribute("participants", listOf(alice, bob)))
            .andExpect(model().attribute("categories", listOf(groceriesCat)))
            .andExpect(model().attributeExists("expenseForm"))
            .andExpect(content().string(containsString("Record Expense")))
            .andExpect(content().string(containsString("Category")))
            .andExpect(content().string(containsString("Groceries")))
            .andExpect(content().string(containsString("Split Mode")))
            .andExpect(content().string(containsString("Split Equally")))
            .andExpect(content().string(containsString("Exact Amounts")))
            .andExpect(content().string(containsString("Split by Exact Amounts")))
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

    @Test
    fun `POST create expense with splitMode EXACT and valid amounts creates expense and redirects`() {
        val testDate = java.time.LocalDate.of(2026, 9, 17)
        val expectedInstant = testDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant()
        val createdTx = Transaction(
            id = 101L,
            group = group,
            description = "Concert Tickets",
            amount = 5000L,
            payer = alice,
            createdAt = expectedInstant
        )
        `when`(
            transactionService.createExpense(
                groupId = 1L,
                payerId = 10L,
                amount = 5000L,
                description = "Concert Tickets",
                consumerIds = listOf(10L, 20L),
                date = expectedInstant,
                splitMode = com.jacobcraig.debtmngr.domain.SplitMode.EXACT,
                exactAmounts = mapOf(10L to 2000L, 20L to 3000L)
            )
        ).thenReturn(createdTx)

        mockMvc.perform(
            post("/groups/1/transactions")
                .param("description", "Concert Tickets")
                .param("amount", "50.00")
                .param("payerId", "10")
                .param("splitMode", "EXACT")
                .param("exactAmounts[10]", "20.00")
                .param("exactAmounts[20]", "30.00")
                .param("date", "2026-09-17")
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/groups/1"))

        verify(transactionService).createExpense(
            groupId = 1L,
            payerId = 10L,
            amount = 5000L,
            description = "Concert Tickets",
            consumerIds = listOf(10L, 20L),
            date = expectedInstant,
            splitMode = com.jacobcraig.debtmngr.domain.SplitMode.EXACT,
            exactAmounts = mapOf(10L to 2000L, 20L to 3000L)
        )
    }

    @Test
    fun `POST create expense with splitMode EXACT where sum does not match total returns form with errors`() {
        `when`(groupService.getGroup(1L)).thenReturn(group)
        `when`(groupService.getParticipants(1L)).thenReturn(listOf(alice, bob))

        mockMvc.perform(
            post("/groups/1/transactions")
                .param("description", "Concert Tickets")
                .param("amount", "50.00")
                .param("payerId", "10")
                .param("splitMode", "EXACT")
                .param("exactAmounts[10]", "20.00")
                .param("exactAmounts[20]", "25.00")
                .param("date", "2026-09-17")
        )
            .andExpect(status().isOk)
            .andExpect(view().name("groups/transactions/new"))
            .andExpect(model().attributeHasFieldErrors("expenseForm", "exactAmounts"))

        verifyNoInteractions(transactionService)
    }

    @Test
    fun `POST create expense with splitMode EXACT and negative split amount returns form with errors`() {
        `when`(groupService.getGroup(1L)).thenReturn(group)
        `when`(groupService.getParticipants(1L)).thenReturn(listOf(alice, bob))

        mockMvc.perform(
            post("/groups/1/transactions")
                .param("description", "Concert Tickets")
                .param("amount", "50.00")
                .param("payerId", "10")
                .param("splitMode", "EXACT")
                .param("exactAmounts[10]", "60.00")
                .param("exactAmounts[20]", "-10.00")
                .param("date", "2026-09-17")
        )
            .andExpect(status().isOk)
            .andExpect(view().name("groups/transactions/new"))
            .andExpect(model().attributeHasFieldErrors("expenseForm", "exactAmounts"))

        verifyNoInteractions(transactionService)
    }

    @Test
    fun `POST create expense with splitMode EXACT and empty exact amounts returns form with errors`() {
        `when`(groupService.getGroup(1L)).thenReturn(group)
        `when`(groupService.getParticipants(1L)).thenReturn(listOf(alice, bob))

        mockMvc.perform(
            post("/groups/1/transactions")
                .param("description", "Concert Tickets")
                .param("amount", "50.00")
                .param("payerId", "10")
                .param("splitMode", "EXACT")
                .param("date", "2026-09-17")
        )
            .andExpect(status().isOk)
            .andExpect(view().name("groups/transactions/new"))
            .andExpect(model().attributeHasFieldErrors("expenseForm", "exactAmounts"))

        verifyNoInteractions(transactionService)
    }

    @Test
    fun `POST create expense catches IllegalArgumentException from service and returns form with global error`() {
        val testDate = java.time.LocalDate.of(2026, 9, 17)
        val expectedInstant = testDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant()
        `when`(groupService.getGroup(1L)).thenReturn(group)
        `when`(groupService.getParticipants(1L)).thenReturn(listOf(alice, bob))
        `when`(
            transactionService.createExpense(
                groupId = 1L,
                payerId = 10L,
                amount = 5000L,
                description = "Concert Tickets",
                consumerIds = listOf(10L, 20L),
                date = expectedInstant,
                splitMode = com.jacobcraig.debtmngr.domain.SplitMode.EXACT,
                exactAmounts = mapOf(10L to 2000L, 20L to 3000L)
            )
        ).thenThrow(IllegalArgumentException("Service layer validation failed"))

        mockMvc.perform(
            post("/groups/1/transactions")
                .param("description", "Concert Tickets")
                .param("amount", "50.00")
                .param("payerId", "10")
                .param("splitMode", "EXACT")
                .param("exactAmounts[10]", "20.00")
                .param("exactAmounts[20]", "30.00")
                .param("date", "2026-09-17")
        )
            .andExpect(status().isOk)
            .andExpect(view().name("groups/transactions/new"))
            .andExpect(model().attributeHasErrors("expenseForm"))
            .andExpect(content().string(containsString("Service layer validation failed")))
    }

    @Test
    fun `POST create expense with existing categoryId passes categoryId to service`() {
        val testDate = java.time.LocalDate.of(2026, 9, 17)
        val expectedInstant = testDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant()
        val createdTx = Transaction(
            id = 102L,
            group = group,
            description = "Groceries trip",
            amount = 3000L,
            payer = alice,
            category = groceriesCat,
            createdAt = expectedInstant
        )
        `when`(
            transactionService.createExpense(
                groupId = 1L,
                payerId = 10L,
                amount = 3000L,
                description = "Groceries trip",
                consumerIds = listOf(10L, 20L),
                date = expectedInstant,
                categoryId = 50L
            )
        ).thenReturn(createdTx)

        mockMvc.perform(
            post("/groups/1/transactions")
                .param("description", "Groceries trip")
                .param("amount", "30.00")
                .param("payerId", "10")
                .param("consumerIds", "10", "20")
                .param("date", "2026-09-17")
                .param("categoryId", "50")
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/groups/1"))

        verify(transactionService).createExpense(
            groupId = 1L,
            payerId = 10L,
            amount = 3000L,
            description = "Groceries trip",
            consumerIds = listOf(10L, 20L),
            date = expectedInstant,
            categoryId = 50L
        )
    }

    @Test
    fun `POST create expense with customCategoryName creates category and passes id to service`() {
        val testDate = java.time.LocalDate.of(2026, 9, 17)
        val expectedInstant = testDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant()
        val newCustomCat = Category(id = 99L, name = "Ski Equipment", group = group)
        `when`(categoryService.createCustomCategory(1L, "Ski Equipment")).thenReturn(newCustomCat)

        val createdTx = Transaction(
            id = 103L,
            group = group,
            description = "Ski pass and poles",
            amount = 8000L,
            payer = alice,
            category = newCustomCat,
            createdAt = expectedInstant
        )
        `when`(
            transactionService.createExpense(
                groupId = 1L,
                payerId = 10L,
                amount = 8000L,
                description = "Ski pass and poles",
                consumerIds = listOf(10L, 20L),
                date = expectedInstant,
                categoryId = 99L
            )
        ).thenReturn(createdTx)

        mockMvc.perform(
            post("/groups/1/transactions")
                .param("description", "Ski pass and poles")
                .param("amount", "80.00")
                .param("payerId", "10")
                .param("consumerIds", "10", "20")
                .param("date", "2026-09-17")
                .param("customCategoryName", "Ski Equipment")
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/groups/1"))

        verify(categoryService).createCustomCategory(1L, "Ski Equipment")
        verify(transactionService).createExpense(
            groupId = 1L,
            payerId = 10L,
            amount = 8000L,
            description = "Ski pass and poles",
            consumerIds = listOf(10L, 20L),
            date = expectedInstant,
            categoryId = 99L
        )
    }

    @Test
    fun `POST create expense with custom category choice but blank customCategoryName returns form with error`() {
        `when`(groupService.getGroup(1L)).thenReturn(group)
        `when`(groupService.getParticipants(1L)).thenReturn(listOf(alice, bob))
        `when`(categoryService.getCategoriesForGroup(1L)).thenReturn(listOf(groceriesCat))

        mockMvc.perform(
            post("/groups/1/transactions")
                .param("description", "Ski pass and poles")
                .param("amount", "80.00")
                .param("payerId", "10")
                .param("consumerIds", "10", "20")
                .param("date", "2026-09-17")
                .param("categoryId", "-1")
                .param("customCategoryName", "   ")
        )
            .andExpect(status().isOk)
            .andExpect(view().name("groups/transactions/new"))
            .andExpect(model().attributeHasFieldErrors("expenseForm", "customCategoryName"))

        verifyNoInteractions(transactionService)
    }
}
