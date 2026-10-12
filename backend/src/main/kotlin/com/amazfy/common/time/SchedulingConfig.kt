package com.amazfy.common.time

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/** Turns on @Scheduled jobs (e.g. hourly clean-up of expired login attempts; data syncs later). */
@Configuration
@EnableScheduling
class SchedulingConfig
