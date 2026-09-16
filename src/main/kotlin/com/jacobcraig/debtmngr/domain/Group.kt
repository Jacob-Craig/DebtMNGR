package com.jacobcraig.debtmngr.domain

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "\"groups\"")
class Group(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    var name: String,

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)
