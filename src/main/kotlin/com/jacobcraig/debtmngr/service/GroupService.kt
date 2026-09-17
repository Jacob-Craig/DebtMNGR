package com.jacobcraig.debtmngr.service

import com.jacobcraig.debtmngr.domain.Group
import com.jacobcraig.debtmngr.domain.Participant
import com.jacobcraig.debtmngr.repository.GroupRepository
import com.jacobcraig.debtmngr.repository.ParticipantRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class GroupService(
    private val groupRepository: GroupRepository,
    private val participantRepository: ParticipantRepository
) {

    fun createGroup(name: String, description: String?): Group {
        require(name.isNotBlank()) { "Group name cannot be blank" }
        val group = Group(
            name = name.trim(),
            description = description?.trim()?.takeIf { it.isNotBlank() }
        )
        return groupRepository.save(group)
    }

    @Transactional(readOnly = true)
    fun getAllGroups(): List<Group> {
        return groupRepository.findAll()
    }

    @Transactional(readOnly = true)
    fun getGroup(id: Long): Group {
        return groupRepository.findById(id)
            .orElseThrow { EntityNotFoundException("Group not found with id: $id") }
    }

    @Transactional(readOnly = true)
    fun getParticipants(groupId: Long): List<Participant> {
        return participantRepository.findByGroupIdOrderByIdAsc(groupId)
    }

    fun addParticipant(groupId: Long, name: String, isSelf: Boolean = false): Participant {
        require(name.isNotBlank()) { "Participant name cannot be blank" }
        val group = getGroup(groupId)

        if (isSelf) {
            clearExistingSelf(groupId)
        }

        val participant = Participant(
            group = group,
            name = name.trim(),
            isSelf = isSelf
        )
        val saved = participantRepository.save(participant)
        if (!group.participants.contains(saved)) {
            group.participants.add(saved)
        }
        return saved
    }

    fun designateSelf(groupId: Long, participantId: Long): Participant {
        getGroup(groupId)
        val participant = participantRepository.findById(participantId)
            .orElseThrow { EntityNotFoundException("Participant not found with id: $participantId") }

        require(participant.group.id == groupId) { "Participant $participantId does not belong to group $groupId" }

        clearExistingSelf(groupId, participantId)

        participant.isSelf = true
        return participantRepository.save(participant)
    }

    private fun clearExistingSelf(groupId: Long, excludeParticipantId: Long? = null) {
        val existingSelfList = participantRepository.findByGroupIdOrderByIdAsc(groupId)
            .filter { it.isSelf && it.id != excludeParticipantId }
        for (existingSelf in existingSelfList) {
            existingSelf.isSelf = false
            participantRepository.save(existingSelf)
        }
    }
}
