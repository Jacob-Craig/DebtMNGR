package com.jacobcraig.debtmngr.repository

import com.jacobcraig.debtmngr.domain.Group
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class GroupRepositoryTest @Autowired constructor(
    private val entityManager: TestEntityManager,
    private val groupRepository: GroupRepository
) {

    @Test
    fun `save and find group by id`() {
        val group = Group(
            name = "Trip to Spain",
            description = "Summer holiday shared expenses"
        )

        val saved = groupRepository.save(group)
        entityManager.flush()
        entityManager.clear()

        val id = checkNotNull(saved.id) { "Saved group must have an ID" }
        val found = groupRepository.findById(id).orElse(null)
        assertNotNull(found)
        assertEquals("Trip to Spain", found.name)
        assertEquals("Summer holiday shared expenses", found.description)
        assertNotNull(found.createdAt)
    }

    @Test
    fun `findAll returns all saved groups`() {
        val group1 = Group(name = "Apartment", description = "Rent and utilities")
        val group2 = Group(name = "Road Trip")

        groupRepository.save(group1)
        groupRepository.save(group2)
        entityManager.flush()
        entityManager.clear()

        val allGroups = groupRepository.findAll()
        assertTrue(allGroups.any { it.name == "Apartment" })
        assertTrue(allGroups.any { it.name == "Road Trip" })
    }
}
