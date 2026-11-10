package dev.rooni.wallify.util

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import dev.rooni.wallify.service.WallifyService

object WallpaperManagerHelper {

    const val ACTION_RELOAD_WALLPAPER = "dev.rooni.wallify.ACTION_RELOAD_WALLPAPER"
    const val ACTION_RESTART_WALLPAPER = "dev.rooni.wallify.ACTION_RESTART_WALLPAPER"
    const val ACTION_PAUSE_WALLPAPER = "dev.rooni.wallify.ACTION_PAUSE_WALLPAPER"
    const val ACTION_RESUME_WALLPAPER = "dev.rooni.wallify.ACTION_RESUME_WALLPAPER"

    fun isWallpaperActive(context: Context): Boolean {
        return try {
            val wm = WallpaperManager.getInstance(context)
            val info = wm.wallpaperInfo
            info?.packageName == context.packageName
        } catch (e: Exception) {
            false
        }
    }

    fun openWallpaperPicker(context: Context) {
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(context, WallifyService::class.java)
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun sendReloadSignal(context: Context) {
        val intent = Intent(ACTION_RELOAD_WALLPAPER).apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }

    fun sendRestartSignal(context: Context) {
        val intent = Intent(ACTION_RESTART_WALLPAPER).apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }

    fun sendPauseSignal(context: Context) {
        val intent = Intent(ACTION_PAUSE_WALLPAPER).apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }

    fun sendResumeSignal(context: Context) {
        val intent = Intent(ACTION_RESUME_WALLPAPER).apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }
}
