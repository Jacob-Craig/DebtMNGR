package com.jacobcraig.debtmngr

import com.jacobcraig.debtmngr.repository.*
import org.hamcrest.Matchers.containsString
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
class GroupCreationIntegrationTest {

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
    fun `full flow - empty state, create group, and list on dashboard`() {
        // 1. A user navigates to the dashboard (/) and sees an empty state for groups
        mockMvc.perform(get("/"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("No groups yet")))
            .andExpect(content().string(containsString("Create Group")))

        // 2. A user clicks "Create Group" and submits a form with a name and description
        mockMvc.perform(
            post("/groups")
                .param("name", "Holiday Trip")
                .param("description", "Flights and hotel")
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/"))

        // 3. The new Group is saved to the database
        val groups = groupRepository.findAll()
        org.junit.jupiter.api.Assertions.assertEquals(1, groups.size)
        org.junit.jupiter.api.Assertions.assertEquals("Holiday Trip", groups[0].name)
        org.junit.jupiter.api.Assertions.assertEquals("Flights and hotel", groups[0].description)

        // 4. The dashboard lists the newly created Group
        mockMvc.perform(get("/"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Holiday Trip")))
            .andExpect(content().string(containsString("Flights and hotel")))
    }
}
