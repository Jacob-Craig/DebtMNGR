package com.jacobcraig.debtmngr.repository

import com.jacobcraig.debtmngr.domain.Participant
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ParticipantRepository : JpaRepository<Participant, Long> {
    fun findByGroupIdOrderByIdAsc(groupId: Long): List<Participant>
    fun findByGroupIdAndIsSelfTrue(groupId: Long): Participant?
}
