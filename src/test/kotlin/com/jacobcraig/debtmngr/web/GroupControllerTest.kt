package com.jacobcraig.debtmngr.web

import com.jacobcraig.debtmngr.domain.Group
import com.jacobcraig.debtmngr.service.GroupService
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

@WebMvcTest(GroupController::class)
class GroupControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var groupService: GroupService

    @Test
    fun `GET new group form displays form view`() {
        mockMvc.perform(get("/groups/new"))
            .andExpect(status().isOk)
            .andExpect(view().name("groups/new"))
            .andExpect(model().attributeExists("groupForm"))
            .andExpect(content().string(containsString("Create Group")))
            .andExpect(content().string(containsString("name=\"name\"")))
            .andExpect(content().string(containsString("name=\"description\"")))
    }

    @Test
    fun `POST create group with valid inputs redirects to root`() {
        val createdGroup = Group(id = 1L, name = "Apartment", description = "Flatmates")
        `when`(groupService.createGroup("Apartment", "Flatmates")).thenReturn(createdGroup)

        mockMvc.perform(
            post("/groups")
                .param("name", "Apartment")
                .param("description", "Flatmates")
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/"))

        verify(groupService).createGroup("Apartment", "Flatmates")
    }

    @Test
    fun `POST create group with blank name returns form with validation errors`() {
        mockMvc.perform(
            post("/groups")
                .param("name", "   ")
                .param("description", "Flatmates")
        )
            .andExpect(status().isOk)
            .andExpect(view().name("groups/new"))
            .andExpect(model().attributeHasFieldErrors("groupForm", "name"))

        verify(groupService, never()).createGroup(anyString(), anyString())
    }
}
