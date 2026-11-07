package com.example.ablob.data

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class EntraAuthenticationDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun msalInitializesAndInstalledSigningRedirectResolvesToBrowserTab() {
        val auth = MsalEntraAuthentication(context)
        val redirect = auth.redirectUri()
        assertTrue(redirect.startsWith("msauth://${context.packageName}/"))
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(redirect))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addCategory(Intent.CATEGORY_DEFAULT)
            .setPackage(context.packageName)
        val matches = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        assertTrue(matches.any { it.activityInfo.name == "com.microsoft.identity.client.BrowserTabActivity" })

        // Initializes the real MSAL SDK and encrypted cache, without contacting a user account.
        val config = StorageConfig(
            accountName = "example", containerName = "test", authType = AuthType.ENTRA_ID,
            tenantId = "11111111-2222-3333-4444-555555555555",
            clientId = "11111111-2222-3333-4444-555555555555", entraAccountId = "nonexistent-test-account",
        )
        val error = assertThrows(IOException::class.java) { auth.getToken(config) }
        assertEquals("Microsoft session unavailable. Sign in again from Settings.", error.message)
    }

    @Suppress("DEPRECATION")
    @Test
    fun upgradeRemovesLegacySecretAndRequiresInteractiveSignIn() {
        val prefix = "entra-migration-test-"
        val isolated = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int) =
                context.getSharedPreferences(prefix + name, mode)
        }
        val prefs = EncryptedSharedPreferences.create(
            "ablob_secure_prefs", MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC), isolated,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        try {
            prefs.edit().clear()
                .putString("account_name", "example")
                .putString("container_name", "test")
                .putString("auth_type", AuthType.ENTRA_ID.name)
                .putString("tenant_id", "tenant.onmicrosoft.com")
                .putString("client_id", "11111111-2222-3333-4444-555555555555")
                .putString("client_secret", "legacy-test-secret")
                .commit()
            val security = SecurityManager(isolated)
            assertEquals("example", security.loadConfig().accountName)
            assertFalse(prefs.contains("client_secret"))
            assertFalse(security.hasConfig())
            security.saveConfig(security.loadConfig().copy(entraAccountId = "signed-in-user"))
            assertTrue(security.hasConfig())
            assertFalse(prefs.contains("client_secret"))
        } finally {
            context.deleteSharedPreferences(prefix + "ablob_secure_prefs")
            context.deleteSharedPreferences(prefix + "ablob_settings")
        }
    }
}
