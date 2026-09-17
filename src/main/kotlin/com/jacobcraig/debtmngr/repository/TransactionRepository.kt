package com.jacobcraig.debtmngr.repository

import com.jacobcraig.debtmngr.domain.Transaction
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface TransactionRepository : JpaRepository<Transaction, Long> {
    @EntityGraph(attributePaths = ["payer", "category"])
    fun findByGroupIdAndIsDeletedFalseOrderByCreatedAtDescIdDesc(groupId: Long): List<Transaction>

    @EntityGraph(attributePaths = ["payer", "category"])
    fun findByGroupIdAndCategoryIdAndIsDeletedFalseOrderByCreatedAtDescIdDesc(
        groupId: Long,
        categoryId: Long
    ): List<Transaction>

    @Query("""
        SELECT DISTINCT t FROM Transaction t
        JOIN t.entries e1
        JOIN t.entries e2
        WHERE t.group.id = :groupId
          AND t.isLocked = false
          AND t.isDeleted = false
          AND t.id != :excludeId
          AND t.createdAt <= :beforeInstant
          AND (
            (e1.account.id = :account1Id AND e1.type = com.jacobcraig.debtmngr.domain.EntryType.CREDIT AND e2.account.id = :account2Id AND e2.type = com.jacobcraig.debtmngr.domain.EntryType.DEBIT)
            OR
            (e1.account.id = :account2Id AND e1.type = com.jacobcraig.debtmngr.domain.EntryType.CREDIT AND e2.account.id = :account1Id AND e2.type = com.jacobcraig.debtmngr.domain.EntryType.DEBIT)
          )
    """)
    fun findUnlockedTransactionsBetweenAccounts(
        @Param("groupId") groupId: Long,
        @Param("account1Id") account1Id: Long,
        @Param("account2Id") account2Id: Long,
        @Param("beforeInstant") beforeInstant: java.time.Instant,
        @Param("excludeId") excludeId: Long
    ): List<Transaction>
}
