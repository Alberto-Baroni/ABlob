package com.example.ablob

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricPrompt
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.ablob.data.AzureBlobClient
import com.example.ablob.data.SecurityManager
import com.example.ablob.ui.browser.BrowserScreen
import com.example.ablob.ui.login.LoginScreen
import com.example.ablob.ui.settings.SettingsScreen

@Composable
fun MainNavigation(
    activity: FragmentActivity,
    securityManager: SecurityManager,
    azureBlobClient: AzureBlobClient
) {
    // Determine the initial route. If config exists, we start at Splash (biometric) or Browser. 
    // If not, we start at Login.
    val initialRoute = remember {
        if (securityManager.hasConfig()) Splash else Login
    }

    val backStack = rememberNavBackStack(initialRoute)

    NavDisplay(
        backStack = backStack,
        onBack = { 
            // Do not pop past the root screen
            if (backStack.size > 1) {
                backStack.removeLastOrNull() 
            }
        },
        entryProvider = entryProvider {
            // Splash Screen Route
            entry<Splash> {
                SplashScreen(
                    activity = activity,
                    securityManager = securityManager,
                    onAuthSuccess = {
                        // Clear Splash from stack and navigate to Browser
                        backStack.add(Browser(""))
                        // Remove Splash from history so back button exits the app
                        backStack.remove(Splash)
                    },
                    onAuthFallback = {
                        // Fallback to login screen
                        backStack.add(Login)
                        backStack.remove(Splash)
                    }
                )
            }

            // Login Screen Route
            entry<Login> {
                LoginScreen(
                    securityManager = securityManager,
                    azureBlobClient = azureBlobClient,
                    onLoginSuccess = {
                        backStack.add(Browser(""))
                        backStack.remove(Login)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Browser Screen Route
            entry<Browser> { key ->
                val currentPrefix = key.prefix
                BrowserScreen(
                    azureBlobClient = azureBlobClient,
                    prefix = currentPrefix,
                    onNavigateToFolder = { subFolderPrefix ->
                        backStack.add(Browser(subFolderPrefix))
                    },
                    onNavigateUp = {
                        backStack.removeLastOrNull()
                    },
                    onSettingsClick = {
                        backStack.add(Settings)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Settings Screen Route
            entry<Settings> {
                SettingsScreen(
                    securityManager = securityManager,
                    azureBlobClient = azureBlobClient,
                    onBackClick = {
                        backStack.removeLastOrNull()
                    },
                    onLogout = {
                        // Clear the stack and go to Login
                        backStack.add(Login)
                        // Clean up other screens from history
                        while (backStack.size > 1) {
                            backStack.remove(backStack.first())
                        }
                    },
                    onEditCredentials = {
                        backStack.add(Login)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    )
}

@Composable
fun SplashScreen(
    activity: FragmentActivity,
    securityManager: SecurityManager,
    onAuthSuccess: () -> Unit,
    onAuthFallback: () -> Unit
) {
    var authError by remember { mutableStateOf<String?>(null) }
    var showRetryButton by remember { mutableStateOf(false) }

    fun triggerBiometric() {
        authError = null
        showRetryButton = false
        
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                authError = errString.toString()
                showRetryButton = true
                
                // If user cancels or clicks use password/pin, we can fallback to Login screen
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED || 
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_CANCELED) {
                    onAuthFallback()
                }
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onAuthSuccess()
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                authError = "Impronta non riconosciuta. Riprova."
                showRetryButton = true
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Accesso a ABlob")
            .setSubtitle("Usa i dati biometrici per accedere")
            .setNegativeButtonText("Usa credenziali")
            .build()

        val biometricPrompt = BiometricPrompt(activity, executor, callback)
        biometricPrompt.authenticate(promptInfo)
    }

    LaunchedEffect(Unit) {
        if (securityManager.isBiometricEnabled()) {
            triggerBiometric()
        } else {
            // Biometrics is not enabled, directly go to browser
            onAuthSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.surface
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            // Elegant Splash Logo Card
            Card(
                modifier = Modifier.size(100.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "AB",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "ABlob Storage",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Caricamento sicuro in corso...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            Spacer(modifier = Modifier.height(48.dp))

            // Error display
            AnimatedVisibility(
                visible = authError != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = authError ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (showRetryButton) {
                Button(
                    onClick = { triggerBiometric() },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Icon(imageVector = Icons.Default.Fingerprint, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sblocca con Impronta", fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                TextButton(onClick = onAuthFallback) {
                    Text("Usa credenziali alternative")
                }
            } else {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
