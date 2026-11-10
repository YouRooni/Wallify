package dev.rooni.wallify.util

import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

object WallpaperPageScripts {

    const val BOOTSTRAP = """
(function () {
  if (window.__wallifyBootstrap) return;
  window.__wallifyBootstrap = true;
  window.__wallifyFrameInterval = 0;
  window.__wallifyFrozen = false;

  try {
    var raf = window.requestAnimationFrame.bind(window);
    var caf = window.cancelAnimationFrame.bind(window);
    var pending = {};
    var nextId = 1;
    var lastFrame = -1e9;

    window.__wallifyResetFrameClock = function () { lastFrame = -1e9; };

    window.requestAnimationFrame = function (cb) {
      var id = nextId++;
      var tick = function (t) {
        if (window.__wallifyFrozen) {
          pending[id] = raf(tick);
          return;
        }
        var interval = window.__wallifyFrameInterval || 0;
        if (interval > 0 && (t - lastFrame) < interval * 0.5) {
          pending[id] = raf(tick);
          return;
        }
        lastFrame = (t - lastFrame > interval * 2) ? t : lastFrame + interval;
        delete pending[id];
        cb(t);
      };
      pending[id] = raf(tick);
      return id;
    };

    window.cancelAnimationFrame = function (id) {
      var handle = pending[id];
      if (handle !== undefined) {
        caf(handle);
        delete pending[id];
      }
    };
  } catch (e) {}
})();
"""


    const val FREEZE_JS = """
try {
  window.__wallifyFrozen = true;
  var m = document.querySelectorAll('video,audio');
  window.__wallifyResumeMedia = [];
  for (var i = 0; i < m.length; i++) {
    if (!m[i].paused) { window.__wallifyResumeMedia.push(m[i]); m[i].pause(); }
  }
  window.dispatchEvent(new CustomEvent('wallpaperPause'));
} catch (e) {}
"""

    const val UNFREEZE_JS = """
try {
  window.__wallifyFrozen = false;
  if (window.__wallifyResetFrameClock) window.__wallifyResetFrameClock();
  var list = window.__wallifyResumeMedia || [];
  for (var i = 0; i < list.length; i++) { try { list[i].play(); } catch (e) {} }
  window.__wallifyResumeMedia = [];
  window.dispatchEvent(new CustomEvent('wallpaperResume'));
} catch (e) {}
"""

    fun frameIntervalJs(intervalMs: Double): String =
        "try { window.__wallifyFrameInterval = $intervalMs; } catch(e){}"

    const val RESET_FRAME_CLOCK_JS =
        "try { if (window.__wallifyResetFrameClock) window.__wallifyResetFrameClock(); } catch(e){}"

    fun installDocumentStart(webView: WebView): Boolean {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return false
        return try {
            WebViewCompat.addDocumentStartJavaScript(webView, BOOTSTRAP, setOf("*"))
            true
        } catch (t: Throwable) {
            t.printStackTrace()
            false
        }
    }
}
