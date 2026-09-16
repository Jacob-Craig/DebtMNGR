package com.jacobcraig.debtmngr.web

import com.jacobcraig.debtmngr.service.GroupService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class DashboardController(private val groupService: GroupService) {

    @GetMapping("/")
    fun dashboard(model: Model): String {
        val groups = groupService.getAllGroups()
        model.addAttribute("groups", groups)
        return "dashboard"
    }
}
