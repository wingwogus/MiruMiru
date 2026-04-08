package com.example.application.bootstrap

import mu.KotlinLogging
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Profile
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.boot.SpringApplication
import kotlin.system.exitProcess

@Component
@Profile("bootstrap")
@Order(Ordered.LOWEST_PRECEDENCE)
class BootstrapShutdownRunner(
    private val applicationContext: ConfigurableApplicationContext
) : ApplicationRunner {

    private val logger = KotlinLogging.logger {}

    override fun run(args: ApplicationArguments) {
        logger.info { "Bootstrap profile completed. Shutting down application context." }
        val exitCode = SpringApplication.exit(applicationContext, { 0 })
        exitProcess(exitCode)
    }
}
