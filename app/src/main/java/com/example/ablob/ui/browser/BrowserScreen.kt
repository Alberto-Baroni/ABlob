package com.example.ablob.ui.browser

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import androidx.compose.ui.res.stringResource
import com.example.ablob.R
import com.example.ablob.data.AzureBlobClient
import com.example.ablob.data.BlobItem
import com.example.ablob.data.SecurityManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    azureBlobClient: AzureBlobClient,
    securityManager: SecurityManager,
    prefix: String,
    onNavigateToFolder: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onSettingsClick: () -> Unit,
    onNavigateToViewer: (path: String, name: String, contentType: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var loadedItems by remember { mutableStateOf<List<BlobItem>>(emptyList()) }
    var nextMarker by remember { mutableStateOf("") }
    var isLoadingInitial by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    var selectedTab by remember { mutableStateOf(0) } // 0 = Documenti, 1 = Immagini
    var searchQuery by remember { mutableStateOf("") }
    
    // Lightbox state
    var activeLightboxImage by remember { mutableStateOf<BlobItem.File?>(null) }
    
    // External preview loader overlay
    var isOpeningExternalFile by remember { mutableStateOf<String?>(null) }

    // Fetch initial list of items
    fun fetchInitial() {
        coroutineScope.launch {
            isLoadingInitial = true
            errorMessage = null
            try {
                val result = azureBlobClient.listBlobs(prefix = prefix, maxResults = 10)
                loadedItems = result.items
                nextMarker = result.nextMarker
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "Impossibile caricare i file: ${e.localizedMessage ?: "Errore di rete"}"
            } finally {
                isLoadingInitial = false
            }
        }
    }

    // Action overlay state
    var isProcessingAction by remember { mutableStateOf<String?>(null) }

    // Speed dial add menu / folder creation dialog states
    var showAddMenu by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    
    // Deletion states
    var itemToDelete by remember { mutableStateOf<BlobItem?>(null) }

    // Rename states
    var showRenameDialog by remember { mutableStateOf(false) }
    var itemToRename by remember { mutableStateOf<BlobItem?>(null) }
    var renameInputName by remember { mutableStateOf("") }

    // Favorites tick to trigger recomposition
    var favoritesTick by remember { mutableStateOf(0) }

    val multiFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch {
                val totalCount = uris.size
                uris.forEachIndexed { index, uri ->
                    val fileInfo = getFileInfoFromUri(context, uri)
                    if (fileInfo != null) {
                        isProcessingAction = context.getString(R.string.action_upload_progress, index + 1, totalCount, fileInfo.name, 0)
                        
                        val uploadSuccess = withContext(Dispatchers.IO) {
                            var tempFile: File? = null
                            try {
                                val prefixName = "up_${System.currentTimeMillis()}_"
                                val ext = fileInfo.name.substringAfterLast('.', "tmp")
                                tempFile = File.createTempFile(prefixName, ".$ext", context.cacheDir)
                                
                                context.contentResolver.openInputStream(uri)?.use { input ->
                                    FileOutputStream(tempFile).use { output ->
                                        input.copyTo(output)
                                    }
                                } ?: throw IOException("ContentResolver failed to open stream")
                                
                                val targetBlobPath = "$prefix${fileInfo.name}"
                                
                                azureBlobClient.uploadBlob(targetBlobPath, tempFile, fileInfo.mimeType) { bytesWritten, totalBytes ->
                                    val percent = if (totalBytes > 0) (bytesWritten * 100 / totalBytes).toInt() else 0
                                    isProcessingAction = context.getString(R.string.action_upload_progress, index + 1, totalCount, fileInfo.name, percent)
                                }
                                true
                            } catch (e: Exception) {
                                e.printStackTrace()
                                false
                            } finally {
                                tempFile?.delete()
                            }
                        }
                        
                        if (!uploadSuccess) {
                            Toast.makeText(context, context.getString(R.string.toast_upload_error, fileInfo.name), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                
                isProcessingAction = null
                Toast.makeText(context, context.getString(R.string.toast_uploads_completed), Toast.LENGTH_SHORT).show()
                fetchInitial()
            }
        }
    }

    fun createFolder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        
        val cleanName = trimmed.replace("^/+|/+$".toRegex(), "")
        if (cleanName.isEmpty()) return
        
        isProcessingAction = context.getString(R.string.action_creating_folder, cleanName)
        coroutineScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    val folderBlobPath = "$prefix$cleanName/"
                    azureBlobClient.createVirtualFolder(folderBlobPath)
                    true
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
            
            isProcessingAction = null
            if (success) {
                Toast.makeText(context, context.getString(R.string.toast_folder_created), Toast.LENGTH_SHORT).show()
                fetchInitial()
            } else {
                Toast.makeText(context, context.getString(R.string.toast_folder_create_error), Toast.LENGTH_LONG).show()
            }
        }
    }

    fun deleteItem(item: BlobItem) {
        coroutineScope.launch {
            isProcessingAction = context.getString(R.string.action_deleting_item, item.name)
            
            if (item is BlobItem.Folder) {
                val isEmpty = withContext(Dispatchers.IO) {
                    try {
                        val result = azureBlobClient.listBlobs(prefix = item.path, delimiter = "/", maxResults = 1)
                        result.items.isEmpty()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        false
                    }
                }
                
                if (!isEmpty) {
                    isProcessingAction = null
                    Toast.makeText(context, context.getString(R.string.toast_delete_folder_not_empty), Toast.LENGTH_LONG).show()
                    return@launch
                }
            }
            
            val success = withContext(Dispatchers.IO) {
                try {
                    azureBlobClient.deleteBlob(item.path)
                    true
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
            
            isProcessingAction = null
            if (success) {
                if (securityManager.isFavorite(item.path)) {
                    securityManager.toggleFavorite(item.path)
                    favoritesTick++
                }
                Toast.makeText(context, context.getString(R.string.toast_deleted_success), Toast.LENGTH_SHORT).show()
                fetchInitial()
            } else {
                Toast.makeText(context, context.getString(R.string.toast_delete_error), Toast.LENGTH_LONG).show()
            }
        }
    }

    fun renameItem(item: BlobItem, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed == item.name) return
        
        coroutineScope.launch {
            isProcessingAction = context.getString(R.string.action_renaming_item, item.name, trimmed)
            
            val newPath = if (item is BlobItem.Folder) {
                val parentPrefix = item.path.substringBeforeLast(item.name + "/")
                "$parentPrefix$trimmed/"
            } else {
                val parentPrefix = item.path.substringBeforeLast(item.name)
                "$parentPrefix$trimmed"
            }
            
            val success = withContext(Dispatchers.IO) {
                try {
                    azureBlobClient.renameBlob(item.path, newPath)
                    true
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
            
            isProcessingAction = null
            if (success) {
                if (securityManager.isFavorite(item.path)) {
                    securityManager.toggleFavorite(item.path)
                    securityManager.toggleFavorite(newPath)
                    favoritesTick++
                }
                Toast.makeText(context, context.getString(R.string.toast_rename_success), Toast.LENGTH_SHORT).show()
                fetchInitial()
            } else {
                Toast.makeText(context, context.getString(R.string.toast_rename_error), Toast.LENGTH_LONG).show()
            }
        }
    }

    // Load next page
    fun fetchNextPage() {
        if (nextMarker.isEmpty() || isLoadingMore) return
        coroutineScope.launch {
            isLoadingMore = true
            try {
                val result = azureBlobClient.listBlobs(prefix = prefix, maxResults = 10, marker = nextMarker)
                loadedItems = loadedItems + result.items
                nextMarker = result.nextMarker
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, context.getString(R.string.toast_load_error, e.localizedMessage ?: ""), Toast.LENGTH_SHORT).show()
            } finally {
                isLoadingMore = false
            }
        }
    }

    LaunchedEffect(prefix) {
        fetchInitial()
    }

    // Categorize loaded items
    val documents = remember(loadedItems) {
        loadedItems.filter {
            it is BlobItem.Folder || (it is BlobItem.File && !isImageFile(it.name, it.contentType))
        }
    }

    val images = remember(loadedItems) {
        loadedItems.filterIsInstance<BlobItem.File>().filter {
            isImageFile(it.name, it.contentType)
        }
    }

    // Search filter
    val filteredDocuments = remember(documents, searchQuery) {
        if (searchQuery.isBlank()) {
            documents
        } else {
            documents.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    val filteredImages = remember(images, searchQuery) {
        if (searchQuery.isBlank()) {
            images
        } else {
            images.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    val favoriteItems = remember(loadedItems, searchQuery, favoritesTick) {
        loadedItems.filter { item ->
            val nameMatches = item.name.contains(searchQuery, ignoreCase = true)
            nameMatches && securityManager.isFavorite(item.path)
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = stringResource(R.string.browser_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                            Text(
                                text = stringResource(R.string.browser_subtitle, azureBlobClient.getCurrentConfig().containerName),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        if (prefix.isNotEmpty()) {
                            IconButton(onClick = onNavigateUp) {
                                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = stringResource(R.string.browser_nav_up))
                            }
                        } else {
                            IconButton(onClick = { /* No-op, root */ }, enabled = false) {
                                Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null)
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = onSettingsClick) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = stringResource(R.string.browser_settings))
                        }
                    }
                )
                
                // Breadcrumbs path indicator
                BreadcrumbsView(prefix = prefix, onSegmentClick = { targetPrefix ->
                    onNavigateToFolder(targetPrefix)
                })

                // Tab Selector
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.browser_tab_docs, filteredDocuments.size))
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.browser_tab_images, filteredImages.size))
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.browser_tab_favorites, favoriteItems.size))
                            }
                        }
                    )
                }

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.browser_search_placeholder)) },
                    leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = stringResource(R.string.browser_btn_clear))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddMenu = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(R.string.browser_btn_add))
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isLoadingInitial) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (errorMessage != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(60.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { fetchInitial() }) {
                        Text(stringResource(R.string.browser_btn_retry))
                    }
                }
            } else if (selectedTab == 0 && filteredDocuments.isEmpty()) {
                EmptyStateView(message = stringResource(R.string.empty_docs))
            } else if (selectedTab == 1 && filteredImages.isEmpty()) {
                EmptyStateView(message = stringResource(R.string.empty_images))
            } else if (selectedTab == 2 && favoriteItems.isEmpty()) {
                EmptyStateView(message = stringResource(R.string.empty_favorites))
            } else {
                if (selectedTab == 0) {
                    // Documents and Folders list
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredDocuments) { item ->
                            DocumentRow(
                                item = item,
                                isFavorite = securityManager.isFavorite(item.path),
                                onFavoriteToggle = {
                                    securityManager.toggleFavorite(item.path)
                                    favoritesTick++
                                },
                                onClick = {
                                    if (item is BlobItem.Folder) {
                                        onNavigateToFolder(item.path)
                                    } else if (item is BlobItem.File) {
                                        if (isInternalDocument(item.name, item.contentType)) {
                                            onNavigateToViewer(item.path, item.name, item.contentType)
                                        } else {
                                            openFileExternally(context, azureBlobClient, item, coroutineScope) { loadingName ->
                                                isOpeningExternalFile = loadingName
                                            }
                                        }
                                    }
                                },
                                onDownload = {
                                    if (item is BlobItem.File) {
                                        downloadFile(context, azureBlobClient, item, coroutineScope)
                                    }
                                },
                                onDelete = {
                                    itemToDelete = item
                                },
                                onRename = {
                                    itemToRename = item
                                    renameInputName = item.name
                                    showRenameDialog = true
                                },
                                searchQuery = searchQuery
                            )
                        }

                        // Load More Item
                        if (nextMarker.isNotEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isLoadingMore) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    } else {
                                        OutlinedButton(onClick = { fetchNextPage() }) {
                                            Text("Carica altri 10")
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (selectedTab == 1) {
                    // Images Grid Gallery
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredImages) { imageFile ->
                            ImageGalleryCard(
                                imageFile = imageFile,
                                azureBlobClient = azureBlobClient,
                                isFavorite = securityManager.isFavorite(imageFile.path),
                                onFavoriteToggle = {
                                    securityManager.toggleFavorite(imageFile.path)
                                    favoritesTick++
                                },
                                onClick = {
                                    activeLightboxImage = imageFile
                                },
                                onDownload = {
                                    downloadFile(context, azureBlobClient, imageFile, coroutineScope)
                                },
                                onDelete = {
                                    itemToDelete = imageFile
                                },
                                onRename = {
                                    itemToRename = imageFile
                                    renameInputName = imageFile.name
                                    showRenameDialog = true
                                },
                                searchQuery = searchQuery
                            )
                        }

                        // Load More Item in Grid
                        if (nextMarker.isNotEmpty()) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isLoadingMore) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    } else {
                                        OutlinedButton(onClick = { fetchNextPage() }) {
                                            Text("Carica altri 10")
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Favorites Tab combined view
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(favoriteItems) { item ->
                            DocumentRow(
                                item = item,
                                isFavorite = true,
                                onFavoriteToggle = {
                                    securityManager.toggleFavorite(item.path)
                                    favoritesTick++
                                },
                                onClick = {
                                    if (item is BlobItem.Folder) {
                                        onNavigateToFolder(item.path)
                                    } else if (item is BlobItem.File) {
                                        if (isInternalDocument(item.name, item.contentType)) {
                                            onNavigateToViewer(item.path, item.name, item.contentType)
                                        } else {
                                            openFileExternally(context, azureBlobClient, item, coroutineScope) { loadingName ->
                                                isOpeningExternalFile = loadingName
                                            }
                                        }
                                    }
                                },
                                onDownload = {
                                    if (item is BlobItem.File) {
                                        downloadFile(context, azureBlobClient, item, coroutineScope)
                                    }
                                },
                                onDelete = {
                                    itemToDelete = item
                                },
                                onRename = {
                                    itemToRename = item
                                    renameInputName = item.name
                                    showRenameDialog = true
                                },
                                searchQuery = searchQuery
                            )
                        }
                    }
                }
            }
        }
    }

    // Lightbox / Zoom Dialog
    activeLightboxImage?.let { imageFile ->
        LightboxDialog(
            imageFile = imageFile,
            azureBlobClient = azureBlobClient,
            onDismiss = { activeLightboxImage = null },
            onDownload = {
                downloadFile(context, azureBlobClient, imageFile, coroutineScope)
            }
        )
    }

    // Progress Dialog Overlay for External Opening
    if (isOpeningExternalFile != null) {
        Dialog(
            onDismissRequest = { /* Prevent dismiss during download */ },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            Card(
                modifier = Modifier.width(280.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.action_loading_external),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = isOpeningExternalFile ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    // Choice Dialog: Upload File or Create Folder
    if (showAddMenu) {
        AlertDialog(
            onDismissRequest = { showAddMenu = false },
            title = { Text(stringResource(R.string.dialog_add_title)) },
            text = { Text(stringResource(R.string.dialog_add_text)) },
            confirmButton = {
                Button(
                    onClick = {
                        showAddMenu = false
                        newFolderName = ""
                        showCreateFolderDialog = true
                    }
                ) {
                    Text(stringResource(R.string.dialog_add_folder))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddMenu = false
                        multiFilePickerLauncher.launch("*/*")
                    }
                ) {
                    Text(stringResource(R.string.dialog_add_upload))
                }
            }
        )
    }

    // Input Dialog: Create Folder Name
    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text(stringResource(R.string.dialog_folder_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.dialog_folder_text))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = { Text(stringResource(R.string.dialog_folder_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCreateFolderDialog = false
                        createFolder(newFolderName)
                    },
                    enabled = newFolderName.trim().isNotEmpty()
                ) {
                    Text(stringResource(R.string.dialog_btn_create))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
                    Text(stringResource(R.string.dialog_btn_cancel))
                }
            }
        )
    }

    // Confirm Dialog: Delete File or Folder
    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text(if (item is BlobItem.Folder) stringResource(R.string.dialog_delete_folder_title) else stringResource(R.string.dialog_delete_file_title)) },
            text = {
                Text(
                    if (item is BlobItem.Folder) {
                        stringResource(R.string.dialog_delete_folder_confirm, item.name)
                    } else {
                        stringResource(R.string.dialog_delete_file_confirm, item.name)
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        itemToDelete = null
                        deleteItem(item)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.dialog_btn_delete), color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text(stringResource(R.string.dialog_btn_cancel))
                }
            }
        )
    }

    // Input Dialog: Rename File or Folder
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(stringResource(R.string.dialog_rename_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.dialog_rename_text, itemToRename?.name ?: ""))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = renameInputName,
                        onValueChange = { renameInputName = it },
                        label = { Text(stringResource(R.string.dialog_rename_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRenameDialog = false
                        itemToRename?.let { renameItem(it, renameInputName) }
                    },
                    enabled = renameInputName.trim().isNotEmpty() && renameInputName.trim() != itemToRename?.name
                ) {
                    Text(stringResource(R.string.dialog_btn_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(stringResource(R.string.dialog_btn_cancel))
                }
            }
        )
    }

    // Unified Action Progress Dialog Overlay
    if (isProcessingAction != null) {
        Dialog(
            onDismissRequest = { /* Prevent dismiss during action */ },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            Card(
                modifier = Modifier.width(280.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.action_processing),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = isProcessingAction ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun HighlightedText(
    text: String,
    query: String,
    style: androidx.compose.ui.text.TextStyle,
    highlightColor: Color = Color.Yellow,
    textColor: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    val annotatedString = remember(text, query) {
        if (query.isEmpty() || !text.contains(query, ignoreCase = true)) {
            AnnotatedString(text)
        } else {
            val builder = AnnotatedString.Builder()
            var startIdx = 0
            val lowerText = text.lowercase()
            val lowerQuery = query.lowercase()
            
            while (true) {
                val idx = lowerText.indexOf(lowerQuery, startIdx)
                if (idx == -1) {
                    builder.append(text.substring(startIdx))
                    break
                }
                builder.append(text.substring(startIdx, idx))
                builder.pushStyle(SpanStyle(background = highlightColor, color = Color.Black))
                builder.append(text.substring(idx, idx + query.length))
                builder.pop()
                startIdx = idx + query.length
            }
            builder.toAnnotatedString()
        }
    }
    
    Text(
        text = annotatedString,
        style = style,
        color = textColor,
        maxLines = maxLines,
        overflow = overflow
    )
}

@Composable
fun BreadcrumbsView(
    prefix: String,
    onSegmentClick: (String) -> Unit
) {
    val segments = remember(prefix) {
        if (prefix.isBlank()) emptyList() else prefix.split("/").filter { it.isNotEmpty() }
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            .horizontalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Root",
            color = if (segments.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (segments.isEmpty()) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.clickable { onSegmentClick("") }
        )
        
        var currentAccumulated = ""
        segments.forEachIndexed { index, segment ->
            currentAccumulated += "$segment/"
            val thisPath = currentAccumulated
            
            Text(
                text = " / ",
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
            Text(
                text = segment,
                color = if (index == segments.lastIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (index == segments.lastIndex) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.clickable { onSegmentClick(thisPath) }
            )
        }
    }
}

@Composable
fun DocumentRow(
    item: BlobItem,
    isFavorite: Boolean,
    onFavoriteToggle: () -> Unit,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    searchQuery: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (item is BlobItem.Folder) Icons.Default.Folder else Icons.Default.Description,
                contentDescription = null,
                tint = if (item is BlobItem.Folder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(36.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                HighlightedText(
                    text = item.name,
                    query = searchQuery,
                    style = LocalTextStyle.current.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item is BlobItem.File) {
                    Text(
                        text = "${formatFileSize(item.size)} • ${item.lastModified.substringBefore("GMT")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = stringResource(R.string.row_virtual_folder),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onFavoriteToggle) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = stringResource(R.string.desc_favorites),
                    tint = if (isFavorite) Color(0xFFFFC107) else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(22.dp)
                )
            }
            
            if (item is BlobItem.File) {
                IconButton(onClick = onDownload) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = stringResource(R.string.desc_download_file),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onRename) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.desc_rename_file),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.desc_delete_file),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                IconButton(onClick = onRename) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.desc_rename_folder),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.desc_delete_folder),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageGalleryCard(
    imageFile: BlobItem.File,
    azureBlobClient: AzureBlobClient,
    isFavorite: Boolean,
    onFavoriteToggle: () -> Unit,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    searchQuery: String
) {
    // Generate secure query URL
    val config = azureBlobClient.getCurrentConfig()
    val imageUrl = "https://${config.accountName}.blob.core.windows.net/${config.containerName}/${imageFile.path}"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .combinedClickable(
                onClick = onClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = imageFile.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            // Text Label Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        HighlightedText(
                            text = imageFile.name,
                            query = searchQuery,
                            style = LocalTextStyle.current.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                            textColor = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = formatFileSize(imageFile.size),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        )
                    }
                    IconButton(
                        onClick = onFavoriteToggle,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = stringResource(R.string.desc_favorites),
                            tint = if (isFavorite) Color(0xFFFFC107) else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    IconButton(
                        onClick = onDownload,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = stringResource(R.string.desc_download),
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(4.dp))
 
                    IconButton(
                        onClick = onRename,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.desc_rename),
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.desc_delete),
                            tint = Color.Red,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LightboxDialog(
    imageFile: BlobItem.File,
    azureBlobClient: AzureBlobClient,
    onDismiss: () -> Unit,
    onDownload: () -> Unit
) {
    val config = azureBlobClient.getCurrentConfig()
    val imageUrl = "https://${config.accountName}.blob.core.windows.net/${config.containerName}/${imageFile.path}"

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Main Zoomable Image
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale > 1f) {
                                offset += pan
                            } else {
                                offset = Offset.Zero
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = imageFile.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                )
            }

            // Close and Download floating actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.5f)
                    )
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = stringResource(R.string.dialog_btn_cancel), tint = Color.White)
                }

                Text(
                    text = imageFile.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                )

                IconButton(
                    onClick = onDownload,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.5f)
                    )
                ) {
                    Icon(imageVector = Icons.Default.FileDownload, contentDescription = stringResource(R.string.desc_download), tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun EmptyStateView(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(54.dp),
                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// Utility function to format file size
private fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
}

// Utility function to check if a file path is an image
private fun isImageFile(fileName: String, contentType: String): Boolean {
    val mimeType = contentType.lowercase()
    if (mimeType.startsWith("image/")) return true
    
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return ext in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
}

// Download implementation using Scoped Storage for Android 10+
private fun downloadFile(
    context: Context,
    azureBlobClient: AzureBlobClient,
    file: BlobItem.File,
    coroutineScope: kotlinx.coroutines.CoroutineScope
) {
    Toast.makeText(context, context.getString(R.string.toast_download_started), Toast.LENGTH_SHORT).show()
    
    coroutineScope.launch {
        val success = withContext(Dispatchers.IO) {
            try {
                val responseBody = azureBlobClient.downloadBlob(file.path)
                val resolver = context.contentResolver
                
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, file.contentType)
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                }
                
                val downloadsUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI
                } else {
                    // Fallback to older MediaStore uri or direct files directory
                    MediaStore.Files.getContentUri("external")
                }
                
                val uri = resolver.insert(downloadsUri, contentValues) ?: throw IOException("Cannot create downloads media record")
                
                resolver.openOutputStream(uri)?.use { output ->
                    responseBody.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                        }
                    }
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
        
        if (success) {
            Toast.makeText(context, context.getString(R.string.toast_download_success), Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, context.getString(R.string.toast_download_error), Toast.LENGTH_LONG).show()
        }
    }
}

private fun isInternalDocument(fileName: String, contentType: String): Boolean {
    val mime = contentType.lowercase()
    if (mime == "application/pdf" || fileName.endsWith(".pdf", ignoreCase = true)) return true
    if (mime == "application/json" || fileName.endsWith(".json", ignoreCase = true)) return true
    if (mime == "text/plain" || fileName.endsWith(".txt", ignoreCase = true)) return true
    return false
}

private fun openFileExternally(
    context: Context,
    azureBlobClient: AzureBlobClient,
    file: BlobItem.File,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onProgress: (String?) -> Unit
) {
    onProgress(file.name)
    coroutineScope.launch {
        val tempFile = withContext(Dispatchers.IO) {
            try {
                val responseBody = azureBlobClient.downloadBlob(file.path)
                val ext = file.name.substringAfterLast('.', "tmp")
                val safeName = file.name.replace("[^a-zA-Z0-9.-]".toRegex(), "_")
                val temp = File.createTempFile("ext_${safeName.substringBeforeLast(".")}_", ".$ext", context.cacheDir)
                temp.deleteOnExit()
                responseBody.byteStream().use { input ->
                    FileOutputStream(temp).use { output ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                        }
                    }
                }
                temp
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        onProgress(null)
        if (tempFile != null) {
            try {
                val authority = "${context.packageName}.fileprovider"
                val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, tempFile)
                
                val mimeType = if (file.contentType.isNotEmpty() && file.contentType != "application/octet-stream") {
                    file.contentType
                } else {
                    val fileExt = file.name.substringAfterLast('.', "").lowercase()
                    android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExt) ?: "*/*"
                }
 
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: android.content.ActivityNotFoundException) {
                Toast.makeText(context, context.getString(R.string.toast_external_no_app), Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, context.getString(R.string.toast_external_open_error), Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, context.getString(R.string.toast_external_download_error), Toast.LENGTH_LONG).show()
        }
    }
}

private data class SelectedFileInfo(
    val name: String,
    val size: Long,
    val mimeType: String
)

private fun getFileInfoFromUri(context: Context, uri: Uri): SelectedFileInfo? {
    var name = ""
    var size: Long = 0
    val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

    val cursor = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (nameIndex != -1) name = it.getString(nameIndex)
            if (sizeIndex != -1) size = it.getLong(sizeIndex)
        }
    }

    if (name.isEmpty()) {
        name = uri.path?.substringAfterLast('/') ?: "file"
    }

    return SelectedFileInfo(name, size, mimeType)
}
