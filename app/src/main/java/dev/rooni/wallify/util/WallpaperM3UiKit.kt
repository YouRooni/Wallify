package dev.rooni.wallify.util

import android.content.Context
import android.webkit.WebView
import org.json.JSONObject

object WallpaperM3UiKit {
    private const val STYLE_ASSET = "wallify-ui/wallify-m3.css"
    private const val SCRIPT_ASSET = "wallify-ui/wallify-m3.js"

    @Volatile private var injection: String? = null

    fun install(webView: WebView, context: Context) {
        val script = injection ?: synchronized(this) {
            injection ?: buildInjection(context).also { injection = it }
        }
        webView.evaluateJavascript(script, null)
    }

    private fun buildInjection(context: Context): String {
        val css = context.assets.open(STYLE_ASSET).bufferedReader().use { it.readText() }
        val js = context.assets.open(SCRIPT_ASSET).bufferedReader().use { it.readText() }
        return """
            (function () {
              var id = 'wallify-m3-ui-kit';
              var old = document.getElementById(id);
              if (old) old.remove();
              var style = document.createElement('style');
              style.id = id;
              style.textContent = ${JSONObject.quote(css)};
              (document.head || document.documentElement).appendChild(style);
              ${js}
              if (window.WallifyM3) {
                window.WallifyM3.setColors((window.wallpaperMetadata || {}).accentColors);
                window.WallifyM3.refresh();
              }
            })();
        """.trimIndent()
    }
}
