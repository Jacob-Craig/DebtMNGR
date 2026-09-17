package com.jacobcraig.debtmngr.web

import com.jacobcraig.debtmngr.domain.Participant
import com.jacobcraig.debtmngr.service.GroupService
import com.jacobcraig.debtmngr.service.TransactionService
import jakarta.validation.Valid
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.*
import java.time.ZoneOffset

@Controller
@RequestMapping("/groups/{groupId}/transactions")
class TransactionController(
    private val groupService: GroupService,
    private val transactionService: TransactionService
) {

    @GetMapping("/new")
    fun newExpenseForm(@PathVariable("groupId") groupId: Long, model: Model): String {
        populateGroupAndParticipants(model, groupId)
        @Suppress("UNCHECKED_CAST")
        val participants = (model.getAttribute("participants") as? List<*>)?.filterIsInstance<Participant>()
            ?: groupService.getParticipants(groupId)

        val form = CreateExpenseForm()
        val selfParticipant = participants.find { it.isSelf }
        if (selfParticipant != null) {
            form.payerId = selfParticipant.id
        }
        form.consumerIds = participants.mapNotNull { it.id }

        model.addAttribute("expenseForm", form)
        return "groups/transactions/new"
    }

    @PostMapping
    fun createExpense(
        @PathVariable("groupId") groupId: Long,
        @Valid @ModelAttribute("expenseForm") form: CreateExpenseForm,
        bindingResult: BindingResult,
        model: Model
    ): String {
        if (bindingResult.hasErrors()) {
            populateGroupAndParticipants(model, groupId)
            return "groups/transactions/new"
        }

        try {
            val amount = form.amount ?: throw IllegalArgumentException("Amount is required")
            val payerId = form.payerId ?: throw IllegalArgumentException("Payer is required")
            val amountMinorUnits = amount.toMinorUnits()
            val dateInstant = form.date?.atStartOfDay(ZoneOffset.UTC)?.toInstant()
            transactionService.createExpense(
                groupId = groupId,
                payerId = payerId,
                amount = amountMinorUnits,
                description = form.description,
                consumerIds = form.consumerIds,
                date = dateInstant
            )
            return "redirect:/groups/$groupId"
        } catch (e: IllegalArgumentException) {
            bindingResult.reject("error.expense", e.message ?: "Invalid expense data")
            populateGroupAndParticipants(model, groupId)
            return "groups/transactions/new"
        }
    }

    private fun populateGroupAndParticipants(model: Model, groupId: Long) {
        val group = groupService.getGroup(groupId)
        val participants = groupService.getParticipants(groupId)
        model.addAttribute("group", group)
        model.addAttribute("participants", participants)
    }
}
