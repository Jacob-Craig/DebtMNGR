package com.jacobcraig.debtmngr.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant

class ParticipantTest {

    @Test
    fun `default participant has default account and isSelf is false`() {
        val group = Group(name = "Trip")
        val before = Instant.now().minusSeconds(1)
        val participant = Participant(
            group = group,
            name = "Alice"
        )
        val after = Instant.now().plusSeconds(1)

        assertNull(participant.id)
        assertEquals(group, participant.group)
        assertEquals("Alice", participant.name)
        assertFalse(participant.isSelf)
        assertNotNull(participant.account)
        assertNull(participant.account.id)
        assertNotNull(participant.createdAt)
        assertTrue(participant.createdAt.isAfter(before) || participant.createdAt == before)
        assertTrue(participant.createdAt.isBefore(after) || participant.createdAt == after)
    }

    @Test
    fun `can create participant with custom account and isSelf true`() {
        val group = Group(name = "Housemates")
        val customAccount = Account()
        val participant = Participant(
            group = group,
            name = "Bob",
            isSelf = true,
            account = customAccount
        )

        assertTrue(participant.isSelf)
        assertSame(customAccount, participant.account)
    }

    @Test
    fun `can update participant isSelf and name`() {
        val group = Group(name = "Housemates")
        val participant = Participant(
            group = group,
            name = "Charlie"
        )

        participant.name = "Charles"
        participant.isSelf = true

        assertEquals("Charles", participant.name)
        assertTrue(participant.isSelf)
    }
}
