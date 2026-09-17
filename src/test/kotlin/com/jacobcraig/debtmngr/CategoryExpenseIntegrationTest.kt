package com.jacobcraig.debtmngr

import com.jacobcraig.debtmngr.domain.Group
import com.jacobcraig.debtmngr.domain.Participant
import com.jacobcraig.debtmngr.repository.*
import com.jacobcraig.debtmngr.service.CategoryService
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
class CategoryExpenseIntegrationTest {

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

    @Autowired
    private lateinit var categoryRepository: CategoryRepository

    @Autowired
    private lateinit var categoryService: CategoryService

    @BeforeEach
    fun cleanDatabase() {
        entryRepository.deleteAll()
        transactionRepository.deleteAll()
        participantRepository.deleteAll()
        accountRepository.deleteAll()
        categoryRepository.findAll().filter { !it.isSystem }.forEach { categoryRepository.delete(it) }
        groupRepository.deleteAll()
        categoryService.seedSystemCategories()
    }

    @Test
    fun `full category flow - seed data, select category, create custom category, display badges, and filter activity log`() {
        // 1. Verify system categories are seeded in the database
        val systemCategories = categoryRepository.findByGroupIsNullOrderByNameAsc()
        assertTrue(systemCategories.size >= 6, "Expected at least 6 default system categories")
        val groceries = checkNotNull(systemCategories.find { it.systemKey == "GROCERIES" })
        val transport = checkNotNull(systemCategories.find { it.systemKey == "TRANSPORT" })

        // 2. Setup Group 1 and Participants
        val group = groupRepository.save(Group(name = "EuroTrip 2026", description = "Holiday"))
        val groupId = checkNotNull(group.id)
        val alice = participantRepository.save(Participant(group = group, name = "Alice", isSelf = true))
        val bob = participantRepository.save(Participant(group = group, name = "Bob", isSelf = false))
        val aliceId = checkNotNull(alice.id)
        val bobId = checkNotNull(bob.id)

        // 3. GET /groups/{id}/transactions/new shows Category dropdown with seeded system categories
        mockMvc.perform(get("/groups/$groupId/transactions/new"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Category")))
            .andExpect(content().string(containsString("Groceries")))
            .andExpect(content().string(containsString("Utilities")))
            .andExpect(content().string(containsString("Transport")))
            .andExpect(content().string(containsString("+ Create new custom category...")))

        // 4. Record Expense 1 with System Category ("Groceries")
        mockMvc.perform(
            post("/groups/$groupId/transactions")
                .param("description", "Supermarket run")
                .param("amount", "40.00")
                .param("payerId", aliceId.toString())
                .param("consumerIds", aliceId.toString(), bobId.toString())
                .param("categoryId", groceries.id.toString())
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/groups/$groupId"))

        // 5. Record Expense 2 with Custom Category ("Museum Tickets") scoped to this Group
        mockMvc.perform(
            post("/groups/$groupId/transactions")
                .param("description", "Prado Museum")
                .param("amount", "30.00")
                .param("payerId", bobId.toString())
                .param("consumerIds", aliceId.toString(), bobId.toString())
                .param("customCategoryName", "Museum Tickets")
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/groups/$groupId"))

        // Verify custom category was created and scoped to Group 1
        val customCat = categoryRepository.findByGroupIdAndNameIgnoreCase(groupId, "Museum Tickets")
        assertNotNull(customCat, "Custom category should be saved in database")
        assertEquals(groupId, customCat?.group?.id)
        val customCatId = checkNotNull(customCat?.id)

        // 6. View Group activity log without filter - should show both transactions and their category badges
        mockMvc.perform(get("/groups/$groupId"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Supermarket run")))
            .andExpect(content().string(containsString("Groceries")))
            .andExpect(content().string(containsString("Prado Museum")))
            .andExpect(content().string(containsString("Museum Tickets")))
            .andExpect(content().string(containsString("Filter:")))

        // 7. Filter Group activity log by "Groceries"
        mockMvc.perform(get("/groups/$groupId").param("categoryId", groceries.id.toString()))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Supermarket run")))
            .andExpect(content().string(containsString("Groceries")))
            .andExpect(content().string(not(containsString("Prado Museum"))))
            .andExpect(content().string(containsString("Clear filter")))

        // 8. Filter Group activity log by Custom Category ("Museum Tickets")
        mockMvc.perform(get("/groups/$groupId").param("categoryId", customCatId.toString()))
            .andExpect(status().isOk)
            .andExpect(content().string(not(containsString("Supermarket run"))))
            .andExpect(content().string(containsString("Prado Museum")))
            .andExpect(content().string(containsString("Museum Tickets")))
            .andExpect(content().string(containsString("Clear filter")))

        // 9. Filter by a category with no transactions (e.g. Transport)
        mockMvc.perform(get("/groups/$groupId").param("categoryId", transport.id.toString()))
            .andExpect(status().isOk)
            .andExpect(content().string(not(containsString("Supermarket run"))))
            .andExpect(content().string(not(containsString("Prado Museum"))))
            .andExpect(content().string(containsString("No transactions in this category")))

        // 10. Verify Group 2 does NOT see the custom category from Group 1
        val group2 = groupRepository.save(Group(name = "Ski Weekend", description = "Alps"))
        val group2Id = checkNotNull(group2.id)

        mockMvc.perform(get("/groups/$group2Id/transactions/new"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Groceries")))
            .andExpect(content().string(not(containsString("Museum Tickets"))))

        mockMvc.perform(get("/groups/$group2Id"))
            .andExpect(status().isOk)
            .andExpect(content().string(not(containsString("Museum Tickets"))))
    }
}
