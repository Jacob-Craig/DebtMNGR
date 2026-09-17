package com.jacobcraig.debtmngr.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant

class GroupTest {

    @Test
    fun `default group initialization`() {
        val before = Instant.now().minusSeconds(1)
        val group = Group(
            name = "Trip to Spain",
            description = "Summer vacation"
        )
        val after = Instant.now().plusSeconds(1)

        assertNull(group.id)
        assertEquals("Trip to Spain", group.name)
        assertEquals("Summer vacation", group.description)
        assertTrue(group.participants.isEmpty())
        assertNotNull(group.createdAt)
        assertTrue(group.createdAt.isAfter(before) || group.createdAt == before)
        assertTrue(group.createdAt.isBefore(after) || group.createdAt == after)
    }

    @Test
    fun `can add participants to group`() {
        val group = Group(name = "Trip to Spain")
        val participant = Participant(group = group, name = "Alice")
        group.participants.add(participant)

        assertEquals(1, group.participants.size)
        assertSame(participant, group.participants[0])
    }

    @Test
    fun `can add categories to group`() {
        val group = Group(name = "Trip to Spain")
        assertTrue(group.categories.isEmpty())
        val category = Category(name = "Museums", group = group)
        group.categories.add(category)

        assertEquals(1, group.categories.size)
        assertSame(category, group.categories[0])
    }
}
