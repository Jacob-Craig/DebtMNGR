package com.jacobcraig.debtmngr.web

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

data class FormattedBalance(
    val amount: Long,
    val formatted: String,
    val isPositive: Boolean,
    val isNegative: Boolean,
    val isZero: Boolean
)

@JvmOverloads
fun Long.toFormattedBalanceModel(currencySymbol: String = "£"): FormattedBalance {
    return FormattedBalance(
        amount = this,
        formatted = this.toFormattedBalance(currencySymbol),
        isPositive = this > 0,
        isNegative = this < 0,
        isZero = this == 0L
    )
}

@JvmOverloads
fun Long.toFormattedBalance(currencySymbol: String = "£"): String {
    val isNeg = this < 0
    val absolute = abs(this)
    val major = absolute / 100
    val minor = absolute % 100
    val formatted = String.format("%s%d.%02d", currencySymbol, major, minor)
    return when {
        isNeg -> "-$formatted"
        this > 0 -> "+$formatted"
        else -> formatted
    }
}

@JvmOverloads
fun Long.toFormattedMoney(currencySymbol: String = "£"): String {
    val absolute = abs(this)
    val major = absolute / 100
    val minor = absolute % 100
    return String.format("%s%d.%02d", currencySymbol, major, minor)
}

fun BigDecimal.toMinorUnits(): Long {
    return this.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
}

fun Long.toMoneyBigDecimal(): BigDecimal {
    return BigDecimal.valueOf(this).movePointLeft(2)
}
