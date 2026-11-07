package com.example.ablob.data

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import com.microsoft.identity.client.AcquireTokenParameters
import com.microsoft.identity.client.AcquireTokenSilentParameters
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IMultipleAccountPublicClientApplication
import com.microsoft.identity.client.Prompt
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.exception.MsalException
import com.microsoft.identity.client.exception.MsalUiRequiredException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Delegated browser authentication. MSAL owns PKCE, state, token cache and refresh. */
class MsalEntraAuthentication(context: Context) : EntraAuthentication {
    private val context = context.applicationContext
    private val applications = mutableMapOf<Pair<String, String>, IMultipleAccountPublicClientApplication>()

    @Synchronized
    private fun application(config: StorageConfig): IMultipleAccountPublicClientApplication {
        val key = config.tenantId to config.clientId
        return applications.getOrPut(key) {
            val file = File.createTempFile("msal-", ".json", context.cacheDir)
            try {
                file.writeText(EntraPublicClientConfig.json(config, redirectUri()))
                PublicClientApplication.createMultipleAccountPublicClientApplication(context, file)
            } catch (e: MsalException) {
                throw authError(e)
            } finally {
                file.delete()
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun redirectUri(): String {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners
        } else {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
        }
        val signature = signatures?.firstOrNull() ?: throw IOException("App signing certificate unavailable.")
        val hash = Base64.encodeToString(MessageDigest.getInstance("SHA-1").digest(signature.toByteArray()), Base64.NO_WRAP)
        // The URI contains the signing hash of the installed app, including Play App Signing builds.
        return "msauth://${context.packageName}/" + java.net.URLEncoder.encode(hash, "UTF-8")
    }

    override suspend fun signIn(activity: Activity, config: StorageConfig): StorageConfig {
        val app = withContext(Dispatchers.IO) { application(config) }
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val parameters = AcquireTokenParameters.Builder()
                    .startAuthorizationFromActivity(activity)
                    .withScopes(EntraPublicClientConfig.scopes)
                    .fromAuthority(EntraPublicClientConfig.authority(config.tenantId))
                    .withPrompt(Prompt.SELECT_ACCOUNT)
                    .withCallback(object : AuthenticationCallback {
                        override fun onSuccess(result: IAuthenticationResult) {
                            if (continuation.isActive) continuation.resume(config.copy(entraAccountId = result.account.id))
                        }
                        override fun onError(exception: MsalException) {
                            if (continuation.isActive) continuation.resumeWithException(authError(exception))
                        }
                        override fun onCancel() {
                            if (continuation.isActive) continuation.resumeWithException(IOException("Microsoft sign-in cancelled."))
                        }
                    })
                    .build()
                app.acquireToken(parameters)
            }
        }
    }

    override fun getToken(config: StorageConfig): String {
        if (config.entraAccountId.isBlank()) throw IOException("Sign in with Microsoft from Settings to connect.")
        try {
            val app = application(config)
            val account = app.getAccount(config.entraAccountId)
                ?: throw IOException("Microsoft session unavailable. Sign in again from Settings.")
            val parameters = AcquireTokenSilentParameters.Builder()
                .forAccount(account)
                .fromAuthority(EntraPublicClientConfig.authority(config.tenantId))
                .withScopes(EntraPublicClientConfig.scopes)
                .build()
            return app.acquireTokenSilent(parameters).accessToken
        } catch (_: MsalUiRequiredException) {
            throw IOException("Microsoft requires sign-in or consent. Reconnect from Settings.")
        } catch (e: MsalException) {
            throw authError(e)
        }
    }

    override suspend fun signOut(config: StorageConfig) = withContext(Dispatchers.IO) {
        if (config.entraAccountId.isNotBlank()) {
            try {
                val app = application(config)
                app.getAccount(config.entraAccountId)?.let {
                    if (!app.removeAccount(it)) throw IOException("Microsoft sign-out failed. Please retry.")
                }
            } catch (e: MsalException) {
                throw authError(e)
            }
        }
        Unit
    }

    private fun authError(error: MsalException): IOException {
        // SDK error codes are useful for diagnosis; raw responses may contain personal data.
        val code = error.errorCode.orEmpty().filter { it.isLetterOrDigit() || it == '_' }.take(80)
        return IOException("Microsoft Entra sign-in failed ($code). Check tenant, client ID and Android redirect URI.")
    }
}

internal object EntraPublicClientConfig {
    val scopes = listOf("https://storage.azure.com/user_impersonation")

    fun authority(tenant: String): String {
        require(tenant.matches(Regex("[A-Za-z0-9][A-Za-z0-9.-]{0,252}"))) { "Invalid tenant ID or domain." }
        return "https://login.microsoftonline.com/".toHttpUrl().newBuilder().addPathSegment(tenant).build().toString()
    }

    fun json(config: StorageConfig, redirectUri: String): String {
        authority(config.tenantId)
        require(config.clientId.matches(Regex("[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}"))) { "Invalid Entra client ID." }
        return JSONObject()
            .put("client_id", config.clientId)
            .put("redirect_uri", redirectUri)
            .put("account_mode", "MULTIPLE")
            .put("authorization_user_agent", "BROWSER")
            .put("broker_redirect_uri_registered", false)
            .put("authorities", JSONArray().put(JSONObject()
                .put("type", "AAD")
                .put("audience", JSONObject().put("type", "AzureADMyOrg").put("tenant_id", config.tenantId))))
            .toString()
    }
}
