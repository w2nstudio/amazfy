package com.amazfy.common.tenant

import org.hibernate.cfg.MultiTenancySettings
import org.hibernate.context.spi.CurrentTenantIdentifierResolver
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Tells Hibernate which account the current work is for (CurrentAccount). Hibernate asks when it opens a session,
 * i.e. at the start of each transaction, so CurrentAccount must be set before the transaction begins.
 */
@Component
class AccountTenantResolver :
    CurrentTenantIdentifierResolver<UUID>,
    HibernatePropertiesCustomizer {
    override fun resolveCurrentTenantIdentifier(): UUID = CurrentAccount.get()

    // Sessions are opened per transaction by Spring, never reused across accounts
    override fun validateExistingCurrentSessions() = false

    override fun customize(hibernateProperties: MutableMap<String, Any>) {
        hibernateProperties[MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER] = this
    }
}
