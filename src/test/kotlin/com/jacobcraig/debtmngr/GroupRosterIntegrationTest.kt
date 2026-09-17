package com.jacobcraig.debtmngr

import com.jacobcraig.debtmngr.domain.Group
import com.jacobcraig.debtmngr.repository.*
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Assertions.*
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
class GroupRosterIntegrationTest {

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
    fun `full flow - group roster, adding participants, designating self, and viewing roster`() {
        // Setup: Create a group
        val group = groupRepository.save(Group(name = "Apartment 4B", description = "Shared flat expenses"))
        val groupId = checkNotNull(group.id)

        // 1. Navigating to /groups/{id} displays the Group's details and an empty participant roster
        mockMvc.perform(get("/groups/$groupId"))
            .andExpect(status().isOk)
            .andExpect(view().name("groups/show"))
            .andExpect(content().string(containsString("Apartment 4B")))
            .andExpect(content().string(containsString("Shared flat expenses")))
            .andExpect(content().string(containsString("No participants yet")))

        // 2. A user can submit a form on the Group page to add a new Participant by name
        mockMvc.perform(
            post("/groups/$groupId/participants")
                .param("name", "Alice")
                .param("isSelf", "true")
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/groups/$groupId"))

        // Verify Alice in DB and Cascade-created Account
        val participantsAfterFirst = participantRepository.findByGroupIdOrderByIdAsc(groupId)
        assertEquals(1, participantsAfterFirst.size)
        val alice = participantsAfterFirst[0]
        assertEquals("Alice", alice.name)
        assertTrue(alice.isSelf)
        assertNotNull(alice.account)
        val aliceAccountId = checkNotNull(alice.account.id) { "Alice account id must not be null" }
        assertTrue(accountRepository.findById(aliceAccountId).isPresent)

        // 3. Add a second participant (Bob) without isSelf
        mockMvc.perform(
            post("/groups/$groupId/participants")
                .param("name", "Bob")
                .param("isSelf", "false")
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/groups/$groupId"))

        // 4. The roster displays all added Participants
        val participantsAfterSecond = participantRepository.findByGroupIdOrderByIdAsc(groupId)
        assertEquals(2, participantsAfterSecond.size)
        val bob = participantsAfterSecond.first { it.name == "Bob" }
        assertFalse(bob.isSelf)
        assertNotNull(bob.account.id)

        mockMvc.perform(get("/groups/$groupId"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Alice")))
            .andExpect(content().string(containsString("Bob")))
            .andExpect(content().string(not(containsString("No participants yet"))))

        // 5. A user can designate exactly one Participant in the Group as isSelf = true
        // Designate Bob as self
        mockMvc.perform(post("/groups/$groupId/participants/${bob.id}/self"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/groups/$groupId"))

        // Verify Bob is now self and Alice is not
        val aliceId = checkNotNull(alice.id) { "Alice id must not be null" }
        val bobId = checkNotNull(bob.id) { "Bob id must not be null" }
        val refreshedAlice = participantRepository.findById(aliceId).get()
        val refreshedBob = participantRepository.findById(bobId).get()
        assertFalse(refreshedAlice.isSelf)
        assertTrue(refreshedBob.isSelf)

        // Exactly one participant is self
        val selfParticipants = participantRepository.findByGroupIdOrderByIdAsc(groupId).filter { it.isSelf }
        assertEquals(1, selfParticipants.size)
        assertEquals("Bob", selfParticipants[0].name)

        // 6. Roster displays the updated state
        mockMvc.perform(get("/groups/$groupId"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Alice")))
            .andExpect(content().string(containsString("Bob")))
            .andExpect(content().string(containsString("You (Operator)")))
    }
}
