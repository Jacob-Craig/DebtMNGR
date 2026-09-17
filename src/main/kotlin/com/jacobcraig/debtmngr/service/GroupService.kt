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
        return participantRepository.findByGroupId(groupId)
    }

    fun addParticipant(groupId: Long, name: String, isSelf: Boolean = false): Participant {
        require(name.isNotBlank()) { "Participant name cannot be blank" }
        val group = getGroup(groupId)

        if (isSelf) {
            val existingSelf = participantRepository.findByGroupIdAndIsSelfTrue(groupId)
            if (existingSelf != null) {
                existingSelf.isSelf = false
                participantRepository.save(existingSelf)
            }
        }

        val participant = Participant(
            group = group,
            name = name.trim(),
            isSelf = isSelf
        )
        return participantRepository.save(participant)
    }

    fun designateSelf(groupId: Long, participantId: Long): Participant {
        getGroup(groupId)
        val participant = participantRepository.findById(participantId)
            .orElseThrow { EntityNotFoundException("Participant not found with id: $participantId") }

        require(participant.group.id == groupId) { "Participant $participantId does not belong to group $groupId" }

        val existingSelf = participantRepository.findByGroupIdAndIsSelfTrue(groupId)
        if (existingSelf != null && existingSelf.id != participantId) {
            existingSelf.isSelf = false
            participantRepository.save(existingSelf)
        }

        participant.isSelf = true
        return participantRepository.save(participant)
    }
}
