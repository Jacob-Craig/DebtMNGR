package com.jacobcraig.debtmngr.domain

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "categories")
class Category(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    var name: String,

    @Column(name = "system_key", unique = true)
    val systemKey: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    var group: Group? = null,

    @Column
    var icon: String? = null,

    @Column
    var color: String? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
) {
    init {
        require(name.isNotBlank()) { "Category name cannot be blank" }
        name = name.trim()
    }

    val isSystem: Boolean
        get() = group == null

    fun isAvailableIn(targetGroup: Group): Boolean =
        isSystem || group == targetGroup || (group?.id != null && group?.id == targetGroup.id)
}
