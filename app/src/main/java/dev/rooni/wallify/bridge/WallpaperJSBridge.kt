package dev.rooni.wallify.bridge

import android.content.Context
import android.content.SharedPreferences
import android.webkit.JavascriptInterface
import com.google.gson.Gson
import dev.rooni.wallify.util.StorageUtils
import dev.rooni.wallify.util.WallpaperManagerHelper

class WallpaperJSBridge(
    private val context: Context,
    private var screenType: String = "home",
    private val targetFolderName: String? = null
) {
    private val gson = Gson()

    private fun getPrefs(): SharedPreferences {
        val rawFolder = targetFolderName ?: StorageUtils.getActiveWallpaperFolder(context) ?: "global"
        val folder = rawFolder.lowercase().trim().replace("[^a-z0-9_-]".toRegex(), "_")
        return context.getSharedPreferences("wallpaper_settings_$folder", Context.MODE_PRIVATE)
    }

    @JavascriptInterface
    fun getMetadata(): String = getMetadataJson(null)

    fun getMetadataJson(overrideUnlocked: Boolean? = null): String {
        var isLite = false
        var animDuration = 3.0
        var animDurationUnlock = 2.5
        var animDurationScreenOn = 1.2
        var animDurationScreenOff = 0.4
        var animateOn = "both"

        val rawFolder = targetFolderName ?: StorageUtils.getActiveWallpaperFolder(context)
        if (rawFolder != null) {
            val item = StorageUtils.findWallpaperCached(context, rawFolder)
            if (item != null) {
                isLite = item.manifest.isLite
                animDuration = item.manifest.animationDuration
                animDurationUnlock = item.manifest.animationDurationUnlock
                animDurationScreenOn = item.manifest.animationDurationScreenOn
                animDurationScreenOff = item.manifest.animationDurationScreenOff
                animateOn = item.manifest.animateOn
            }
        }

        val unlocked = overrideUnlocked ?: SystemMetadataProvider.isDeviceUnlocked(context)
        val effectiveScreenType = when {
            screenType == "preview" -> "preview"
            unlocked -> "home"
            else -> "lock"
        }
        val metadata = SystemMetadataProvider.getMetadata(
            context = context,
            screenType = effectiveScreenType,
            overrideUnlocked = unlocked,
            isLite = isLite,
            animationDuration = animDuration,
            animationDurationUnlock = animDurationUnlock,
            animationDurationScreenOn = animDurationScreenOn,
            animationDurationScreenOff = animDurationScreenOff,
            animateOn = animateOn
        )
        return gson.toJson(metadata)
    }

    @JavascriptInterface
    fun saveSetting(key: String, value: String) {
        getPrefs().edit().putString(key, value).apply()
        WallpaperManagerHelper.sendReloadSignal(context)
    }

    @JavascriptInterface
    fun getSetting(key: String, defaultValue: String?): String {
        return getPrefs().getString(key, defaultValue) ?: (defaultValue ?: "")
    }

    @JavascriptInterface
    fun getAllSettings(): String {
        val allMap = getPrefs().all
        return gson.toJson(allMap)
    }

    companion object {
        const val INTERFACE_NAME = "AndroidBridge"

        private fun jsString(raw: String): String = org.json.JSONObject.quote(raw)

        fun getInjectionJs(metadataJson: String): String {
            return """
                (function() {
                    try {
                        const initData = JSON.parse(${jsString(metadataJson)});

                        window.WallpaperEngine = {
                            metadata: initData,
                            getMetadata: function() {
                                if (window.AndroidBridge && window.AndroidBridge.getMetadata) {
                                    return JSON.parse(window.AndroidBridge.getMetadata());
                                }
                                return this.metadata;
                            },
                            saveSetting: function(key, val) {
                                if (window.AndroidBridge && window.AndroidBridge.saveSetting) {
                                    window.AndroidBridge.saveSetting(key, String(val));
                                }
                                try { localStorage.setItem(key, String(val)); } catch(e){}
                            },
                            getSetting: function(key, defaultVal) {
                                if (window.AndroidBridge && window.AndroidBridge.getSetting) {
                                    return window.AndroidBridge.getSetting(key, defaultVal || "");
                                }
                                return localStorage.getItem(key) || defaultVal || "";
                            },
                            getAllSettings: function() {
                                if (window.AndroidBridge && window.AndroidBridge.getAllSettings) {
                                    return JSON.parse(window.AndroidBridge.getAllSettings());
                                }
                                return {};
                            },
                            importFile: function(acceptType) {
                                const input = document.createElement('input');
                                input.type = 'file';
                                if (acceptType) input.accept = acceptType;
                                input.click();
                                return new Promise((resolve) => {
                                    input.onchange = () => {
                                        if (input.files && input.files[0]) {
                                            resolve(URL.createObjectURL(input.files[0]));
                                        } else resolve(null);
                                    };
                                });
                            }
                        };

                        window.dispatchEvent(new CustomEvent('wallpaperEngineReady', { detail: initData }));
                        window.dispatchEvent(new CustomEvent('wallpaperUpdate', { detail: initData }));
                        if (initData.isUnlocked) {
                            window.dispatchEvent(new CustomEvent('wallpaperUnlock', { detail: initData }));
                        }

                        (function() {
                            var polyfillProbes = 0;
                            var polyfillTimer = null;
                            function setupVideoPolyfill() {
                                const videos = document.querySelectorAll('video');
                                if (videos.length === 0) {
                                    polyfillProbes++;
                                    if (polyfillProbes > 5 && polyfillTimer !== null) {
                                        clearInterval(polyfillTimer);
                                        polyfillTimer = null;
                                    }
                                    return;
                                }
                                videos.forEach(video => {
                                    if (video.dataset.wallifyPolyfilled) return;
                                    video.dataset.wallifyPolyfilled = 'true';

                                    const canvas = document.createElement('canvas');
                                    canvas.className = video.className;
                                    if (video.id) canvas.id = video.id + '_canvas_wallify';

                                    const compStyle = window.getComputedStyle(video);
                                    canvas.style.cssText = compStyle.cssText;
                                    canvas.style.opacity = '1';
                                    canvas.style.pointerEvents = 'none';

                                    video.style.opacity = '0.001';

                                    if (video.parentNode) {
                                        video.parentNode.insertBefore(canvas, video);
                                    }

                                    const ctx = canvas.getContext('2d');

                                    function syncAndDraw() {
                                        if (video.readyState >= 2) {
                                            if (canvas.width !== video.videoWidth || canvas.height !== video.videoHeight) {
                                                canvas.width = video.videoWidth || 300;
                                                canvas.height = video.videoHeight || 150;
                                            }
                                            ctx.clearRect(0, 0, canvas.width, canvas.height);
                                            ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
                                        }

                                        if (video.style.filter !== canvas.style.filter) {
                                            canvas.style.filter = video.style.filter;
                                        }

                                        requestAnimationFrame(syncAndDraw);
                                    }

                                    video.play().catch(function(){});
                                    requestAnimationFrame(syncAndDraw);
                                });
                            }

                            if (document.readyState === 'loading') {
                                document.addEventListener('DOMContentLoaded', setupVideoPolyfill);
                            } else {
                                setupVideoPolyfill();
                            }
                            polyfillTimer = setInterval(setupVideoPolyfill, 1000);
                        })();
                    } catch (e) {
                        console.error('WallpaperEngine Init Error:', e);
                    }
                })();
            """.trimIndent()
        }

        fun getUpdateEventJs(metadataJson: String, screenEvent: String? = null): String {
            val screenEventJs = if (screenEvent != null) {
                "window.dispatchEvent(new CustomEvent('$screenEvent', { detail: newData }));"
            } else ""

            return """
                (function() {
                    try {
                        const newData = JSON.parse(${jsString(metadataJson)});
                        if (window.WallpaperEngine) {
                            const oldTheme = window.WallpaperEngine.metadata ? window.WallpaperEngine.metadata.theme : null;
                            window.WallpaperEngine.metadata = newData;
                            window.dispatchEvent(new CustomEvent('wallpaperUpdate', { detail: newData }));

                            if (typeof triggerAnimation === 'function') {
                                try { triggerAnimation(); } catch(e){}
                            }

                            $screenEventJs

                            if (oldTheme !== newData.theme) {
                                window.dispatchEvent(new CustomEvent('wallpaperThemeChange', { detail: newData }));
                            }
                        }
                    } catch (e) {
                        console.error('WallpaperEngine Update Error:', e);
                    }
                })();
            """.trimIndent()
        }

        fun getGyroscopeEventJs(pitch: Float, roll: Float, x: Float, y: Float, z: Float): String {
            return """
                (function() {
                    try {
                        const gyroData = { pitch: $pitch, roll: $roll, x: $x, y: $y, z: $z };
                        window.dispatchEvent(new CustomEvent('wallpaperGyroscope', { detail: gyroData }));
                    } catch(e){}
                })();
            """.trimIndent()
        }

        fun getDiagnosticsJs(env: String): String {
            return """
                (function() {
                    try {
                        var ticks = 0;
                        var stop = false;
                        function tick() { if (stop) return; ticks++; requestAnimationFrame(tick); }
                        requestAnimationFrame(tick);

                        setTimeout(function() {
                            stop = true;
                            var out = [];
                            out.push('env=$env');
                            out.push('viewport=' + window.innerWidth + 'x' + window.innerHeight);
                            out.push('dpr=' + window.devicePixelRatio);
                            out.push('body=' + document.body.scrollWidth + 'x' + document.body.scrollHeight);
                            out.push('raf=' + ticks);
                            out.push('engine=' + (window.WallpaperEngine ? 'yes' : 'no'));

                            var kinds = ['canvas', 'svg', 'img', 'video'];
                            for (var k = 0; k < kinds.length; k++) {
                                var els = document.querySelectorAll(kinds[k]);
                                out.push(kinds[k] + 'Count=' + els.length);
                                for (var i = 0; i < els.length && i < 3; i++) {
                                    var el = els[i];
                                    var r = el.getBoundingClientRect();
                                    var cs = window.getComputedStyle(el);
                                    out.push(kinds[k] + i +
                                        ' at=' + Math.round(r.left) + ',' + Math.round(r.top) +
                                        ' size=' + Math.round(r.width) + 'x' + Math.round(r.height) +
                                        ' buf=' + (el.width || '-') + 'x' + (el.height || '-') +
                                        ' vis=' + cs.visibility +
                                        ' disp=' + cs.display +
                                        ' opacity=' + cs.opacity);
                                }
                            }
                            console.log('WALLIFY_DIAG ' + out.join(' | '));
                        }, 1500);
                    } catch (e) {
                        console.log('WALLIFY_DIAG failed: ' + e);
                    }
                })();
            """.trimIndent()
        }

        fun getOffsetEventJs(xOffset: Float, yOffset: Float): String {
            return """
                (function() {
                    try {
                        const offsetData = { xOffset: $xOffset, yOffset: $yOffset };
                        window.dispatchEvent(new CustomEvent('wallpaperOffset', { detail: offsetData }));
                    } catch(e){}
                })();
            """.trimIndent()
        }
    }
}
