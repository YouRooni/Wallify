package dev.rooni.wallify.util

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

object IframeBypassHelper {

    private val BLOCKED_HEADERS = setOf(
        "x-frame-options",
        "frame-options",
        "content-security-policy",
        "content-security-policy-report-only",
        "content-encoding",
        "content-length",
        "transfer-encoding",
        "access-control-allow-origin",
        "access-control-allow-methods",
        "access-control-allow-headers",
        "access-control-allow-credentials",
        "access-control-expose-headers"
    )

    fun interceptAndBypassIfNeeded(
        context: Context,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        if (request == null) return null
        if (!StorageUtils.isBypassIframeEnabled(context)) return null

        val urlStr = request.url.toString()
        if (urlStr.startsWith("http://127.0.0.1") ||
            urlStr.startsWith("http://localhost") ||
            urlStr.startsWith("file://") ||
            urlStr.startsWith("data:")
        ) {
            return null
        }

        val scheme = request.url.scheme
        if (!scheme.equals("http", ignoreCase = true) && !scheme.equals("https", ignoreCase = true)) {
            return null
        }

        if (!isDocumentRequest(request)) return null

        return try {
            val url = URL(urlStr)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = request.method
                connectTimeout = 8000
                readTimeout = 8000
                instanceFollowRedirects = true

                request.requestHeaders?.forEach { (key, value) ->
                    setRequestProperty(key, value)
                }
            }

            val statusCode = connection.responseCode
            val responseMessage = connection.responseMessage ?: "OK"
            val contentType = connection.contentType ?: "text/html"

            var mimeType = "text/html"
            var encoding = "UTF-8"
            if (contentType.contains(";")) {
                val parts = contentType.split(";")
                mimeType = parts[0].trim()
                for (i in 1 until parts.size) {
                    val p = parts[i].trim()
                    if (p.lowercase(Locale.ROOT).startsWith("charset=")) {
                        encoding = p.substring("charset=".length).trim()
                    }
                }
            } else {
                mimeType = contentType.trim()
            }

            val responseHeaders = mutableMapOf<String, String>()
            connection.headerFields?.forEach { (key, values) ->
                if (key != null && values.isNotEmpty()) {
                    if (key.lowercase(Locale.ROOT) !in BLOCKED_HEADERS) {
                        responseHeaders[key] = values.joinToString(", ")
                    }
                }
            }

            responseHeaders["Access-Control-Allow-Origin"] = "*"
            responseHeaders["Access-Control-Allow-Methods"] = "GET, POST, OPTIONS, HEAD"

            val stream: InputStream = if (statusCode >= 400) {
                connection.errorStream ?: connection.inputStream
            } else {
                connection.inputStream
            }

            WebResourceResponse(
                mimeType,
                encoding,
                statusCode,
                if (responseMessage.isBlank()) "OK" else responseMessage,
                responseHeaders,
                stream
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun isDocumentRequest(request: WebResourceRequest): Boolean {
        if (!request.method.equals("GET", ignoreCase = true)) return false
        val accept = request.requestHeaders
            ?.entries
            ?.firstOrNull { it.key.equals("Accept", ignoreCase = true) }
            ?.value
            ?: return false
        return accept.contains("text/html", ignoreCase = true)
    }
}
