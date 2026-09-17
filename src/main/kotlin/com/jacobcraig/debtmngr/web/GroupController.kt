package com.jacobcraig.debtmngr.web

import com.jacobcraig.debtmngr.service.GroupService
import com.jacobcraig.debtmngr.service.TransactionService
import jakarta.validation.Valid
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping("/groups")
class GroupController(
    private val groupService: GroupService,
    private val transactionService: TransactionService
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
    fun showGroup(@PathVariable("id") id: Long, model: Model): String {
        populateGroupAndParticipants(model, id)
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

    private fun populateGroupAndParticipants(model: Model, groupId: Long) {
        val group = groupService.getGroup(groupId)
        val participants = groupService.getParticipants(groupId)
        val rawBalances = transactionService.getParticipantBalances(groupId)
        val balances = participants.associate { p ->
            val pId = checkNotNull(p.id)
            val net = rawBalances[pId] ?: 0L
            pId to FormattedBalance(
                amount = net,
                formatted = net.toFormattedBalance(),
                isPositive = net > 0,
                isNegative = net < 0,
                isZero = net == 0L
            )
        }
        val transactions = transactionService.getTransactionsForGroup(groupId)

        model.addAttribute("group", group)
        model.addAttribute("participants", participants)
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
