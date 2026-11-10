package dev.rooni.wallify

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.google.gson.Gson
import dev.rooni.wallify.model.WallpaperItem
import dev.rooni.wallify.model.WallpaperManifest
import dev.rooni.wallify.ui.screens.MainScreen
import dev.rooni.wallify.ui.theme.HTMLWallpaperTheme
import dev.rooni.wallify.util.StorageUtils
import dev.rooni.wallify.util.WallpaperManagerHelper
import dev.rooni.wallify.util.WallpaperShortcutHelper
import dev.rooni.wallify.util.WebViewTimerGate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleShortcut(intent)
        setContent {
            HTMLWallpaperTheme {
                MainScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        WebViewTimerGate.acquire(WebViewTimerGate.OWNER_MAIN, null)
    }

    override fun onPause() {
        super.onPause()
        WebViewTimerGate.release(WebViewTimerGate.OWNER_MAIN, null)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShortcut(intent)
    }

    private fun handleShortcut(intent: Intent?) {
        if (intent?.action != WallpaperShortcutHelper.ACTION_APPLY_WALLPAPER) return
        val folder = intent.getStringExtra(WallpaperShortcutHelper.EXTRA_FOLDER_NAME) ?: return
        val folderUri = intent.getStringExtra(WallpaperShortcutHelper.EXTRA_FOLDER_URI)
        val manifestJson = intent.getStringExtra(WallpaperShortcutHelper.EXTRA_MANIFEST_JSON)
        if (folderUri != null && manifestJson != null) {
            val manifest = runCatching {
                Gson().fromJson(manifestJson, WallpaperManifest::class.java)
            }.getOrNull()
            if (manifest != null) {
                StorageUtils.setActiveWallpaper(
                    applicationContext,
                    WallpaperItem(
                        id = folder,
                        folderName = folder,
                        folderUriString = folderUri,
                        manifest = manifest
                    )
                )
            } else {
                StorageUtils.setActiveWallpaperFolder(applicationContext, folder)
            }
        } else {
            StorageUtils.setActiveWallpaperFolder(applicationContext, folder)
        }
        WallpaperManagerHelper.sendRestartSignal(applicationContext)
        Toast.makeText(
            this,
            getString(R.string.shortcut_applied, folder),
            Toast.LENGTH_SHORT
        ).show()
    }
}
