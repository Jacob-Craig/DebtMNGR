package com.jacobcraig.debtmngr.web

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class AddParticipantForm(
    @field:NotBlank(message = "Participant name is required")
    @field:Size(max = 100, message = "Participant name cannot exceed 100 characters")
    val name: String = "",

    val isSelf: Boolean = false
)
