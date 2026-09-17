package com.jacobcraig.debtmngr.web

import org.springframework.validation.BindingResult
import java.math.BigDecimal

/**
 * Shared validation logic for exact split amounts used by both
 * [TransactionController] and [TransactionDetailController].
 */
object ExactSplitValidator {

    fun validate(
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
}
