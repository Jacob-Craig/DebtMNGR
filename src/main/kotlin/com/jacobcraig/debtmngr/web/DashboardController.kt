package com.jacobcraig.debtmngr.web

import com.jacobcraig.debtmngr.service.GroupService
import com.jacobcraig.debtmngr.service.TransactionService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class DashboardController(
    private val groupService: GroupService,
    private val transactionService: TransactionService
) {

    @GetMapping("/")
    fun dashboard(model: Model): String {
        val groups = groupService.getAllGroups()
        val groupBalances = mutableMapOf<Long, FormattedBalance>()

        for (group in groups) {
            val groupId = group.id ?: continue
            val participants = groupService.getParticipants(groupId)
            val self = participants.find { it.isSelf }
            if (self != null && self.id != null) {
                val rawBalances = transactionService.getParticipantBalances(groupId)
                val net = rawBalances[self.id] ?: 0L
                groupBalances[groupId] = net.toFormattedBalanceModel()
            }
        }

        model.addAttribute("groups", groups)
        model.addAttribute("groupBalances", groupBalances)
        return "dashboard"
    }
}
