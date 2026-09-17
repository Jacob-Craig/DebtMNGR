package com.jacobcraig.debtmngr.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class AuditLogTest {

    @Test
    fun `audit log initializes with valid parameters`() {
        val group = Group(name = "Apartment")
        val alice = Participant(group = group, name = "Alice")
        val tx = Transaction(group = group, description = "Dinner", amount = 2500L, payer = alice)

        val before = Instant.now()
        val auditLog = AuditLog(
            transaction = tx,
            action = AuditAction.EDIT,
            serializedPriorState = """{"description":"Dinner","amount":2500}""",
            reason = "Corrected typo in description"
        )
        val after = Instant.now()

        assertNull(auditLog.id)
        assertSame(tx, auditLog.transaction)
        assertEquals(AuditAction.EDIT, auditLog.action)
        assertEquals("""{"description":"Dinner","amount":2500}""", auditLog.serializedPriorState)
        assertEquals("Corrected typo in description", auditLog.reason)
        assertFalse(auditLog.createdAt.isBefore(before))
        assertFalse(auditLog.createdAt.isAfter(after))
    }

    @Test
    fun `blank serializedPriorState throws IllegalArgumentException`() {
        val group = Group(name = "Apartment")
        val alice = Participant(group = group, name = "Alice")
        val tx = Transaction(group = group, description = "Dinner", amount = 2500L, payer = alice)

        val ex = assertThrows<IllegalArgumentException> {
            AuditLog(
                transaction = tx,
                action = AuditAction.DELETE,
                serializedPriorState = "   "
            )
        }
        assertEquals("Serialized prior state cannot be blank", ex.message)
    }
}
