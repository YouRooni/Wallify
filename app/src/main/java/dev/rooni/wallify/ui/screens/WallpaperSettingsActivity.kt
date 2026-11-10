package dev.rooni.wallify.ui.screens

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.JsResult
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.rooni.wallify.bridge.WallpaperJSBridge
import dev.rooni.wallify.service.WallpaperLocalServer
import dev.rooni.wallify.ui.theme.HTMLWallpaperTheme
import dev.rooni.wallify.util.IframeBypassHelper
import dev.rooni.wallify.util.StorageUtils
import dev.rooni.wallify.util.WallpaperM3UiKit
import dev.rooni.wallify.util.WebViewTimerGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WallpaperSettingsActivity : ComponentActivity() {

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var activeFolderUri: Uri? = null
    private var webView: WebView? = null
    @Volatile
    private var scopedServer: WallpaperLocalServer? = null
    private var pendingUrl by mutableStateOf<String?>(null)

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        val cb = filePathCallback
        filePathCallback = null
        if (cb != null) {
            if (!uris.isNullOrEmpty()) {
                val known = activeFolderUri
                lifecycleScope.launch(Dispatchers.IO) {
                    val currentFolder = known ?: run {
                        val activeFolder = StorageUtils.getActiveWallpaperFolder(applicationContext)
                        if (activeFolder != null) {
                            StorageUtils.scanWallpapers(applicationContext)
                                .find { it.folderName == activeFolder }
                                ?.folderUriString?.let { Uri.parse(it) }
                        } else null
                    }
                    val savedPaths = ArrayList<String>()
                    if (currentFolder != null) {
                        for (u in uris) {
                            StorageUtils.copyFileToWallpaperFolder(applicationContext, currentFolder, u)
                                ?.let { savedPaths.add(it) }
                        }
                    }
                    if (savedPaths.isNotEmpty()) {
                        val json = org.json.JSONArray(savedPaths).toString()
                        withContext(Dispatchers.Main) {
                            webView?.evaluateJavascript(
                                "try { window.dispatchEvent(new CustomEvent('wallpaperFileImported'," +
                                    " { detail: { paths: $json } })); } catch(e){}",
                                null
                            )
                        }
                    }
                }

                try {
                    cb.onReceiveValue(uris.toTypedArray())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                cb.onReceiveValue(null)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)

        val folderUriString = intent.getStringExtra("FOLDER_URI")
        val folderName = intent.getStringExtra("FOLDER_NAME")
        val settingsPath = intent.getStringExtra("SETTINGS_PATH") ?: "settings/index.html"

        val cleanSettingsPath = settingsPath.trimStart('/')

        lifecycleScope.launch(Dispatchers.IO) {
            val folderUri = if (folderUriString != null) {
                Uri.parse(folderUriString)
            } else {
                val activeFolder = StorageUtils.getActiveWallpaperFolder(applicationContext)
                if (activeFolder != null) {
                    StorageUtils.scanWallpapers(applicationContext)
                        .find { it.folderName == activeFolder }
                        ?.folderUriString?.let { Uri.parse(it) }
                } else null
            }

            val server = folderUri?.let {
                WallpaperLocalServer.createScoped(applicationContext, it)
            }
            scopedServer = server
            val base = if (server != null && folderUri != null) {
                server.urlFor(folderUri, cleanSettingsPath)
            } else null
            withContext(Dispatchers.Main) {
                activeFolderUri = folderUri
                pendingUrl = base?.let { "$it?t=${System.currentTimeMillis()}" }
            }
        }

        val jsBridge = WallpaperJSBridge(applicationContext, "settings", folderName)
        var isLoading by mutableStateOf(true)

        setContent {
            HTMLWallpaperTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(getString(dev.rooni.wallify.R.string.wallpaper_settings_title)) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = getString(dev.rooni.wallify.R.string.action_back)
                                    )
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        AndroidView(
                            factory = { context ->
                                WebView(context).apply {
                                    this@WallpaperSettingsActivity.webView = this
                                    layoutParams = android.view.ViewGroup.LayoutParams(
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    setBackgroundColor(Color.TRANSPARENT)
                                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        allowFileAccess = true
                                        allowContentAccess = true
                                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                        cacheMode = WebSettings.LOAD_DEFAULT
                                    }

                                    addJavascriptInterface(jsBridge, WallpaperJSBridge.INTERFACE_NAME)

                                    webChromeClient = object : WebChromeClient() {
                                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                            super.onProgressChanged(view, newProgress)
                                            if (newProgress >= 90) {
                                                isLoading = false
                                            }
                                        }

                                        override fun onShowFileChooser(
                                            webView: WebView?,
                                            filePathCallback: ValueCallback<Array<Uri>>?,
                                            fileChooserParams: FileChooserParams?
                                        ): Boolean {
                                            this@WallpaperSettingsActivity.filePathCallback?.onReceiveValue(null)
                                            this@WallpaperSettingsActivity.filePathCallback = filePathCallback

                                            val mimeType = fileChooserParams?.acceptTypes?.firstOrNull { it.isNotBlank() } ?: "*/*"
                                            try {
                                                filePickerLauncher.launch(mimeType)
                                            } catch (e: Exception) {
                                                filePickerLauncher.launch("*/*")
                                            }
                                            return true
                                        }

                                        override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                                            MaterialAlertDialogBuilder(this@WallpaperSettingsActivity)
                                                .setTitle(dev.rooni.wallify.R.string.wallpaper_settings_title)
                                                .setMessage(message)
                                                .setPositiveButton(dev.rooni.wallify.R.string.action_ok) { dialogInterface, _ ->
                                                    dialogInterface.dismiss()
                                                    result?.confirm()
                                                }
                                                .setOnCancelListener { result?.confirm() }
                                                .show()
                                            return true
                                        }

                                        override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                                            MaterialAlertDialogBuilder(this@WallpaperSettingsActivity)
                                                .setTitle(dev.rooni.wallify.R.string.dialog_confirm_title)
                                                .setMessage(message)
                                                .setPositiveButton(dev.rooni.wallify.R.string.action_yes) { dialogInterface, _ ->
                                                    dialogInterface.dismiss()
                                                    result?.confirm()
                                                }
                                                .setNegativeButton(dev.rooni.wallify.R.string.action_cancel) { dialogInterface, _ ->
                                                    dialogInterface.dismiss()
                                                    result?.cancel()
                                                }
                                                .setOnCancelListener { result?.cancel() }
                                                .show()
                                            return true
                                        }
                                    }

                                    addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                                        loadSettingsWhenMeasured(v as WebView, pendingUrl)
                                    }

                                    webViewClient = object : WebViewClient() {
                                        override fun shouldInterceptRequest(
                                            view: WebView?,
                                            request: WebResourceRequest?
                                        ): WebResourceResponse? {
                                            val bypassed = IframeBypassHelper.interceptAndBypassIfNeeded(applicationContext, request)
                                            if (bypassed != null) return bypassed
                                            return super.shouldInterceptRequest(view, request)
                                        }

                                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                            super.onPageStarted(view, url, favicon)
                                            isLoading = true
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            isLoading = false
                                            val metadata = jsBridge.getMetadataJson(null)
                                            val injectJs = WallpaperJSBridge.getInjectionJs(metadata)
                                            view?.evaluateJavascript(injectJs, null)
                                            view?.let { WallpaperM3UiKit.install(it, applicationContext) }
                                            view?.evaluateJavascript("window.dispatchEvent(new CustomEvent('wallpaperEngineReady'));", null)
                                        }
                                    }

                                }
                            },
                            update = { view ->
                                loadSettingsWhenMeasured(view, pendingUrl)
                            },
                            onRelease = { it.stopLoading() },
                            modifier = Modifier.fillMaxSize()
                        )

                        if (isLoading) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter)
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        WebViewTimerGate.acquire(WebViewTimerGate.OWNER_SETTINGS, webView)
    }

    override fun onPause() {
        super.onPause()
        WebViewTimerGate.release(WebViewTimerGate.OWNER_SETTINGS, webView)
    }

    override fun onDestroy() {
        WebViewTimerGate.forget(webView)
        webView?.let { web ->
            try {
                web.stopLoading()
                web.loadUrl("about:blank")
                web.onPause()
                web.destroy()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        webView = null
        try {
            scopedServer?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        scopedServer = null
        super.onDestroy()
    }
}

private fun loadSettingsWhenMeasured(view: WebView, url: String?) {
    if (url == null) return
    if (view.width <= 0 || view.height <= 0) return
    if (view.tag == url) return
    view.tag = url
    view.loadUrl(url)
}
