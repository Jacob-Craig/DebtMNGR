package com.jacobcraig.debtmngr.web

import com.jacobcraig.debtmngr.domain.Group
import com.jacobcraig.debtmngr.domain.Participant
import com.jacobcraig.debtmngr.service.GroupService
import com.jacobcraig.debtmngr.service.TransactionService
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest(DashboardController::class)
class DashboardControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var groupService: GroupService

    @MockitoBean
    private lateinit var transactionService: TransactionService

    @Test
    fun `GET root displays dashboard with empty state when no groups exist`() {
        `when`(groupService.getAllGroups()).thenReturn(emptyList())

        mockMvc.perform(get("/"))
            .andExpect(status().isOk)
            .andExpect(view().name("dashboard"))
            .andExpect(model().attributeExists("groups"))
            .andExpect(content().string(containsString("No groups yet")))
            .andExpect(content().string(containsString("/groups/new")))
    }

    @Test
    fun `GET root displays dashboard listing existing groups with operator balances`() {
        val group1 = Group(id = 1L, name = "Trip to Spain", description = "Holiday trip")
        val group2 = Group(id = 2L, name = "Apartment", description = "Flatmates")
        val groups = listOf(group1, group2)
        `when`(groupService.getAllGroups()).thenReturn(groups)

        val aliceSelf = Participant(id = 10L, group = group1, name = "Alice", isSelf = true)
        val bob = Participant(id = 20L, group = group1, name = "Bob", isSelf = false)
        `when`(groupService.getParticipants(1L)).thenReturn(listOf(aliceSelf, bob))
        `when`(groupService.getParticipants(2L)).thenReturn(emptyList())

        `when`(transactionService.getParticipantBalances(1L)).thenReturn(mapOf(10L to 1500L, 20L to -1500L))

        mockMvc.perform(get("/"))
            .andExpect(status().isOk)
            .andExpect(view().name("dashboard"))
            .andExpect(model().attribute("groups", groups))
            .andExpect(model().attributeExists("groupBalances"))
            .andExpect(content().string(containsString("Trip to Spain")))
            .andExpect(content().string(containsString("Holiday trip")))
            .andExpect(content().string(containsString("Apartment")))
            .andExpect(content().string(containsString("+£15.00")))
    }
}
