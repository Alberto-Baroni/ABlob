package com.example.ablob.data

import android.content.Context
import android.content.SharedPreferences
import androidx.biometric.BiometricManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

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
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        return biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun isBiometricEnabled(): Boolean {
        return loadConfig().useBiometrics && isBiometricHardwareAvailable()
    }
}
