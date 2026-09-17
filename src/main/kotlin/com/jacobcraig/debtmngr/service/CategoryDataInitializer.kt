package com.jacobcraig.debtmngr.service

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Component
class CategoryDataInitializer(
    private val categoryService: CategoryService
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        categoryService.seedSystemCategories()
    }
}
