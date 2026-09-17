package com.jacobcraig.debtmngr.domain

data class SplitShare(
    val participantId: Long,
    val amount: Long
)

object EqualSplitCalculator {

    fun calculate(totalAmount: Long, payerId: Long, consumerIds: List<Long>): List<SplitShare> {
        require(totalAmount > 0) { "Total amount must be greater than zero" }
        require(consumerIds.isNotEmpty()) { "Consumer list cannot be empty" }

        val n = consumerIds.size
        val baseShare = totalAmount / n
        val remainder = totalAmount % n

        val payerInConsumers = consumerIds.contains(payerId)

        return consumerIds.mapIndexed { index, participantId ->
            val share = if (payerInConsumers) {
                if (participantId == payerId) baseShare + remainder else baseShare
            } else {
                if (index == 0) baseShare + remainder else baseShare
            }
            SplitShare(participantId = participantId, amount = share)
        }
    }
}
