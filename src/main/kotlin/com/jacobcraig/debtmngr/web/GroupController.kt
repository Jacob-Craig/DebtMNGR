package com.jacobcraig.debtmngr.web

import com.jacobcraig.debtmngr.service.GroupService
import jakarta.validation.Valid
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
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

    @GetMapping("/{id}")
    fun showGroup(@PathVariable("id") id: Long, model: Model): String {
        val group = groupService.getGroup(id)
        val participants = groupService.getParticipants(id)
        model.addAttribute("group", group)
        model.addAttribute("participants", participants)
        if (!model.containsAttribute("participantForm")) {
            model.addAttribute("participantForm", AddParticipantForm())
        }
        return "groups/show"
    }

    @PostMapping("/{id}/participants")
    fun addParticipant(
        @PathVariable("id") id: Long,
        @Valid @ModelAttribute("participantForm") form: AddParticipantForm,
        bindingResult: BindingResult,
        model: Model
    ): String {
        if (bindingResult.hasErrors()) {
            val group = groupService.getGroup(id)
            val participants = groupService.getParticipants(id)
            model.addAttribute("group", group)
            model.addAttribute("participants", participants)
            return "groups/show"
        }
        groupService.addParticipant(id, form.name, form.isSelf)
        return "redirect:/groups/$id"
    }

    @PostMapping("/{id}/participants/{participantId}/self")
    fun designateSelf(
        @PathVariable("id") id: Long,
        @PathVariable("participantId") participantId: Long
    ): String {
        groupService.designateSelf(id, participantId)
        return "redirect:/groups/$id"
    }
}
