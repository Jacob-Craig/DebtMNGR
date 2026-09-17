package com.jacobcraig.debtmngr.web

import com.jacobcraig.debtmngr.domain.Group
import com.jacobcraig.debtmngr.domain.Participant
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

    @Test
    fun `GET group detail displays group and roster`() {
        val group = Group(id = 1L, name = "Trip to Spain", description = "Vacation")
        val participants = listOf(
            Participant(id = 10L, group = group, name = "Alice", isSelf = true),
            Participant(id = 11L, group = group, name = "Bob", isSelf = false)
        )
        `when`(groupService.getGroup(1L)).thenReturn(group)
        `when`(groupService.getParticipants(1L)).thenReturn(participants)

        mockMvc.perform(get("/groups/1"))
            .andExpect(status().isOk)
            .andExpect(view().name("groups/show"))
            .andExpect(model().attribute("group", group))
            .andExpect(model().attribute("participants", participants))
            .andExpect(model().attributeExists("participantForm"))
    }

    @Test
    fun `POST add participant with valid name redirects to group detail`() {
        val group = Group(id = 1L, name = "Trip to Spain")
        val participant = Participant(id = 10L, group = group, name = "Charlie", isSelf = true)
        `when`(groupService.addParticipant(1L, "Charlie", true)).thenReturn(participant)

        mockMvc.perform(
            post("/groups/1/participants")
                .param("name", "Charlie")
                .param("isSelf", "true")
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/groups/1"))

        verify(groupService).addParticipant(1L, "Charlie", true)
    }

    @Test
    fun `POST add participant with blank name returns show view with validation errors`() {
        val group = Group(id = 1L, name = "Trip to Spain")
        `when`(groupService.getGroup(1L)).thenReturn(group)
        `when`(groupService.getParticipants(1L)).thenReturn(emptyList())

        mockMvc.perform(
            post("/groups/1/participants")
                .param("name", "   ")
        )
            .andExpect(status().isOk)
            .andExpect(view().name("groups/show"))
            .andExpect(model().attributeHasFieldErrors("participantForm", "name"))
            .andExpect(model().attribute("group", group))

        verify(groupService, never()).addParticipant(anyLong(), anyString(), anyBoolean())
    }

    @Test
    fun `POST designate participant as self redirects to group detail`() {
        val group = Group(id = 1L, name = "Trip to Spain")
        val participant = Participant(id = 11L, group = group, name = "Bob", isSelf = true)
        `when`(groupService.designateSelf(1L, 11L)).thenReturn(participant)

        mockMvc.perform(post("/groups/1/participants/11/self"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/groups/1"))

        verify(groupService).designateSelf(1L, 11L)
    }
}
