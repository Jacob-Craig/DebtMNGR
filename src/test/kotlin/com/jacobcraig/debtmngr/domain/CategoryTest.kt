package com.jacobcraig.debtmngr.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CategoryTest {

    @Test
    fun `can create system category`() {
        val category = Category(
            name = "Groceries",
            systemKey = "GROCERIES",
            icon = "shopping-cart",
            color = "green"
        )

        assertNull(category.id)
        assertEquals("Groceries", category.name)
        assertEquals("GROCERIES", category.systemKey)
        assertNull(category.group)
        assertTrue(category.isSystem)
        assertEquals("shopping-cart", category.icon)
        assertEquals("green", category.color)
        assertNotNull(category.createdAt)
    }

    @Test
    fun `can create custom category scoped to a group`() {
        val group = Group(name = "Trip")
        val category = Category(
            name = "Museums",
            group = group
        )

        assertNull(category.id)
        assertEquals("Museums", category.name)
        assertNull(category.systemKey)
        assertSame(group, category.group)
        assertFalse(category.isSystem)
    }

    @Test
    fun `blank name throws IllegalArgumentException`() {
        val ex = assertThrows<IllegalArgumentException> {
            Category(name = "   ")
        }
        assertEquals("Category name cannot be blank", ex.message)
    }

    @Test
    fun `category name is trimmed on creation`() {
        val category = Category(name = "  Utilities  ")
        assertEquals("Utilities", category.name)
    }
}
