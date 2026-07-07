package com.example.ablob

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
import com.example.ablob.data.AzureBlobClient
import com.example.ablob.data.SecurityManager
import com.example.ablob.theme.ABlobTheme

class MainActivity : FragmentActivity() {

    lateinit var securityManager: SecurityManager
    lateinit var azureBlobClient: AzureBlobClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        securityManager = SecurityManager(applicationContext)
        azureBlobClient = AzureBlobClient()

        // Configure Coil to use our authenticated client for Azure image requests
        val imageLoader = ImageLoader.Builder(applicationContext)
            .okHttpClient(azureBlobClient.authenticatedClient)
            .build()
        Coil.setImageLoader(imageLoader)

        // Load existing configuration if present
        if (securityManager.hasConfig()) {
            azureBlobClient.updateConfig(securityManager.loadConfig())
        }

        enableEdgeToEdge()
        setContent {
            ABlobTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation(
                        activity = this,
                        securityManager = securityManager,
                        azureBlobClient = azureBlobClient
                    )
                }
            }
        }
    }
}
