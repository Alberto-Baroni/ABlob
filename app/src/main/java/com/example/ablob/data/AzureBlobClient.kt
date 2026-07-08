package com.example.ablob.data

import android.util.Base64
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.IOException
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AzureBlobClient {

    @Volatile
    private var config: StorageConfig = StorageConfig()

    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var tokenExpiryTime: Long = 0

    fun updateConfig(newConfig: StorageConfig) {
        config = newConfig
        cachedToken = null
        tokenExpiryTime = 0
    }

    fun getCurrentConfig(): StorageConfig = config

    val authenticatedClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                // Only intercept and sign requests targeting Azure Blob Storage
                if (!request.url.host.contains("blob.core.windows.net")) {
                    return@addInterceptor chain.proceed(request)
                }

                val currentConfig = config
                val urlBuilder = request.url.newBuilder()
                val dateString = getRFC1123Date()
                val requestBuilder = request.newBuilder()
                    .header("x-ms-date", dateString)
                    .header("x-ms-version", "2020-04-08")

                when (currentConfig.authType) {
                    AuthType.SAS_TOKEN -> {
                        val sas = currentConfig.sasToken.trimStart('?')
                        if (sas.isNotEmpty()) {
                            sas.split('&').forEach { param ->
                                val parts = param.split('=', limit = 2)
                                if (parts.size == 2) {
                                    urlBuilder.addQueryParameter(parts[0], parts[1])
                                }
                            }
                        }
                        requestBuilder.url(urlBuilder.build())
                    }
                    AuthType.ENTRA_ID -> {
                        val token = getEntraToken(currentConfig)
                        if (!token.isNullOrEmpty()) {
                            requestBuilder.header("Authorization", "Bearer $token")
                        }
                    }
                    AuthType.SHARED_KEY -> {
                        val tempRequest = requestBuilder.build()
                        val signature = calculateSharedKeySignature(tempRequest, currentConfig.accountName, currentConfig.accountKey)
                        requestBuilder.header("Authorization", "SharedKey ${currentConfig.accountName}:$signature")
                    }
                }

                chain.proceed(requestBuilder.build())
            }
            .build()
    }

    private fun getRFC1123Date(): String {
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
        dateFormat.timeZone = TimeZone.getTimeZone("GMT")
        return dateFormat.format(calendar.time)
    }

    private fun getEntraToken(currentConfig: StorageConfig): String? {
        val currentTime = System.currentTimeMillis()
        if (cachedToken != null && currentTime < tokenExpiryTime) {
            return cachedToken
        }

        synchronized(this) {
            // Check again inside block
            if (cachedToken != null && currentTime < tokenExpiryTime) {
                return cachedToken
            }

            val formBody = FormBody.Builder()
                .add("grant_type", "client_credentials")
                .add("client_id", currentConfig.clientId)
                .add("client_secret", currentConfig.clientSecret)
                .add("scope", "https://storage.azure.com/.default")
                .build()

            val request = Request.Builder()
                .url("https://login.microsoftonline.com/${currentConfig.tenantId}/oauth2/v2.0/token")
                .post(formBody)
                .build()

            try {
                // Use clean OkHttpClient without signing interceptor for fetching OAuth tokens
                val client = OkHttpClient()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return null
                    val body = response.body?.string() ?: return null

                    val tokenRegex = """"access_token"\s*:\s*"([^"]+)"""".toRegex()
                    val expiryRegex = """"expires_in"\s*:\s*(\d+)""".toRegex()

                    val tokenMatch = tokenRegex.find(body)
                    val expiryMatch = expiryRegex.find(body)

                    if (tokenMatch != null) {
                        cachedToken = tokenMatch.groupValues[1]
                        val expiresInSeconds = expiryMatch?.groupValues[1]?.toLongOrNull() ?: 3600
                        tokenExpiryTime = currentTime + (expiresInSeconds - 60) * 1000 // Refresh 1 min early
                        return cachedToken
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return null
        }
    }

    private fun calculateSharedKeySignature(request: Request, accountName: String, accountKey: String): String {
        val verb = request.method
        val contentEncoding = request.header("Content-Encoding") ?: ""
        val contentLanguage = request.header("Content-Language") ?: ""
        val contentLength = request.header("Content-Length") ?: ""
        val contentMd5 = request.header("Content-MD5") ?: ""
        val contentType = request.header("Content-Type") ?: ""
        val date = "" // x-ms-date is used instead
        val ifModifiedSince = request.header("If-Modified-Since") ?: ""
        val ifMatch = request.header("If-Match") ?: ""
        val ifNoneMatch = request.header("If-None-Match") ?: ""
        val ifUnmodifiedSince = request.header("If-Unmodified-Since") ?: ""
        val range = request.header("Range") ?: ""

        val msHeaders = request.headers
            .filter { it.first.lowercase().startsWith("x-ms-") }
            .map { it.first.lowercase().trim() to it.second.trim() }
            .sortedBy { it.first }
        val canonicalizedHeaders = msHeaders.joinToString("") { "${it.first}:${it.second}\n" }

        val canonicalizedResource = buildString {
            append("/$accountName")
            append(request.url.encodedPath)

            val queryParams = mutableMapOf<String, MutableList<String>>()
            val url = request.url
            for (i in 0 until url.querySize) {
                val name = url.queryParameterName(i).lowercase()
                val value = url.queryParameterValue(i) ?: ""
                queryParams.getOrPut(name) { mutableListOf() }.add(value)
            }

            val sortedParams = queryParams.toSortedMap()
            sortedParams.forEach { (name, values) ->
                values.sort()
                val joinedValues = values.joinToString(",")
                append("\n$name:$joinedValues")
            }
        }

        val stringToSign = buildString {
            append(verb).append("\n")
            append(contentEncoding).append("\n")
            append(contentLanguage).append("\n")
            append(contentLength).append("\n")
            append(contentMd5).append("\n")
            append(contentType).append("\n")
            append(date).append("\n")
            append(ifModifiedSince).append("\n")
            append(ifMatch).append("\n")
            append(ifNoneMatch).append("\n")
            append(ifUnmodifiedSince).append("\n")
            append(range).append("\n")
            append(canonicalizedHeaders)
            append(canonicalizedResource)
        }

        val keyBytes = Base64.decode(accountKey, Base64.DEFAULT)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(keyBytes, "HmacSHA256"))
        val signatureBytes = mac.doFinal(stringToSign.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(signatureBytes, Base64.NO_WRAP)
    }

    suspend fun listBlobs(prefix: String, delimiter: String = "/", maxResults: Int = 10, marker: String = ""): ListBlobsResult = withContext(Dispatchers.IO) {
        val currentConfig = config
        val url = "https://${currentConfig.accountName}.blob.core.windows.net/${currentConfig.containerName}"
        
        val urlBuilder = Request.Builder()
            .url(url)
            .get()

        val httpUrlBuilder = requestHttpUrlBuilder(url, currentConfig)
            .addQueryParameter("restype", "container")
            .addQueryParameter("comp", "list")
            .addQueryParameter("maxresults", maxResults.toString())
            .addQueryParameter("delimiter", delimiter)
            
        if (prefix.isNotEmpty()) {
            httpUrlBuilder.addQueryParameter("prefix", prefix)
        }
        if (marker.isNotEmpty()) {
            httpUrlBuilder.addQueryParameter("marker", marker)
        }

        val request = urlBuilder.url(httpUrlBuilder.build()).build()

        authenticatedClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: ""
                throw IOException("Failed to list blobs: HTTP ${response.code}\n$errBody")
            }
            val xml = response.body?.string() ?: ""
            parseListBlobsResponse(xml, prefix)
        }
    }

    private fun requestHttpUrlBuilder(baseUrl: String, currentConfig: StorageConfig): okhttp3.HttpUrl.Builder {
        val urlBuilder = try {
            baseUrl.toHttpUrl().newBuilder()
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid base URL: $baseUrl", e)
        }
        return urlBuilder
    }

    suspend fun downloadBlob(blobPath: String): okhttp3.ResponseBody = withContext(Dispatchers.IO) {
        val currentConfig = config
        val url = "https://${currentConfig.accountName}.blob.core.windows.net/${currentConfig.containerName}/$blobPath"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val response = authenticatedClient.newCall(request).execute()
        if (response.isSuccessful) {
            response.body ?: throw IOException("Empty response body for $blobPath")
        } else {
            response.close()
            throw IOException("Failed to download blob: HTTP ${response.code}")
        }
    }

    suspend fun uploadBlob(
        blobPath: String,
        file: File,
        contentType: String,
        onProgress: ((bytesSent: Long, totalBytes: Long) -> Unit)? = null
    ): Unit = withContext(Dispatchers.IO) {
        val currentConfig = config
        val url = "https://${currentConfig.accountName}.blob.core.windows.net/${currentConfig.containerName}/$blobPath"
        
        val requestBody = ProgressRequestBody(file, contentType, onProgress)
        
        val request = Request.Builder()
            .url(url)
            .put(requestBody)
            .header("Content-Length", file.length().toString())
            .header("Content-Type", contentType)
            .header("x-ms-blob-type", "BlockBlob")
            .build()

        authenticatedClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: ""
                throw IOException("Failed to upload blob: HTTP ${response.code}\n$errBody")
            }
        }
    }

    suspend fun copyBlob(sourcePath: String, destPath: String): Unit = withContext(Dispatchers.IO) {
        val currentConfig = config
        val url = "https://${currentConfig.accountName}.blob.core.windows.net/${currentConfig.containerName}/$destPath"
        
        val request = Request.Builder()
            .url(url)
            .put(ByteArray(0).toRequestBody(null))
            .header("Content-Length", "0")
            .header("x-ms-copy-source", "/${currentConfig.accountName}/${currentConfig.containerName}/$sourcePath")
            .build()

        authenticatedClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: ""
                throw IOException("Failed to copy blob: HTTP ${response.code}\n$errBody")
            }
        }
    }

    suspend fun renameBlob(sourcePath: String, destPath: String) {
        copyBlob(sourcePath, destPath)
        deleteBlob(sourcePath)
    }

    suspend fun createVirtualFolder(folderPath: String): Unit = withContext(Dispatchers.IO) {
        val currentConfig = config
        val normalizedPath = if (folderPath.endsWith("/")) folderPath else "$folderPath/"
        val url = "https://${currentConfig.accountName}.blob.core.windows.net/${currentConfig.containerName}/$normalizedPath"
        
        val requestBody = ByteArray(0).toRequestBody(null)
        
        val request = Request.Builder()
            .url(url)
            .put(requestBody)
            .header("Content-Length", "0")
            .header("x-ms-blob-type", "BlockBlob")
            .build()

        authenticatedClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: ""
                throw IOException("Failed to create folder: HTTP ${response.code}\n$errBody")
            }
        }
    }

    suspend fun deleteBlob(blobPath: String): Unit = withContext(Dispatchers.IO) {
        val currentConfig = config
        val url = "https://${currentConfig.accountName}.blob.core.windows.net/${currentConfig.containerName}/$blobPath"
        val request = Request.Builder()
            .url(url)
            .delete()
            .build()

        authenticatedClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: ""
                throw IOException("Failed to delete blob: HTTP ${response.code}\n$errBody")
            }
        }
    }

    private fun parseListBlobsResponse(xml: String, currentPrefix: String): ListBlobsResult {
        val items = mutableListOf<BlobItem>()
        var nextMarker = ""

        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var currentTag = ""

            var inBlobPrefix = false
            var inBlob = false
            var inProperties = false

            var blobName = ""
            var blobSize: Long = 0
            var blobLastModified = ""
            var blobContentType = ""
            var folderPath = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name

                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = tagName
                        when (tagName) {
                            "BlobPrefix" -> {
                                inBlobPrefix = true
                                folderPath = ""
                            }
                            "Blob" -> {
                                inBlob = true
                                blobName = ""
                                blobSize = 0
                                blobLastModified = ""
                                blobContentType = ""
                            }
                            "Properties" -> {
                                inProperties = true
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text.trim()
                        if (text.isNotEmpty()) {
                            if (inBlobPrefix) {
                                if (currentTag == "Name") {
                                    folderPath = text
                                }
                            } else if (inBlob) {
                                when (currentTag) {
                                    "Name" -> blobName = text
                                    "Content-Length" -> blobSize = text.toLongOrNull() ?: 0
                                    "Last-Modified" -> blobLastModified = text
                                    "Content-Type" -> blobContentType = text
                                }
                            } else {
                                if (currentTag == "NextMarker") {
                                    nextMarker = text
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (tagName) {
                            "BlobPrefix" -> {
                                inBlobPrefix = false
                                if (folderPath.isNotEmpty()) {
                                    val name = folderPath.substring(currentPrefix.length).removeSuffix("/")
                                    if (name.isNotEmpty()) {
                                        items.add(BlobItem.Folder(name, folderPath))
                                    }
                                }
                            }
                            "Blob" -> {
                                inBlob = false
                                if (blobName.isNotEmpty()) {
                                    val name = blobName.substring(currentPrefix.length)
                                    if (name.isNotEmpty()) {
                                        items.add(BlobItem.File(name, blobName, blobSize, blobLastModified, blobContentType))
                                    }
                                }
                            }
                            "Properties" -> {
                                inProperties = false
                            }
                        }
                        currentTag = ""
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return ListBlobsResult(items, nextMarker)
    }
}

data class ListBlobsResult(
    val items: List<BlobItem>,
    val nextMarker: String
)

class ProgressRequestBody(
    private val file: File,
    private val contentType: String,
    private val onProgress: ((bytesSent: Long, totalBytes: Long) -> Unit)?
) : okhttp3.RequestBody() {

    override fun contentType(): okhttp3.MediaType? = 
        contentType.toMediaTypeOrNull()

    override fun contentLength(): Long = file.length()

    override fun writeTo(sink: okio.BufferedSink) {
        val fileLength = contentLength()
        if (onProgress == null) {
            val buffer = ByteArray(8192)
            file.inputStream().use { input ->
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    sink.write(buffer, 0, read)
                }
            }
            return
        }
        
        val buffer = ByteArray(8192)
        var uploaded = 0L
        file.inputStream().use { input ->
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                sink.write(buffer, 0, read)
                uploaded += read
                onProgress.invoke(uploaded, fileLength)
            }
        }
    }
}
