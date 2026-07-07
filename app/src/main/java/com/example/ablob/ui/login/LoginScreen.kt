package com.example.ablob.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ablob.data.AuthType
import com.example.ablob.data.AzureBlobClient
import com.example.ablob.data.SecurityManager
import com.example.ablob.data.StorageConfig
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    securityManager: SecurityManager,
    azureBlobClient: AzureBlobClient,
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var accountName by remember { mutableStateOf("") }
    var containerName by remember { mutableStateOf("") }
    var authType by remember { mutableStateOf(AuthType.SHARED_KEY) }
    
    // Auth-specific inputs
    var accountKey by remember { mutableStateOf("") }
    var sasToken by remember { mutableStateOf("") }
    var tenantId by remember { mutableStateOf("") }
    var clientId by remember { mutableStateOf("") }
    var clientSecret by remember { mutableStateOf("") }
    
    var useBiometrics by remember { mutableStateOf(false) }
    
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isSecretVisible by remember { mutableStateOf(false) }
    
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val isBiometricAvailable = remember { securityManager.isBiometricHardwareAvailable() }

    // Pre-populate with existing configurations if any (e.g. for modification)
    LaunchedEffect(Unit) {
        val existing = securityManager.loadConfig()
        if (existing.isValid()) {
            accountName = existing.accountName
            containerName = existing.containerName
            authType = existing.authType
            accountKey = existing.accountKey
            sasToken = existing.sasToken
            tenantId = existing.tenantId
            clientId = existing.clientId
            clientSecret = existing.clientSecret
            useBiometrics = existing.useBiometrics
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(20.dp))
            
            // Premium Logo Card
            Card(
                modifier = Modifier.size(90.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "AB",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "ABlob Storage",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Text(
                text = "Inserisci i parametri di connessione ad Azure",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Configuration Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Common parameters
                    OutlinedTextField(
                        value = accountName,
                        onValueChange = { accountName = it },
                        label = { Text("Nome Account Storage") },
                        placeholder = { Text("es. ablobstorage") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    
                    OutlinedTextField(
                        value = containerName,
                        onValueChange = { containerName = it },
                        label = { Text("Nome Container") },
                        placeholder = { Text("es. documenti") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    
                    // Auth Selector Tab
                    Text(
                        text = "Modalità di Autenticazione",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    TabRow(
                        selectedTabIndex = authType.ordinal,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        containerColor = Color.Transparent
                    ) {
                        AuthType.values().forEach { type ->
                            Tab(
                                selected = authType == type,
                                onClick = { authType = type },
                                text = {
                                    Text(
                                        text = when (type) {
                                            AuthType.SHARED_KEY -> "Chiave"
                                            AuthType.SAS_TOKEN -> "SAS"
                                            AuthType.ENTRA_ID -> "Entra ID"
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // Specific inputs based on Auth Type
                    when (authType) {
                        AuthType.SHARED_KEY -> {
                            OutlinedTextField(
                                value = accountKey,
                                onValueChange = { accountKey = it },
                                label = { Text("Chiave Account (Storage Key)") },
                                singleLine = true,
                                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                        Icon(
                                            imageVector = if (isPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                            contentDescription = "Visualizza chiave"
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                        AuthType.SAS_TOKEN -> {
                            OutlinedTextField(
                                value = sasToken,
                                onValueChange = { sasToken = it },
                                label = { Text("Token SAS") },
                                placeholder = { Text("?sv=2020-08-04&ss=b&srt=co&sp=rwdl...") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                        AuthType.ENTRA_ID -> {
                            OutlinedTextField(
                                value = tenantId,
                                onValueChange = { tenantId = it },
                                label = { Text("Tenant ID (Azure AD)") },
                                placeholder = { Text("00000000-0000-0000-0000-000000000000") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = clientId,
                                onValueChange = { clientId = it },
                                label = { Text("Client ID (App Registration)") },
                                placeholder = { Text("00000000-0000-0000-0000-000000000000") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = clientSecret,
                                onValueChange = { clientSecret = it },
                                label = { Text("Client Secret") },
                                singleLine = true,
                                visualTransformation = if (isSecretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { isSecretVisible = !isSecretVisible }) {
                                        Icon(
                                            imageVector = if (isSecretVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                            contentDescription = "Visualizza segreto"
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                    
                    // Biometric Configuration Toggle
                    if (isBiometricAvailable) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Abilita Impronta Digitale",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Accedi rapidamente all'avvio",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = useBiometrics,
                                onCheckedChange = { useBiometrics = it }
                            )
                        }
                    }
                }
            }
            
            // Error Message display
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                errorMessage?.let { error ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Connect Button
            Button(
                onClick = {
                    val config = StorageConfig(
                        accountName = accountName.trim(),
                        containerName = containerName.trim(),
                        authType = authType,
                        accountKey = accountKey.trim(),
                        sasToken = sasToken.trim(),
                        tenantId = tenantId.trim(),
                        clientId = clientId.trim(),
                        clientSecret = clientSecret.trim(),
                        useBiometrics = useBiometrics
                    )
                    
                    if (!config.isValid()) {
                        errorMessage = "Per favore compila tutti i campi richiesti."
                        return@Button
                    }
                    
                    isLoading = true
                    errorMessage = null
                    
                    coroutineScope.launch {
                        try {
                            // Temporary update client configuration to test it
                            azureBlobClient.updateConfig(config)
                            
                            // Test connection by attempting to list 1 blob
                            azureBlobClient.listBlobs(prefix = "", maxResults = 1)
                            
                            // Success! Save config securely
                            securityManager.saveConfig(config)
                            onLoginSuccess()
                        } catch (e: Exception) {
                            e.printStackTrace()
                            errorMessage = "Connessione fallita: ${e.localizedMessage ?: "Errore sconosciuto"}"
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Text(
                        text = "Connetti",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
