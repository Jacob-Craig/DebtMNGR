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

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @OneToMany(mappedBy = "transaction", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("id ASC")
    val entries: MutableList<Entry> = mutableListOf()
) {
    init {
        require(description.isNotBlank()) { "Transaction description cannot be blank" }
        require(amount > 0) { "Transaction amount must be positive" }
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
}
