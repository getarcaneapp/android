package app.getarcane.android.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerIdentityTest {
    @Test
    fun `equivalent server spellings share one identity and token account`() {
        val identities = listOf(
            "arcane.example.com",
            "HTTPS://ARCANE.EXAMPLE.COM.:443/",
            "https://arcane.example.com/dashboard?tab=updates#images",
        ).map { requireNotNull(ServerIdentities.from(it)) }

        assertEquals(listOf("https://arcane.example.com:443"), identities.map { it.canonicalOrigin }.distinct())
        assertEquals(1, identities.map { it.tokenAccount }.distinct().size)
        assertEquals(listOf("https://arcane.example.com"), identities.map { it.normalizedUrl }.distinct())
    }

    @Test
    fun `scheme host path and non-default port remain distinct`() {
        val identities = listOf(
            "https://arcane.example.com",
            "http://arcane.example.com",
            "https://other.example.com",
            "https://arcane.example.com/tenant",
            "https://arcane.example.com:8443",
        ).map { requireNotNull(ServerIdentities.from(it)) }

        assertEquals(identities.size, identities.map { it.canonicalOrigin }.distinct().size)
        assertEquals(identities.size, identities.map { it.tokenAccount }.distinct().size)
    }

    @Test
    fun `token account is a stable pref-key-safe digest`() {
        val identity = requireNotNull(ServerIdentities.from("https://arcane.example.com"))
        val repeated = requireNotNull(ServerIdentities.from("https://arcane.example.com:443"))

        assertEquals(identity.tokenAccount, repeated.tokenAccount)
        assertTrue(identity.tokenAccount.matches(Regex("server\\.[0-9a-f]{64}")))
        assertNotEquals(identity.canonicalOrigin, identity.tokenAccount)
    }

    @Test
    fun `invalid server URL has no identity`() {
        assertNull(ServerIdentities.from(""))
        assertNull(ServerIdentities.from("ftp://arcane.example.com"))
        assertNull(ServerIdentities.from("https://user:password@arcane.example.com"))
    }
}
