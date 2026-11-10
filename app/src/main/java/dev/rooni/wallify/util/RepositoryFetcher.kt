package dev.rooni.wallify.util

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import dev.rooni.wallify.model.GitHubRelease
import dev.rooni.wallify.model.RepoCatalog
import dev.rooni.wallify.model.RepoWallpaperItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object RepositoryFetcher {

    private const val RAW_BASE = "https://raw.githubusercontent.com/YouRooni/Wallify-Repo/main"
    const val REPOSITORY_URL = "https://github.com/YouRooni/Wallify-Repo"
    private const val CATALOG_URL = "$RAW_BASE/index.json"
    private const val RELEASE_URL =
        "https://api.github.com/repos/YouRooni/Wallify-Repo/releases/tags/bundles"
    private val gson = Gson()

    fun coverUrl(item: RepoWallpaperItem): String =
        "$RAW_BASE/${item.path}/${item.cover ?: item.icon ?: "cover.png"}"

    fun iconUrl(item: RepoWallpaperItem): String =
        "$RAW_BASE/${item.path}/${item.icon ?: item.cover ?: "cover.png"}"

    fun readmeUrl(item: RepoWallpaperItem): String = "$RAW_BASE/${item.path}/README.md"
    fun readmeBaseUrl(item: RepoWallpaperItem): String = "$RAW_BASE/${item.path}/"

    fun githubUrl(item: RepoWallpaperItem): String = "$REPOSITORY_URL/tree/main/${item.path}"

    suspend fun fetchDownloadCounts(): Map<String, Int> = withContext(Dispatchers.IO) {
        val connection = URL(RELEASE_URL).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext emptyMap()
            val json = connection.inputStream.bufferedReader().use { it.readText() }
            val release = gson.fromJson(json, GitHubRelease::class.java) ?: return@withContext emptyMap()
            return@withContext release.assets
                .filter { it.name.endsWith(".zip") }
                .associate { it.name.removeSuffix(".zip") to it.downloadCount }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            connection.disconnect()
        }
        emptyMap()
    }

    fun downloadKey(item: RepoWallpaperItem): String =
        item.downloadUrl?.substringAfterLast('/')?.removeSuffix(".zip") ?: item.id

    suspend fun fetchCatalog(): RepoCatalog = withContext(Dispatchers.IO) {
        val url = URL(CATALOG_URL)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000
        connection.readTimeout = 10000

        try {
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val json = connection.inputStream.bufferedReader().use { it.readText() }
                return@withContext gson.fromJson(json, RepoCatalog::class.java) ?: RepoCatalog()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            connection.disconnect()
        }
        return@withContext RepoCatalog()
    }

    suspend fun installWallpaperFromRepo(
        context: Context,
        item: RepoWallpaperItem
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val rootUri = StorageUtils.getRootFolderUri(context) ?: return@withContext false
            val rootDoc = DocumentFile.fromTreeUri(context, rootUri) ?: return@withContext false

            val zipUrl = item.downloadUrl
            if (!zipUrl.isNullOrBlank()) {
                val installed = installFromZipUrl(context, zipUrl, item.name)
                if (installed) return@withContext true
            }

            val folderName = StorageUtils.sanitizeFolderName(item.name)
            var targetDir = rootDoc.listFiles().firstOrNull {
                it.isDirectory && it.name.equals(folderName, ignoreCase = true)
            }
            if (targetDir == null) {
                targetDir = rootDoc.createDirectory(folderName) ?: return@withContext false
            }

            val baseUrl = "$RAW_BASE/${item.path}"

            val mainOk = downloadAndSaveFile(context, "$baseUrl/${item.main}", targetDir, item.main)
            downloadAndSaveFile(context, "$baseUrl/manifest.json", targetDir, "manifest.json")
            item.cover?.let {
                downloadAndSaveFile(context, "$baseUrl/$it", targetDir, it)
            }
            item.icon?.takeIf { it != item.cover }?.let {
                downloadAndSaveFile(context, "$baseUrl/$it", targetDir, it)
            }
            downloadAndSaveFile(context, "$baseUrl/README.md", targetDir, "README.md")
            StorageUtils.ensureNoMedia(targetDir)

            StorageUtils.invalidateCache()
            return@withContext mainOk
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    private fun installFromZipUrl(context: Context, zipUrl: String, name: String): Boolean {
        var tempFile: java.io.File? = null
        return try {
            val url = URL(zipUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                conn.disconnect()
                return false
            }
            val cacheDir = java.io.File(context.cacheDir, "repo_downloads").apply { mkdirs() }
            val file = java.io.File(cacheDir, "dl_${System.currentTimeMillis()}.zip")
            tempFile = file
            conn.inputStream.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            conn.disconnect()

            StorageUtils.importWallpaperFromZip(context, Uri.fromFile(file), name) != null
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            try { tempFile?.delete() } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun downloadAndSaveFile(
        context: Context,
        fileUrl: String,
        targetDir: DocumentFile,
        fileName: String
    ): Boolean {
        return try {
            val url = URL(fileUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            var ok = false
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                var fileDoc = targetDir.findFile(fileName)
                if (fileDoc == null) {
                    val mime = if (fileName.endsWith(".html")) "text/html" else "application/json"
                    fileDoc = targetDir.createFile(mime, fileName)
                }
                if (fileDoc != null) {
                    conn.inputStream.use { input ->
                        val out = try {
                            context.contentResolver.openOutputStream(fileDoc.uri, "wt")
                        } catch (e: Exception) {
                            context.contentResolver.openOutputStream(fileDoc.uri)
                        }
                        out?.use { output -> input.copyTo(output) }
                    }
                    ok = true
                }
            }
            conn.disconnect()
            ok
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
