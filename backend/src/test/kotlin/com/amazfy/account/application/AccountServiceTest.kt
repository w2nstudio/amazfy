package com.amazfy.account.application

import com.amazfy.TestcontainersConfiguration
import com.amazfy.account.domain.InvalidAccountNameException
import com.amazfy.account.domain.Role
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Account module end to end: service → persistence adapter → real PostgreSQL (rolled back after each test). */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@Transactional
class AccountServiceTest {
    @Autowired
    lateinit var accountService: AccountService

    @Autowired
    lateinit var jdbc: JdbcClient

    // Users belong to the auth module (built next); until then tests insert them directly
    private fun newUser(email: String): UUID =
        jdbc
            .sql("INSERT INTO users (email) VALUES (:email) RETURNING id")
            .param("email", email)
            .query(UUID::class.java)
            .single()

    @Test
    fun `creating an account makes the user its owner`() {
        val john = newUser("john@aqualife.com")

        val account = accountService.createAccount("  AquaLife US ", john)

        assertEquals("AquaLife US", account.name.value)
        assertEquals(7, account.id.version())
        assertEquals(Role.OWNER, accountService.roleOf(john, account.id))
    }

    @Test
    fun `a user sees only the accounts they belong to, sorted by name`() {
        val john = newUser("john@aqualife.com")
        val maria = newUser("maria@example.com")
        accountService.createAccount("AquaLife US", john)
        accountService.createAccount("AquaLife EU", john)
        accountService.createAccount("Maria's Shop", maria)

        val johnsAccounts = accountService.accessibleAccounts(john)

        assertEquals(listOf("AquaLife EU", "AquaLife US"), johnsAccounts.map { it.account.name.value })
        assertTrue(johnsAccounts.all { it.role == Role.OWNER })
    }

    @Test
    fun `a user has no role in someone else's account`() {
        val john = newUser("john@aqualife.com")
        val maria = newUser("maria@example.com")
        val mariasAccount = accountService.createAccount("Maria's Shop", maria)

        assertNull(accountService.roleOf(john, mariasAccount.id))
    }

    @Test
    fun `renaming changes the name`() {
        val john = newUser("john@aqualife.com")
        val account = accountService.createAccount("Old Name", john)

        assertEquals("New Name", accountService.renameAccount(account.id, "New Name").name.value)
        assertEquals("New Name", accountService.findAccount(account.id)?.name?.value)
    }

    @Test
    fun `invalid name is rejected before anything is stored`() {
        val john = newUser("john@aqualife.com")
        assertFailsWith<InvalidAccountNameException> { accountService.createAccount("   ", john) }
        assertTrue(accountService.accessibleAccounts(john).isEmpty())
    }
}
