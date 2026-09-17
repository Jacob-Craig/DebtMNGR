package com.jacobcraig.debtmngr.domain

object ExactSplitCalculator {

    fun calculate(totalAmount: Long, exactAmounts: Map<Long, Long>): List<SplitShare> {
        require(totalAmount > 0) { "Total amount must be greater than zero" }
        require(exactAmounts.isNotEmpty()) { "At least one consumer must be assigned an amount" }
        require(exactAmounts.values.all { it >= 0 }) { "Individual split amounts cannot be negative" }

        val sum = exactAmounts.values.sum()
        require(sum == totalAmount) {
            "The sum of exact split amounts ($sum) must equal the total expense amount ($totalAmount)"
        }

        return exactAmounts.filter { it.value > 0 }.map { (participantId, amount) ->
            SplitShare(participantId = participantId, amount = amount)
        }
    }
}
