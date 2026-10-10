package com.amazfy.system.adapter.inbound.web

import com.amazfy.api.generated.SystemApi
import com.amazfy.api.generated.model.SystemInfo
import org.springframework.boot.info.BuildProperties
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

/**
 * Implements the generated [SystemApi] contract (api/openapi.yaml).
 * "system" is a light module: no domain or use case layer is needed for read-only service info.
 */
@RestController
class SystemController(
    private val buildProperties: BuildProperties,
) : SystemApi {
    override fun getSystemInfo(): ResponseEntity<SystemInfo> =
        ResponseEntity.ok(
            SystemInfo(
                name = buildProperties.name ?: "unknown",
                version = buildProperties.version ?: "unknown",
            ),
        )
}
