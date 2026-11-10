package dev.rooni.wallify.ui.screens

import android.content.Context
import android.graphics.Color as AndroidColor
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import dev.rooni.wallify.R
import dev.rooni.wallify.bridge.WallpaperJSBridge
import dev.rooni.wallify.service.WallpaperLocalServer
import dev.rooni.wallify.ui.theme.HTMLWallpaperTheme
import dev.rooni.wallify.util.IframeBypassHelper
import dev.rooni.wallify.util.StorageUtils
import dev.rooni.wallify.util.WallpaperM3UiKit
import dev.rooni.wallify.util.WallpaperManagerHelper
import dev.rooni.wallify.util.WebViewTimerGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WallpaperPreviewActivity : ComponentActivity(), SensorEventListener {

    companion object {
        const val EXTRA_FOLDER_URI = "FOLDER_URI"
        const val EXTRA_FOLDER_NAME = "FOLDER_NAME"
        const val EXTRA_MAIN = "MAIN"
    }

    private var webView: WebView? = null
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var lastGyroNanos = 0L
    private var folderUri: Uri? = null

    private var isAlreadyApplied by mutableStateOf(false)

    @Volatile
    private var scopedServer: WallpaperLocalServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val folderUriString = intent.getStringExtra(EXTRA_FOLDER_URI)
        val folderName = intent.getStringExtra(EXTRA_FOLDER_NAME)
        val main = (intent.getStringExtra(EXTRA_MAIN) ?: "index.html").trimStart('/')

        if (folderUriString == null) {
            finish()
            return
        }
        val uri = Uri.parse(folderUriString)
        folderUri = uri

        refreshAppliedState()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val jsBridge = WallpaperJSBridge(applicationContext, "home", folderName)

        var pendingUrl by mutableStateOf<String?>(null)
        var isLoading by mutableStateOf(true)
        var simulateLocked by mutableStateOf(false)

        lifecycleScope.launch(Dispatchers.IO) {
            val server = WallpaperLocalServer.createScoped(applicationContext, uri)
            scopedServer = server
            val url = server?.urlFor(uri, main)
            withContext(Dispatchers.Main) {
                if (url == null) {
                    Toast.makeText(
                        this@WallpaperPreviewActivity,
                        getString(R.string.preview_server_error),
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                } else {
                    pendingUrl = url
                }
            }
        }

        setContent {
            HTMLWallpaperTheme {
                val statusPadding = WindowInsets.statusBars.asPaddingValues()
                val navPadding = WindowInsets.navigationBars.asPaddingValues()

                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                this@WallpaperPreviewActivity.webView = this
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                setBackgroundColor(AndroidColor.BLACK)
                                resumeTimers()
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    allowFileAccess = true
                                    allowContentAccess = true
                                    allowFileAccessFromFileURLs = true
                                    allowUniversalAccessFromFileURLs = true
                                    mediaPlaybackRequiresUserGesture = false
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    cacheMode = WebSettings.LOAD_DEFAULT
                                }
                                addJavascriptInterface(jsBridge, WallpaperJSBridge.INTERFACE_NAME)

                                webChromeClient = object : WebChromeClient() {
                                    override fun onJsAlert(
                                        view: WebView?, url: String?, message: String?, result: JsResult?
                                    ): Boolean {
                                        result?.confirm()
                                        return true
                                    }

                                    override fun onConsoleMessage(
                                        message: android.webkit.ConsoleMessage?
                                    ): Boolean {
                                        if (message != null) {
                                            android.util.Log.d(
                                                "WallifyJS",
                                                "${message.messageLevel()} ${message.sourceId()}:" +
                                                    "${message.lineNumber()} ${message.message()}"
                                            )
                                        }
                                        return true
                                    }
                                }

                                addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                                    loadWhenMeasured(v as WebView, pendingUrl)
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun shouldInterceptRequest(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): WebResourceResponse? {
                                        val bypassed = IframeBypassHelper
                                            .interceptAndBypassIfNeeded(applicationContext, request)
                                        if (bypassed != null) return bypassed
                                        return super.shouldInterceptRequest(view, request)
                                    }

                                    override fun onReceivedHttpError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        errorResponse: WebResourceResponse?
                                    ) {
                                        super.onReceivedHttpError(view, request, errorResponse)
                                        android.util.Log.w(
                                            "WallifyJS",
                                            "HTTP ${errorResponse?.statusCode} for ${request?.url}"
                                        )
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        isLoading = false
                                        val metadata = jsBridge.getMetadataJson(true)
                                        view?.evaluateJavascript(
                                            WallpaperJSBridge.getInjectionJs(metadata), null
                                        )
                                        view?.let { WallpaperM3UiKit.install(it, applicationContext) }
                                        view?.evaluateJavascript(
                                            WallpaperJSBridge.getDiagnosticsJs("preview"), null
                                        )
                                    }
                                }
                            }
                        },
                        update = { view -> loadWhenMeasured(view, pendingUrl) },
                        modifier = Modifier.fillMaxSize()
                    )

                    AnimatedVisibility(
                        visible = isLoading,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        CircularProgressIndicator(color = Color.White)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)
                                )
                            )
                    )

                    IconButton(
                        onClick = { finish() },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                        modifier = Modifier
                            .padding(statusPadding)
                            .padding(8.dp)
                            .size(44.dp)
                            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                            .align(Alignment.TopStart)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }

                    IconButton(
                        onClick = {
                            simulateLocked = !simulateLocked
                            pushLockState(jsBridge, simulateLocked)
                        },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                        modifier = Modifier
                            .padding(statusPadding)
                            .padding(8.dp)
                            .size(44.dp)
                            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                            .align(Alignment.TopEnd)
                    ) {
                        Icon(
                            imageVector = if (simulateLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                            contentDescription = stringResource(
                                if (simulateLocked) R.string.preview_unlock else R.string.preview_lock
                            )
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(navPadding)
                            .padding(horizontal = 24.dp, vertical = 20.dp)
                    ) {
                        Button(
                            onClick = { applyWallpaper(folderName) },
                            enabled = !isAlreadyApplied,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.height(50.dp)
                        ) {
                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                stringResource(
                                    if (isAlreadyApplied) R.string.repo_btn_applied
                                    else R.string.action_apply
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun pushLockState(jsBridge: WallpaperJSBridge, locked: Boolean) {
        val metadata = jsBridge.getMetadataJson(!locked)
        val event = if (locked) "wallpaperScreenOff" else "wallpaperUnlock"
        webView?.evaluateJavascript(
            WallpaperJSBridge.getUpdateEventJs(metadata, event),
            null
        )
    }

    private fun applyWallpaper(folderName: String?) {
        val uri = folderUri ?: return
        if (folderName == null) return
        lifecycleScope.launch(Dispatchers.IO) {
            StorageUtils.setActiveWallpaperFolder(applicationContext, folderName)
            WallpaperLocalServer.getInstance(applicationContext).setWallpaperUri(uri)
            withContext(Dispatchers.Main) {
                WallpaperManagerHelper.sendRestartSignal(applicationContext)
                if (!WallpaperManagerHelper.isWallpaperActive(applicationContext)) {
                    WallpaperManagerHelper.openWallpaperPicker(this@WallpaperPreviewActivity)
                } else {
                    Toast.makeText(
                        this@WallpaperPreviewActivity,
                        getString(R.string.toast_wallpaper_applied),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                finish()
            }
        }
    }

    private fun refreshAppliedState() {
        val folderName = intent.getStringExtra(EXTRA_FOLDER_NAME) ?: return
        isAlreadyApplied = StorageUtils.getActiveWallpaperFolder(applicationContext) == folderName &&
            WallpaperManagerHelper.isWallpaperActive(applicationContext)
    }

    override fun onResume() {
        super.onResume()
        refreshAppliedState()
        WebViewTimerGate.acquire(WebViewTimerGate.OWNER_PREVIEW, webView)
        webView?.onResume()
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
        webView?.onPause()
        WebViewTimerGate.release(WebViewTimerGate.OWNER_PREVIEW, webView)
    }

    override fun onDestroy() {
        sensorManager?.unregisterListener(this)
        WebViewTimerGate.forget(webView)
        webView?.let {
            try {
                it.stopLoading()
                it.loadUrl("about:blank")
                it.destroy()
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

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        if (event.timestamp - lastGyroNanos < 33_000_000L) return
        lastGyroNanos = event.timestamp
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        webView?.evaluateJavascript(
            WallpaperJSBridge.getGyroscopeEventJs(pitch = y, roll = x, x = x, y = y, z = z),
            null
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

private fun loadWhenMeasured(view: WebView, url: String?) {
    if (url == null) return
    if (view.width <= 0 || view.height <= 0) return
    if (view.tag == url) return
    view.tag = url
    view.loadUrl(url)
}
