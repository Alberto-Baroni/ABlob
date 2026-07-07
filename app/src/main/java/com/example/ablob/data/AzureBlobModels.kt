package com.example.ablob.data

import kotlinx.serialization.Serializable

@Serializable
enum class AuthType {
    SHARED_KEY,
    SAS_TOKEN,
    ENTRA_ID
}

@Serializable
data class StorageConfig(
    val accountName: String = "",
    val containerName: String = "",
    val authType: AuthType = AuthType.SHARED_KEY,
    val accountKey: String = "",
    val sasToken: String = "",
    val tenantId: String = "",
    val clientId: String = "",
    val clientSecret: String = "",
    val useBiometrics: Boolean = false
) {
    fun isValid(): Boolean {
        if (accountName.isBlank() || containerName.isBlank()) return false
        return when (authType) {
            AuthType.SHARED_KEY -> accountKey.isNotBlank()
            AuthType.SAS_TOKEN -> sasToken.isNotBlank()
            AuthType.ENTRA_ID -> tenantId.isNotBlank() && clientId.isNotBlank() && clientSecret.isNotBlank()
        }
    }
}

sealed interface BlobItem {
    val name: String
    val path: String

    data class Folder(
        override val name: String,
        override val path: String
    ) : BlobItem

    data class File(
        override val name: String,
        override val path: String,
        val size: Long,
        val lastModified: String,
        val contentType: String
    ) : BlobItem
}
