package com.jacobcraig.debtmngr.service

import com.jacobcraig.debtmngr.domain.Category
import com.jacobcraig.debtmngr.domain.Group
import com.jacobcraig.debtmngr.repository.CategoryRepository
import com.jacobcraig.debtmngr.repository.GroupRepository
import jakarta.persistence.EntityNotFoundException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import java.util.Optional

class CategoryServiceTest {

    private lateinit var categoryRepository: CategoryRepository
    private lateinit var groupRepository: GroupRepository
    private lateinit var categoryService: CategoryService

    private val group = Group(id = 1L, name = "Trip to Spain")

    @BeforeEach
    fun setUp() {
        categoryRepository = mock(CategoryRepository::class.java)
        groupRepository = mock(GroupRepository::class.java)
        categoryService = CategoryService(categoryRepository, groupRepository)

        `when`(groupRepository.findById(1L)).thenReturn(Optional.of(group))
        `when`(groupRepository.existsById(1L)).thenReturn(true)
    }

    @Test
    fun `seedSystemCategories creates missing default system categories`() {
        `when`(categoryRepository.findBySystemKey(anyString())).thenReturn(null)
        `when`(categoryRepository.save(any(Category::class.java))).thenAnswer { it.arguments[0] }

        val seeded = categoryService.seedSystemCategories()

        assertTrue(seeded.isNotEmpty())
        val keys = seeded.map { it.systemKey }
        assertTrue(keys.contains("GROCERIES"))
        assertTrue(keys.contains("DINING_OUT"))
        assertTrue(keys.contains("UTILITIES"))
        assertTrue(keys.contains("TRANSPORT"))
        assertTrue(keys.contains("ENTERTAINMENT"))
        assertTrue(keys.contains("GENERAL"))

        verify(categoryRepository, atLeast(6)).save(any(Category::class.java))
    }

    @Test
    fun `seedSystemCategories does not duplicate existing system categories`() {
        val existingGroceries = Category(id = 1L, name = "Groceries", systemKey = "GROCERIES")
        `when`(categoryRepository.findBySystemKey("GROCERIES")).thenReturn(existingGroceries)
        `when`(categoryRepository.save(any(Category::class.java))).thenAnswer { it.arguments[0] }

        categoryService.seedSystemCategories()

        verify(categoryRepository, never()).save(argThat { it != null && it.systemKey == "GROCERIES" })
    }

    @Test
    fun `getCategoriesForGroup returns available categories for group`() {
        val cat1 = Category(id = 1L, name = "Groceries", systemKey = "GROCERIES")
        val cat2 = Category(id = 2L, name = "Museums", group = group)
        `when`(categoryRepository.findAvailableForGroup(1L)).thenReturn(listOf(cat1, cat2))

        val result = categoryService.getCategoriesForGroup(1L)

        assertEquals(2, result.size)
        assertEquals(listOf(cat1, cat2), result)
        verify(categoryRepository).findAvailableForGroup(1L)
    }

    @Test
    fun `createCustomCategory saves and returns new category for group`() {
        val captor = ArgumentCaptor.forClass(Category::class.java)
        val saved = Category(id = 10L, name = "Museums", group = group)
        `when`(categoryRepository.findByGroupIdAndNameIgnoreCase(1L, "Museums")).thenReturn(null)
        `when`(categoryRepository.findByGroupIsNullAndNameIgnoreCase("Museums")).thenReturn(null)
        `when`(categoryRepository.save(captor.capture())).thenReturn(saved)

        val result = categoryService.createCustomCategory(1L, "  Museums  ")

        assertEquals(saved, result)
        val captured = captor.value
        assertEquals("Museums", captured.name)
        assertSame(group, captured.group)
        assertNull(captured.systemKey)
        assertTrue(group.categories.contains(saved))
    }

    @Test
    fun `createCustomCategory with existing group custom category returns existing`() {
        val existing = Category(id = 10L, name = "Museums", group = group)
        `when`(categoryRepository.findByGroupIdAndNameIgnoreCase(1L, "Museums")).thenReturn(existing)

        val result = categoryService.createCustomCategory(1L, "Museums")

        assertEquals(existing, result)
        verify(categoryRepository, never()).save(any(Category::class.java))
    }

    @Test
    fun `createCustomCategory saves custom category for group even if a system category shares the name`() {
        val saved = Category(id = 11L, name = "Groceries", group = group)
        `when`(categoryRepository.findByGroupIdAndNameIgnoreCase(1L, "Groceries")).thenReturn(null)
        `when`(categoryRepository.save(any(Category::class.java))).thenReturn(saved)

        val result = categoryService.createCustomCategory(1L, "Groceries")

        assertEquals(saved, result)
        assertEquals(group, result.group)
        verify(categoryRepository).save(any(Category::class.java))
    }

    @Test
    fun `createCustomCategory with blank name throws IllegalArgumentException`() {
        val ex = assertThrows<IllegalArgumentException> {
            categoryService.createCustomCategory(1L, "   ")
        }
        assertEquals("Category name cannot be blank", ex.message)
        verify(categoryRepository, never()).save(any(Category::class.java))
    }

    @Test
    fun `createCustomCategory with nonexistent group throws EntityNotFoundException`() {
        `when`(groupRepository.findById(99L)).thenReturn(Optional.empty())

        val ex = assertThrows<EntityNotFoundException> {
            categoryService.createCustomCategory(99L, "Museums")
        }
        assertEquals("Group not found with id: 99", ex.message)
    }

    @Test
    fun `getCategory returns category when found`() {
        val cat = Category(id = 1L, name = "Groceries", systemKey = "GROCERIES")
        `when`(categoryRepository.findById(1L)).thenReturn(Optional.of(cat))

        val result = categoryService.getCategory(1L)

        assertEquals(cat, result)
    }

    @Test
    fun `getCategory throws EntityNotFoundException when not found`() {
        `when`(categoryRepository.findById(99L)).thenReturn(Optional.empty())

        val ex = assertThrows<EntityNotFoundException> {
            categoryService.getCategory(99L)
        }
        assertEquals("Category not found with id: 99", ex.message)
    }
}
