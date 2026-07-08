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
import androidx.compose.ui.res.stringResource
import com.example.ablob.R
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    securityManager: SecurityManager,
    azureBlobClient: AzureBlobClient,
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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
                .padding(16.dp),
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
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = com.example.ablob.R.drawable.app_logo),
                    contentDescription = "Logo",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "ABlob Storage",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Text(
                text = stringResource(R.string.login_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Configuration Card Container
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                    // Common parameters
                    OutlinedTextField(
                        value = accountName,
                        onValueChange = { accountName = it },
                        label = { Text(stringResource(R.string.login_account_name)) },
                        placeholder = { Text(stringResource(R.string.login_account_name_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    
                    OutlinedTextField(
                        value = containerName,
                        onValueChange = { containerName = it },
                        label = { Text(stringResource(R.string.login_container_name)) },
                        placeholder = { Text(stringResource(R.string.login_container_name_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    
                    // Auth Selector Tab
                    Text(
                        text = stringResource(R.string.login_auth_mode),
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
                                            AuthType.SHARED_KEY -> stringResource(R.string.login_tab_key)
                                            AuthType.SAS_TOKEN -> stringResource(R.string.login_tab_sas)
                                            AuthType.ENTRA_ID -> stringResource(R.string.login_tab_entra)
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
                                label = { Text(stringResource(R.string.login_account_key)) },
                                singleLine = true,
                                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                        Icon(
                                            imageVector = if (isPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                            contentDescription = stringResource(R.string.login_view_key)
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
                                label = { Text(stringResource(R.string.login_sas_token)) },
                                placeholder = { Text(stringResource(R.string.login_sas_placeholder)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                        AuthType.ENTRA_ID -> {
                            OutlinedTextField(
                                value = tenantId,
                                onValueChange = { tenantId = it },
                                label = { Text(stringResource(R.string.login_tenant_id)) },
                                placeholder = { Text("00000000-0000-0000-0000-000000000000") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = clientId,
                                onValueChange = { clientId = it },
                                label = { Text(stringResource(R.string.login_client_id)) },
                                placeholder = { Text("00000000-0000-0000-0000-000000000000") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = clientSecret,
                                onValueChange = { clientSecret = it },
                                label = { Text(stringResource(R.string.login_client_secret)) },
                                singleLine = true,
                                visualTransformation = if (isSecretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { isSecretVisible = !isSecretVisible }) {
                                        Icon(
                                            imageVector = if (isSecretVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                            contentDescription = stringResource(R.string.login_view_secret)
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
                                    text = stringResource(R.string.login_enable_biometric),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stringResource(R.string.login_enable_biometric_sub),
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
                        errorMessage = context.getString(R.string.login_error_fields_required)
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
                            val details = e.localizedMessage ?: context.getString(R.string.login_error_unknown)
                            errorMessage = context.getString(R.string.login_error_connection_failed, details)
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
                        text = stringResource(R.string.login_btn_connect),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
