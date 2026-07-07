package com.example.ablob.ui.browser

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
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
import com.example.ablob.data.AzureBlobClient
import com.example.ablob.data.BlobItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    azureBlobClient: AzureBlobClient,
    prefix: String,
    onNavigateToFolder: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onSettingsClick: () -> Unit,
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
                Toast.makeText(context, "Errore caricamento pagina: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
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

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "ABlob Browser",
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                            Text(
                                text = "Container: ${azureBlobClient.getCurrentConfig().containerName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        if (prefix.isNotEmpty()) {
                            IconButton(onClick = onNavigateUp) {
                                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Su di un livello")
                            }
                        } else {
                            IconButton(onClick = { /* No-op, root */ }, enabled = false) {
                                Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null)
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = onSettingsClick) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = "Impostazioni")
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
                                Text("Documenti (${filteredDocuments.size})")
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
                                Text("Immagini (${filteredImages.size})")
                            }
                        }
                    )
                }

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cerca nei file caricati...") },
                    leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Cancella")
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
                        Text("Riprova")
                    }
                }
            } else if (selectedTab == 0 && filteredDocuments.isEmpty()) {
                EmptyStateView(message = "Nessun documento o cartella trovata.")
            } else if (selectedTab == 1 && filteredImages.isEmpty()) {
                EmptyStateView(message = "Nessuna immagine trovata.")
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
                                onClick = {
                                    if (item is BlobItem.Folder) {
                                        onNavigateToFolder(item.path)
                                    }
                                },
                                onDownload = {
                                    if (item is BlobItem.File) {
                                        downloadFile(context, azureBlobClient, item, coroutineScope)
                                    }
                                }
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
                } else {
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
                                onClick = {
                                    activeLightboxImage = imageFile
                                },
                                onDownload = {
                                    downloadFile(context, azureBlobClient, imageFile, coroutineScope)
                                }
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
    onClick: () -> Unit,
    onDownload: () -> Unit
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
                Text(
                    text = item.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
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
                        text = "Cartella virtuale",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            if (item is BlobItem.File) {
                IconButton(onClick = onDownload) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = "Scarica file",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
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
    onClick: () -> Unit,
    onDownload: () -> Unit
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
                        Text(
                            text = imageFile.name,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
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
                        onClick = onDownload,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Scarica",
                            tint = Color.White,
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
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Chiudi", tint = Color.White)
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
                    Icon(imageVector = Icons.Default.FileDownload, contentDescription = "Scarica", tint = Color.White)
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
    Toast.makeText(context, "Download avviato...", Toast.LENGTH_SHORT).show()
    
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
            Toast.makeText(context, "Scaricato con successo in Downloads!", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "Errore durante il download del file.", Toast.LENGTH_LONG).show()
        }
    }
}
