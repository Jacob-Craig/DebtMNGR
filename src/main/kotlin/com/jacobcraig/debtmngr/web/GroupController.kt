package com.jacobcraig.debtmngr.web

import com.jacobcraig.debtmngr.service.CategoryService
import com.jacobcraig.debtmngr.service.GroupService
import com.jacobcraig.debtmngr.service.TransactionService
import jakarta.validation.Valid
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.*

@Controller
@RequestMapping("/groups")
class GroupController(
    private val groupService: GroupService,
    private val transactionService: TransactionService,
    private val categoryService: CategoryService
) {

    @GetMapping("/new")
    fun newGroupForm(model: Model): String {
        model.addAttribute("groupForm", CreateGroupForm())
        return "groups/new"
    }

    @PostMapping
    fun createGroup(
        @Valid @ModelAttribute("groupForm") form: CreateGroupForm,
        bindingResult: BindingResult
    ): String {
        if (bindingResult.hasErrors()) {
            return "groups/new"
        }
        groupService.createGroup(form.name, form.description)
        return "redirect:/"
    }

    @GetMapping("/{id}")
    fun showGroup(
        @PathVariable("id") id: Long,
        @RequestParam(name = "categoryId", required = false) categoryId: Long?,
        model: Model
    ): String {
        populateGroupAndParticipants(model, id, categoryId)
        if (!model.containsAttribute("participantForm")) {
            model.addAttribute("participantForm", AddParticipantForm())
        }
        return "groups/show"
    }

    @PostMapping("/{id}/participants")
    fun addParticipant(
        @PathVariable("id") id: Long,
        @Valid @ModelAttribute("participantForm") form: AddParticipantForm,
        bindingResult: BindingResult,
        model: Model
    ): String {
        if (bindingResult.hasErrors()) {
            populateGroupAndParticipants(model, id)
            return "groups/show"
        }
        groupService.addParticipant(id, form.name, form.isSelf)
        return "redirect:/groups/$id"
    }

    private fun populateGroupAndParticipants(model: Model, groupId: Long, categoryId: Long? = null) {
        val group = groupService.getGroup(groupId)
        val participants = groupService.getParticipants(groupId)
        val categories = categoryService.getCategoriesForGroup(groupId)
        val rawBalances = transactionService.getParticipantBalances(groupId)
        val balances = participants.associate { p ->
            val pId = checkNotNull(p.id)
            pId to (rawBalances[pId] ?: 0L).toFormattedBalanceModel()
        }
        val transactions = transactionService.getTransactionsForGroup(groupId, categoryId)

        model.addAttribute("group", group)
        model.addAttribute("participants", participants)
        model.addAttribute("categories", categories)
        if (categoryId != null) {
            model.addAttribute("selectedCategoryId", categoryId)
        }
        model.addAttribute("balances", balances)
        model.addAttribute("transactions", transactions)
    }

    @PostMapping("/{id}/participants/{participantId}/self")
    fun designateSelf(
        @PathVariable("id") id: Long,
        @PathVariable("participantId") participantId: Long
    ): String {
        groupService.designateSelf(id, participantId)
        return "redirect:/groups/$id"
    }
}
