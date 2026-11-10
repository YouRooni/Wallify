package dev.rooni.wallify.service

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import fi.iki.elonen.NanoHTTPD
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

class WallpaperLocalServer(
    private val context: Context,
    port: Int
) : NanoHTTPD("127.0.0.1", port) {

    val boundPort: Int
        get() = listeningPort

    private data class Entry(
        val uri: Uri,
        val size: Long,
        val lastModified: Long,
        val mime: String
    ) {
        val eTag: String get() = "\"$size-$lastModified\""
    }

    private class Mount(val id: String, val uri: Uri) {
        @Volatile
        var root: DocumentFile? = null

        val entryCache = ConcurrentHashMap<String, Entry>()
    }

    private val mounts = ConcurrentHashMap<String, Mount>()

    @Volatile
    private var activeMountId: String? = null


    fun mount(uri: Uri): String {
        val id = idFor(uri)
        mounts.getOrPut(id) { Mount(id, uri) }
        return id
    }

    fun setWallpaperUri(uri: Uri?) {
        if (uri == null) {
            activeMountId = null
            return
        }
        activeMountId = mount(uri)
    }

    fun urlFor(uri: Uri, relativePath: String): String? {
        val port = boundPort
        if (port <= 0) return null
        val clean = relativePath.trimStart('/')
        val id = mount(uri)
        return if (id == activeMountId) {
            "http://127.0.0.1:$port/$clean"
        } else {
            "http://127.0.0.1:$port/$MOUNT_PREFIX/$id/$clean"
        }
    }

    fun invalidateFileCache() {
        for (mount in mounts.values) {
            mount.entryCache.clear()
            mount.root = null
        }
    }

    fun unmount(uri: Uri) {
        val id = idFor(uri)
        if (id != activeMountId) mounts.remove(id)
    }

    private fun idFor(uri: Uri): String {
        val s = uri.toString()
        return java.lang.Long.toHexString((s.hashCode().toLong() and 0xFFFFFFFFL) or (s.length.toLong() shl 32))
    }

    private fun resolveRoot(mount: Mount): DocumentFile? {
        mount.root?.let { return it }
        var doc: DocumentFile? = try {
            DocumentFile.fromTreeUri(context, mount.uri)
        } catch (e: Exception) {
            null
        }
        if (doc == null || !doc.exists()) {
            doc = try {
                DocumentFile.fromSingleUri(context, mount.uri)
            } catch (e: Exception) {
                null
            }
        }
        if (doc != null && doc.exists()) {
            mount.root = doc
            return doc
        }
        return null
    }


    override fun serve(session: IHTTPSession): Response {
        var path = session.uri.trimStart('/').substringBefore('?')

        var mount: Mount? = null
        var explicitMount = false

        if (path == MOUNT_PREFIX || path.startsWith("$MOUNT_PREFIX/")) {
            val rest = path.removePrefix(MOUNT_PREFIX).trimStart('/')
            val id = rest.substringBefore('/')
            path = rest.substringAfter('/', "")
            mount = mounts[id]
            explicitMount = true
        }

        if (explicitMount && mount == null) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Unknown mount")
        }

        if (mount == null) {
            mountIdFromUrl(session.headers["referer"])?.let { mount = mounts[it] }
        }

        if (mount == null) {
            mount = activeMountId?.let { mounts[it] }
        }

        val target = if (path.isEmpty()) "index.html" else path

        if (target.equals("favicon.ico", ignoreCase = true)) {
            return newFixedLengthResponse(Response.Status.NO_CONTENT, "image/x-icon", null, 0)
        }

        if (mount == null) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "No wallpaper mounted")
        }
        if (target.split('/').any { it == ".." }) {
            return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Forbidden")
        }

        return try {
            val activeMount = mount!!
            val root = resolveRoot(activeMount)
                ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Folder not found")

            val entry = resolveEntry(activeMount, root, target)
                ?: return newFixedLengthResponse(
                    Response.Status.NOT_FOUND, MIME_PLAINTEXT, "File not found: $target"
                )

            val ifNoneMatch = session.headers["if-none-match"]
            if (ifNoneMatch != null && ifNoneMatch.trim() == entry.eTag) {
                val notModified = newFixedLengthResponse(Response.Status.NOT_MODIFIED, entry.mime, "")
                applyCommonHeaders(notModified, entry)
                return notModified
            }

            val rangeHeader = session.headers["range"]
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                return serveRange(entry, rangeHeader)
            }

            val inputStream: InputStream = context.contentResolver.openInputStream(entry.uri)
                ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Cannot open stream")

            val response = newFixedLengthResponse(Response.Status.OK, entry.mime, inputStream, entry.size)
            applyCommonHeaders(response, entry)
            response
        } catch (e: Exception) {
            e.printStackTrace()
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error: ${e.message}")
        }
    }

    private fun mountIdFromUrl(url: String?): String? {
        if (url.isNullOrEmpty()) return null
        val marker = "/$MOUNT_PREFIX/"
        val idx = url.indexOf(marker)
        if (idx < 0) return null
        val rest = url.substring(idx + marker.length)
        val id = rest.substringBefore('/').substringBefore('?')
        return id.ifEmpty { null }
    }

    private fun serveRange(entry: Entry, rangeHeader: String): Response {
        val fileSize = entry.size
        var start = 0L
        var end = fileSize - 1
        val rangeValue = rangeHeader.substring(6)
        val dashPos = rangeValue.indexOf('-')
        if (dashPos != -1) {
            val startStr = rangeValue.substring(0, dashPos).trim()
            val endStr = rangeValue.substring(dashPos + 1).trim()
            try {
                if (startStr.isNotEmpty()) start = startStr.toLong()
                if (endStr.isNotEmpty()) end = endStr.toLong()
            } catch (_: NumberFormatException) {
            }
        }

        if (start >= fileSize) {
            val response = newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "")
            response.addHeader("Content-Range", "bytes */$fileSize")
            return response
        }
        if (end >= fileSize) end = fileSize - 1

        val contentLength = end - start + 1
        val inputStream: InputStream = context.contentResolver.openInputStream(entry.uri)
            ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Cannot open stream")

        skipBytes(inputStream, start)

        val response = newFixedLengthResponse(
            Response.Status.PARTIAL_CONTENT, entry.mime, inputStream, contentLength
        )
        response.addHeader("Content-Range", "bytes $start-$end/$fileSize")
        applyCommonHeaders(response, entry)
        return response
    }

    private fun applyCommonHeaders(response: Response, entry: Entry) {
        response.addHeader("Accept-Ranges", "bytes")
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        response.addHeader("ETag", entry.eTag)
        response.addHeader("Cache-Control", "no-cache")
    }

    private fun skipBytes(inputStream: InputStream, bytesToSkip: Long) {
        var remaining = bytesToSkip
        while (remaining > 0) {
            val skipped = inputStream.skip(remaining)
            if (skipped <= 0) {
                if (inputStream.read() == -1) break
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }

    private fun resolveEntry(mount: Mount, root: DocumentFile, relativePath: String): Entry? {
        val cleanPath = relativePath.trimStart('/')
        mount.entryCache[cleanPath]?.let { cached ->
            val doc = try { DocumentFile.fromSingleUri(context, cached.uri) } catch (e: Exception) { null }
            val size = doc?.length() ?: -1L
            val lastMod = doc?.lastModified() ?: -1L
            if (size == cached.size && lastMod == cached.lastModified) return cached
            mount.entryCache.remove(cleanPath)
        }

        val parts = cleanPath.split("/").filter { it.isNotEmpty() }
        var current: DocumentFile = root
        for (part in parts) {
            val child = current.listFiles().firstOrNull { it.name.equals(part, ignoreCase = true) }
                ?: return null
            current = child
        }
        if (!current.isFile) return null

        val entry = Entry(
            uri = current.uri,
            size = current.length(),
            lastModified = current.lastModified(),
            mime = getMimeTypeForPath(current.name ?: cleanPath)
        )
        mount.entryCache[cleanPath] = entry
        return entry
    }

    private fun getMimeTypeForPath(path: String): String {
        val lower = path.lowercase()
        return when {
            lower.endsWith(".html") || lower.endsWith(".htm") -> "text/html; charset=utf-8"
            lower.endsWith(".js") || lower.endsWith(".mjs") -> "application/javascript; charset=utf-8"
            lower.endsWith(".css") -> "text/css; charset=utf-8"
            lower.endsWith(".json") -> "application/json; charset=utf-8"
            lower.endsWith(".txt") || lower.endsWith(".md") -> "text/plain; charset=utf-8"
            lower.endsWith(".xml") -> "text/xml; charset=utf-8"
            lower.endsWith(".png") || lower.endsWith(".apng") -> "image/png"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".gif") -> "image/gif"
            lower.endsWith(".svg") -> "image/svg+xml"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".avif") -> "image/avif"
            lower.endsWith(".ico") -> "image/x-icon"
            lower.endsWith(".mp4") || lower.endsWith(".m4v") -> "video/mp4"
            lower.endsWith(".webm") -> "video/webm"
            lower.endsWith(".ogv") -> "video/ogg"
            lower.endsWith(".mov") -> "video/quicktime"
            lower.endsWith(".mkv") -> "video/x-matroska"
            lower.endsWith(".mp3") -> "audio/mpeg"
            lower.endsWith(".ogg") || lower.endsWith(".opus") -> "audio/ogg"
            lower.endsWith(".m4a") -> "audio/mp4"
            lower.endsWith(".flac") -> "audio/flac"
            lower.endsWith(".wav") -> "audio/wav"
            lower.endsWith(".ttf") -> "font/ttf"
            lower.endsWith(".otf") -> "font/otf"
            lower.endsWith(".woff") -> "font/woff"
            lower.endsWith(".woff2") -> "font/woff2"
            lower.endsWith(".wasm") -> "application/wasm"
            else -> "application/octet-stream"
        }
    }

    companion object {
        const val DEFAULT_PORT = 8088
        const val MOUNT_PREFIX = "~w"

        @Volatile
        private var instance: WallpaperLocalServer? = null

        fun getInstance(context: Context): WallpaperLocalServer {
            return instance ?: synchronized(this) {
                instance ?: create(context.applicationContext).also { instance = it }
            }
        }

        fun createScoped(context: Context, uri: Uri): WallpaperLocalServer? {
            return try {
                val server = WallpaperLocalServer(context.applicationContext, 0)
                server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true)
                server.setWallpaperUri(uri)
                if (server.boundPort > 0) {
                    server
                } else {
                    server.stop()
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        private fun create(appContext: Context): WallpaperLocalServer {
            try {
                val server = WallpaperLocalServer(appContext, DEFAULT_PORT)
                server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true)
                return server
            } catch (e: Exception) {
                Log.w("WallpaperLocalServer", "Port $DEFAULT_PORT busy, falling back to a free port", e)
            }
            val fallback = WallpaperLocalServer(appContext, 0)
            try {
                fallback.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return fallback
        }
    }
}
