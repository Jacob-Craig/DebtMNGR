package com.jacobcraig.debtmngr.service

import com.jacobcraig.debtmngr.domain.Group
import com.jacobcraig.debtmngr.repository.GroupRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class GroupService(private val groupRepository: GroupRepository) {

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
}
