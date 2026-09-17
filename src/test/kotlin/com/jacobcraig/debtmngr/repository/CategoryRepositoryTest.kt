package com.jacobcraig.debtmngr.repository

import com.jacobcraig.debtmngr.domain.Category
import com.jacobcraig.debtmngr.domain.Group
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CategoryRepositoryTest @Autowired constructor(
    private val entityManager: TestEntityManager,
    private val categoryRepository: CategoryRepository
) {

    @Test
    fun `save and retrieve system categories`() {
        val cat1 = entityManager.persist(Category(name = "Z-Custom-System-Cat", systemKey = "TEST_Z"))
        val cat2 = entityManager.persist(Category(name = "A-Custom-System-Cat", systemKey = "TEST_A"))
        entityManager.flush()
        entityManager.clear()

        val systemCategories = categoryRepository.findByGroupIsNullOrderByNameAsc()
        assertTrue(systemCategories.size >= 2)
        val names = systemCategories.map { it.name }
        assertTrue(names.contains("A-Custom-System-Cat"))
        assertTrue(names.contains("Z-Custom-System-Cat"))
        val aIdx = names.indexOf("A-Custom-System-Cat")
        val zIdx = names.indexOf("Z-Custom-System-Cat")
        assertTrue(aIdx < zIdx, "System categories should be ordered by name ascending")
    }

    @Test
    fun `findAvailableForGroup returns system categories and group custom categories, but not other groups`() {
        val group1 = entityManager.persist(Group(name = "Apartment"))
        val group2 = entityManager.persist(Group(name = "Holiday"))

        val sysCat = entityManager.persist(Category(name = "A-System-Category", systemKey = "TEST_SYS"))
        val custom1 = entityManager.persist(Category(name = "Z-Cleaning", group = group1))
        val custom2 = entityManager.persist(Category(name = "Museums", group = group2))
        entityManager.flush()
        entityManager.clear()

        val available = categoryRepository.findAvailableForGroup(checkNotNull(group1.id))
        val names = available.map { it.name }

        assertTrue(names.contains("A-System-Category"))
        assertTrue(names.contains("Z-Cleaning"))
        assertFalse(names.contains("Museums"))

        // System categories should come before custom categories
        val sysIdx = names.indexOf("A-System-Category")
        val customIdx = names.indexOf("Z-Cleaning")
        assertTrue(sysIdx < customIdx)
    }

    @Test
    fun `findBySystemKey returns matching category`() {
        entityManager.persist(Category(name = "Test Transport", systemKey = "TEST_TRANSPORT"))
        entityManager.flush()
        entityManager.clear()

        val found = categoryRepository.findBySystemKey("TEST_TRANSPORT")
        assertNotNull(found)
        assertEquals("Test Transport", found?.name)
    }
}
