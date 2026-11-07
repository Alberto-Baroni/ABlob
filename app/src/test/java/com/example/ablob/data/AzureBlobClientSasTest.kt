package com.example.ablob.data

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

class AzureBlobClientSasTest {
    @Test
    fun encodedSignatureIsPreservedWhenSendingRequest() {
        verifySignature("sv=2020-04-08&sr=c&sig=abc%2Bdef%2Fghi%3D")
    }

    @Test
    fun tokenWithLeadingQuestionMarkPreservesSignatureAndListParameters() {
        verifySignature("?sv=2020-04-08&sr=c&sig=abc%2Bdef%2Fghi%3D")
    }

    private fun verifySignature(token: String) {
        val storage = AzureBlobClient()
        storage.updateConfig(StorageConfig(
            accountName = "example",
            containerName = "test",
            authType = AuthType.SAS_TOKEN,
            sasToken = token,
        ))
        val client = storage.authenticatedClient.newBuilder()
            .addInterceptor { chain ->
                val request = chain.request()
                assertEquals("abc+def/ghi=", request.url.queryParameter("sig"))
                assertEquals("container", request.url.queryParameter("restype"))
                assertEquals("list", request.url.queryParameter("comp"))
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("".toResponseBody())
                    .build()
            }
            .build()
        val request = Request.Builder()
            .url("https://example.blob.core.windows.net/test?restype=container&comp=list")
            .build()
        client.newCall(request).execute().use { assertEquals(200, it.code) }
    }
}
