package dev.rooni.wallify.util

import android.webkit.WebView

object WebViewTimerGate {

    const val OWNER_WALLPAPER = "wallpaper"
    const val OWNER_MAIN = "main"
    const val OWNER_PREVIEW = "preview"
    const val OWNER_SETTINGS = "settings"

    private val holders = mutableSetOf<String>()
    private var paused = false

    private var lastKnownView: WebView? = null

    fun acquire(owner: String, webView: WebView?) {
        if (webView != null) lastKnownView = webView
        holders.add(owner)
        if (!paused) return
        paused = false
        invoke(webView) { it.resumeTimers() }
    }

    fun release(owner: String, webView: WebView?) {
        if (webView != null) lastKnownView = webView
        holders.remove(owner)
        if (paused || holders.isNotEmpty()) return
        paused = true
        invoke(webView) { it.pauseTimers() }
    }

    fun forget(webView: WebView?) {
        if (lastKnownView === webView) lastKnownView = null
    }

    private inline fun invoke(preferred: WebView?, action: (WebView) -> Unit) {
        val view = preferred ?: lastKnownView ?: return
        try {
            action(view)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }
}
