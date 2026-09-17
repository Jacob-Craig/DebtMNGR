package com.jacobcraig.debtmngr.web

import com.jacobcraig.debtmngr.service.GroupService
import com.jacobcraig.debtmngr.service.TransactionService
import jakarta.validation.Valid
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.*
import java.time.ZoneOffset

@Controller
@RequestMapping("/groups/{groupId}")
class SettlementController(
    private val groupService: GroupService,
    private val transactionService: TransactionService
) {

    @GetMapping("/settle")
    fun settleUpForm(
        @PathVariable("groupId") groupId: Long,
        @RequestParam(name = "payerId", required = false) payerId: Long?,
        @RequestParam(name = "receiverId", required = false) receiverId: Long?,
        model: Model
    ): String {
        populateGroupAndParticipants(model, groupId)
        val form = SettleUpForm()
        if (payerId != null) {
            form.payerId = payerId
        }
        if (receiverId != null) {
            form.receiverId = receiverId
        }
        if (form.payerId == null && form.receiverId == null) {
            val participants = groupService.getParticipants(groupId)
            val self = participants.find { it.isSelf }
            val rawBalances = transactionService.getParticipantBalances(groupId)
            if (self != null && self.id != null) {
                val selfBalance = rawBalances[self.id] ?: 0L
                if (selfBalance < 0) {
                    form.payerId = self.id
                } else if (selfBalance > 0) {
                    form.receiverId = self.id
                }
            }
        }
        model.addAttribute("settleForm", form)
        return "groups/settle"
    }

    @PostMapping("/settle")
    fun createSettlement(
        @PathVariable("groupId") groupId: Long,
        @Valid @ModelAttribute("settleForm") form: SettleUpForm,
        bindingResult: BindingResult,
        model: Model
    ): String {
        if (form.payerId != null && form.receiverId != null && form.payerId == form.receiverId) {
            bindingResult.rejectValue("receiverId", "error.receiverId", "Payer and receiver cannot be the same person")
        }

        if (bindingResult.hasErrors()) {
            populateGroupAndParticipants(model, groupId)
            return "groups/settle"
        }

        try {
            val amount = checkNotNull(form.amount) { "Amount is required" }
            val payerId = checkNotNull(form.payerId) { "Payer is required" }
            val receiverId = checkNotNull(form.receiverId) { "Receiver is required" }
            val amountMinorUnits = amount.toMinorUnits()
            val dateInstant = form.date?.atStartOfDay(ZoneOffset.UTC)?.toInstant()

            transactionService.createSettlement(
                groupId = groupId,
                payerId = payerId,
                receiverId = receiverId,
                amount = amountMinorUnits,
                date = dateInstant,
                notes = form.notes
            )
            return "redirect:/groups/$groupId"
        } catch (e: IllegalArgumentException) {
            bindingResult.reject("error.settlement", e.message ?: "Invalid settlement data")
            populateGroupAndParticipants(model, groupId)
            return "groups/settle"
        }
    }

    private fun populateGroupAndParticipants(model: Model, groupId: Long) {
        val group = groupService.getGroup(groupId)
        val participants = groupService.getParticipants(groupId)
        val rawBalances = transactionService.getParticipantBalances(groupId)
        val balances = participants.associate { p ->
            val pId = checkNotNull(p.id)
            pId to (rawBalances[pId] ?: 0L).toFormattedBalanceModel()
        }
        model.addAttribute("group", group)
        model.addAttribute("participants", participants)
        model.addAttribute("balances", balances)
    }
}
