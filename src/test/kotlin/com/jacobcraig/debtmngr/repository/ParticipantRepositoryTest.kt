package com.jacobcraig.debtmngr.repository

import com.jacobcraig.debtmngr.domain.Group
import com.jacobcraig.debtmngr.domain.Participant
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ParticipantRepositoryTest @Autowired constructor(
    private val entityManager: TestEntityManager,
    private val groupRepository: GroupRepository,
    private val participantRepository: ParticipantRepository,
    private val accountRepository: AccountRepository
) {

    @Test
    fun `save and find participant cascades account creation`() {
        val group = groupRepository.save(Group(name = "Trip to Spain"))
        val participant = Participant(group = group, name = "Alice", isSelf = true)

        val saved = participantRepository.save(participant)
        entityManager.flush()
        entityManager.clear()

        val participantId = checkNotNull(saved.id) { "Participant id must not be null" }
        val found = participantRepository.findById(participantId).orElse(null)

        assertNotNull(found)
        assertEquals("Alice", found.name)
        assertTrue(found.isSelf)
        assertNotNull(found.account)
        val accountId = checkNotNull(found.account.id) { "Account id must be cascade-generated" }

        val foundAccount = accountRepository.findById(accountId).orElse(null)
        assertNotNull(foundAccount)
    }

    @Test
    fun `findByGroupIdOrderByIdAsc returns participants for the given group in ascending order`() {
        val group1 = groupRepository.save(Group(name = "Group 1"))
        val group2 = groupRepository.save(Group(name = "Group 2"))

        val p1 = participantRepository.save(Participant(group = group1, name = "Alice"))
        val p2 = participantRepository.save(Participant(group = group1, name = "Bob"))
        val p3 = participantRepository.save(Participant(group = group2, name = "Charlie"))

        entityManager.flush()
        entityManager.clear()

        val group1Id = checkNotNull(group1.id) { "Group 1 id must not be null" }
        val group1Participants = participantRepository.findByGroupIdOrderByIdAsc(group1Id)
        assertEquals(2, group1Participants.size)
        assertEquals(listOf("Alice", "Bob"), group1Participants.map { it.name })
        assertEquals(listOf(checkNotNull(p1.id), checkNotNull(p2.id)), group1Participants.map { it.id })
        assertFalse(group1Participants.any { it.name == "Charlie" })
    }

    @Test
    fun `findByGroupIdAndIsSelfTrue returns participant with isSelf true`() {
        val group = groupRepository.save(Group(name = "Flat"))

        participantRepository.save(Participant(group = group, name = "Alice", isSelf = false))
        participantRepository.save(Participant(group = group, name = "Bob", isSelf = true))

        entityManager.flush()
        entityManager.clear()

        val groupId = checkNotNull(group.id) { "Group id must not be null" }
        val selfParticipant = participantRepository.findByGroupIdAndIsSelfTrue(groupId)
        assertNotNull(selfParticipant)
        val participant = checkNotNull(selfParticipant) { "Participant must not be null" }
        assertEquals("Bob", participant.name)
        assertTrue(participant.isSelf)
    }

    @Test
    fun `findByGroupIdAndIsSelfTrue returns null when no self participant exists`() {
        val group = groupRepository.save(Group(name = "Flat"))

        participantRepository.save(Participant(group = group, name = "Alice", isSelf = false))

        entityManager.flush()
        entityManager.clear()

        val groupId = checkNotNull(group.id) { "Group id must not be null" }
        val selfParticipant = participantRepository.findByGroupIdAndIsSelfTrue(groupId)
        assertNull(selfParticipant)
    }
}
