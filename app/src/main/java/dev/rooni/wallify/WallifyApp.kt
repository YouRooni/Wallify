package dev.rooni.wallify

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import dev.rooni.wallify.bridge.SystemMetadataProvider
import dev.rooni.wallify.util.StorageUtils

class WallifyApp : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        Thread {
            try {
                StorageUtils.getPrefs(applicationContext).all
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        SystemMetadataProvider.invalidateColorCache()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(coil3.network.okhttp.OkHttpNetworkFetcherFactory())
            }
            .crossfade(true)
            .build()
    }
}
