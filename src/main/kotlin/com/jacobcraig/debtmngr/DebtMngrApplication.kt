package com.jacobcraig.debtmngr

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class DebtMngrApplication

fun main(args: Array<String>) {
    runApplication<DebtMngrApplication>(*args)
}
