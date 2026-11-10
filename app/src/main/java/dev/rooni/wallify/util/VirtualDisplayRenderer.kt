package dev.rooni.wallify.util

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.view.Surface
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import android.widget.FrameLayout

class VirtualDisplayRenderer(private val context: Context) {

    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var host: FrameLayout? = null

    val isAttached: Boolean
        get() = presentation != null

    fun attach(
        surface: Surface,
        width: Int,
        height: Int,
        densityDpi: Int,
        webView: WebView
    ): Boolean {
        detach(webView)
        if (!surface.isValid || width <= 0 || height <= 0) return false

        return try {
            val displayManager =
                context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return false

            val display = displayManager.createVirtualDisplay(
                DISPLAY_NAME,
                width,
                height,
                densityDpi.coerceAtLeast(1),
                surface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
            ) ?: return false
            virtualDisplay = display

            val container = FrameLayout(context)
            (webView.parent as? ViewGroup)?.removeView(webView)
            container.addView(
                webView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            host = container

            val dialog = Presentation(context, display.display)
            dialog.setContentView(container)
            dialog.window?.apply {
                setType(WindowManager.LayoutParams.TYPE_PRIVATE_PRESENTATION)
                addFlags(
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                )
                setBackgroundDrawable(ColorDrawable(Color.BLACK))
            }
            dialog.show()
            presentation = dialog
            true
        } catch (t: Throwable) {
            t.printStackTrace()
            detach(webView)
            false
        }
    }

    fun detach(webView: WebView?) {
        try {
            presentation?.dismiss()
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        presentation = null

        try {
            if (webView != null) host?.removeView(webView) else host?.removeAllViews()
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        host = null

        try {
            virtualDisplay?.surface = null
            virtualDisplay?.release()
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        virtualDisplay = null
    }

    private companion object {
        const val DISPLAY_NAME = "wallify-wallpaper"
    }
}
