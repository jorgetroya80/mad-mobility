package io.github.jorgetroya80.madtransit

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MadTransitApplication

fun main(args: Array<String>) {
    runApplication<MadTransitApplication>(*args)
}
