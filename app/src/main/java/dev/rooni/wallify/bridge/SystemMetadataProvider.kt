package dev.rooni.wallify.bridge

import android.app.KeyguardManager
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.toArgb
import dev.rooni.wallify.BuildConfig
import dev.rooni.wallify.model.AccentColors
import dev.rooni.wallify.model.BatteryInfo
import dev.rooni.wallify.model.DeviceInfo
import dev.rooni.wallify.model.WallpaperMetadata

object SystemMetadataProvider {

    @Volatile
    private var cachedDarkColors: AccentColors? = null

    @Volatile
    private var cachedLightColors: AccentColors? = null

    fun invalidateColorCache() {
        cachedDarkColors = null
        cachedLightColors = null
    }

    private fun accentColorsCached(context: Context, isDark: Boolean): AccentColors {
        val cached = if (isDark) cachedDarkColors else cachedLightColors
        if (cached != null) return cached
        val fresh = getMonetColors(context, isDark)
        if (isDark) cachedDarkColors = fresh else cachedLightColors = fresh
        return fresh
    }

    fun getMetadata(
        context: Context,
        screenType: String = "home",
        overrideUnlocked: Boolean? = null,
        isLite: Boolean = false,
        animationDuration: Double = 3.0,
        animationDurationUnlock: Double = 2.5,
        animationDurationScreenOn: Double = 1.2,
        animationDurationScreenOff: Double = 0.4,
        animateOn: String = "both"
    ): WallpaperMetadata {
        val isDark = isDarkTheme(context)
        val themeStr = if (isDark) "dark" else "light"
        val accentColors = accentColorsCached(context, isDark)
        val isUnlocked = overrideUnlocked ?: isDeviceUnlocked(context)

        val device = DeviceInfo(
            model = Build.MODEL ?: "Unknown",
            manufacturer = Build.MANUFACTURER ?: "Unknown",
            androidVersion = Build.VERSION.SDK_INT,
            appVersion = BuildConfig.VERSION_NAME
        )

        return WallpaperMetadata(
            theme = themeStr,
            accentColors = accentColors,
            battery = getBatteryInfo(context),
            screen = screenType,
            isUnlocked = isUnlocked,
            device = device,
            isLite = isLite,
            animationDuration = animationDuration,
            animationDurationUnlock = animationDurationUnlock,
            animationDurationScreenOn = animationDurationScreenOn,
            animationDurationScreenOff = animationDurationScreenOff,
            animateOn = animateOn
        )
    }

    fun isDarkTheme(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        if (uiModeManager != null) {
            val mode = uiModeManager.nightMode
            if (mode == UiModeManager.MODE_NIGHT_YES) return true
            if (mode == UiModeManager.MODE_NIGHT_NO) return false
        }

        val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) return true
        if (nightModeFlags == Configuration.UI_MODE_NIGHT_NO) return false

        return try {
            val mode = Settings.Secure.getInt(context.contentResolver, "ui_night_mode", 0)
            mode == 2
        } catch (e: Exception) {
            false
        }
    }

    fun isDeviceUnlocked(context: Context): Boolean {
        return try {
            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            val isLocked = km?.isKeyguardLocked ?: false
            !isLocked
        } catch (e: Exception) {
            true
        }
    }

    fun getBatteryInfo(context: Context): BatteryInfo {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val percent = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else -1
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val pluggedName = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "ac"
            BatteryManager.BATTERY_PLUGGED_USB -> "usb"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
            else -> "none"
        }
        return BatteryInfo(
            level = percent,
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL,
            plugged = pluggedName
        )
    }

    fun getMonetColors(context: Context, isDark: Boolean): AccentColors {
        val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                if (isDark) androidx.compose.material3.dynamicDarkColorScheme(context)
                else androidx.compose.material3.dynamicLightColorScheme(context)
            } catch (_: Exception) {
                if (isDark) darkColorScheme() else lightColorScheme()
            }
        } else {
            if (isDark) darkColorScheme() else lightColorScheme()
        }
        return scheme.toAccentColors()
    }

    private fun ColorScheme.toAccentColors() = AccentColors(
        primary.hex(), onPrimary.hex(), primaryContainer.hex(), onPrimaryContainer.hex(), inversePrimary.hex(),
        secondary.hex(), onSecondary.hex(), secondaryContainer.hex(), onSecondaryContainer.hex(),
        tertiary.hex(), onTertiary.hex(), tertiaryContainer.hex(), onTertiaryContainer.hex(),
        background.hex(), onBackground.hex(), surface.hex(), onSurface.hex(),
        surfaceVariant.hex(), onSurfaceVariant.hex(), surfaceTint.hex(),
        inverseSurface.hex(), inverseOnSurface.hex(),
        error.hex(), onError.hex(), errorContainer.hex(), onErrorContainer.hex(),
        outline.hex(), outlineVariant.hex(), scrim.hex(),
        surfaceBright.hex(), surfaceDim.hex(), surfaceContainer.hex(), surfaceContainerHigh.hex(),
        surfaceContainerHighest.hex(), surfaceContainerLow.hex(), surfaceContainerLowest.hex(),
        primaryFixed.hex(), primaryFixedDim.hex(), onPrimaryFixed.hex(), onPrimaryFixedVariant.hex(),
        secondaryFixed.hex(), secondaryFixedDim.hex(), onSecondaryFixed.hex(), onSecondaryFixedVariant.hex(),
        tertiaryFixed.hex(), tertiaryFixedDim.hex(), onTertiaryFixed.hex(), onTertiaryFixedVariant.hex()
    )

    private fun androidx.compose.ui.graphics.Color.hex(): String =
        String.format("#%06X", 0xFFFFFF and toArgb())
}
