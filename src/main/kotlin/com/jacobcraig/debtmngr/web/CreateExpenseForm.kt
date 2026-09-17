package com.jacobcraig.debtmngr.web

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import org.springframework.format.annotation.DateTimeFormat
import java.math.BigDecimal
import java.time.LocalDate

class CreateExpenseForm(
    @field:NotBlank(message = "Expense description cannot be blank")
    var description: String = "",

    @field:NotNull(message = "Amount is required")
    @field:DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    @field:Digits(integer = 10, fraction = 2, message = "Amount cannot have more than 2 decimal places")
    var amount: BigDecimal? = null,

    @field:NotNull(message = "Payer must be selected")
    var payerId: Long? = null,

    @field:NotEmpty(message = "At least one consumer must be selected")
    var consumerIds: List<Long> = emptyList(),

    @field:NotNull(message = "Date is required")
    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    var date: LocalDate? = LocalDate.now()
)
