package com.jacobcraig.debtmngr.domain

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "participants")
class Participant(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    val group: Group,

    @Column(nullable = false)
    var name: String,

    @Column(nullable = false)
    var isSelf: Boolean = false,

    @OneToOne(cascade = [CascadeType.ALL], fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    val account: Account = Account(),

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)
