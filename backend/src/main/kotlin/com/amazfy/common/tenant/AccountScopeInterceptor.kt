package com.amazfy.common.tenant

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.HandlerMapping
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.util.UUID

/**
 * For every request whose path has an {accountId} (/api/v1/accounts/{accountId}/...), makes that account the
 * CurrentAccount for the whole request, and forgets it afterwards. So account-scoped endpoints are filtered without
 * any code of their own. Whether the user may use that account is checked separately (@PreAuthorize, accountAccess).
 */
@Configuration
class AccountScopeInterceptor :
    HandlerInterceptor,
    WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(this).addPathPatterns("/api/v1/accounts/*/**")
    }

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        @Suppress("UNCHECKED_CAST")
        val pathVariables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as? Map<String, String>
        pathVariables?.get("accountId")?.let { runCatching { UUID.fromString(it) }.getOrNull() }?.let(CurrentAccount::set)
        return true
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?,
    ) = CurrentAccount.clear() // threads are reused for other requests: never leave an account behind
}
