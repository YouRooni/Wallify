package dev.rooni.wallify.model

import com.google.gson.annotations.SerializedName

data class WallpaperManifest(
    @SerializedName("name") val name: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("author") val author: String? = null,
    @SerializedName("version") val version: String? = null,
    @SerializedName("minAppVersion") val minAppVersion: String? = null,
    @SerializedName("cover") val cover: String? = null,
    @SerializedName("icon") val icon: String? = null,
    @SerializedName("orientations") val orientations: List<String>? = null,
    @SerializedName("themeSupport") val themeSupport: List<String>? = null,
    @SerializedName("supportsMonet") val supportsMonet: Boolean = false,
    @SerializedName("tags") val tags: List<String>? = null,
    @SerializedName("main") val main: String? = "index.html",
    @SerializedName("settingsPath") val settingsPath: String? = null,
    @SerializedName("useGyroscope") val useGyroscope: Boolean = false,
    @SerializedName("isLite") val isLite: Boolean = false,
    @SerializedName("animationDuration") val animationDuration: Double = 3.0,
    @SerializedName("animationDurationUnlock") val animationDurationUnlock: Double = 2.5,
    @SerializedName("animationDurationScreenOn") val animationDurationScreenOn: Double = 1.2,
    @SerializedName("animationDurationScreenOff") val animationDurationScreenOff: Double = 0.4,
    @SerializedName("animateOn") val animateOn: String = "both"
)

data class WallpaperItem(
    val id: String,
    val folderName: String,
    val folderUriString: String,
    val manifest: WallpaperManifest,
    val isCompatible: Boolean = true,
    val compatibilityReason: String = "",
    val hasSettings: Boolean = false,
    val settingsUriString: String? = null,
    val coverUriString: String? = null,
    val iconUriString: String? = null,
    val lastModified: Long = 0L
)

data class WallpaperMetadata(
    val theme: String,
    val accentColors: AccentColors,
    val battery: BatteryInfo,
    val screen: String,
    val isUnlocked: Boolean,
    val device: DeviceInfo,
    val isLite: Boolean = false,
    val animationDuration: Double = 3.0,
    val animationDurationUnlock: Double = 2.5,
    val animationDurationScreenOn: Double = 1.2,
    val animationDurationScreenOff: Double = 0.4,
    val animateOn: String = "both"
)

data class AccentColors(
    val primary: String,
    val onPrimary: String,
    val primaryContainer: String,
    val onPrimaryContainer: String,
    val inversePrimary: String,
    val secondary: String,
    val onSecondary: String,
    val secondaryContainer: String,
    val onSecondaryContainer: String,
    val tertiary: String,
    val onTertiary: String,
    val tertiaryContainer: String,
    val onTertiaryContainer: String,
    val background: String,
    val onBackground: String,
    val surface: String,
    val onSurface: String,
    val surfaceVariant: String,
    val onSurfaceVariant: String,
    val surfaceTint: String,
    val inverseSurface: String,
    val inverseOnSurface: String,
    val error: String,
    val onError: String,
    val errorContainer: String,
    val onErrorContainer: String,
    val outline: String,
    val outlineVariant: String,
    val scrim: String,
    val surfaceBright: String,
    val surfaceDim: String,
    val surfaceContainer: String,
    val surfaceContainerHigh: String,
    val surfaceContainerHighest: String,
    val surfaceContainerLow: String,
    val surfaceContainerLowest: String,
    val primaryFixed: String,
    val primaryFixedDim: String,
    val onPrimaryFixed: String,
    val onPrimaryFixedVariant: String,
    val secondaryFixed: String,
    val secondaryFixedDim: String,
    val onSecondaryFixed: String,
    val onSecondaryFixedVariant: String,
    val tertiaryFixed: String,
    val tertiaryFixedDim: String,
    val onTertiaryFixed: String,
    val onTertiaryFixedVariant: String
)

data class BatteryInfo(
    val level: Int,
    val isCharging: Boolean,
    val plugged: String
)

data class DeviceInfo(
    val model: String,
    val manufacturer: String,
    val androidVersion: Int,
    val appVersion: String
)

data class RepoCatalog(
    @SerializedName("version") val version: Int = 1,
    @SerializedName("updatedAt") val updatedAt: String? = null,
    @SerializedName("wallpapers") val wallpapers: List<RepoWallpaperItem>? = null
)

data class RepoWallpaperItem(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("author") val author: String,
    @SerializedName("version") val version: String,
    @SerializedName("minAppVersion") val minAppVersion: String? = null,
    @SerializedName("description") val description: String,
    @SerializedName("path") val path: String,
    @SerializedName("main") val main: String = "index.html",
    @SerializedName("cover") val cover: String? = null,
    @SerializedName("icon") val icon: String? = null,
    @SerializedName("orientations") val orientations: List<String>? = null,
    @SerializedName("themeSupport") val themeSupport: List<String>? = null,
    @SerializedName("supportsMonet") val supportsMonet: Boolean = false,
    @SerializedName("tags") val tags: List<String>? = null,
    @SerializedName("useGyroscope") val useGyroscope: Boolean = false,
    @SerializedName("isLite") val isLite: Boolean = false,
    @SerializedName("animationDuration") val animationDuration: Double = 3.0,
    @SerializedName("animateOn") val animateOn: String = "both",
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("updatedAt") val updatedAt: String? = null,
    @SerializedName("downloadUrl") val downloadUrl: String? = null
)

data class GitHubRelease(
    @SerializedName("assets") val assets: List<GitHubReleaseAsset> = emptyList()
)

data class GitHubReleaseAsset(
    @SerializedName("name") val name: String,
    @SerializedName("download_count") val downloadCount: Int = 0
)
