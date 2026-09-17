package com.jacobcraig.debtmngr.service

import com.jacobcraig.debtmngr.domain.Category
import com.jacobcraig.debtmngr.repository.CategoryRepository
import com.jacobcraig.debtmngr.repository.GroupRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class CategoryService(
    private val categoryRepository: CategoryRepository,
    private val groupRepository: GroupRepository
) {

    companion object {
        val DEFAULT_SYSTEM_CATEGORIES = listOf(
            Triple("GROCERIES", "Groceries", "shopping-cart"),
            Triple("DINING_OUT", "Dining Out", "utensils"),
            Triple("UTILITIES", "Utilities", "bolt"),
            Triple("TRANSPORT", "Transport", "truck"),
            Triple("ENTERTAINMENT", "Entertainment", "film"),
            Triple("GENERAL", "General", "tag")
        )
    }

    fun seedSystemCategories(): List<Category> {
        val seeded = mutableListOf<Category>()
        for ((systemKey, name, icon) in DEFAULT_SYSTEM_CATEGORIES) {
            val existing = categoryRepository.findBySystemKey(systemKey)
            if (existing != null) {
                seeded.add(existing)
            } else {
                val category = Category(
                    name = name,
                    systemKey = systemKey,
                    icon = icon
                )
                seeded.add(categoryRepository.save(category))
            }
        }
        return seeded
    }

    @Transactional(readOnly = true)
    fun getSystemCategories(): List<Category> {
        return categoryRepository.findByGroupIsNullOrderByNameAsc()
    }

    @Transactional(readOnly = true)
    fun getCategoriesForGroup(groupId: Long): List<Category> {
        return categoryRepository.findAvailableForGroup(groupId)
    }

    fun createCustomCategory(
        groupId: Long,
        name: String,
        icon: String? = null,
        color: String? = null
    ): Category {
        require(name.isNotBlank()) { "Category name cannot be blank" }
        val trimmedName = name.trim()

        val group = groupRepository.findById(groupId)
            .orElseThrow { EntityNotFoundException("Group not found with id: $groupId") }

        val existingGroupCategory = categoryRepository.findByGroupIdAndNameIgnoreCase(groupId, trimmedName)
        if (existingGroupCategory != null) {
            return existingGroupCategory
        }

        val category = Category(
            name = trimmedName,
            group = group,
            icon = icon,
            color = color
        )
        val saved = categoryRepository.save(category)
        if (!group.categories.contains(saved)) {
            group.categories.add(saved)
        }
        return saved
    }

    @Transactional(readOnly = true)
    fun getCategory(id: Long): Category {
        return categoryRepository.findById(id)
            .orElseThrow { EntityNotFoundException("Category not found with id: $id") }
    }
}
