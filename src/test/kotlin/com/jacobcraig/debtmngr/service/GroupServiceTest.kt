package com.jacobcraig.debtmngr.service

import com.jacobcraig.debtmngr.domain.Group
import com.jacobcraig.debtmngr.repository.GroupRepository
import com.jacobcraig.debtmngr.domain.Participant
import com.jacobcraig.debtmngr.repository.ParticipantRepository
import jakarta.persistence.EntityNotFoundException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import java.util.Optional

class GroupServiceTest {

    private lateinit var groupRepository: GroupRepository
    private lateinit var participantRepository: ParticipantRepository
    private lateinit var groupService: GroupService

    @BeforeEach
    fun setUp() {
        groupRepository = mock(GroupRepository::class.java)
        participantRepository = mock(ParticipantRepository::class.java)
        groupService = GroupService(groupRepository, participantRepository)
    }

    @Test
    fun `createGroup saves and returns group when valid`() {
        val captor = ArgumentCaptor.forClass(Group::class.java)
        val savedGroup = Group(id = 1L, name = "Trip to Spain", description = "Holiday trip")
        `when`(groupRepository.save(captor.capture())).thenReturn(savedGroup)

        val result = groupService.createGroup("  Trip to Spain  ", "  Holiday trip  ")

        assertEquals(savedGroup, result)
        val captured = captor.value
        assertEquals("Trip to Spain", captured.name)
        assertEquals("Holiday trip", captured.description)
        verify(groupRepository).save(any(Group::class.java))
    }

    @Test
    fun `createGroup with blank name throws IllegalArgumentException`() {
        val ex = assertThrows<IllegalArgumentException> {
            groupService.createGroup("   ", "Description")
        }
        assertEquals("Group name cannot be blank", ex.message)
        verify(groupRepository, never()).save(any(Group::class.java))
    }

    @Test
    fun `createGroup with blank description saves with null description`() {
        val captor = ArgumentCaptor.forClass(Group::class.java)
        val savedGroup = Group(id = 2L, name = "Apartment", description = null)
        `when`(groupRepository.save(captor.capture())).thenReturn(savedGroup)

        val result = groupService.createGroup("Apartment", "   ")

        assertNotNull(result)
        val captured = captor.value
        assertEquals("Apartment", captured.name)
        assertNull(captured.description)
    }

    @Test
    fun `getAllGroups returns all groups`() {
        val groups = listOf(
            Group(id = 1L, name = "Apartment"),
            Group(id = 2L, name = "Trip")
        )
        `when`(groupRepository.findAll()).thenReturn(groups)

        val result = groupService.getAllGroups()

        assertEquals(2, result.size)
        assertEquals(groups, result)
        verify(groupRepository).findAll()
    }

    @Test
    fun `getGroup returns group when found`() {
        val group = Group(id = 1L, name = "Apartment")
        `when`(groupRepository.findById(1L)).thenReturn(Optional.of(group))

        val result = groupService.getGroup(1L)

        assertEquals(group, result)
        verify(groupRepository).findById(1L)
    }

    @Test
    fun `getGroup throws EntityNotFoundException when not found`() {
        `when`(groupRepository.findById(99L)).thenReturn(Optional.empty())

        val ex = assertThrows<EntityNotFoundException> {
            groupService.getGroup(99L)
        }
        assertEquals("Group not found with id: 99", ex.message)
    }

    @Test
    fun `getParticipants returns participants for group`() {
        val group = Group(id = 1L, name = "Apartment")
        val participants = listOf(
            Participant(id = 1L, group = group, name = "Alice"),
            Participant(id = 2L, group = group, name = "Bob")
        )
        `when`(participantRepository.findByGroupId(1L)).thenReturn(participants)

        val result = groupService.getParticipants(1L)

        assertEquals(2, result.size)
        assertEquals(participants, result)
        verify(participantRepository).findByGroupId(1L)
    }

    @Test
    fun `addParticipant with valid name creates and returns participant`() {
        val group = Group(id = 1L, name = "Apartment")
        `when`(groupRepository.findById(1L)).thenReturn(Optional.of(group))

        val captor = ArgumentCaptor.forClass(Participant::class.java)
        val savedParticipant = Participant(id = 10L, group = group, name = "Alice", isSelf = false)
        `when`(participantRepository.save(captor.capture())).thenReturn(savedParticipant)

        val result = groupService.addParticipant(1L, "  Alice  ", false)

        assertEquals(savedParticipant, result)
        val captured = captor.value
        assertEquals("Alice", captured.name)
        assertFalse(captured.isSelf)
        assertEquals(group, captured.group)
        assertNotNull(captured.account)
    }

    @Test
    fun `addParticipant with blank name throws IllegalArgumentException`() {
        val ex = assertThrows<IllegalArgumentException> {
            groupService.addParticipant(1L, "   ", false)
        }
        assertEquals("Participant name cannot be blank", ex.message)
        verify(participantRepository, never()).save(any(Participant::class.java))
    }

    @Test
    fun `addParticipant with isSelf true unsets existing isSelf participant`() {
        val group = Group(id = 1L, name = "Apartment")
        `when`(groupRepository.findById(1L)).thenReturn(Optional.of(group))

        val existingSelf = Participant(id = 5L, group = group, name = "Alice", isSelf = true)
        `when`(participantRepository.findByGroupIdAndIsSelfTrue(1L)).thenReturn(existingSelf)

        val newParticipant = Participant(id = 6L, group = group, name = "Bob", isSelf = true)
        `when`(participantRepository.save(any(Participant::class.java))).thenAnswer { it.arguments[0] }

        val result = groupService.addParticipant(1L, "Bob", true)

        assertTrue(result.isSelf)
        assertFalse(existingSelf.isSelf)
        verify(participantRepository).save(existingSelf)
        verify(participantRepository).save(result)
    }

    @Test
    fun `designateSelf sets participant to isSelf true and unsets existing isSelf participant`() {
        val group = Group(id = 1L, name = "Apartment")
        `when`(groupRepository.findById(1L)).thenReturn(Optional.of(group))

        val existingSelf = Participant(id = 5L, group = group, name = "Alice", isSelf = true)
        val target = Participant(id = 6L, group = group, name = "Bob", isSelf = false)

        `when`(participantRepository.findById(6L)).thenReturn(Optional.of(target))
        `when`(participantRepository.findByGroupIdAndIsSelfTrue(1L)).thenReturn(existingSelf)
        `when`(participantRepository.save(any(Participant::class.java))).thenAnswer { it.arguments[0] }

        val result = groupService.designateSelf(1L, 6L)

        assertTrue(result.isSelf)
        assertFalse(existingSelf.isSelf)
        verify(participantRepository).save(existingSelf)
        verify(participantRepository).save(target)
    }

    @Test
    fun `designateSelf with participant from different group throws IllegalArgumentException`() {
        val group1 = Group(id = 1L, name = "Apartment")
        val group2 = Group(id = 2L, name = "Trip")
        `when`(groupRepository.findById(1L)).thenReturn(Optional.of(group1))

        val target = Participant(id = 6L, group = group2, name = "Bob", isSelf = false)
        `when`(participantRepository.findById(6L)).thenReturn(Optional.of(target))

        val ex = assertThrows<IllegalArgumentException> {
            groupService.designateSelf(1L, 6L)
        }
        assertEquals("Participant 6 does not belong to group 1", ex.message)
    }

    @Test
    fun `designateSelf with nonexistent participant throws EntityNotFoundException`() {
        val group = Group(id = 1L, name = "Apartment")
        `when`(groupRepository.findById(1L)).thenReturn(Optional.of(group))
        `when`(participantRepository.findById(99L)).thenReturn(Optional.empty())

        val ex = assertThrows<EntityNotFoundException> {
            groupService.designateSelf(1L, 99L)
        }
        assertEquals("Participant not found with id: 99", ex.message)
    }
}
