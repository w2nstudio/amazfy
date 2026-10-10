package com.amazfy

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class AmazfyBackendApplication

fun main(args: Array<String>) {
	runApplication<AmazfyBackendApplication>(*args)
}
