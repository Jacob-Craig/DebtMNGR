package com.jacobcraig.debtmngr.repository

import com.jacobcraig.debtmngr.domain.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import java.time.Instant

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AuditLogRepositoryTest @Autowired constructor(
    private val entityManager: TestEntityManager,
    private val auditLogRepository: AuditLogRepository
) {

    @Test
    fun `save and retrieve audit logs for a transaction ordered by createdAt asc and id asc`() {
        val group = entityManager.persist(Group(name = "Trip to Tokyo"))
        val alice = entityManager.persist(Participant(group = group, name = "Alice"))
        val tx = entityManager.persist(
            Transaction(
                group = group,
                description = "Shinkansen Tickets",
                amount = 20000L,
                payer = alice
            )
        )

        val log1 = AuditLog(
            transaction = tx,
            action = AuditAction.EDIT,
            serializedPriorState = """{"description":"Old Shinkansen","amount":18000}""",
            reason = "Price increase",
            createdAt = Instant.parse("2026-09-01T10:00:00Z")
        )
        val log2 = AuditLog(
            transaction = tx,
            action = AuditAction.DELETE,
            serializedPriorState = """{"description":"Shinkansen Tickets","amount":20000}""",
            reason = "Duplicate entry",
            createdAt = Instant.parse("2026-09-01T11:00:00Z")
        )

        auditLogRepository.save(log1)
        auditLogRepository.save(log2)
        entityManager.flush()
        entityManager.clear()

        val logs = auditLogRepository.findByTransactionIdOrderByCreatedAtAscIdAsc(checkNotNull(tx.id))
        assertEquals(2, logs.size)
        assertEquals(AuditAction.EDIT, logs[0].action)
        assertEquals("Price increase", logs[0].reason)
        assertEquals(AuditAction.DELETE, logs[1].action)
        assertEquals("Duplicate entry", logs[1].reason)
    }
}
