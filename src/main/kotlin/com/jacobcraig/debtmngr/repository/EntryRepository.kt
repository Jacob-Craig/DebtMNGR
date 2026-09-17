package com.jacobcraig.debtmngr.repository

import com.jacobcraig.debtmngr.domain.Entry
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface EntryRepository : JpaRepository<Entry, Long> {
    fun findByAccountId(accountId: Long): List<Entry>

    @Query("""
        SELECT e FROM Entry e
        JOIN FETCH e.account
        JOIN e.transaction t
        WHERE t.group.id = :groupId
          AND t.isDeleted = false
    """)
    fun findActiveByGroupId(@Param("groupId") groupId: Long): List<Entry>
}
