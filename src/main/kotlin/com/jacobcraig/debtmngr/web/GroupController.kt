package com.jacobcraig.debtmngr.web

import com.jacobcraig.debtmngr.service.GroupService
import jakarta.validation.Valid
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping("/groups")
class GroupController(private val groupService: GroupService) {

    @GetMapping("/new")
    fun newGroupForm(model: Model): String {
        model.addAttribute("groupForm", CreateGroupForm())
        return "groups/new"
    }

    @PostMapping
    fun createGroup(
        @Valid @ModelAttribute("groupForm") form: CreateGroupForm,
        bindingResult: BindingResult
    ): String {
        if (bindingResult.hasErrors()) {
            return "groups/new"
        }
        groupService.createGroup(form.name, form.description)
        return "redirect:/"
    }
}
