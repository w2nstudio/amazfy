package com.amazfy.common.time

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/** One shared clock (UTC). Code asks it for "now" instead of calling Instant.now(), so tests can control time. */
@Configuration
class ClockConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
