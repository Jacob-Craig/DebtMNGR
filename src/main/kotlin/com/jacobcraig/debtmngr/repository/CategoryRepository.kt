package com.jacobcraig.debtmngr.repository

import com.jacobcraig.debtmngr.domain.Category
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface CategoryRepository : JpaRepository<Category, Long> {

    fun findByGroupIsNullOrderByNameAsc(): List<Category>

    fun findByGroupIdOrderByNameAsc(groupId: Long): List<Category>

    @Query(
        """
        SELECT c FROM Category c 
        WHERE c.group IS NULL OR c.group.id = :groupId 
        ORDER BY CASE WHEN c.group IS NULL THEN 0 ELSE 1 END, c.name ASC
        """
    )
    fun findAvailableForGroup(@Param("groupId") groupId: Long): List<Category>

    fun findBySystemKey(systemKey: String): Category?

    fun findByGroupIdAndNameIgnoreCase(groupId: Long, name: String): Category?

    fun findByGroupIsNullAndNameIgnoreCase(name: String): Category?
}
