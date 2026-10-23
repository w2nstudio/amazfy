package com.amazfy.auth.adapter.outbound.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UuidGenerator
import java.time.Instant
import java.util.UUID

// JPA class for the user_identities table (V2__auth_users.sql), used only inside UserPersistenceAdapter.

@Entity
@Table(name = "user_identities")
class UserIdentityEntity(
    val userId: UUID,
    val provider: String,
    val subject: String,
    var passwordHash: String?,
) {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    val id: UUID? = null

    @CreationTimestamp
    @Column(updatable = false)
    val createdAt: Instant? = null

    companion object {
        const val PASSWORD = "password"
    }
}
