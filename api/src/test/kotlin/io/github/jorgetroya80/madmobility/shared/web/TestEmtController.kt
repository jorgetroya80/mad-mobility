package io.github.jorgetroya80.madmobility.shared.web

import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** Test-only endpoint to exercise shared web infrastructure without domain modules. */
@RestController
class TestEmtController {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/v1/test/ping")
    fun ping(): String {
        log.info("ping received")
        return "pong"
    }
}
