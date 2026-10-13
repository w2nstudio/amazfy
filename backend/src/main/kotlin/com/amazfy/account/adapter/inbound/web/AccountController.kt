package com.amazfy.account.adapter.inbound.web

import com.amazfy.account.application.AccountService
import com.amazfy.account.application.AccountSummary
import com.amazfy.api.generated.AccountsApi
import com.amazfy.api.generated.model.Account
import com.amazfy.api.generated.model.AccountRequest
import com.amazfy.common.security.currentUserId
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RestController
import java.time.ZoneOffset
import java.util.UUID

/** Implements the Accounts part of api/openapi.yaml. Access rules: AccountAccessGuard ("accountAccess"). */
@RestController
class AccountController(
    private val accountService: AccountService,
) : AccountsApi {
    /** Any logged-in user can create an account ("Add account"); they become its owner. */
    override fun createAccount(accountRequest: AccountRequest): ResponseEntity<Account> =
        ResponseEntity.status(HttpStatus.CREATED).body(accountService.createAccount(accountRequest.name, currentUserId()).toApi())

    @PreAuthorize("@accountAccess.canView(#accountId)")
    override fun getAccount(accountId: UUID): ResponseEntity<Account> = ResponseEntity.ok(accountService.findAccount(accountId)!!.toApi())

    @PreAuthorize("@accountAccess.canManage(#accountId)")
    override fun renameAccount(
        accountId: UUID,
        accountRequest: AccountRequest,
    ): ResponseEntity<Account> = ResponseEntity.ok(accountService.renameAccount(accountId, accountRequest.name).toApi())
}

private fun AccountSummary.toApi() = Account(id = id, name = name, createdAt = createdAt.atOffset(ZoneOffset.UTC))
