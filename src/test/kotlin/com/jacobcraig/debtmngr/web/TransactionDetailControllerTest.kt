package com.jacobcraig.debtmngr.web

import com.jacobcraig.debtmngr.domain.*
import com.jacobcraig.debtmngr.service.CategoryService
import com.jacobcraig.debtmngr.service.GroupService
import com.jacobcraig.debtmngr.service.TransactionService
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
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
import java.time.Instant

@WebMvcTest(TransactionDetailController::class)
class TransactionDetailControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var transactionService: TransactionService

    @MockitoBean
    private lateinit var groupService: GroupService

    @MockitoBean
    private lateinit var categoryService: CategoryService

    private val group = Group(id = 1L, name = "Apartment")
    private val aliceAccount = Account(id = 100L)
    private val bobAccount = Account(id = 200L)
    private val alice = Participant(id = 10L, group = group, name = "Alice", isSelf = true, account = aliceAccount)
    private val bob = Participant(id = 20L, group = group, name = "Bob", isSelf = false, account = bobAccount)

    private fun <T> eqNonNull(value: T): T {
        org.mockito.Mockito.eq(value)
        return value
    }

    init {
        group.participants.add(alice)
        group.participants.add(bob)
    }

    @Test
    fun `GET transaction detail for unlocked transaction displays entries and edit delete actions`() {
        val tx = Transaction(id = 1L, group = group, description = "Groceries", amount = 2000L, payer = alice, isLocked = false)
        tx.addEntry(Entry(id = 101L, transaction = tx, account = aliceAccount, type = EntryType.CREDIT, amount = 2000L))
        tx.addEntry(Entry(id = 102L, transaction = tx, account = aliceAccount, type = EntryType.DEBIT, amount = 1000L))
        tx.addEntry(Entry(id = 103L, transaction = tx, account = bobAccount, type = EntryType.DEBIT, amount = 1000L))

        `when`(transactionService.getTransaction(1L)).thenReturn(tx)
        `when`(transactionService.getAuditLogs(1L)).thenReturn(emptyList())
        `when`(transactionService.getAdjustments(1L)).thenReturn(emptyList())
        `when`(groupService.getParticipants(1L)).thenReturn(listOf(alice, bob))

        mockMvc.perform(get("/transactions/1"))
            .andExpect(status().isOk)
            .andExpect(view().name("transactions/show"))
            .andExpect(model().attribute("transaction", tx))
            .andExpect(model().attributeExists("auditLogs"))
            .andExpect(model().attributeExists("adjustments"))
            .andExpect(content().string(containsString("Groceries")))
            .andExpect(content().string(containsString("£20.00")))
            .andExpect(content().string(containsString("Edit Transaction")))
            .andExpect(content().string(containsString("Delete Transaction")))
            .andExpect(content().string(not(containsString("Adjust Expense"))))
    }

    @Test
    fun `GET transaction detail for locked transaction disables edit delete and offers adjust`() {
        val tx = Transaction(id = 2L, group = group, description = "Locked Rent", amount = 50000L, payer = alice, isLocked = true)
        tx.addEntry(Entry(id = 201L, transaction = tx, account = aliceAccount, type = EntryType.CREDIT, amount = 50000L))
        tx.addEntry(Entry(id = 202L, transaction = tx, account = bobAccount, type = EntryType.DEBIT, amount = 50000L))

        `when`(transactionService.getTransaction(2L)).thenReturn(tx)
        `when`(transactionService.getAuditLogs(2L)).thenReturn(emptyList())
        `when`(transactionService.getAdjustments(2L)).thenReturn(emptyList())
        `when`(groupService.getParticipants(1L)).thenReturn(listOf(alice, bob))

        mockMvc.perform(get("/transactions/2"))
            .andExpect(status().isOk)
            .andExpect(view().name("transactions/show"))
            .andExpect(content().string(containsString("Locked")))
            .andExpect(content().string(containsString("Adjust")))
            .andExpect(content().string(not(containsString("href=\"/transactions/2/edit\""))))
    }

    @Test
    fun `GET edit returns 200 for unlocked transaction`() {
        val tx = Transaction(id = 1L, group = group, description = "Dinner", amount = 2000L, payer = alice, isLocked = false)
        tx.addEntry(Entry(id = 101L, transaction = tx, account = aliceAccount, type = EntryType.CREDIT, amount = 2000L))
        tx.addEntry(Entry(id = 102L, transaction = tx, account = bobAccount, type = EntryType.DEBIT, amount = 2000L))

        `when`(transactionService.getTransaction(1L)).thenReturn(tx)
        `when`(groupService.getGroup(1L)).thenReturn(group)
        `when`(groupService.getParticipants(1L)).thenReturn(listOf(alice, bob))
        `when`(categoryService.getCategoriesForGroup(1L)).thenReturn(emptyList())

        mockMvc.perform(get("/transactions/1/edit"))
            .andExpect(status().isOk)
            .andExpect(view().name("transactions/edit"))
            .andExpect(model().attributeExists("editForm"))
            .andExpect(content().string(containsString("Edit Transaction")))
    }

    @Test
    fun `GET edit on locked transaction redirects to transaction detail`() {
        val tx = Transaction(id = 2L, group = group, description = "Locked", amount = 2000L, payer = alice, isLocked = true)
        `when`(transactionService.getTransaction(2L)).thenReturn(tx)

        mockMvc.perform(get("/transactions/2/edit"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/transactions/2"))
    }

    @Test
    fun `POST edit with valid data calls service and redirects to new transaction`() {
        val oldTx = Transaction(id = 1L, group = group, description = "Dinner", amount = 2000L, payer = alice, isLocked = false)
        val newTx = Transaction(id = 5L, group = group, description = "Dinner updated", amount = 2500L, payer = alice)

        `when`(transactionService.getTransaction(1L)).thenReturn(oldTx)
        `when`(transactionService.editExpense(
            transactionId = eq(1L),
            description = eqNonNull("Dinner updated"),
            amount = eq(2500L),
            payerId = eq(10L),
            consumerIds = eqNonNull(listOf(10L, 20L)),
            date = any(),
            splitMode = eqNonNull(SplitMode.EQUAL),
            exactAmounts = anyMap(),
            categoryId = any(),
            reason = eq("Typo fixed")
        )).thenReturn(newTx)

        mockMvc.perform(
            post("/transactions/1/edit")
                .param("description", "Dinner updated")
                .param("amount", "25.00")
                .param("payerId", "10")
                .param("consumerIds", "10", "20")
                .param("date", "2026-09-17")
                .param("splitMode", "EQUAL")
                .param("reason", "Typo fixed")
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/transactions/5"))
    }

    @Test
    fun `POST delete on unlocked transaction calls service and redirects to transaction detail`() {
        val tx = Transaction(id = 1L, group = group, description = "Dinner", amount = 2000L, payer = alice, isLocked = false)
        `when`(transactionService.deleteTransaction(1L, "Accidental duplicate")).thenReturn(tx)

        mockMvc.perform(
            post("/transactions/1/delete")
                .param("reason", "Accidental duplicate")
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/transactions/1"))

        verify(transactionService).deleteTransaction(1L, "Accidental duplicate")
    }

    @Test
    fun `GET adjust returns 200 for locked transaction`() {
        val tx = Transaction(id = 2L, group = group, description = "Locked Rent", amount = 50000L, payer = alice, isLocked = true)
        `when`(transactionService.getTransaction(2L)).thenReturn(tx)
        `when`(groupService.getGroup(1L)).thenReturn(group)
        `when`(groupService.getParticipants(1L)).thenReturn(listOf(alice, bob))

        mockMvc.perform(get("/transactions/2/adjust"))
            .andExpect(status().isOk)
            .andExpect(view().name("transactions/adjust"))
            .andExpect(model().attributeExists("adjustmentForm"))
            .andExpect(content().string(containsString("Adjust Transaction")))
    }

    @Test
    fun `GET adjust on unlocked transaction redirects to transaction detail`() {
        val tx = Transaction(id = 1L, group = group, description = "Unlocked", amount = 2000L, payer = alice, isLocked = false)
        `when`(transactionService.getTransaction(1L)).thenReturn(tx)

        mockMvc.perform(get("/transactions/1/adjust"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/transactions/1"))
    }

    @Test
    fun `POST adjust with valid data creates adjustment and redirects to adjustment detail`() {
        val originalTx = Transaction(id = 2L, group = group, description = "Locked Rent", amount = 50000L, payer = alice, isLocked = true)
        val adjustmentTx = Transaction(id = 9L, group = group, description = "Adjustment: Late fee", amount = 2500L, payer = alice, type = TransactionType.ADJUSTMENT, originalTransaction = originalTx)

        `when`(transactionService.getTransaction(2L)).thenReturn(originalTx)
        `when`(transactionService.createAdjustment(
            originalTransactionId = eq(2L),
            description = eqNonNull("Adjustment: Late fee"),
            amount = eq(2500L),
            payerId = eq(10L),
            consumerIds = eqNonNull(listOf(10L, 20L)),
            splitMode = eqNonNull(SplitMode.EQUAL),
            exactAmounts = anyMap(),
            date = any()
        )).thenReturn(adjustmentTx)

        mockMvc.perform(
            post("/transactions/2/adjust")
                .param("description", "Adjustment: Late fee")
                .param("amount", "25.00")
                .param("payerId", "10")
                .param("consumerIds", "10", "20")
                .param("date", "2026-09-17")
                .param("splitMode", "EQUAL")
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/transactions/9"))
    }
}
