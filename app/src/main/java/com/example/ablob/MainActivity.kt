package com.example.ablob

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import coil.Coil
import coil.ImageLoader
import coil.memory.MemoryCache
import coil.disk.DiskCache
import com.example.ablob.data.AzureBlobClient
import com.example.ablob.data.SecurityManager
import com.example.ablob.theme.ABlobTheme
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import java.util.Locale

class MainActivity : FragmentActivity() {

    lateinit var securityManager: SecurityManager
    lateinit var azureBlobClient: AzureBlobClient

    override fun attachBaseContext(newBase: Context) {
        val languageName = newBase.getSharedPreferences("ablob_settings", Context.MODE_PRIVATE)
            .getString("app_language", "SYSTEM") ?: "SYSTEM"
        val context = if (languageName != "SYSTEM") {
            val locale = if (languageName == "IT") Locale.forLanguageTag("it") else Locale.forLanguageTag("en")
            Locale.setDefault(locale)
            val config = Configuration(newBase.resources.configuration)
            config.setLocale(locale)
            newBase.createConfigurationContext(config)
        } else {
            newBase
        }
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        securityManager = SecurityManager(applicationContext)
        azureBlobClient = AzureBlobClient()

        // Configure Coil to use our authenticated client for Azure image requests
        // Configure Coil with cache optimization to prevent excessive memory/disk usage
        val imageLoader = ImageLoader.Builder(applicationContext)
            .okHttpClient(azureBlobClient.authenticatedClient)
            .memoryCache {
                MemoryCache.Builder(applicationContext)
                    .maxSizePercent(0.15) // limit RAM cache to 15% of available memory
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(applicationContext.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02) // limit to 2% of disk space
                    .maxSizeBytes(50 * 1024 * 1024) // 50MB maximum disk size
                    .build()
            }
            .build()
        Coil.setImageLoader(imageLoader)

        // Load existing configuration if present
        if (securityManager.hasConfig()) {
            azureBlobClient.updateConfig(securityManager.loadConfig())
        }

        enableEdgeToEdge()
        setContent {
            val currentTheme = remember { mutableStateOf(securityManager.getTheme()) }
            ABlobTheme(appTheme = currentTheme.value) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation(
                        activity = this,
                        securityManager = securityManager,
                        azureBlobClient = azureBlobClient,
                        currentTheme = currentTheme,
                        onLanguageChange = { recreate() }
                    )
                }
            }
        }
    }
}
