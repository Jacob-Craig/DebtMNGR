package com.jacobcraig.debtmngr.service

import com.jacobcraig.debtmngr.domain.Group
import com.jacobcraig.debtmngr.repository.GroupRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*

class GroupServiceTest {

    private lateinit var groupRepository: GroupRepository
    private lateinit var groupService: GroupService

    @BeforeEach
    fun setUp() {
        groupRepository = mock(GroupRepository::class.java)
        groupService = GroupService(groupRepository)
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
}
