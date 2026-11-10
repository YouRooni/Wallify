package dev.rooni.wallify.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.Choreographer
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import dev.rooni.wallify.bridge.SystemMetadataProvider
import dev.rooni.wallify.bridge.WallpaperJSBridge
import dev.rooni.wallify.model.WallpaperItem
import dev.rooni.wallify.util.StorageUtils
import dev.rooni.wallify.util.WallpaperManagerHelper
import dev.rooni.wallify.util.WebViewTimerGate
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.roundToInt

class WallifyService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return HTMLEngine()
    }

    inner class HTMLEngine : Engine(), Choreographer.FrameCallback, SensorEventListener {

        private val engineId = Integer.toHexString(System.identityHashCode(this))
        private var webView: WebView? = null
        private var jsBridge: WallpaperJSBridge? = null
        private val handler = Handler(Looper.getMainLooper())
        private val ioExecutor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "wallify-engine-io").apply { priority = Thread.NORM_PRIORITY - 1 }
        }

        private var surfaceWidth = 0
        private var surfaceHeight = 0
        private var isVisible = false
        private var isManualPaused = false
        private var useGyroscope = false
        private var initialLoadDone = false

        private var targetFps = 60
        private var renderScale = 1f
        private var isTouchEnabled = true
        private var autoPausePowerSave = true
        private var frameIntervalNanos = 1_000_000_000L / 60

        private var powerSaveActive = false

        private var pageRunning = false

        private var simpleRenderMode = false
        private var virtualDisplayRenderer: dev.rooni.wallify.util.VirtualDisplayRenderer? = null
        private var virtualDisplayFailed = false
        private var documentStartInstalled = false
        private var lockFailures = 0
        private var naturalWidth = 0
        private var naturalHeight = 0
        private var appliedSurfaceScale = 0f

        private var sensorManager: SensorManager? = null
        private var accelerometerSensor: Sensor? = null

        private var isUnlockedCached = true
        private var lastUnlockedReported: Boolean? = null
        private var screenOnEventSentForWake = false
        private var pendingScreenOnUnlocked: Boolean? = null
        private var unlockEventSentForWake = false
        private var lastKeyguardPollMs = 0L
        private var unlockFastPollingUntilMs = 0L
        private var unlockPollingUntilMs = 0L

        private val unlockPollRunnable = object : Runnable {
            override fun run() {
                if (unlockEventSentForWake) return
                if (pollKeyguard(force = true)) return
                val now = SystemClock.uptimeMillis()
                if (now < unlockPollingUntilMs) {
                    handler.postDelayed(
                        this,
                        if (now < unlockFastPollingUntilMs) 16L else 100L
                    )
                }
            }
        }

        private val screenOnDispatchRunnable = Runnable {
            deliverPendingScreenOn()
        }

        private var lastFrameTimeNanos = 0L
        private var frameLoopRunning = false
        private val choreographer = Choreographer.getInstance()

        private var offscreen: Bitmap? = null
        private var offscreenCanvas: Canvas? = null
        private val blitPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        private val srcRect = Rect()
        private val dstRect = Rect()

        private val prefsListener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                handler.post { reloadSettings() }
            }

        private val controlBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_USER_PRESENT -> {
                        reportUnlocked()
                        startFrameLoop()
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        val unlocked = SystemMetadataProvider.isDeviceUnlocked(applicationContext)
                        android.util.Log.d(
                            "WallifyUnlock",
                            "engine=$engineId SCREEN_ON unlocked=$unlocked visible=$isVisible surface=${surfaceWidth}x$surfaceHeight"
                        )
                        isUnlockedCached = unlocked
                        lastUnlockedReported = unlocked
                        unlockEventSentForWake = unlocked
                        queueScreenOn(unlocked)
                        if (!unlocked) startUnlockPolling()
                        enableGyroscopeIfAllowed()
                        startFrameLoop()
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        handler.removeCallbacks(unlockPollRunnable)
                        handler.removeCallbacks(screenOnDispatchRunnable)
                        pendingScreenOnUnlocked = null
                        isUnlockedCached = false
                        screenOnEventSentForWake = false
                        unlockEventSentForWake = false
                        notifyMetadataChange(overrideUnlocked = false, screenEvent = "wallpaperScreenOff")
                        disableGyroscope()
                    }
                    Intent.ACTION_POWER_CONNECTED -> {
                        notifyMetadataChange(screenEvent = "wallpaperCharging")
                    }
                    Intent.ACTION_POWER_DISCONNECTED -> {
                        notifyMetadataChange(screenEvent = "wallpaperDischarging")
                    }
                    Intent.ACTION_BATTERY_CHANGED -> {
                        notifyMetadataChange()
                    }
                    Intent.ACTION_CONFIGURATION_CHANGED -> {
                        SystemMetadataProvider.invalidateColorCache()
                        releaseFixedSurfaceSize()
                        notifyMetadataChange()
                        webView?.evaluateJavascript("try { window.location.reload(true); } catch(e){}", null)
                    }
                    PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                        refreshPowerSaveState()
                        applyRunState()
                        notifyMetadataChange()
                    }
                    WallpaperManagerHelper.ACTION_RELOAD_WALLPAPER -> {
                        reloadSettings()
                        notifyMetadataChange()
                    }
                    WallpaperManagerHelper.ACTION_RESTART_WALLPAPER -> {
                        reloadSettings()
                        webView?.stopLoading()
                        loadActiveWallpaper(bustCache = true)
                        pageRunning = true
                        applyRunState()
                    }
                    WallpaperManagerHelper.ACTION_PAUSE_WALLPAPER -> {
                        isManualPaused = true
                        applyRunState()
                    }
                    WallpaperManagerHelper.ACTION_RESUME_WALLPAPER -> {
                        isManualPaused = false
                        applyRunState()
                    }
                }
            }
        }


        private fun reloadSettings() {
            val ctx = applicationContext
            targetFps = StorageUtils.getTargetFps(ctx).coerceIn(1, 240)
            renderScale = StorageUtils.getResolutionScale(ctx).coerceIn(25, 100) / 100f
            isTouchEnabled = StorageUtils.isTouchEnabled(ctx)
            autoPausePowerSave = StorageUtils.isAutoPausePowerSave(ctx)

            val simple = StorageUtils.isSimpleRenderMode(ctx)
            val modeChanged = simple != simpleRenderMode
            if (modeChanged) {
                simpleRenderMode = simple
                virtualDisplayFailed = false
                lockFailures = 0
            }

            updateFrameInterval()
            applyRunState()
            applySurfaceScaleIfNeeded()

            if (modeChanged) {
                if (webView != null) recreateWebViewForRenderMode()
                return
            }
            updateRenderMode()
        }

        private fun virtualDisplayActive(): Boolean = virtualDisplayRenderer?.isAttached == true

        private fun surfaceScale(): Float =
            if (naturalWidth > 0) surfaceWidth.toFloat() / naturalWidth else 1f

        private fun releaseFixedSurfaceSize() {
            if (appliedSurfaceScale <= 0f) return
            appliedSurfaceScale = 0f
            naturalWidth = 0
            naturalHeight = 0
            try {
                surfaceHolder.setSizeFromLayout()
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }

        private fun applySurfaceScaleIfNeeded() {
            if (naturalWidth <= 0 || naturalHeight <= 0) return
            val wanted = if (simpleRenderMode || virtualDisplayFailed) 1f else renderScale
            if (kotlin.math.abs(wanted - appliedSurfaceScale) < 0.001f) return
            appliedSurfaceScale = wanted

            val w = max(1, (naturalWidth * wanted).roundToInt())
            val h = max(1, (naturalHeight * wanted).roundToInt())
            if (w == surfaceWidth && h == surfaceHeight) return
            try {
                surfaceHolder.setFixedSize(w, h)
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }

        private fun virtualDisplayEligible(): Boolean =
            !simpleRenderMode &&
                !virtualDisplayFailed &&
                webView != null &&
                surfaceWidth > 0 &&
                surfaceHeight > 0

        private fun updateRenderMode() {
            val want = virtualDisplayEligible()
            val active = virtualDisplayActive()
            if (want == active) {
                applyFrameIntervalToPage()
                return
            }

            val view = webView
            if (want && view != null) {
                releaseOffscreen()

                val renderer = virtualDisplayRenderer
                    ?: dev.rooni.wallify.util.VirtualDisplayRenderer(this@WallifyService)
                        .also { virtualDisplayRenderer = it }

                val dpi = max(1, (resources.displayMetrics.densityDpi * surfaceScale()).roundToInt())

                val ok = renderer.attach(surfaceHolder.surface, surfaceWidth, surfaceHeight, dpi, view)
                if (ok) {
                    stopFrameLoop()
                    applyFrameIntervalToPage()
                } else {
                    virtualDisplayFailed = true
                    layoutWebView(surfaceWidth, surfaceHeight)
                    applyFrameIntervalToPage()
                    startFrameLoop()
                    requestRedraw()
                }
            } else {
                virtualDisplayRenderer?.detach(view)
                if (view != null && surfaceWidth > 0 && surfaceHeight > 0) {
                    layoutWebView(surfaceWidth, surfaceHeight)
                }
                applyFrameIntervalToPage()
                startFrameLoop()
                requestRedraw()
            }
        }

        private fun forceSurfaceRecreate() {
            lockFailures = 0
            try {
                surfaceHolder.setFormat(PixelFormat.TRANSLUCENT)
                surfaceHolder.setFormat(PixelFormat.OPAQUE)
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }

        private fun applyFrameIntervalToPage() {
            val intervalMs = if (virtualDisplayActive()) 1000.0 / max(1, effectiveFps()) else 0.0
            webView?.evaluateJavascript(
                dev.rooni.wallify.util.WallpaperPageScripts.frameIntervalJs(intervalMs),
                null
            )
        }

        private fun refreshPowerSaveState() {
            powerSaveActive = try {
                val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
                pm?.isPowerSaveMode == true
            } catch (e: Exception) {
                false
            }
            updateFrameInterval()
        }

        private fun effectiveFps(): Int = targetFps

        private fun updateFrameInterval() {
            frameIntervalNanos = 1_000_000_000L / max(1, effectiveFps())
            applyFrameIntervalToPage()
        }

        private fun shouldFreeze(): Boolean =
            isManualPaused || (powerSaveActive && autoPausePowerSave)

        private fun setPageRunning(running: Boolean) {
            if (running == pageRunning) return
            pageRunning = running
            val view = webView
            if (running) {
                WebViewTimerGate.acquire(WebViewTimerGate.OWNER_WALLPAPER, view)
                view?.onResume()
                view?.evaluateJavascript(dev.rooni.wallify.util.WallpaperPageScripts.UNFREEZE_JS, null)
                applyFrameIntervalToPage()
            } else if (view != null) {
                view.evaluateJavascript(dev.rooni.wallify.util.WallpaperPageScripts.FREEZE_JS) {
                    if (!pageRunning) {
                        view.onPause()
                        WebViewTimerGate.release(WebViewTimerGate.OWNER_WALLPAPER, view)
                    }
                }
            } else {
                WebViewTimerGate.release(WebViewTimerGate.OWNER_WALLPAPER, null)
            }
        }

        private fun applyRunState() {
            if (isVisible && !shouldFreeze()) {
                setPageRunning(true)
                enableGyroscopeIfAllowed()
                startFrameLoop()
            } else {
                disableGyroscope()
                stopFrameLoop()
                setPageRunning(false)
            }
        }


        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            android.util.Log.d("WallifyUnlock", "engine=$engineId created")
            setTouchEventsEnabled(true)

            reloadSettings()
            refreshPowerSaveState()
            isUnlockedCached = SystemMetadataProvider.isDeviceUnlocked(applicationContext)

            StorageUtils.getPrefs(applicationContext)
                .registerOnSharedPreferenceChangeListener(prefsListener)

            sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            accelerometerSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

            ioExecutor.execute { WallpaperLocalServer.getInstance(applicationContext) }

            handler.post { initWebView() }

            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_CONFIGURATION_CHANGED)
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
                addAction(WallpaperManagerHelper.ACTION_RELOAD_WALLPAPER)
                addAction(WallpaperManagerHelper.ACTION_RESTART_WALLPAPER)
                addAction(WallpaperManagerHelper.ACTION_PAUSE_WALLPAPER)
                addAction(WallpaperManagerHelper.ACTION_RESUME_WALLPAPER)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(controlBroadcastReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(controlBroadcastReceiver, filter)
            }
        }

        override fun onDestroy() {
            android.util.Log.d("WallifyUnlock", "engine=$engineId destroyed")
            super.onDestroy()
            handler.removeCallbacks(unlockPollRunnable)
            handler.removeCallbacks(screenOnDispatchRunnable)
            disableGyroscope()
            stopFrameLoop()
            try {
                StorageUtils.getPrefs(applicationContext)
                    .unregisterOnSharedPreferenceChangeListener(prefsListener)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                unregisterReceiver(controlBroadcastReceiver)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            ioExecutor.shutdownNow()
            handler.post {
                virtualDisplayRenderer?.detach(webView)
                virtualDisplayRenderer = null
                WebViewTimerGate.release(WebViewTimerGate.OWNER_WALLPAPER, webView)
                WebViewTimerGate.forget(webView)
                webView?.destroy()
                webView = null
                offscreenCanvas = null
                offscreen?.recycle()
                offscreen = null
            }
        }


        private var lastGyroTimestampNanos = 0L

        private fun enableGyroscopeIfAllowed() {
            if (useGyroscope && isVisible && !shouldFreeze() && accelerometerSensor != null) {
                disableGyroscope()
                sensorManager?.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_UI)
            }
        }

        private fun disableGyroscope() {
            sensorManager?.unregisterListener(this)
        }

        override fun onSensorChanged(event: SensorEvent?) {
            if (event == null || !useGyroscope || !isVisible || shouldFreeze()) return
            if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

            if (event.timestamp - lastGyroTimestampNanos < 33_000_000L) return
            lastGyroTimestampNanos = event.timestamp

            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            webView?.evaluateJavascript(
                WallpaperJSBridge.getGyroscopeEventJs(pitch = y, roll = x, x = x, y = y, z = z),
                null
            )
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

        override fun onOffsetsChanged(
            xOffset: Float,
            yOffset: Float,
            xOffsetStep: Float,
            yOffsetStep: Float,
            xPixelOffset: Int,
            yPixelOffset: Int
        ) {
            super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep, xPixelOffset, yPixelOffset)
            if (isVisible && !shouldFreeze()) {
                webView?.evaluateJavascript(WallpaperJSBridge.getOffsetEventJs(xOffset, yOffset), null)
            }
        }


        private fun initWebView() {
            val context = applicationContext
            val screenType = if (isPreview) "preview" else "home"
            jsBridge = WallpaperJSBridge(context, screenType)

            webView = WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                setBackgroundColor(Color.BLACK)

                setWillNotDraw(false)
                setLayerType(View.LAYER_TYPE_NONE, null)

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

                addJavascriptInterface(jsBridge!!, WallpaperJSBridge.INTERFACE_NAME)

                webChromeClient = object : WebChromeClient() {
                    override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                        result?.confirm()
                        return true
                    }

                    override fun onConsoleMessage(message: android.webkit.ConsoleMessage?): Boolean {
                        if (message != null) {
                            android.util.Log.d(
                                "WallifyJS",
                                "${message.messageLevel()} ${message.sourceId()}:${message.lineNumber()} ${message.message()}"
                            )
                        }
                        return true
                    }

                    override fun getDefaultVideoPoster(): android.graphics.Bitmap? {
                        return android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun onRenderProcessGone(
                        view: WebView?,
                        detail: android.webkit.RenderProcessGoneDetail?
                    ): Boolean {
                        val failedFolder = StorageUtils.getActiveWallpaperFolder(applicationContext)
                        StorageUtils.quarantineWallpaper(applicationContext, failedFolder)
                        android.util.Log.e(
                            "WallifySafety",
                            "WebView renderer остановлен для $failedFolder, crash=${detail?.didCrash()}"
                        )
                        handler.post {
                            stopFrameLoop()
                            virtualDisplayRenderer?.detach(view)
                            if (webView === view) webView = null
                            try {
                                view?.destroy()
                            } catch (_: Throwable) {
                            }
                            initWebView()
                        }
                        return true
                    }

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: android.webkit.WebResourceRequest?
                    ): android.webkit.WebResourceResponse? {
                        val bypassed = dev.rooni.wallify.util.IframeBypassHelper
                            .interceptAndBypassIfNeeded(applicationContext, request)
                        if (bypassed != null) return bypassed
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onReceivedHttpError(
                        view: WebView?,
                        request: android.webkit.WebResourceRequest?,
                        errorResponse: android.webkit.WebResourceResponse?
                    ) {
                        super.onReceivedHttpError(view, request, errorResponse)
                        android.util.Log.w(
                            "WallifyJS",
                            "HTTP ${errorResponse?.statusCode} for ${request?.url}"
                        )
                    }

                    override fun onPageStarted(
                        view: WebView?,
                        url: String?,
                        favicon: android.graphics.Bitmap?
                    ) {
                        super.onPageStarted(view, url, favicon)
                        if (!documentStartInstalled) {
                            view?.evaluateJavascript(
                                dev.rooni.wallify.util.WallpaperPageScripts.BOOTSTRAP,
                                null
                            )
                        }
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        val initialMetadata = jsBridge?.getMetadata() ?: return
                        view?.evaluateJavascript(WallpaperJSBridge.getInjectionJs(initialMetadata), null)
                        view?.let { dev.rooni.wallify.util.WallpaperM3UiKit.install(it, applicationContext) }
                        view?.evaluateJavascript(WallpaperJSBridge.getDiagnosticsJs("engine"), null)
                        requestRedraw()
                        this@HTMLEngine.handler.post { applyFrameIntervalToPage() }
                    }
                }
            }

            documentStartInstalled = webView?.let {
                dev.rooni.wallify.util.WallpaperPageScripts.installDocumentStart(it)
            } == true

            if (surfaceWidth > 0 && surfaceHeight > 0) {
                updateRenderMode()
                if (!virtualDisplayActive()) {
                    webView?.dispatchWindowVisibilityChanged(View.VISIBLE)
                }
                initialLoadDone = true
                loadActiveWallpaper(bustCache = false)
            }
        }

        private fun recreateWebViewForRenderMode() {
            stopFrameLoop()
            virtualDisplayRenderer?.detach(webView)
            releaseOffscreen()

            webView?.let { old ->
                try {
                    old.stopLoading()
                    old.loadUrl("about:blank")
                    old.destroy()
                } catch (t: Throwable) {
                    t.printStackTrace()
                }
            }
            webView = null
            initialLoadDone = false
            initWebView()
        }

        private fun layoutWebView(width: Int, height: Int) {
            if (virtualDisplayActive()) return
            val view = webView ?: return
            view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, width, height)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            android.util.Log.d(
                "WallifyUnlock",
                "engine=$engineId surfaceChanged=${width}x$height visible=$isVisible"
            )
            if (appliedSurfaceScale <= 0f) {
                naturalWidth = width
                naturalHeight = height
            }
            surfaceWidth = width
            surfaceHeight = height
            releaseOffscreen()
            if (virtualDisplayActive()) virtualDisplayRenderer?.detach(webView)
            handler.post {
                applySurfaceScaleIfNeeded()
                layoutWebView(surfaceWidth, surfaceHeight)
                if (!initialLoadDone && webView != null) {
                    initialLoadDone = true
                    loadActiveWallpaper(bustCache = false)
                }
                updateRenderMode()
                requestRedraw()
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            android.util.Log.d("WallifyUnlock", "engine=$engineId surfaceDestroyed visible=$isVisible")
            stopFrameLoop()
            virtualDisplayRenderer?.detach(webView)
            releaseOffscreen()
        }

        private val defaultHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <style>
                    body {
                        background: linear-gradient(135deg, #6750A4, #1D192B);
                        color: #ffffff;
                        font-family: sans-serif;
                        display: flex;
                        flex-direction: column;
                        justify-content: center;
                        align-items: center;
                        height: 100vh;
                        margin: 0;
                        text-align: center;
                        padding: 20px;
                    }
                    h1 { font-size: 26px; margin-bottom: 8px; color: #D0BCFF; }
                    p { font-size: 15px; opacity: 0.9; }
                </style>
            </head>
            <body>
                <h1>Wallify</h1>
                <p>Выберите комплект обоев в приложении</p>
            </body>
            </html>
        """.trimIndent()

        private fun loadActiveWallpaper(bustCache: Boolean) {
            disableGyroscope()
            webView?.stopLoading()
            webView?.loadUrl("about:blank")
            if (bustCache) webView?.clearCache(true)

            val ctx = applicationContext
            ioExecutor.execute {
                if (bustCache) {
                    StorageUtils.invalidateCache()
                    WallpaperLocalServer.getInstance(ctx).invalidateFileCache()
                }
                val activeFolder = StorageUtils.getActiveWallpaperFolder(ctx)
                android.util.Log.d("WallifyState", "activeFolder=$activeFolder")
                val rootUri = StorageUtils.getRootFolderUri(ctx)
                var found: WallpaperItem? = null
                var url: String? = null

                val quarantined = StorageUtils.isWallpaperQuarantined(ctx, activeFolder)
                if (!quarantined && activeFolder != null && rootUri != null) {
                    found = StorageUtils.scanWallpapers(ctx).find { it.folderName == activeFolder }
                    found?.let { StorageUtils.persistActiveWallpaperSnapshot(ctx, it) }
                }
                if (!quarantined && found == null) {
                    found = StorageUtils.getPersistedActiveWallpaper(ctx)
                }
                if (found != null) {
                    val server = WallpaperLocalServer.getInstance(ctx)
                    val folderUri = Uri.parse(found.folderUriString)
                    server.setWallpaperUri(folderUri)
                    val mainFile = (found.manifest.main ?: "index.html").trimStart('/')
                    val base = server.urlFor(folderUri, mainFile)
                    if (base != null) {
                        url = if (bustCache) "$base?t=${System.currentTimeMillis()}" else base
                    } else if (quarantined) {
                        useGyroscope = false
                        webView?.loadDataWithBaseURL(
                            null,
                            safetyFallbackHtml(activeFolder),
                            "text/html",
                            "UTF-8",
                            null
                        )
                    } else {
                        found = null
                    }
                }

                val item = found
                val finalUrl = url
                handler.post {
                    if (item != null && finalUrl != null) {
                        useGyroscope = item.manifest.useGyroscope
                        enableGyroscopeIfAllowed()
                        webView?.loadUrl(finalUrl)
                    } else {
                        useGyroscope = false
                        webView?.loadDataWithBaseURL(null, defaultHtml, "text/html", "UTF-8", null)
                    }
                }
            }
        }

        private fun safetyFallbackHtml(folderName: String?): String = """
            <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
            <style>html,body{height:100%;margin:0;background:#121212;color:#fff;font-family:sans-serif}
            body{display:flex;align-items:center;justify-content:center;text-align:center}
            main{max-width:420px;padding:32px}h1{color:#d0bcff;font-size:24px}p{line-height:1.5;opacity:.88}
            small{opacity:.6}</style></head><body><main><h1>Обои остановлены</h1>
            <p>Wallify обнаружил критическую перегрузку WebView и отключил эти обои, чтобы защитить телефон.</p>
            <small>${android.text.TextUtils.htmlEncode(folderName ?: "Неизвестные обои")}<br>
            Откройте Wallify и выберите другой комплект либо переустановите этот.</small>
            </main></body></html>
        """.trimIndent()


        private fun startFrameLoop() {
            if (!isVisible || shouldFreeze()) return
            if (virtualDisplayActive()) return
            if (frameLoopRunning) return
            frameLoopRunning = true
            lastFrameTimeNanos = 0L
            choreographer.postFrameCallback(this)
        }

        private fun stopFrameLoop() {
            frameLoopRunning = false
            choreographer.removeFrameCallback(this)
        }

        override fun doFrame(frameTimeNanos: Long) {
            if (!frameLoopRunning) return
            if (!isVisible || shouldFreeze()) {
                frameLoopRunning = false
                return
            }

            if (!isUnlockedCached) pollKeyguard(force = false)

            if (lastFrameTimeNanos == 0L) {
                lastFrameTimeNanos = frameTimeNanos - frameIntervalNanos
            }

            val elapsed = frameTimeNanos - lastFrameTimeNanos
            if (elapsed >= frameIntervalNanos) {
                lastFrameTimeNanos += frameIntervalNanos * (elapsed / frameIntervalNanos)
                if (frameTimeNanos - lastFrameTimeNanos > frameIntervalNanos * 4) {
                    lastFrameTimeNanos = frameTimeNanos
                }
                drawFrame()
            }
            choreographer.postFrameCallback(this)
        }

        private fun startUnlockPolling() {
            handler.removeCallbacks(unlockPollRunnable)
            val now = SystemClock.uptimeMillis()
            unlockFastPollingUntilMs = now + 2_000L
            unlockPollingUntilMs = now + 60_000L
            handler.post(unlockPollRunnable)
        }

        private fun queueScreenOn(unlocked: Boolean) {
            pendingScreenOnUnlocked = unlocked
            if (!virtualDisplayActive()) {
                webView?.dispatchWindowVisibilityChanged(View.VISIBLE)
            }
            if (isVisible && !shouldFreeze()) setPageRunning(true)
            webView?.evaluateJavascript(
                dev.rooni.wallify.util.WallpaperPageScripts.RESET_FRAME_CLOCK_JS,
                null
            )
            applyFrameIntervalToPage()
            requestRedraw()
            handler.removeCallbacks(screenOnDispatchRunnable)
            if (isVisible) {
                handler.postDelayed(screenOnDispatchRunnable, 16L)
            }
        }

        private fun deliverPendingScreenOn() {
            val unlocked = pendingScreenOnUnlocked ?: return
            pendingScreenOnUnlocked = null
            screenOnEventSentForWake = true
            android.util.Log.d(
                "WallifyUnlock",
                "engine=$engineId SCREEN_ON dispatched visible=$isVisible virtual=${virtualDisplayActive()} uptime=${SystemClock.uptimeMillis()}"
            )
            notifyMetadataChange(
                overrideUnlocked = unlocked,
                screenEvent = "wallpaperScreenOn"
            )
        }

        private fun reportUnlocked() {
            handler.removeCallbacks(unlockPollRunnable)
            isUnlockedCached = true
            lastUnlockedReported = true
            if (!unlockEventSentForWake) {
                android.util.Log.d(
                    "WallifyUnlock",
                    "engine=$engineId UNLOCK dispatched visible=$isVisible uptime=${SystemClock.uptimeMillis()}"
                )
                unlockEventSentForWake = true
                notifyMetadataChange(overrideUnlocked = true, screenEvent = "wallpaperUnlock")
            } else {
                notifyMetadataChange(overrideUnlocked = true)
            }
        }

        private fun pollKeyguard(force: Boolean): Boolean {
            val now = SystemClock.uptimeMillis()
            if (!force && now - lastKeyguardPollMs < 250L) return isUnlockedCached
            lastKeyguardPollMs = now

            val unlocked = SystemMetadataProvider.isDeviceUnlocked(applicationContext)
            val wasUnlocked = lastUnlockedReported
            isUnlockedCached = unlocked
            if (unlocked && wasUnlocked == false) {
                reportUnlocked()
            } else if (wasUnlocked == null || wasUnlocked != unlocked) {
                lastUnlockedReported = unlocked
                notifyMetadataChange(overrideUnlocked = unlocked)
            }
            return unlocked
        }

        private fun ensureOffscreen(w: Int, h: Int): Boolean {
            val current = offscreen
            if (current != null && !current.isRecycled && current.width == w && current.height == h) {
                return true
            }
            releaseOffscreen()
            return try {
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                offscreen = bmp
                offscreenCanvas = Canvas(bmp)
                true
            } catch (e: Throwable) {
                offscreen = null
                offscreenCanvas = null
                false
            }
        }

        private fun releaseOffscreen() {
            offscreenCanvas = null
            offscreen?.let { if (!it.isRecycled) it.recycle() }
            offscreen = null
        }

        private fun drawFrame() {
            if (virtualDisplayActive()) return
            val view = webView ?: return
            if (surfaceWidth <= 0 || surfaceHeight <= 0) return
            val holder = surfaceHolder
            val surface = holder.surface
            if (!surface.isValid) return

            val scale = renderScale
            val useOffscreen = scale < 0.999f

            var canvas: Canvas? = null
            try {
                if (useOffscreen) {
                    val rw = max(1, (surfaceWidth * scale).roundToInt())
                    val rh = max(1, (surfaceHeight * scale).roundToInt())
                    if (!ensureOffscreen(rw, rh)) {
                        canvas = holder.lockCanvas() ?: return onLockFailed()
                        view.draw(canvas)
                    } else {
                        val oc = offscreenCanvas ?: return
                        val bmp = offscreen ?: return
                        oc.save()
                        oc.scale(rw.toFloat() / surfaceWidth, rh.toFloat() / surfaceHeight)
                        view.draw(oc)
                        oc.restore()

                        canvas = holder.lockCanvas() ?: return onLockFailed()
                        srcRect.set(0, 0, rw, rh)
                        dstRect.set(0, 0, surfaceWidth, surfaceHeight)
                        canvas.drawBitmap(bmp, srcRect, dstRect, blitPaint)
                    }
                } else {
                    canvas = holder.lockCanvas() ?: return onLockFailed()
                    view.draw(canvas)
                }
                lockFailures = 0
            } catch (e: Throwable) {
                e.printStackTrace()
            } finally {
                val c = canvas
                if (c != null) {
                    try {
                        holder.unlockCanvasAndPost(c)
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                }
            }
        }

        private fun onLockFailed() {
            lockFailures++
            if (lockFailures >= 5) {
                forceSurfaceRecreate()
            } else {
                requestRedrawSoon()
            }
        }

        private fun requestRedrawSoon() {
            if (!isVisible || frameLoopRunning) return
            handler.postDelayed({ if (isVisible) drawFrame() }, 32L)
        }

        private fun requestRedraw() {
            if (isVisible) drawFrame()
        }


        override fun onTouchEvent(event: MotionEvent) {
            super.onTouchEvent(event)
            if (isTouchEnabled && !shouldFreeze()) {
                webView?.dispatchTouchEvent(event)
                requestRedraw()
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            this.isVisible = visible
            android.util.Log.d(
                "WallifyUnlock",
                "engine=$engineId visibility=$visible surface=${surfaceWidth}x$surfaceHeight virtual=${virtualDisplayActive()}"
            )
            if (visible) {
                if (!virtualDisplayActive()) {
                    webView?.dispatchWindowVisibilityChanged(View.VISIBLE)
                }
                setPageRunning(!shouldFreeze())
                webView?.evaluateJavascript(
                    dev.rooni.wallify.util.WallpaperPageScripts.RESET_FRAME_CLOCK_JS,
                    null
                )

                val unlocked = SystemMetadataProvider.isDeviceUnlocked(applicationContext)
                val wasUnlocked = lastUnlockedReported
                isUnlockedCached = unlocked
                if (pendingScreenOnUnlocked != null) {
                    handler.removeCallbacks(screenOnDispatchRunnable)
                    deliverPendingScreenOn()
                }
                if (unlocked && wasUnlocked == false) {
                    reportUnlocked()
                } else {
                    lastUnlockedReported = unlocked
                    notifyMetadataChange(overrideUnlocked = unlocked)
                    if (!unlocked) startUnlockPolling()
                }

                enableGyroscopeIfAllowed()
                startFrameLoop()
            } else {
                handler.removeCallbacks(unlockPollRunnable)
                handler.removeCallbacks(screenOnDispatchRunnable)
                pendingScreenOnUnlocked = null
                isUnlockedCached = false
                lastUnlockedReported = false
                screenOnEventSentForWake = false
                unlockEventSentForWake = false
                disableGyroscope()
                stopFrameLoop()
                setPageRunning(false)
                if (!virtualDisplayActive()) {
                    webView?.dispatchWindowVisibilityChanged(View.GONE)
                }
            }
        }

        private fun notifyMetadataChange(overrideUnlocked: Boolean? = null, screenEvent: String? = null) {
            val view = webView ?: return
            if (!pageRunning && screenEvent == null) return
            val updatedMetadata = jsBridge?.getMetadataJson(overrideUnlocked) ?: return
            view.evaluateJavascript(
                WallpaperJSBridge.getUpdateEventJs(updatedMetadata, screenEvent)
            ) {
                if (screenEvent != null) {
                    android.util.Log.d(
                        "WallifyUnlock",
                        "engine=$engineId JS executed event=$screenEvent visible=$isVisible"
                    )
                }
            }
            requestRedraw()
        }
    }
}
