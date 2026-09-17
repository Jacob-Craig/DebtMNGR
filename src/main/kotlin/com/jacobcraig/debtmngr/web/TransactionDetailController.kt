package com.jacobcraig.debtmngr.web

import com.jacobcraig.debtmngr.domain.EntryType
import com.jacobcraig.debtmngr.domain.SplitMode
import com.jacobcraig.debtmngr.service.CategoryService
import com.jacobcraig.debtmngr.service.GroupService
import com.jacobcraig.debtmngr.service.TransactionService
import jakarta.validation.Valid
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.time.ZoneOffset

@Controller
@RequestMapping("/transactions")
class TransactionDetailController(
    private val transactionService: TransactionService,
    private val groupService: GroupService,
    private val categoryService: CategoryService
) {

    @GetMapping("/{id}")
    fun showTransaction(@PathVariable("id") id: Long, model: Model): String {
        val transaction = transactionService.getTransaction(id)
        val auditLogs = transactionService.getAuditLogs(id)
        val adjustments = transactionService.getAdjustments(id)
        val participants = groupService.getParticipants(checkNotNull(transaction.group.id))
        val participantsByAccountId = participants.associateBy { it.account.id }

        model.addAttribute("transaction", transaction)
        model.addAttribute("entries", transaction.entries)
        model.addAttribute("auditLogs", auditLogs)
        model.addAttribute("adjustments", adjustments)
        model.addAttribute("participants", participants)
        model.addAttribute("participantsByAccountId", participantsByAccountId)
        return "transactions/show"
    }

    @GetMapping("/{id}/edit")
    fun editTransactionForm(@PathVariable("id") id: Long, model: Model): String {
        val transaction = transactionService.getTransaction(id)
        if (transaction.isLocked || transaction.isDeleted) {
            return "redirect:/transactions/$id"
        }

        val groupId = checkNotNull(transaction.group.id)
        val participants = groupService.getParticipants(groupId)
        val accountIdToParticipantId = participants.associate { it.account.id to it.id }

        val debitEntries = transaction.entries.filter { it.type == EntryType.DEBIT }
        val consumerIds = debitEntries.mapNotNull { accountIdToParticipantId[it.account.id] }

        val form = EditExpenseForm(
            description = transaction.description,
            amount = transaction.amount.toMoneyBigDecimal(),
            payerId = transaction.payer.id,
            date = transaction.createdAt.atZone(ZoneOffset.UTC).toLocalDate(),
            categoryId = transaction.category?.id,
            consumerIds = consumerIds
        )

        val amounts = debitEntries.map { it.amount }.distinct()
        if (amounts.size > 1) {
            form.splitMode = SplitMode.EXACT
        }
        for (debit in debitEntries) {
            val pId = accountIdToParticipantId[debit.account.id]
            if (pId != null) {
                form.exactAmounts[pId] = debit.amount.toMoneyBigDecimal()
            }
        }

        populateEditModel(model, groupId, form, transaction.id ?: id)
        return "transactions/edit"
    }

    @PostMapping("/{id}/edit")
    fun updateExpense(
        @PathVariable("id") id: Long,
        @Valid @ModelAttribute("editForm") form: EditExpenseForm,
        bindingResult: BindingResult,
        model: Model
    ): String {
        val transaction = transactionService.getTransaction(id)
        if (transaction.isLocked || transaction.isDeleted) {
            return "redirect:/transactions/$id"
        }

        val groupId = checkNotNull(transaction.group.id)

        if (form.splitMode == SplitMode.EQUAL) {
            if (form.consumerIds.isEmpty()) {
                bindingResult.rejectValue("consumerIds", "NotEmpty", "At least one consumer must be selected")
            }
        } else if (form.splitMode == SplitMode.EXACT) {
            validateExactSplits(form.exactAmounts, form.amount, bindingResult)
        }

        val customName = form.customCategoryName?.trim()
        val selectedCatId = form.categoryId
        if (selectedCatId == -1L && customName.isNullOrEmpty()) {
            bindingResult.rejectValue("customCategoryName", "NotBlank", "Custom category name cannot be blank")
        }

        if (bindingResult.hasErrors()) {
            populateEditModel(model, groupId, form, id)
            return "transactions/edit"
        }

        try {
            val amount = form.amount ?: throw IllegalArgumentException("Amount is required")
            val payerId = form.payerId ?: throw IllegalArgumentException("Payer is required")
            val amountMinorUnits = amount.toMinorUnits()
            val dateInstant = form.date?.atStartOfDay(ZoneOffset.UTC)?.toInstant()

            val resolvedCategoryId = when {
                !customName.isNullOrEmpty() -> {
                    val created = categoryService.createCustomCategory(groupId, customName)
                    created.id
                }
                selectedCatId != null && selectedCatId > 0 -> selectedCatId
                else -> null
            }

            val newTx = when (form.splitMode) {
                SplitMode.EQUAL -> {
                    transactionService.editExpense(
                        transactionId = id,
                        description = form.description,
                        amount = amountMinorUnits,
                        payerId = payerId,
                        consumerIds = form.consumerIds,
                        date = dateInstant,
                        categoryId = resolvedCategoryId,
                        reason = form.reason
                    )
                }
                SplitMode.EXACT -> {
                    val exactAmountsMinor = form.exactAmounts
                        .filterValues { it != null && it > BigDecimal.ZERO }
                        .mapValues { checkNotNull(it.value).toMinorUnits() }

                    transactionService.editExpense(
                        transactionId = id,
                        description = form.description,
                        amount = amountMinorUnits,
                        payerId = payerId,
                        consumerIds = exactAmountsMinor.keys.toList(),
                        date = dateInstant,
                        splitMode = SplitMode.EXACT,
                        exactAmounts = exactAmountsMinor,
                        categoryId = resolvedCategoryId,
                        reason = form.reason
                    )
                }
            }

            return "redirect:/transactions/${newTx.id}"
        } catch (e: IllegalArgumentException) {
            bindingResult.reject("error.expense", e.message ?: "Invalid expense data")
            populateEditModel(model, groupId, form, id)
            return "transactions/edit"
        } catch (e: IllegalStateException) {
            bindingResult.reject("error.expense", e.message ?: "Transaction cannot be edited")
            populateEditModel(model, groupId, form, id)
            return "transactions/edit"
        }
    }

    @PostMapping("/{id}/delete")
    fun deleteTransaction(
        @PathVariable("id") id: Long,
        @RequestParam(name = "reason", required = false) reason: String?
    ): String {
        transactionService.deleteTransaction(id, reason)
        return "redirect:/transactions/$id"
    }

    @GetMapping("/{id}/adjust")
    fun adjustTransactionForm(@PathVariable("id") id: Long, model: Model): String {
        val transaction = transactionService.getTransaction(id)
        if (!transaction.isLocked || transaction.isDeleted) {
            return "redirect:/transactions/$id"
        }

        val groupId = checkNotNull(transaction.group.id)
        val participants = groupService.getParticipants(groupId)
        val accountIdToParticipantId = participants.associate { it.account.id to it.id }

        val debitEntries = transaction.entries.filter { it.type == EntryType.DEBIT }
        val consumerIds = debitEntries.mapNotNull { accountIdToParticipantId[it.account.id] }

        val form = CreateAdjustmentForm(
            description = "Adjustment: ${transaction.description}",
            payerId = transaction.payer.id,
            consumerIds = consumerIds
        )

        model.addAttribute("originalTransaction", transaction)
        model.addAttribute("group", groupService.getGroup(groupId))
        model.addAttribute("participants", participants)
        model.addAttribute("adjustmentForm", form)
        return "transactions/adjust"
    }

    @PostMapping("/{id}/adjust")
    fun createAdjustment(
        @PathVariable("id") id: Long,
        @Valid @ModelAttribute("adjustmentForm") form: CreateAdjustmentForm,
        bindingResult: BindingResult,
        model: Model
    ): String {
        val transaction = transactionService.getTransaction(id)
        if (!transaction.isLocked || transaction.isDeleted) {
            return "redirect:/transactions/$id"
        }

        val groupId = checkNotNull(transaction.group.id)

        if (form.splitMode == SplitMode.EQUAL) {
            if (form.consumerIds.isEmpty()) {
                bindingResult.rejectValue("consumerIds", "NotEmpty", "At least one consumer must be selected")
            }
        } else if (form.splitMode == SplitMode.EXACT) {
            validateExactSplits(form.exactAmounts, form.amount, bindingResult)
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("originalTransaction", transaction)
            model.addAttribute("group", groupService.getGroup(groupId))
            model.addAttribute("participants", groupService.getParticipants(groupId))
            return "transactions/adjust"
        }

        try {
            val amount = form.amount ?: throw IllegalArgumentException("Amount is required")
            val payerId = form.payerId ?: throw IllegalArgumentException("Payer is required")
            val amountMinorUnits = amount.toMinorUnits()
            val dateInstant = form.date?.atStartOfDay(ZoneOffset.UTC)?.toInstant()

            val adjustment = when (form.splitMode) {
                SplitMode.EQUAL -> {
                    transactionService.createAdjustment(
                        originalTransactionId = id,
                        description = form.description,
                        amount = amountMinorUnits,
                        payerId = payerId,
                        consumerIds = form.consumerIds,
                        date = dateInstant
                    )
                }
                SplitMode.EXACT -> {
                    val exactAmountsMinor = form.exactAmounts
                        .filterValues { it != null && it > BigDecimal.ZERO }
                        .mapValues { checkNotNull(it.value).toMinorUnits() }

                    transactionService.createAdjustment(
                        originalTransactionId = id,
                        description = form.description,
                        amount = amountMinorUnits,
                        payerId = payerId,
                        consumerIds = exactAmountsMinor.keys.toList(),
                        splitMode = SplitMode.EXACT,
                        exactAmounts = exactAmountsMinor,
                        date = dateInstant
                    )
                }
            }

            return "redirect:/transactions/${adjustment.id}"
        } catch (e: IllegalArgumentException) {
            bindingResult.reject("error.adjustment", e.message ?: "Invalid adjustment data")
            model.addAttribute("originalTransaction", transaction)
            model.addAttribute("group", groupService.getGroup(groupId))
            model.addAttribute("participants", groupService.getParticipants(groupId))
            return "transactions/adjust"
        }
    }

    private fun validateExactSplits(
        exactAmounts: Map<Long, BigDecimal?>,
        totalAmount: BigDecimal?,
        bindingResult: BindingResult
    ) {
        val nonNullEntries = exactAmounts.filterValues { it != null }

        if (nonNullEntries.values.any { it != null && it < BigDecimal.ZERO }) {
            bindingResult.rejectValue("exactAmounts", "error.exactAmounts", "Individual split amounts cannot be negative")
            return
        }

        val positiveEntries = nonNullEntries.filterValues { it != null && it > BigDecimal.ZERO }
        if (positiveEntries.isEmpty()) {
            bindingResult.rejectValue("exactAmounts", "error.exactAmounts", "At least one participant must be assigned an amount")
            return
        }

        if (totalAmount != null && !bindingResult.hasFieldErrors("amount")) {
            val totalMinor = totalAmount.toMinorUnits()
            val sumMinor = positiveEntries.values.filterNotNull().sumOf { it.toMinorUnits() }
            if (sumMinor != totalMinor) {
                val formattedSum = sumMinor.toFormattedMoney()
                val formattedTotal = totalMinor.toFormattedMoney()
                bindingResult.rejectValue(
                    "exactAmounts",
                    "error.exactAmounts",
                    "The sum of exact split amounts ($formattedSum) must equal the total expense amount ($formattedTotal)"
                )
            }
        }
    }

    private fun populateEditModel(model: Model, groupId: Long, form: EditExpenseForm, transactionId: Long) {
        val group = groupService.getGroup(groupId)
        val participants = groupService.getParticipants(groupId)
        val categories = categoryService.getCategoriesForGroup(groupId)
        model.addAttribute("transactionId", transactionId)
        model.addAttribute("group", group)
        model.addAttribute("participants", participants)
        model.addAttribute("categories", categories)
        model.addAttribute("editForm", form)
    }
}
