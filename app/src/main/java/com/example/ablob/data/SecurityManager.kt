package com.example.ablob.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class SecurityManager(private val context: Context) {

    private val sharedPrefs: SharedPreferences by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "ablob_secure_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val plainPrefs: SharedPreferences by lazy {
        context.getSharedPreferences("ablob_settings", Context.MODE_PRIVATE)
    }

    fun saveConfig(config: StorageConfig) {
        sharedPrefs.edit().apply {
            putString("account_name", config.accountName)
            putString("container_name", config.containerName)
            putString("auth_type", config.authType.name)
            putString("account_key", config.accountKey)
            putString("sas_token", config.sasToken)
            putString("tenant_id", config.tenantId)
            putString("client_id", config.clientId)
            putString("client_secret", config.clientSecret)
            putBoolean("use_biometrics", config.useBiometrics)
            apply()
        }
    }

    fun loadConfig(): StorageConfig {
        val accountName = sharedPrefs.getString("account_name", "") ?: ""
        val containerName = sharedPrefs.getString("container_name", "") ?: ""
        val authTypeName = sharedPrefs.getString("auth_type", AuthType.SHARED_KEY.name) ?: AuthType.SHARED_KEY.name
        val authType = try { AuthType.valueOf(authTypeName) } catch (e: Exception) { AuthType.SHARED_KEY }
        val accountKey = sharedPrefs.getString("account_key", "") ?: ""
        val sasToken = sharedPrefs.getString("sas_token", "") ?: ""
        val tenantId = sharedPrefs.getString("tenant_id", "") ?: ""
        val clientId = sharedPrefs.getString("client_id", "") ?: ""
        val clientSecret = sharedPrefs.getString("client_secret", "") ?: ""
        val useBiometrics = sharedPrefs.getBoolean("use_biometrics", false)

        return StorageConfig(
            accountName = accountName,
            containerName = containerName,
            authType = authType,
            accountKey = accountKey,
            sasToken = sasToken,
            tenantId = tenantId,
            clientId = clientId,
            clientSecret = clientSecret,
            useBiometrics = useBiometrics
        )
    }

    fun clearConfig() {
        sharedPrefs.edit().clear().apply()
    }

    fun hasConfig(): Boolean {
        val config = loadConfig()
        return config.isValid()
    }

    fun isBiometricHardwareAvailable(): Boolean {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate(getAllowedAuthenticators()) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun getAllowedAuthenticators(): Int {
        val biometricStrong = BiometricManager.Authenticators.BIOMETRIC_STRONG
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            biometricStrong or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            biometricStrong
        }
    }

    fun isBiometricEnabled(): Boolean {
        return loadConfig().useBiometrics && isBiometricHardwareAvailable()
    }

    fun createBiometricCryptoObject(): BiometricPrompt.CryptoObject? {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val secretKey = if (keyStore.containsAlias(BIOMETRIC_KEY_ALIAS)) {
                val entry = keyStore.getEntry(BIOMETRIC_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
                entry?.secretKey ?: generateBiometricKey()
            } else {
                generateBiometricKey()
            }

            val cipher = Cipher.getInstance(
                "${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_CBC}/${KeyProperties.ENCRYPTION_PADDING_PKCS7}"
            )
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            BiometricPrompt.CryptoObject(cipher)
        } catch (e: KeyPermanentlyInvalidatedException) {
            deleteBiometricKey()
            try {
                val secretKey = generateBiometricKey()
                val cipher = Cipher.getInstance(
                    "${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_CBC}/${KeyProperties.ENCRYPTION_PADDING_PKCS7}"
                )
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                BiometricPrompt.CryptoObject(cipher)
            } catch (_: Exception) {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun generateBiometricKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        val builder = KeyGenParameterSpec.Builder(
            BIOMETRIC_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
            .setUserAuthenticationRequired(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        }

        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }

    private fun deleteBiometricKey() {
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keyStore.containsAlias(BIOMETRIC_KEY_ALIAS)) {
                keyStore.deleteEntry(BIOMETRIC_KEY_ALIAS)
            }
        } catch (_: Exception) {}
    }

    companion object {
        private const val BIOMETRIC_KEY_ALIAS = "ablob_biometric_key"
    }

    enum class AppTheme { SYSTEM, LIGHT, DARK }

    fun getTheme(): AppTheme {
        val name = plainPrefs.getString("app_theme", null)
            ?: sharedPrefs.getString("app_theme", AppTheme.SYSTEM.name)
            ?: AppTheme.SYSTEM.name
        return try { AppTheme.valueOf(name) } catch (e: Exception) { AppTheme.SYSTEM }
    }

    fun setTheme(theme: AppTheme) {
        plainPrefs.edit().putString("app_theme", theme.name).apply()
    }

    enum class AppLanguage { SYSTEM, IT, EN }

    fun getLanguage(): AppLanguage {
        val name = plainPrefs.getString("app_language", AppLanguage.SYSTEM.name) ?: AppLanguage.SYSTEM.name
        return try { AppLanguage.valueOf(name) } catch (e: Exception) { AppLanguage.SYSTEM }
    }

    fun setLanguage(language: AppLanguage) {
        plainPrefs.edit().putString("app_language", language.name).apply()
    }

    fun getFavorites(): Set<String> {
        return sharedPrefs.getStringSet("favorite_blobs", emptySet()) ?: emptySet()
    }

    fun toggleFavorite(path: String) {
        val current = getFavorites().toMutableSet()
        if (current.contains(path)) {
            current.remove(path)
        } else {
            current.add(path)
        }
        sharedPrefs.edit().putStringSet("favorite_blobs", current).apply()
    }

    fun isFavorite(path: String): Boolean {
        return getFavorites().contains(path)
    }
}
