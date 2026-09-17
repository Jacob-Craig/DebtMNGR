package com.jacobcraig.debtmngr.web

import com.jacobcraig.debtmngr.domain.Participant
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
@RequestMapping("/groups/{groupId}/transactions")
class TransactionController(
    private val groupService: GroupService,
    private val transactionService: TransactionService,
    private val categoryService: CategoryService
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
        if (form.splitMode == SplitMode.EQUAL) {
            if (form.consumerIds.isEmpty()) {
                bindingResult.rejectValue("consumerIds", "NotEmpty", "At least one consumer must be selected")
            }
        } else if (form.splitMode == SplitMode.EXACT) {
            validateExactSplits(form, bindingResult)
        }

        val customName = form.customCategoryName?.trim()
        val selectedCatId = form.categoryId
        if (selectedCatId == -1L && customName.isNullOrEmpty()) {
            bindingResult.rejectValue("customCategoryName", "NotBlank", "Custom category name cannot be blank")
        }

        if (bindingResult.hasErrors()) {
            populateGroupAndParticipants(model, groupId)
            return "groups/transactions/new"
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

            when (form.splitMode) {
                SplitMode.EQUAL -> {
                    transactionService.createExpense(
                        groupId = groupId,
                        payerId = payerId,
                        amount = amountMinorUnits,
                        description = form.description,
                        consumerIds = form.consumerIds,
                        date = dateInstant,
                        categoryId = resolvedCategoryId
                    )
                }
                SplitMode.EXACT -> {
                    val exactAmountsMinor = form.exactAmounts
                        .filterValues { it != null && it > BigDecimal.ZERO }
                        .mapValues { checkNotNull(it.value).toMinorUnits() }

                    transactionService.createExpense(
                        groupId = groupId,
                        payerId = payerId,
                        amount = amountMinorUnits,
                        description = form.description,
                        consumerIds = exactAmountsMinor.keys.toList(),
                        date = dateInstant,
                        splitMode = SplitMode.EXACT,
                        exactAmounts = exactAmountsMinor,
                        categoryId = resolvedCategoryId
                    )
                }
            }
            return "redirect:/groups/$groupId"
        } catch (e: IllegalArgumentException) {
            bindingResult.reject("error.expense", e.message ?: "Invalid expense data")
            populateGroupAndParticipants(model, groupId)
            return "groups/transactions/new"
        }
    }

    private fun validateExactSplits(form: CreateExpenseForm, bindingResult: BindingResult) {
        val nonNullEntries = form.exactAmounts.filterValues { it != null }

        if (nonNullEntries.values.any { it != null && it < BigDecimal.ZERO }) {
            bindingResult.rejectValue("exactAmounts", "error.exactAmounts", "Individual split amounts cannot be negative")
            return
        }

        val positiveEntries = nonNullEntries.filterValues { it != null && it > BigDecimal.ZERO }
        if (positiveEntries.isEmpty()) {
            bindingResult.rejectValue("exactAmounts", "error.exactAmounts", "At least one participant must be assigned an amount")
            return
        }

        val amount = form.amount ?: return
        if (!bindingResult.hasFieldErrors("amount")) {
            val totalMinor = amount.toMinorUnits()
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

    private fun populateGroupAndParticipants(model: Model, groupId: Long) {
        val group = groupService.getGroup(groupId)
        val participants = groupService.getParticipants(groupId)
        val categories = categoryService.getCategoriesForGroup(groupId)
        model.addAttribute("group", group)
        model.addAttribute("participants", participants)
        model.addAttribute("categories", categories)
    }
}
