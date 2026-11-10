package dev.rooni.wallify.util

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import dev.rooni.wallify.BuildConfig
import dev.rooni.wallify.bridge.VersionEvaluator
import dev.rooni.wallify.model.WallpaperItem
import dev.rooni.wallify.model.WallpaperManifest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object StorageUtils {

    private const val PREFS_NAME = "wallpaper_app_prefs"
    private const val KEY_ROOT_FOLDER_URI = "root_folder_uri"
    private const val KEY_ACTIVE_WALLPAPER_FOLDER = "active_wallpaper_folder"
    private const val KEY_ACTIVE_WALLPAPER_URI = "active_wallpaper_uri"
    private const val KEY_ACTIVE_WALLPAPER_MANIFEST = "active_wallpaper_manifest"
    private const val KEY_TOUCH_ENABLED = "touch_enabled"
    private const val KEY_TARGET_FPS = "target_fps"
    private const val KEY_QUARANTINED_WALLPAPER = "quarantined_wallpaper"

    private val gson = Gson()

    const val WALLPAPER_EXTENSION = "wallify"

    const val WALLPAPER_MIME_TYPE = "application/octet-stream"

    val IMPORT_MIME_TYPES = arrayOf(
        "application/zip",
        "application/x-zip-compressed",
        "multipart/x-zip",
        WALLPAPER_MIME_TYPE
    )

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveRootFolderUri(context: Context, uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        invalidateCache()
        getPrefs(context).edit().putString(KEY_ROOT_FOLDER_URI, uri.toString()).apply()
    }

    fun getRootFolderUri(context: Context): Uri? {
        val uriStr = getPrefs(context).getString(KEY_ROOT_FOLDER_URI, null) ?: return null
        return Uri.parse(uriStr)
    }

    private const val KEY_LAST_USED_PREFIX = "last_used_"

    fun setActiveWallpaperFolder(context: Context, folderName: String) {
        val cachedItem = wallpapersCache?.find { it.folderName == folderName }
        val editor = getPrefs(context).edit()
            .putString(KEY_ACTIVE_WALLPAPER_FOLDER, folderName)
            .putLong(KEY_LAST_USED_PREFIX + folderName, System.currentTimeMillis())
        if (cachedItem != null) {
            editor
                .putString(KEY_ACTIVE_WALLPAPER_URI, cachedItem.folderUriString)
                .putString(KEY_ACTIVE_WALLPAPER_MANIFEST, gson.toJson(cachedItem.manifest))
        }
        editor.apply()
    }

    fun setActiveWallpaper(context: Context, item: WallpaperItem) {
        getPrefs(context).edit()
            .putString(KEY_ACTIVE_WALLPAPER_FOLDER, item.folderName)
            .putString(KEY_ACTIVE_WALLPAPER_URI, item.folderUriString)
            .putString(KEY_ACTIVE_WALLPAPER_MANIFEST, gson.toJson(item.manifest))
            .putLong(KEY_LAST_USED_PREFIX + item.folderName, System.currentTimeMillis())
            .apply()
    }

    fun getActiveWallpaperFolder(context: Context): String? {
        return getPrefs(context).getString(KEY_ACTIVE_WALLPAPER_FOLDER, null)
    }

    fun getPersistedActiveWallpaper(context: Context): WallpaperItem? {
        val prefs = getPrefs(context)
        val folder = prefs.getString(KEY_ACTIVE_WALLPAPER_FOLDER, null) ?: return null
        val uri = prefs.getString(KEY_ACTIVE_WALLPAPER_URI, null) ?: return null
        val manifestJson = prefs.getString(KEY_ACTIVE_WALLPAPER_MANIFEST, null) ?: return null
        return try {
            WallpaperItem(
                id = folder,
                folderName = folder,
                folderUriString = uri,
                manifest = gson.fromJson(manifestJson, WallpaperManifest::class.java)
                    ?: WallpaperManifest(name = folder)
            )
        } catch (e: Exception) {
            null
        }
    }

    fun persistActiveWallpaperSnapshot(context: Context, item: WallpaperItem) {
        getPrefs(context).edit()
            .putString(KEY_ACTIVE_WALLPAPER_URI, item.folderUriString)
            .putString(KEY_ACTIVE_WALLPAPER_MANIFEST, gson.toJson(item.manifest))
            .apply()
    }

    fun quarantineWallpaper(context: Context, folderName: String?) {
        getPrefs(context).edit().putString(KEY_QUARANTINED_WALLPAPER, folderName).apply()
    }

    fun isWallpaperQuarantined(context: Context, folderName: String?): Boolean =
        folderName != null && getPrefs(context).getString(KEY_QUARANTINED_WALLPAPER, null) == folderName

    fun clearWallpaperQuarantine(context: Context, folderName: String) {
        if (isWallpaperQuarantined(context, folderName)) {
            getPrefs(context).edit().remove(KEY_QUARANTINED_WALLPAPER).apply()
        }
    }

    fun getLastUsedMap(context: Context): Map<String, Long> {
        val result = HashMap<String, Long>()
        for ((key, value) in getPrefs(context).all) {
            if (key.startsWith(KEY_LAST_USED_PREFIX) && value is Long) {
                result[key.removePrefix(KEY_LAST_USED_PREFIX)] = value
            }
        }
        return result
    }

    private const val KEY_RESOLUTION_SCALE = "resolution_scale"

    fun setResolutionScale(context: Context, scalePercent: Int) {
        getPrefs(context).edit().putInt(KEY_RESOLUTION_SCALE, scalePercent).apply()
    }

    fun getResolutionScale(context: Context): Int {
        return getPrefs(context).getInt(KEY_RESOLUTION_SCALE, 80)
    }

    private const val KEY_AUTO_PAUSE_POWER_SAVE = "auto_pause_power_save"

    fun setAutoPausePowerSave(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_PAUSE_POWER_SAVE, enabled).apply()
    }

    fun isAutoPausePowerSave(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_PAUSE_POWER_SAVE, true)
    }

    fun setTargetFps(context: Context, fps: Int) {
        getPrefs(context).edit().putInt(KEY_TARGET_FPS, fps).apply()
    }

    fun getTargetFps(context: Context): Int {
        return getPrefs(context).getInt(KEY_TARGET_FPS, 60)
    }

    private const val KEY_REPO_NOTICE_DISMISSED = "repo_notice_dismissed"

    fun setRepoNoticeDismissed(context: Context, dismissed: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_REPO_NOTICE_DISMISSED, dismissed).apply()
    }

    fun isRepoNoticeDismissed(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_REPO_NOTICE_DISMISSED, false)
    }

    private const val KEY_SIMPLE_RENDER = "simple_render_mode"

    fun setSimpleRenderMode(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SIMPLE_RENDER, enabled).apply()
    }

    fun isSimpleRenderMode(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SIMPLE_RENDER, false)
    }

    private const val KEY_BYPASS_IFRAME = "bypass_iframe_experimental"

    fun setBypassIframeEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_BYPASS_IFRAME, enabled).apply()
    }

    fun isBypassIframeEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_BYPASS_IFRAME, false)
    }

    fun getMaxRefreshRate(context: Context): Int {
        return try {
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.display
            } else {
                null
            }

            var maxRate = display?.refreshRate?.toInt() ?: 60

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && display != null) {
                val modes = display.supportedModes
                for (mode in modes) {
                    val rate = mode.refreshRate.toInt()
                    if (rate > maxRate) {
                        maxRate = rate
                    }
                }
            }
            if (maxRate > 0) maxRate else 60
        } catch (e: Exception) {
            60
        }
    }

    fun setTouchEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_TOUCH_ENABLED, enabled).apply()
    }

    fun isTouchEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_TOUCH_ENABLED, true)
    }

    @Volatile
    private var wallpapersCache: List<WallpaperItem>? = null

    private val scanLock = Any()

    fun invalidateCache() {
        wallpapersCache = null
    }

    fun findWallpaperCached(context: Context, folderName: String): WallpaperItem? {
        return wallpapersCache?.find { it.folderName == folderName }
    }

    fun scanWallpapers(context: Context, forceRefresh: Boolean = false): List<WallpaperItem> {
        if (!forceRefresh) {
            wallpapersCache?.let { return it }
        }
        synchronized(scanLock) {
            if (!forceRefresh) {
                wallpapersCache?.let { return it }
            }

            val rootUri = getRootFolderUri(context) ?: return emptyList()
            val rootDoc = try {
                DocumentFile.fromTreeUri(context, rootUri)
            } catch (e: Exception) {
                null
            } ?: return emptyList()

            if (!rootDoc.exists() || !rootDoc.isDirectory) return emptyList()

            val rootChildren = rootDoc.listFiles()
            if (rootChildren.none { it.name == ".nomedia" }) {
                try {
                    rootDoc.createFile("application/octet-stream", ".nomedia")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val items = mutableListOf<WallpaperItem>()
            val appVersion = BuildConfig.VERSION_NAME

            for (file in rootChildren) {
                if (!file.isDirectory) continue
                val folderName = file.name ?: continue

                val children = file.listFiles()
                val byName = HashMap<String, DocumentFile>(children.size * 2)
                for (child in children) {
                    val n = child.name ?: continue
                    byName[n.lowercase()] = child
                }

                fun pick(vararg names: String?): DocumentFile? {
                    for (n in names) {
                        if (n.isNullOrBlank()) continue
                        byName[n.trimStart('/').lowercase()]?.let { return it }
                    }
                    return null
                }

                val manifestFile = pick("manifest.json", "wallpaper.json")

                var manifest = WallpaperManifest(name = folderName)
                if (manifestFile != null && manifestFile.isFile) {
                    try {
                        context.contentResolver.openInputStream(manifestFile.uri)?.use { stream ->
                            val json = stream.bufferedReader().use { it.readText() }
                            manifest = gson.fromJson(json, WallpaperManifest::class.java) ?: manifest
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                val declaredCover = pick(manifest.cover, "cover.jpg", "cover.png", "cover.webp")
                val declaredIcon = pick(manifest.icon, "icon.png", "icon.jpg", "icon.webp")
                val coverFile = declaredCover ?: declaredIcon
                val iconFile = declaredIcon ?: declaredCover

                var settingsFile = pick(manifest.settingsPath, "settings.html")
                if (settingsFile == null) {
                    val settingsDir = byName["settings"]
                    if (settingsDir != null && settingsDir.isDirectory) {
                        settingsFile = settingsDir.listFiles()
                            .firstOrNull { it.name.equals("index.html", ignoreCase = true) }
                    }
                }

                val (isCompatible, compReason) =
                    VersionEvaluator.isCompatible(appVersion, manifest.minAppVersion)

                items.add(
                    WallpaperItem(
                        id = folderName,
                        folderName = folderName,
                        folderUriString = file.uri.toString(),
                        manifest = manifest,
                        isCompatible = isCompatible,
                        compatibilityReason = compReason,
                        hasSettings = settingsFile != null,
                        settingsUriString = settingsFile?.uri?.toString(),
                        coverUriString = coverFile?.uri?.toString(),
                        iconUriString = iconFile?.uri?.toString(),
                        lastModified = file.lastModified()
                    )
                )
            }
            wallpapersCache = items
            return items
        }
    }

    fun ensureNoMedia(folderDoc: DocumentFile?) {
        try {
            if (folderDoc != null && folderDoc.exists() && folderDoc.isDirectory) {
                val nomedia = folderDoc.findFile(".nomedia")
                if (nomedia == null || !nomedia.exists()) {
                    folderDoc.createFile("application/octet-stream", ".nomedia")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun exportWallpaperToZip(context: Context, wallpaperItem: WallpaperItem, destZipUri: Uri): Boolean {
        return try {
            val folderUri = Uri.parse(wallpaperItem.folderUriString)
            val folderDoc = (try {
                DocumentFile.fromTreeUri(context, folderUri)
            } catch (e: Exception) {
                null
            }) ?: (try {
                DocumentFile.fromSingleUri(context, folderUri)
            } catch (e: Exception) {
                null
            }) ?: return false

            val outStream = if (destZipUri.scheme == "file" && destZipUri.path != null) {
                java.io.File(destZipUri.path!!).outputStream()
            } else {
                context.contentResolver.openOutputStream(destZipUri)
            } ?: return false

            outStream.use { stream ->
                ZipOutputStream(stream).use { zipOut ->
                    zipFolderRecursive(context, folderDoc, "", zipOut)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun readWallpaperText(
        context: Context,
        wallpaperItem: WallpaperItem,
        fileName: String,
        maxChars: Int = 64_000
    ): String? = try {
        val uri = Uri.parse(wallpaperItem.folderUriString)
        val folder = DocumentFile.fromTreeUri(context, uri)
            ?: DocumentFile.fromSingleUri(context, uri)
            ?: return null
        val file = folder.listFiles().firstOrNull {
            it.isFile && it.name.equals(fileName, ignoreCase = true)
        } ?: return null
        context.contentResolver.openInputStream(file.uri)?.bufferedReader()?.use {
            val buffer = CharArray(maxChars)
            val count = it.read(buffer)
            if (count > 0) String(buffer, 0, count) else null
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }

    private fun zipFolderRecursive(
        context: Context,
        folderDoc: DocumentFile,
        parentPath: String,
        zipOut: ZipOutputStream
    ) {
        val files = folderDoc.listFiles()
        val buffer = ByteArray(8192)

        for (file in files) {
            val entryPath = if (parentPath.isEmpty()) file.name!! else "$parentPath/${file.name}"
            if (file.isDirectory) {
                zipFolderRecursive(context, file, entryPath, zipOut)
            } else if (file.isFile) {
                zipOut.putNextEntry(ZipEntry(entryPath))
                context.contentResolver.openInputStream(file.uri)?.use { inputStream ->
                    var length: Int
                    while (inputStream.read(buffer).also { length = it } > 0) {
                        zipOut.write(buffer, 0, length)
                    }
                }
                zipOut.closeEntry()
            }
        }
    }


    data class ZipPreview(
        val manifest: WallpaperManifest?,
        val suggestedName: String,
        val fileCount: Int,
        val hasEntryPoint: Boolean,
        val coverBytes: ByteArray? = null,
        val readme: String? = null
    )

    private fun normalizeEntryName(name: String): String = name.replace('\\', '/').trimStart('/')

    private fun isJunkEntry(name: String): Boolean =
        name.startsWith("__MACOSX/") || name.endsWith("/.DS_Store") || name == ".DS_Store"

    private fun commonRootOf(names: List<String>): String {
        if (names.isEmpty()) return ""
        var prefix = ""
        while (true) {
            val head = names[0].removePrefix(prefix)
            val slash = head.indexOf('/')
            if (slash <= 0) return prefix
            val candidate = prefix + head.substring(0, slash + 1)
            if (names.all { it.startsWith(candidate) }) prefix = candidate else return prefix
        }
    }

    fun sanitizeFolderName(raw: String): String {
        val cleaned = raw.trim()
            .replace("[\\\\/:*?\"<>|]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
            .take(64)
        return cleaned.ifBlank { "wallpaper" }
    }

    fun peekZip(context: Context, zipUri: Uri): ZipPreview? {
        return try {
            val names = mutableListOf<String>()
            context.contentResolver.openInputStream(zipUri)?.use { input ->
                ZipInputStream(input).use { zipIn ->
                    var entry: ZipEntry? = zipIn.nextEntry
                    while (entry != null) {
                        val name = normalizeEntryName(entry.name)
                        if (!entry.isDirectory && name.isNotEmpty() && !isJunkEntry(name)) {
                            names.add(name)
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }
            } ?: return null

            if (names.isEmpty()) return null

            val root = commonRootOf(names)
            val relatives = names.map { it.removePrefix(root) }

            val manifestPath = names.firstOrNull {
                val rel = it.removePrefix(root)
                rel.equals("manifest.json", true) || rel.equals("wallpaper.json", true)
            }

            var manifest: WallpaperManifest? = null
            var coverBytes: ByteArray? = null
            var readme: String? = null
            if (manifestPath != null) {
                context.contentResolver.openInputStream(zipUri)?.use { input ->
                    ZipInputStream(input).use { zipIn ->
                        var entry: ZipEntry? = zipIn.nextEntry
                        while (entry != null) {
                            if (normalizeEntryName(entry.name) == manifestPath) {
                                val json = zipIn.readBytes().toString(Charsets.UTF_8)
                                manifest = try {
                                    gson.fromJson(json, WallpaperManifest::class.java)
                                } catch (e: Exception) {
                                    null
                                }
                                break
                            }
                            zipIn.closeEntry()
                            entry = zipIn.nextEntry
                        }
                    }
                }
            }

            val coverName = manifest?.icon ?: manifest?.cover ?: "cover.png"
            val wantedCover = names.firstOrNull { it.removePrefix(root).equals(coverName.trimStart('/'), true) }
            val wantedReadme = names.firstOrNull { it.removePrefix(root).equals("README.md", true) }
            if (wantedCover != null || wantedReadme != null) {
                context.contentResolver.openInputStream(zipUri)?.use { input ->
                    ZipInputStream(input).use { zipIn ->
                        var entry: ZipEntry? = zipIn.nextEntry
                        while (entry != null) {
                            val entryName = normalizeEntryName(entry.name)
                            when (entryName) {
                                wantedCover -> coverBytes = zipIn.readBytes().takeIf { it.size <= 8 * 1024 * 1024 }
                                wantedReadme -> readme = zipIn.readBytes().toString(Charsets.UTF_8).take(32_000)
                            }
                            zipIn.closeEntry()
                            entry = zipIn.nextEntry
                        }
                    }
                }
            }

            val entryPoint = (manifest?.main ?: "index.html").trimStart('/')
            val hasEntryPoint = relatives.any { it.equals(entryPoint, true) || it.equals("index.html", true) }

            val suggested = manifest?.name?.takeIf { it.isNotBlank() }
                ?: root.trimEnd('/').substringAfterLast('/').takeIf { it.isNotBlank() }
                ?: "wallpaper_${System.currentTimeMillis()}"

            ZipPreview(
                manifest = manifest,
                suggestedName = sanitizeFolderName(suggested),
                fileCount = names.size,
                hasEntryPoint = hasEntryPoint,
                coverBytes = coverBytes,
                readme = readme
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun importWallpaperFromZip(
        context: Context,
        zipUri: Uri,
        targetFolderName: String? = null
    ): String? {
        val rootUri = getRootFolderUri(context) ?: return null
        val rootDoc = DocumentFile.fromTreeUri(context, rootUri) ?: return null
        if (!rootDoc.exists() || !rootDoc.isDirectory) return null

        return try {
            val names = mutableListOf<String>()
            context.contentResolver.openInputStream(zipUri)?.use { input ->
                ZipInputStream(input).use { zipIn ->
                    var entry: ZipEntry? = zipIn.nextEntry
                    while (entry != null) {
                        val name = normalizeEntryName(entry.name)
                        if (!entry.isDirectory && name.isNotEmpty() && !isJunkEntry(name)) names.add(name)
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }
            } ?: return null
            if (names.isEmpty()) return null

            val root = commonRootOf(names)
            val preview = peekZip(context, zipUri)
            val folderName = sanitizeFolderName(
                targetFolderName ?: preview?.suggestedName ?: "wallpaper_${System.currentTimeMillis()}"
            )

            val existing = rootDoc.listFiles().firstOrNull {
                it.isDirectory && it.name.equals(folderName, ignoreCase = true)
            }
            val targetFolder = existing ?: rootDoc.createDirectory(folderName) ?: return null

            val dirCache = HashMap<String, DocumentFile>()
            dirCache[""] = targetFolder

            fun ensureDir(path: String): DocumentFile? {
                dirCache[path]?.let { return it }
                val parentPath = path.substringBeforeLast('/', "")
                val name = path.substringAfterLast('/')
                val parent = ensureDir(parentPath) ?: return null
                val dir = parent.listFiles().firstOrNull { it.isDirectory && it.name == name }
                    ?: parent.createDirectory(name)
                    ?: return null
                dirCache[path] = dir
                return dir
            }

            val buffer = ByteArray(16384)
            context.contentResolver.openInputStream(zipUri)?.use { inStream ->
                ZipInputStream(inStream).use { zipIn ->
                    var entry: ZipEntry? = zipIn.nextEntry
                    while (entry != null) {
                        val rawName = normalizeEntryName(entry.name)
                        if (!entry.isDirectory && rawName.isNotEmpty() && !isJunkEntry(rawName)) {
                            val relative = rawName.removePrefix(root)
                            val segments = relative.split('/').filter { it.isNotEmpty() }
                            if (segments.isNotEmpty() && segments.none { it == ".." }) {
                                val dirPath = segments.dropLast(1).joinToString("/")
                                val fileName = segments.last()
                                val dir = ensureDir(dirPath)
                                if (dir != null) {
                                    val targetFile = dir.listFiles().firstOrNull { it.name == fileName }
                                        ?: dir.createFile("application/octet-stream", fileName)
                                    if (targetFile != null) {
                                        val out = try {
                                            context.contentResolver.openOutputStream(targetFile.uri, "wt")
                                        } catch (e: Exception) {
                                            context.contentResolver.openOutputStream(targetFile.uri)
                                        }
                                        out?.use { outStream ->
                                            var len: Int
                                            while (zipIn.read(buffer).also { len = it } > 0) {
                                                outStream.write(buffer, 0, len)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }
            }

            ensureNoMedia(targetFolder)
            clearWallpaperQuarantine(context, folderName)
            invalidateCache()
            folderName
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun copyFileToWallpaperFolder(context: Context, folderUri: Uri, sourceUri: Uri): String? {
        return try {
            val rootDoc = (try {
                DocumentFile.fromTreeUri(context, folderUri)
            } catch (e: Exception) {
                null
            }) ?: (try {
                DocumentFile.fromSingleUri(context, folderUri)
            } catch (e: Exception) {
                null
            }) ?: return null

            if (!rootDoc.exists() || !rootDoc.isDirectory) return null

            var userFilesDir = rootDoc.findFile("user_files")
            if (userFilesDir == null || !userFilesDir.isDirectory) {
                userFilesDir = rootDoc.createDirectory("user_files") ?: rootDoc
            }
            ensureNoMedia(userFilesDir)

            val contentResolver = context.contentResolver
            val rawType = contentResolver.getType(sourceUri) ?: ""
            val mimeType = if (rawType.isBlank()) "image/jpeg" else rawType

            val extension = when {
                mimeType.contains("png") -> ".png"
                mimeType.contains("jpeg") || mimeType.contains("jpg") -> ".jpg"
                mimeType.contains("webp") -> ".webp"
                mimeType.contains("avif") -> ".avif"
                mimeType.contains("gif") -> ".gif"
                mimeType.contains("webm") -> ".webm"
                mimeType.contains("mp4") -> ".mp4"
                mimeType.contains("quicktime") -> ".mov"
                mimeType.contains("matroska") -> ".mkv"
                mimeType.contains("svg") -> ".svg"
                else -> ".jpg"
            }

            val fileName = "import_" + System.currentTimeMillis() + extension
            val targetDoc = userFilesDir.createFile(mimeType, fileName) ?: return null

            contentResolver.openInputStream(sourceUri)?.use { input ->
                contentResolver.openOutputStream(targetDoc.uri)?.use { output ->
                    input.copyTo(output)
                }
            }

            "user_files/$fileName"
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
