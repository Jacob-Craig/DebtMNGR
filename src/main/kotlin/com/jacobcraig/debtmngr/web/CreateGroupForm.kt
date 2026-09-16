package com.jacobcraig.debtmngr.web

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateGroupForm(
    @field:NotBlank(message = "Group name is required")
    @field:Size(max = 100, message = "Group name cannot exceed 100 characters")
    val name: String = "",

    @field:Size(max = 500, message = "Description cannot exceed 500 characters")
    val description: String? = null
)
