package com.amazfy

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext

@SpringBootApplication
class AmazfyBackendApplication

/** Settings every environment must provide. The local profile supplies them; production gets them from secrets. */
private val requiredSettings =
    arrayOf(
        "DB_URL",
        "DB_USERNAME",
        "DB_PASSWORD",
        "CORS_ALLOWED_ORIGINS",
        "JWT_SECRET",
        "MAIL_HOST",
        "MAIL_PORT",
        "MAIL_FROM",
        "FRONTEND_BASE_URL",
    )

/** Stops startup with a clear message listing missing settings, before anything tries to use them. */
private val requiredSettingsCheck =
    ApplicationContextInitializer<ConfigurableApplicationContext> { context ->
        context.environment.setRequiredProperties(*requiredSettings)
        context.environment.validateRequiredProperties()
    }

fun main(args: Array<String>) {
    runApplication<AmazfyBackendApplication>(*args) {
        addInitializers(requiredSettingsCheck)
    }
}
