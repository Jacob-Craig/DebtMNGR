package com.jacobcraig.debtmngr.repository

import com.jacobcraig.debtmngr.domain.AuditLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AuditLogRepository : JpaRepository<AuditLog, Long> {
    fun findByTransactionIdOrderByCreatedAtAscIdAsc(transactionId: Long): List<AuditLog>
}
