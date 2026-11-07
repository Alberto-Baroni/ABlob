package com.example.ablob.data

import android.app.Activity
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class EntraAuthenticationTest {
    private val config = StorageConfig(
        accountName = "example", containerName = "test", authType = AuthType.ENTRA_ID,
        tenantId = "tenant.onmicrosoft.com", clientId = "11111111-2222-3333-4444-555555555555",
        entraAccountId = "signed-in-account",
    )

    private fun authentication(token: (StorageConfig) -> String) = object : EntraAuthentication {
        override fun getToken(config: StorageConfig) = token(config)
        override suspend fun signIn(activity: Activity, config: StorageConfig) = config
        override suspend fun signOut(config: StorageConfig) = Unit
    }

    @Test
    fun generatesBrowserPublicClientConfigurationForUsersOwnTenantAndClient() {
        val redirect = "msauth://com.alkaloon.ablob/test%2Bsignature%3D"
        val json = JSONObject(EntraPublicClientConfig.json(config, redirect))
        assertEquals(config.clientId, json.getString("client_id"))
        assertEquals(redirect, json.getString("redirect_uri"))
        assertEquals("BROWSER", json.getString("authorization_user_agent"))
        assertFalse(json.getBoolean("broker_redirect_uri_registered"))
        assertFalse(json.has("client_secret"))
        assertEquals(config.tenantId, json.getJSONArray("authorities").getJSONObject(0)
            .getJSONObject("audience").getString("tenant_id"))
        assertEquals(listOf("https://storage.azure.com/user_impersonation"), EntraPublicClientConfig.scopes)
        assertTrue(config.copy(entraAccountId = "").isValid())
    }

    @Test
    fun rejectsTenantPathInjectionAndInvalidClientIds() {
        for (tenant in listOf("", "tenant/other", "tenant?param=value", "tenant#fragment")) {
            assertThrows(IllegalArgumentException::class.java) { EntraPublicClientConfig.authority(tenant) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            EntraPublicClientConfig.json(config.copy(clientId = "invalid"), "msauth://example/hash")
        }
        assertEquals("https://login.microsoftonline.com/tenant.onmicrosoft.com", EntraPublicClientConfig.authority(config.tenantId))
    }

    private fun client(storage: AzureBlobClient, verify: (Request) -> Unit): OkHttpClient =
        storage.authenticatedClient.newBuilder().addInterceptor { chain ->
            verify(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body("".toResponseBody()).build()
        }.build()

    @Test
    fun attachesDelegatedBearerForConfiguredAccount() {
        val storage = AzureBlobClient(authentication {
            assertEquals("signed-in-account", it.entraAccountId)
            "delegated-token"
        })
        storage.updateConfig(config)
        val client = client(storage) { assertEquals("Bearer delegated-token", it.header("Authorization")) }
        client.newCall(Request.Builder().url("https://example.blob.core.windows.net/test").build()).execute().close()
    }

    @Test
    fun authenticationFailureStopsBlobRequest() {
        val storage = AzureBlobClient(authentication { throw IOException("Sign in again") })
        storage.updateConfig(config)
        val client = client(storage) { fail("Unauthenticated request must not be sent") }
        assertThrows(IOException::class.java) {
            client.newCall(Request.Builder().url("https://example.blob.core.windows.net/test").build()).execute()
        }
    }

    @Test
    fun neverAttachesCredentialsToOtherAccountsOrLookalikeHostsOrHttp() {
        val storage = AzureBlobClient(authentication { fail("Must not request a token"); "" })
        storage.updateConfig(config)
        val client = client(storage) { assertNull(it.header("Authorization")) }
        for (url in listOf(
            "https://example.blob.core.windows.net.attacker.test/test",
            "https://another.blob.core.windows.net/test",
            "http://example.blob.core.windows.net/test",
        )) client.newCall(Request.Builder().url(url).build()).execute().close()
    }

    @Test
    fun requestsTokenForNewConnectionAfterConfigChange() {
        var expected = config
        val storage = AzureBlobClient(authentication { assertEquals(expected, it); "token-${it.entraAccountId}" })
        val client = client(storage) { assertEquals("Bearer token-${expected.entraAccountId}", it.header("Authorization")) }
        for (next in listOf(config, config.copy(entraAccountId = "other-user", tenantId = "other.onmicrosoft.com"))) {
            expected = next
            storage.updateConfig(next)
            client.newCall(Request.Builder().url("https://example.blob.core.windows.net/test").build()).execute().close()
        }
    }
}
