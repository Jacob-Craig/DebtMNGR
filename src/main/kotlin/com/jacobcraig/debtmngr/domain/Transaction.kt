package com.jacobcraig.debtmngr.domain

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "transactions")
class Transaction(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    val group: Group,

    @Column(nullable = false)
    var description: String,

    @Column(nullable = false)
    var amount: Long,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payer_id", nullable = false)
    var payer: Participant,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: TransactionType = TransactionType.EXPENSE,

    @Column(nullable = false)
    var isLocked: Boolean = false,

    @Column(nullable = false)
    var isDeleted: Boolean = false,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_transaction_id")
    var originalTransaction: Transaction? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    var category: Category? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @OneToMany(mappedBy = "transaction", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("id ASC")
    val entries: MutableList<Entry> = mutableListOf()
) {
    init {
        require(description.isNotBlank()) { "Transaction description cannot be blank" }
        require(amount > 0) { "Transaction amount must be positive" }
        category?.let { cat ->
            require(cat.isAvailableIn(group)) {
                "Category ${cat.name} does not belong to group ${group.id ?: group.name}"
            }
        }
        if (type == TransactionType.ADJUSTMENT) {
            require(originalTransaction != null) { "Adjustment transaction must link to an original transaction" }
            require(originalTransaction?.isLocked == true) { "Adjustment can only be made to a locked transaction" }
            require(originalTransaction?.group?.id == group.id) {
                "Adjustment transaction must belong to the same group as original transaction"
            }
        }
    }

    fun addEntry(entry: Entry) {
        entries.add(entry)
        entry.transaction = this
    }

    fun totalDebits(): Long = entries.filter { it.type == EntryType.DEBIT }.sumOf { it.amount }

    fun totalCredits(): Long = entries.filter { it.type == EntryType.CREDIT }.sumOf { it.amount }

    fun isBalanced(): Boolean = totalDebits() == totalCredits() && totalCredits() == amount

    fun validateDoubleEntry() {
        val debits = totalDebits()
        val credits = totalCredits()
        check(debits == credits) {
            "Double-entry violation: total debits ($debits) must equal total credits ($credits)"
        }
        check(credits == amount) {
            "Transaction amount ($amount) must equal total credits ($credits)"
        }
    }

    fun lock() {
        isLocked = true
    }

    fun softDelete() {
        check(!isLocked) { "Cannot delete a locked transaction" }
        check(!isDeleted) { "Transaction is already deleted" }
        isDeleted = true
    }
}
