package com.jacobcraig.debtmngr.repository

import com.jacobcraig.debtmngr.domain.Transaction
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
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
}
