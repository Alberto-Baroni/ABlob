package com.example.ablob.ui.viewer

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ablob.data.AzureBlobClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import androidx.compose.ui.res.stringResource
import com.example.ablob.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    azureBlobClient: AzureBlobClient,
    blobPath: String,
    fileName: String,
    contentType: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var fileState by remember { mutableStateOf<FileState>(FileState.Loading) }
    var fontSizeMultiplier by remember { mutableStateOf(1.0f) }

    fun loadFile() {
        coroutineScope.launch {
            fileState = FileState.Loading
            try {
                val tempFile = withContext(Dispatchers.IO) {
                    val responseBody = azureBlobClient.downloadBlob(blobPath)
                    // Create a unique temp file in cache to prevent overlap
                    val safeName = fileName.replace("[^a-zA-Z0-9.-]".toRegex(), "_")
                    val extension = fileName.substringAfterLast(".", "tmp")
                    val prefix = "view_${safeName.substringBeforeLast(".")}_"
                    val file = File.createTempFile(prefix, ".$extension", context.cacheDir)
                    file.deleteOnExit()
                    
                    responseBody.byteStream().use { input ->
                        FileOutputStream(file).use { output ->
                            val buffer = ByteArray(8192)
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                    file
                }
                
                val type = determineFileType(fileName, contentType)
                fileState = FileState.Success(tempFile, type)
            } catch (e: Exception) {
                e.printStackTrace()
                fileState = FileState.Error(e.localizedMessage ?: context.getString(R.string.viewer_download_error))
            }
        }
    }

    LaunchedEffect(blobPath) {
        loadFile()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = fileName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (fileState is FileState.Success) {
                            val successState = fileState as FileState.Success
                            val sizeStr = formatFileSize(successState.file.length())
                            Text(
                                text = "${successState.type.name} • $sizeStr",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
                actions = {
                    if (fileState is FileState.Success) {
                        val type = (fileState as FileState.Success).type
                        if (type == ViewerFileType.TXT || type == ViewerFileType.JSON) {
                            IconButton(
                                onClick = {
                                    if (fontSizeMultiplier > 0.8f) fontSizeMultiplier -= 0.1f
                                }
                            ) {
                                Icon(imageVector = Icons.Default.Remove, contentDescription = stringResource(R.string.viewer_btn_zoom_out))
                            }
                            Text(
                                text = "${(fontSizeMultiplier * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            IconButton(
                                onClick = {
                                    if (fontSizeMultiplier < 2.0f) fontSizeMultiplier += 0.1f
                                }
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(R.string.viewer_btn_zoom_in))
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (val state = fileState) {
                is FileState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(stringResource(R.string.viewer_loading), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                is FileState.Error -> {
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
                            text = stringResource(R.string.viewer_load_failed),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = { loadFile() }) {
                            Text(stringResource(R.string.browser_btn_retry))
                        }
                    }
                }
                is FileState.Success -> {
                    when (state.type) {
                        ViewerFileType.PDF -> {
                            PdfDocumentViewer(file = state.file)
                        }
                        else -> {
                            TextDocumentViewer(file = state.file, type = state.type, fontSizeMultiplier = fontSizeMultiplier)
                        }
                    }
                }
            }
        }
    }
}

sealed interface FileState {
    data object Loading : FileState
    data class Success(val file: File, val type: ViewerFileType) : FileState
    data class Error(val message: String) : FileState
}

enum class ViewerFileType {
    PDF, TXT, JSON
}

private fun determineFileType(fileName: String, contentType: String): ViewerFileType {
    val mime = contentType.lowercase()
    if (mime == "application/pdf" || fileName.endsWith(".pdf", ignoreCase = true)) {
        return ViewerFileType.PDF
    }
    if (mime == "application/json" || fileName.endsWith(".json", ignoreCase = true)) {
        return ViewerFileType.JSON
    }
    return ViewerFileType.TXT
}

private fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    val df = java.text.DecimalFormat("#,##0.#")
    return df.format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
}

@Composable
fun PdfDocumentViewer(file: File) {
    val context = LocalContext.current
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var fileDescriptor by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var rendererError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(file) {
        try {
            fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(fileDescriptor!!)
        } catch (e: Exception) {
            e.printStackTrace()
            rendererError = context.getString(R.string.viewer_pdf_error)
        }
        
        onDispose {
            try {
                pdfRenderer?.close()
                fileDescriptor?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    if (rendererError != null) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(imageVector = Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = rendererError!!, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
            }
        }
    } else {
        pdfRenderer?.let { renderer ->
            val pageCount = renderer.pageCount
            
            if (pageCount == 0) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.viewer_pdf_empty))
                }
            } else {
                val listState = rememberLazyListState()
                val coroutineScope = rememberCoroutineScope()
                val firstVisiblePage by remember { derivedStateOf { listState.firstVisibleItemIndex } }
                var pageInputText by remember(firstVisiblePage) { mutableStateOf((firstVisiblePage + 1).toString()) }

                Column(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(pageCount) { index ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .shadow(4.dp, shape = RoundedCornerShape(8.dp)),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                Column {
                                    PdfPage(pdfRenderer = renderer, pageIndex = index)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFF5F5F5))
                                            .padding(vertical = 6.dp, horizontal = 12.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Text(
                                            text = stringResource(R.string.viewer_pdf_page_info, index + 1, pageCount),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.DarkGray
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    // Bottom Page Navigation Bar
                    Surface(
                        tonalElevation = 8.dp,
                        shadowElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            IconButton(
                                onClick = {
                                    if (firstVisiblePage > 0) {
                                        coroutineScope.launch {
                                            listState.animateScrollToItem(firstVisiblePage - 1)
                                        }
                                    }
                                },
                                enabled = firstVisiblePage > 0
                            ) {
                                Icon(imageVector = Icons.Default.NavigateBefore, contentDescription = stringResource(R.string.viewer_pdf_prev))
                            }
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            OutlinedTextField(
                                value = pageInputText,
                                onValueChange = { input ->
                                    if (input.all { it.isDigit() }) {
                                        pageInputText = input
                                        val pageNum = input.toIntOrNull()
                                        if (pageNum != null && pageNum in 1..pageCount) {
                                            coroutineScope.launch {
                                                listState.scrollToItem(pageNum - 1)
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.width(72.dp),
                                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent
                                )
                            )
                            
                            Text(
                                text = " / $pageCount",
                                modifier = Modifier.padding(horizontal = 8.dp),
                                fontWeight = FontWeight.SemiBold
                            )
                            
                            IconButton(
                                onClick = {
                                    if (firstVisiblePage < pageCount - 1) {
                                        coroutineScope.launch {
                                            listState.animateScrollToItem(firstVisiblePage + 1)
                                        }
                                    }
                                },
                                enabled = firstVisiblePage < pageCount - 1
                            ) {
                                Icon(imageVector = Icons.Default.NavigateNext, contentDescription = stringResource(R.string.viewer_pdf_next))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PdfPage(pdfRenderer: PdfRenderer, pageIndex: Int, modifier: Modifier = Modifier) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loadError by remember { mutableStateOf(false) }

    LaunchedEffect(pageIndex) {
        withContext(Dispatchers.IO) {
            try {
                val page = pdfRenderer.openPage(pageIndex)
                // Render at high resolution (using 2.5x density scale for high readability of texts)
                val scaleFactor = 2.5f
                val width = (page.width * scaleFactor).toInt()
                val height = (page.height * scaleFactor).toInt()
                
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bmp)
                canvas.drawColor(android.graphics.Color.WHITE)
                
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                bitmap = bmp
            } catch (e: Exception) {
                e.printStackTrace()
                loadError = true
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(if (bitmap != null) bitmap!!.width.toFloat() / bitmap!!.height.toFloat() else 0.707f)
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Pagina ${pageIndex + 1}",
                modifier = Modifier.fillMaxSize()
            )
        } else if (loadError) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = "Errore caricamento pagina",
                tint = MaterialTheme.colorScheme.error
            )
        } else {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
fun TextDocumentViewer(file: File, type: ViewerFileType, fontSizeMultiplier: Float) {
    val context = LocalContext.current
    var textContent by remember { mutableStateOf<AnnotatedString?>(null) }
    var readError by remember { mutableStateOf<String?>(null) }
    val colorScheme = MaterialTheme.colorScheme
    val isDark = isSystemInDarkTheme()

    LaunchedEffect(file) {
        withContext(Dispatchers.IO) {
            try {
                // Read up to 2MB to keep memory footprint safe
                val maxBytes = 2 * 1024 * 1024
                val buffer = ByteArray(maxBytes)
                val bytesRead = file.inputStream().use { stream ->
                    stream.read(buffer)
                }
                
                if (bytesRead == -1) {
                    textContent = AnnotatedString("")
                    return@withContext
                }
                
                var rawText = String(buffer, 0, bytesRead, Charsets.UTF_8)
                val isTruncated = file.length() > maxBytes
                
                if (type == ViewerFileType.JSON) {
                    rawText = tryFormatJson(rawText)
                }

                val annotated = if (type == ViewerFileType.JSON) {
                    highlightJson(rawText, colorScheme, isDark)
                } else {
                    AnnotatedString(rawText)
                }

                textContent = if (isTruncated) {
                    AnnotatedString.Builder().apply {
                        append(annotated)
                        pushStyle(SpanStyle(color = colorScheme.error, fontWeight = FontWeight.Bold))
                        append(context.getString(R.string.viewer_text_truncated))
                        pop()
                    }.toAnnotatedString()
                } else {
                    annotated
                }
            } catch (e: Exception) {
                e.printStackTrace()
                readError = context.getString(R.string.viewer_text_error, e.localizedMessage ?: "")
            }
        }
    }

    if (readError != null) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(text = readError!!, color = MaterialTheme.colorScheme.error)
        }
    } else {
        textContent?.let { text ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Notice bar for text tools
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.viewer_text_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Selectable text area
                SelectionContainer {
                    Text(
                        text = text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = (13 * fontSizeMultiplier).sp,
                        lineHeight = (18 * fontSizeMultiplier).sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

private fun tryFormatJson(rawJson: String): String {
    return try {
        val trimmed = rawJson.trim()
        if (trimmed.startsWith("[")) {
            val jsonArray = org.json.JSONArray(trimmed)
            jsonArray.toString(4)
        } else {
            val jsonObject = org.json.JSONObject(trimmed)
            jsonObject.toString(4)
        }
    } catch (e: Exception) {
        rawJson
    }
}

private fun highlightJson(json: String, colorScheme: ColorScheme, isDark: Boolean): AnnotatedString {
    val builder = AnnotatedString.Builder()
    var i = 0
    val len = json.length

    // Tailored theme-friendly highlighting colors
    val stringColor = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
    val numberColor = if (isDark) Color(0xFFFFB74D) else Color(0xFFE65100)
    val booleanColor = if (isDark) Color(0xFF4FC3F7) else Color(0xFF0288D1)
    val nullColor = if (isDark) Color(0xFFE57373) else Color(0xFFD32F2F)
    val keyColor = colorScheme.primary
    val separatorColor = colorScheme.secondary

    while (i < len) {
        val c = json[i]
        when {
            c == '"' -> {
                // String literal (could be key or value)
                val start = i
                i++
                var escaped = false
                while (i < len) {
                    val ch = json[i]
                    if (escaped) {
                        escaped = false
                    } else if (ch == '\\') {
                        escaped = true
                    } else if (ch == '"') {
                        break
                    }
                    i++
                }
                i++ // include closing quote
                val str = json.substring(start, minOf(i, len))

                // Determine if it is a JSON Key by peeking for ':'
                var isKey = false
                var peek = i
                while (peek < len && json[peek].isWhitespace()) {
                    peek++
                }
                if (peek < len && json[peek] == ':') {
                    isKey = true
                }

                if (isKey) {
                    builder.pushStyle(SpanStyle(color = keyColor, fontWeight = FontWeight.Bold))
                } else {
                    builder.pushStyle(SpanStyle(color = stringColor))
                }
                builder.append(str)
                builder.pop()
            }
            c == '{' || c == '}' || c == '[' || c == ']' || c == ',' || c == ':' -> {
                builder.pushStyle(SpanStyle(color = separatorColor, fontWeight = FontWeight.Medium))
                builder.append(c)
                builder.pop()
                i++
            }
            c.isDigit() || c == '-' -> {
                val start = i
                i++
                while (i < len && (json[i].isDigit() || json[i] == '.' || json[i] == 'e' || json[i] == 'E' || json[i] == '+' || json[i] == '-')) {
                    i++
                }
                val num = json.substring(start, i)
                builder.pushStyle(SpanStyle(color = numberColor))
                builder.append(num)
                builder.pop()
            }
            json.startsWith("true", i) -> {
                builder.pushStyle(SpanStyle(color = booleanColor, fontWeight = FontWeight.Bold))
                builder.append("true")
                builder.pop()
                i += 4
            }
            json.startsWith("false", i) -> {
                builder.pushStyle(SpanStyle(color = booleanColor, fontWeight = FontWeight.Bold))
                builder.append("false")
                builder.pop()
                i += 5
            }
            json.startsWith("null", i) -> {
                builder.pushStyle(SpanStyle(color = nullColor, fontWeight = FontWeight.Bold))
                builder.append("null")
                builder.pop()
                i += 4
            }
            else -> {
                builder.append(c)
                i++
            }
        }
    }
    return builder.toAnnotatedString()
}
