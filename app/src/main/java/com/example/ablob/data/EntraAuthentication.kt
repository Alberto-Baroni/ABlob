package com.example.ablob.data

import android.app.Activity
import java.io.IOException

interface EntraAuthentication {
    fun redirectUri(): String? = null
    fun getToken(config: StorageConfig): String
    suspend fun signIn(activity: Activity, config: StorageConfig): StorageConfig
    suspend fun signOut(config: StorageConfig)
}

internal object UnavailableEntraAuthentication : EntraAuthentication {
    override fun getToken(config: StorageConfig): String =
        throw IOException("Microsoft sign-in is unavailable. Reconnect from Settings.")
    override suspend fun signIn(activity: Activity, config: StorageConfig): StorageConfig =
        throw IOException("Microsoft sign-in is unavailable.")
    override suspend fun signOut(config: StorageConfig) = Unit
}
