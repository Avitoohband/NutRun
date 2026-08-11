package com.avitoohband.nutrun.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class NutRunRepositoryAccountScopeTest {
    @Test
    fun expectedAccountRemainsTheWriteOwnerWhenSessionSwitchesAfterValidation() = runBlocking {
        var activeAccount = "account-a"
        var persistedAccount: String? = null

        withExpectedRepositoryAccount(
            expectedAccountId = "account-a",
            currentAccountId = {
                val validated = activeAccount
                activeAccount = "account-b"
                validated
            },
            write = { accountId ->
                persistedAccount = accountId
            }
        )

        assertEquals("account-b", activeAccount)
        assertEquals("account-a", persistedAccount)
    }
}
