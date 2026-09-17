package com.jacobcraig.debtmngr.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant

class AccountTest {

    @Test
    fun `default account has null id and non-null createdAt`() {
        val before = Instant.now().minusSeconds(1)
        val account = Account()
        val after = Instant.now().plusSeconds(1)

        assertNull(account.id)
        assertNotNull(account.createdAt)
        assertTrue(account.createdAt.isAfter(before) || account.createdAt == before)
        assertTrue(account.createdAt.isBefore(after) || account.createdAt == after)
    }
}
