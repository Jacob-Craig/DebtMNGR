package com.jacobcraig.debtmngr.web

import com.jacobcraig.debtmngr.domain.Group
import com.jacobcraig.debtmngr.service.GroupService
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
    fun `GET root displays dashboard listing existing groups`() {
        val groups = listOf(
            Group(id = 1L, name = "Trip to Spain", description = "Holiday trip"),
            Group(id = 2L, name = "Apartment", description = "Flatmates")
        )
        `when`(groupService.getAllGroups()).thenReturn(groups)

        mockMvc.perform(get("/"))
            .andExpect(status().isOk)
            .andExpect(view().name("dashboard"))
            .andExpect(model().attribute("groups", groups))
            .andExpect(content().string(containsString("Trip to Spain")))
            .andExpect(content().string(containsString("Holiday trip")))
            .andExpect(content().string(containsString("Apartment")))
    }
}
