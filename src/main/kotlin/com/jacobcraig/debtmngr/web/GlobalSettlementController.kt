package com.jacobcraig.debtmngr.web

import com.jacobcraig.debtmngr.service.TransactionService
import jakarta.validation.Valid
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.*
import java.time.ZoneOffset

@Controller
@RequestMapping("/settle")
class GlobalSettlementController(
    private val transactionService: TransactionService
) {

    @GetMapping
    fun settleUpForm(
        @RequestParam(name = "contactName", required = false) contactName: String?,
        model: Model
    ): String {
        val summary = transactionService.getGlobalNetSummary()
        val form = GlobalSettleUpForm()

        if (!contactName.isNullOrBlank()) {
            val contact = summary.contacts.find { it.contactName.equals(contactName.trim(), ignoreCase = true) }
            if (contact != null) {
                form.contactName = contact.contactName
                if (contact.totalNet > 0) {
                    form.payerIsSelf = false
                    form.amount = contact.totalNet.toMoneyBigDecimal()
                } else if (contact.totalNet < 0) {
                    form.payerIsSelf = true
                    form.amount = (-contact.totalNet).toMoneyBigDecimal()
                }
            } else {
                form.contactName = contactName.trim()
            }
        }

        model.addAttribute("form", form)
        model.addAttribute("summary", summary)
        return "settle"
    }

    @PostMapping
    fun createSettlement(
        @Valid @ModelAttribute("form") form: GlobalSettleUpForm,
        bindingResult: BindingResult,
        model: Model
    ): String {
        if (bindingResult.hasErrors()) {
            model.addAttribute("summary", transactionService.getGlobalNetSummary())
            return "settle"
        }

        try {
            val amount = checkNotNull(form.amount) { "Amount is required" }
            val amountMinor = amount.toMinorUnits()
            val dateInstant = form.date?.atStartOfDay(ZoneOffset.UTC)?.toInstant()
            val payerIsSelf = form.payerIsSelf ?: false

            transactionService.createGlobalSettlement(
                contactName = form.contactName.trim(),
                payerIsSelf = payerIsSelf,
                amount = amountMinor,
                date = dateInstant,
                notes = form.notes
            )
            return "redirect:/"
        } catch (e: IllegalArgumentException) {
            bindingResult.reject("error.globalSettlement", e.message ?: "Invalid settlement data")
            model.addAttribute("summary", transactionService.getGlobalNetSummary())
            return "settle"
        }
    }
}
