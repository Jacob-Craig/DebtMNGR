package com.jacobcraig.debtmngr.domain

import java.math.BigInteger

object ProportionalSettlementCalculator {

    fun calculate(totalAmount: Long, debtsByGroup: Map<Long, Long>): Map<Long, Long> {
        require(totalAmount > 0) { "Settlement amount must be positive" }
        require(debtsByGroup.isNotEmpty()) { "Debts map cannot be empty" }
        require(debtsByGroup.values.all { it > 0 }) { "Each group debt must be positive" }

        val totalDebt = debtsByGroup.values.sum()
        require(totalAmount <= totalDebt) { "Settlement amount cannot exceed total debt" }

        if (totalAmount == totalDebt) {
            return debtsByGroup
        }

        val totalDebtBig = BigInteger.valueOf(totalDebt)
        val totalAmountBig = BigInteger.valueOf(totalAmount)

        data class GroupShare(
            val groupId: Long,
            var allocated: Long,
            val fractionalRemainder: BigInteger
        )

        val shares = debtsByGroup.map { (groupId, debt) ->
            val numerator = totalAmountBig.multiply(BigInteger.valueOf(debt))
            val (quotient, remainder) = numerator.divideAndRemainder(totalDebtBig)
            GroupShare(
                groupId = groupId,
                allocated = quotient.longValueExact(),
                fractionalRemainder = remainder
            )
        }

        var unallocated = totalAmount - shares.sumOf { it.allocated }

        val sortedShares = shares.sortedWith(
            compareByDescending<GroupShare> { it.fractionalRemainder }
                .thenByDescending { debtsByGroup.getValue(it.groupId) }
                .thenBy { it.groupId }
        )

        for (share in sortedShares) {
            if (unallocated <= 0L) break
            share.allocated += 1L
            unallocated -= 1L
        }

        return shares.associate { it.groupId to it.allocated }
    }
}
