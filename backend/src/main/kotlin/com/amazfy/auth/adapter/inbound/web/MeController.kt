package com.amazfy.auth.adapter.inbound.web

import com.amazfy.account.application.AccountService
import com.amazfy.api.generated.MeApi
import com.amazfy.api.generated.model.Account
import com.amazfy.api.generated.model.Me
import com.amazfy.api.generated.model.Role
import com.amazfy.auth.application.AuthService
import com.amazfy.common.security.currentUserId
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.time.ZoneOffset
import com.amazfy.api.generated.model.AccountAccess as ApiAccountAccess

/**
 * Implements the Me part of api/openapi.yaml: who is logged in, and which accounts they can open.
 * The frontend uses it after login to fill the account switcher.
 */
@RestController
class MeController(
    private val authService: AuthService,
    private val accountService: AccountService,
) : MeApi {
    override fun getMe(): ResponseEntity<Me> {
        val user = authService.findUser(currentUserId())
        val accounts =
            accountService.accessibleAccounts(user.id).map {
                ApiAccountAccess(
                    account =
                        Account(
                            id = it.account.id,
                            name = it.account.name,
                            createdAt = it.account.createdAt.atOffset(ZoneOffset.UTC),
                        ),
                    role = Role.entries.first { role -> role.value == it.role.name },
                )
            }
        return ResponseEntity.ok(Me(id = user.id, email = user.email.value, emailVerified = user.emailVerified, accounts = accounts))
    }
}
