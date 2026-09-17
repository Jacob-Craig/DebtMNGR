package com.jacobcraig.debtmngr.domain

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "audit_logs")
class AuditLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    val transaction: Transaction,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    val action: AuditAction,

    @Column(name = "serialized_prior_state", columnDefinition = "TEXT", nullable = false, updatable = false)
    val serializedPriorState: String,

    @Column(name = "reason")
    val reason: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
) {
    init {
        require(serializedPriorState.isNotBlank()) { "Serialized prior state cannot be blank" }
    }
}
