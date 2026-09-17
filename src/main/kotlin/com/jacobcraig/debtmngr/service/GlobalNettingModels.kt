package com.jacobcraig.debtmngr.service

data class ContactGroupDebt(
    val groupId: Long,
    val groupName: String,
    val amount: Long // Positive: contact owes operator; Negative: operator owes contact
)

data class ContactBreakdown(
    val contactName: String,
    val totalNet: Long, // Positive: contact owes operator; Negative: operator owes contact
    val groupDebts: List<ContactGroupDebt>
)

data class GlobalNetSummary(
    val operatorNetTotal: Long,
    val contacts: List<ContactBreakdown>
)
