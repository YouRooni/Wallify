package dev.rooni.wallify.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import com.google.gson.Gson
import dev.rooni.wallify.MainActivity
import dev.rooni.wallify.R
import dev.rooni.wallify.model.WallpaperItem

object WallpaperShortcutHelper {
    const val ACTION_APPLY_WALLPAPER = "dev.rooni.wallify.APPLY_WALLPAPER"
    const val EXTRA_FOLDER_NAME = "shortcut_folder_name"
    const val EXTRA_FOLDER_URI = "shortcut_folder_uri"
    const val EXTRA_MANIFEST_JSON = "shortcut_manifest_json"

    fun requestPinnedShortcut(context: Context, item: WallpaperItem) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            Toast.makeText(context, R.string.shortcut_not_supported, Toast.LENGTH_SHORT).show()
            return
        }

        val title = item.manifest.name ?: item.folderName
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_APPLY_WALLPAPER
            putExtra(EXTRA_FOLDER_NAME, item.folderName)
            putExtra(EXTRA_FOLDER_URI, item.folderUriString)
            putExtra(EXTRA_MANIFEST_JSON, Gson().toJson(item.manifest))
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val builder = ShortcutInfoCompat.Builder(context, "wallpaper_${item.id}")
            .setShortLabel(title.take(20))
            .setLongLabel(context.getString(R.string.shortcut_apply_label, title))
            .setIntent(intent)

        loadSquareIcon(context, item.iconUriString ?: item.coverUriString)?.let {
            builder.setIcon(androidx.core.graphics.drawable.IconCompat.createWithBitmap(it))
        } ?: builder.setIcon(
            androidx.core.graphics.drawable.IconCompat.createWithResource(context, R.mipmap.ic_launcher)
        )

        val shortcut = builder.build()
        ShortcutManagerCompat.updateShortcuts(context, listOf(shortcut))
        ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }

    private fun loadSquareIcon(context: Context, uriString: String?): Bitmap? {
        if (uriString == null) return null
        return try {
            val source = context.contentResolver.openInputStream(Uri.parse(uriString))?.use {
                BitmapFactory.decodeStream(it)
            } ?: return null
            val side = minOf(source.width, source.height)
            val left = (source.width - side) / 2
            val top = (source.height - side) / 2
            val square = Bitmap.createBitmap(source, left, top, side, side)
            if (square !== source) source.recycle()
            square
        } catch (_: Exception) {
            null
        }
    }
}
