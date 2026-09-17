package com.jacobcraig.debtmngr.web

import com.jacobcraig.debtmngr.domain.Group
import com.jacobcraig.debtmngr.domain.Participant
import com.jacobcraig.debtmngr.domain.Transaction
import com.jacobcraig.debtmngr.domain.TransactionType
import com.jacobcraig.debtmngr.service.*
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasProperty
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset

@WebMvcTest(GlobalSettlementController::class)
class GlobalSettlementControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var transactionService: TransactionService

    @Test
    fun `GET settle returns 200 with settle view and contacts list`() {
        val bobBreakdown = ContactBreakdown(
            contactName = "Bob",
            totalNet = 1500L,
            groupDebts = listOf(ContactGroupDebt(1L, "Flatmates", 1500L))
        )
        val summary = GlobalNetSummary(operatorNetTotal = 1500L, contacts = listOf(bobBreakdown))
        `when`(transactionService.getGlobalNetSummary()).thenReturn(summary)

        mockMvc.perform(get("/settle"))
            .andExpect(status().isOk)
            .andExpect(view().name("settle"))
            .andExpect(model().attributeExists("form"))
            .andExpect(model().attributeExists("summary"))
            .andExpect(content().string(containsString("Global Settle Up")))
            .andExpect(content().string(containsString("Bob")))
    }

    @Test
    fun `GET settle with contactName query param pre-populates form`() {
        val bobBreakdown = ContactBreakdown(
            contactName = "Bob",
            totalNet = 2500L,
            groupDebts = listOf(ContactGroupDebt(1L, "Flatmates", 2500L))
        )
        val summary = GlobalNetSummary(operatorNetTotal = 2500L, contacts = listOf(bobBreakdown))
        `when`(transactionService.getGlobalNetSummary()).thenReturn(summary)

        mockMvc.perform(get("/settle").param("contactName", "Bob"))
            .andExpect(status().isOk)
            .andExpect(view().name("settle"))
            .andExpect(model().attribute("form", hasProperty<GlobalSettleUpForm>("contactName", equalTo("Bob"))))
            .andExpect(model().attribute("form", hasProperty<GlobalSettleUpForm>("amount", equalTo(BigDecimal("25.00")))))
            .andExpect(model().attribute("form", hasProperty<GlobalSettleUpForm>("payerIsSelf", equalTo(false))))
    }

    @Test
    fun `POST settle with valid parameters executes settlement and redirects to dashboard`() {
        val group = Group(id = 1L, name = "Flatmates")
        val bob = Participant(id = 2L, group = group, name = "Bob", isSelf = false)
        val expectedInstant = LocalDate.parse("2026-09-17").atStartOfDay(ZoneOffset.UTC).toInstant()
        val settlementTx = Transaction(
            id = 100L,
            group = group,
            description = "Settlement: Bob paid Alice",
            amount = 2500L,
            payer = bob,
            type = TransactionType.SETTLEMENT
        )

        `when`(
            transactionService.createGlobalSettlement(
                contactName = "Bob",
                payerIsSelf = false,
                amount = 2500L,
                date = expectedInstant,
                notes = "Bank transfer"
            )
        ).thenReturn(listOf(settlementTx))

        mockMvc.perform(
            post("/settle")
                .param("contactName", "Bob")
                .param("payerIsSelf", "false")
                .param("amount", "25.00")
                .param("date", "2026-09-17")
                .param("notes", "Bank transfer")
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/"))

        verify(transactionService).createGlobalSettlement(
            contactName = "Bob",
            payerIsSelf = false,
            amount = 2500L,
            date = expectedInstant,
            notes = "Bank transfer"
        )
    }

    @Test
    fun `POST settle with missing amount fails validation`() {
        val summary = GlobalNetSummary(operatorNetTotal = 0L, contacts = emptyList())
        `when`(transactionService.getGlobalNetSummary()).thenReturn(summary)

        mockMvc.perform(
            post("/settle")
                .param("contactName", "Bob")
                .param("payerIsSelf", "false")
                .param("date", "2026-09-17")
        )
            .andExpect(status().isOk)
            .andExpect(view().name("settle"))
            .andExpect(model().attributeHasFieldErrors("form", "amount"))

        verify(transactionService, never()).createGlobalSettlement(
            anyString(),
            anyBoolean(),
            anyLong(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `POST settle when service throws IllegalArgumentException displays error`() {
        val summary = GlobalNetSummary(operatorNetTotal = 0L, contacts = emptyList())
        `when`(transactionService.getGlobalNetSummary()).thenReturn(summary)
        val expectedInstant = LocalDate.parse("2026-09-17").atStartOfDay(ZoneOffset.UTC).toInstant()

        `when`(
            transactionService.createGlobalSettlement(
                contactName = "Bob",
                payerIsSelf = false,
                amount = 5000L,
                date = expectedInstant,
                notes = null
            )
        ).thenThrow(IllegalArgumentException("Settlement amount cannot exceed total debt"))

        mockMvc.perform(
            post("/settle")
                .param("contactName", "Bob")
                .param("payerIsSelf", "false")
                .param("amount", "50.00")
                .param("date", "2026-09-17")
        )
            .andExpect(status().isOk)
            .andExpect(view().name("settle"))
            .andExpect(content().string(containsString("Settlement amount cannot exceed total debt")))
    }

    private fun <T> anyOrNull(): T = any()
}
