package dev.rooni.wallify.ui.screens

import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.rooni.wallify.R
import dev.rooni.wallify.BuildConfig
import dev.rooni.wallify.bridge.VersionEvaluator
import dev.rooni.wallify.model.RepoWallpaperItem
import dev.rooni.wallify.service.WallpaperLocalServer
import dev.rooni.wallify.util.RepositoryFetcher
import dev.rooni.wallify.util.StorageUtils
import dev.rooni.wallify.util.WallpaperManagerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.RepoDetailScreen(
    item: RepoWallpaperItem,
    installedVersion: String?,
    isActive: Boolean,
    downloadCount: Int?,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onInstalledOrUpdated: () -> Unit,
    onApplied: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isInstalling by remember { mutableStateOf(false) }
    var readmeHtml by remember { mutableStateOf<String?>(null) }
    var isReadmeLoading by remember { mutableStateOf(true) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var effectiveInstalledVersion by remember(item.id) { mutableStateOf(installedVersion) }

    val isInstalled = effectiveInstalledVersion != null
    val effectiveHasUpdate = isInstalled &&
        VersionEvaluator.isNewer(item.version, effectiveInstalledVersion ?: item.version)
    val isCompatible = VersionEvaluator
        .isCompatible(BuildConfig.VERSION_NAME, item.minAppVersion).first

    LaunchedEffect(installedVersion) {
        effectiveInstalledVersion = installedVersion
    }

    LaunchedEffect(item.id) {
        isReadmeLoading = true
        readmeHtml = fetchReadmeHtml(item)
        isReadmeLoading = false
    }

    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }

    suspend fun findLocal() = withContext(Dispatchers.IO) {
        StorageUtils.scanWallpapers(context)
            .find { it.manifest.name == item.name || it.folderName == item.id }
    }

    fun install() {
        isInstalling = true
        coroutineScope.launch {
            val success = RepositoryFetcher.installWallpaperFromRepo(context, item)
            if (success) {
                effectiveInstalledVersion = item.version
                Toast.makeText(
                    context,
                    if (effectiveHasUpdate) context.getString(R.string.toast_updated_to, item.version)
                    else context.getString(R.string.toast_wallpaper_installed, item.name),
                    Toast.LENGTH_SHORT
                ).show()
                onInstalledOrUpdated()
            }
            isInstalling = false
        }
    }

    fun apply() {
        coroutineScope.launch {
            val localItem = findLocal() ?: return@launch
            val folderUri = Uri.parse(localItem.folderUriString)
            WallpaperLocalServer.getInstance(context).setWallpaperUri(folderUri)
            StorageUtils.setActiveWallpaperFolder(context, localItem.folderName)
            WallpaperManagerHelper.sendRestartSignal(context)

            if (!WallpaperManagerHelper.isWallpaperActive(context)) {
                WallpaperManagerHelper.openWallpaperPicker(context)
            } else {
                Toast.makeText(context, R.string.toast_wallpaper_applied, Toast.LENGTH_SHORT).show()
            }
            onApplied()
        }
    }

    fun preview() {
        coroutineScope.launch {
            val localItem = findLocal() ?: return@launch
            context.startActivity(Intent(context, WallpaperPreviewActivity::class.java).apply {
                putExtra(WallpaperPreviewActivity.EXTRA_FOLDER_URI, localItem.folderUriString)
                putExtra(WallpaperPreviewActivity.EXTRA_FOLDER_NAME, localItem.folderName)
                putExtra(WallpaperPreviewActivity.EXTRA_MAIN, localItem.manifest.main ?: "index.html")
            })
        }
    }

    fun delete() {
        coroutineScope.launch {
            val localItem = findLocal()
            val deleted = withContext(Dispatchers.IO) {
                localItem ?: return@withContext false
                try {
                    val uri = Uri.parse(localItem.folderUriString)
                    val doc = androidx.documentfile.provider.DocumentFile
                        .fromTreeUri(context, uri)
                        ?: androidx.documentfile.provider.DocumentFile
                            .fromSingleUri(context, uri)
                    val ok = doc?.delete() == true
                    if (ok) StorageUtils.invalidateCache()
                    ok
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
            Toast.makeText(
                context,
                if (deleted) R.string.toast_wallpaper_deleted
                else R.string.toast_wallpaper_delete_failed,
                Toast.LENGTH_SHORT
            ).show()
            if (deleted) onInstalledOrUpdated()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(RepositoryFetcher.githubUrl(item)))
                            )
                        }
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_github),
                            contentDescription = stringResource(R.string.open_on_github)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            ActionBar(
                isInstalled = isInstalled,
                hasUpdate = effectiveHasUpdate,
                isActive = isActive,
                isInstalling = isInstalling,
                isCompatible = isCompatible,
                version = item.version,
                onInstall = ::install,
                onApply = ::apply,
                onPreview = ::preview,
                onDelete = { showDeleteDialog = true }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Header(
                item = item,
                installedVersion = effectiveInstalledVersion,
                downloadCount = downloadCount,
                coverModifier = Modifier.sharedElement(
                    sharedContentState = rememberSharedContentState(key = repoCoverKey(item.id)),
                    animatedVisibilityScope = animatedVisibilityScope,
                    renderInOverlayDuringTransition = false
                ),
                modifier = Modifier.enterStagger(appeared, delayMillis = 0)
            )

            SectionLabel(
                text = stringResource(R.string.repo_readme_title),
                modifier = Modifier.enterStagger(appeared, delayMillis = 70)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .enterStagger(appeared, delayMillis = 120)
            ) {
                ReadmeView(
                    html = readmeHtml,
                    isLoading = isReadmeLoading,
                    baseUrl = RepositoryFetcher.readmeBaseUrl(item),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.dialog_confirm_title)) },
            text = { Text(stringResource(R.string.repo_delete_confirm, item.name)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    delete()
                }) {
                    Text(
                        stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun Modifier.enterStagger(visible: Boolean, delayMillis: Int): Modifier {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 340, delayMillis = delayMillis, easing = EaseOutCubic),
        label = "enterStagger$delayMillis"
    )
    return this.graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * 20.dp.toPx()
    }
}

@Composable
private fun Header(
    item: RepoWallpaperItem,
    installedVersion: String?,
    downloadCount: Int?,
    coverModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RepoCover(
                item = item,
                cornerRadius = 20.dp,
                iconSize = 30.dp,
                modifier = coverModifier.size(72.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = item.author,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Text(
                    text = if (installedVersion != null) {
                        stringResource(
                            R.string.repo_installed_version,
                            installedVersion,
                            item.version
                        )
                    } else {
                        stringResource(R.string.repo_version, item.version)
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 14.dp)
        ) {
            MetaChip(
                text = stringResource(
                    if (item.isLite) R.string.repo_chip_lite else R.string.repo_chip_full
                ),
                highlighted = item.isLite
            )
            if (item.useGyroscope) {
                MetaChip(text = stringResource(R.string.repo_chip_gyro))
            }
            if (downloadCount != null) {
                MetaChip(text = stringResource(R.string.repo_chip_downloads, downloadCount))
            }
            if (!VersionEvaluator.isCompatible(BuildConfig.VERSION_NAME, item.minAppVersion).first) {
                MetaChip(
                    text = stringResource(
                        R.string.repo_requires_version,
                        item.minAppVersion ?: "?"
                    ),
                    error = true
                )
            }
        }
    }
}

internal fun repoCoverKey(id: String): String = "repo-cover-$id"

private val loadedCovers = java.util.Collections.newSetFromMap(
    java.util.concurrent.ConcurrentHashMap<String, Boolean>()
)

@Composable
internal fun RepoCover(
    item: RepoWallpaperItem,
    cornerRadius: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false
) {
    val context = LocalContext.current
    val url = remember(item.id, item.icon, item.cover, item.path) { RepositoryFetcher.iconUrl(item) }
    val alreadySeen = url in loadedCovers
    val request = remember(url, alreadySeen) {
        ImageRequest.Builder(context)
            .data(url)
            .memoryCacheKey(url)
            .placeholderMemoryCacheKey(url)
            .crossfade(if (alreadySeen) 0 else 220)
            .build()
    }

    var loaded by remember(url) { mutableStateOf(alreadySeen) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                when {
                    loaded -> Color.Transparent
                    highlighted -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.primaryContainer
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (!loaded) {
            Icon(
                Icons.Rounded.Wallpaper,
                contentDescription = null,
                tint = if (highlighted) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(iconSize)
            )
        }
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onState = { state ->
                if (state is AsyncImagePainter.State.Success) {
                    loaded = true
                    loadedCovers.add(url)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
internal fun RepoBanner(
    item: RepoWallpaperItem,
    modifier: Modifier = Modifier
) {
    val url = remember(item.id, item.cover, item.path) { RepositoryFetcher.coverUrl(item) }
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.Wallpaper,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(42.dp)
        )
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun MetaChip(
    text: String,
    highlighted: Boolean = false,
    error: Boolean = false
) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = when {
            error -> MaterialTheme.colorScheme.onErrorContainer
            highlighted -> MaterialTheme.colorScheme.onTertiaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    error -> MaterialTheme.colorScheme.errorContainer
                    highlighted -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                }
            )
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 10.dp)
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
    }
}

@Composable
internal fun ReadmeView(
    html: String?,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    baseUrl: String? = null
) {
    val colors = MaterialTheme.colorScheme
    val emptyReadme = stringResource(R.string.repo_readme_empty)
    val context = LocalContext.current

    AnimatedContent(
        targetState = isLoading,
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
        label = "ReadmeContent",
        modifier = modifier
    ) { loading ->
        if (loading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        } else {
            val body = html ?: "<p class=\"empty\">$emptyReadme</p>"
            val styledHtml = remember(body, colors) { buildReadmeDocument(body, colors) }

            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = false
                        settings.domStorageEnabled = false
                        setBackgroundColor(0)
                        overScrollMode = WebView.OVER_SCROLL_IF_CONTENT_SCROLLS
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest
                            ): Boolean {
                                val url = request.url
                                if (url.scheme == "http" || url.scheme == "https") {
                                    try {
                                        ctx.startActivity(Intent(Intent.ACTION_VIEW, url))
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                                return true
                            }
                        }
                    }
                },
                onRelease = { it.destroy() },
                update = { webView ->
                    val pageKey = baseUrl to styledHtml
                    if (webView.tag != pageKey) {
                        webView.tag = pageKey
                        webView.loadDataWithBaseURL(
                            baseUrl,
                            styledHtml,
                            "text/html",
                            "UTF-8",
                            null
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun ActionBar(
    isInstalled: Boolean,
    hasUpdate: Boolean,
    isActive: Boolean,
    isInstalling: Boolean,
    isCompatible: Boolean,
    version: String,
    onInstall: () -> Unit,
    onApply: () -> Unit,
    onPreview: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            val primaryLabel: String
            val primaryIcon: androidx.compose.ui.graphics.vector.ImageVector
            val primaryAction: () -> Unit
            val primaryEnabled: Boolean
            when {
                hasUpdate -> {
                    primaryLabel = stringResource(R.string.repo_btn_update, version)
                    primaryIcon = Icons.Rounded.Update
                    primaryAction = onInstall
                    primaryEnabled = true
                }
                isActive -> {
                    primaryLabel = stringResource(R.string.repo_btn_applied)
                    primaryIcon = Icons.Rounded.Check
                    primaryAction = {}
                    primaryEnabled = false
                }
                isInstalled -> {
                    primaryLabel = stringResource(R.string.action_apply)
                    primaryIcon = Icons.Rounded.PlayArrow
                    primaryAction = onApply
                    primaryEnabled = true
                }
                else -> {
                    primaryLabel = stringResource(R.string.repo_btn_install)
                    primaryIcon = Icons.Rounded.Download
                    primaryAction = onInstall
                    primaryEnabled = isCompatible
                }
            }

            Button(
                onClick = primaryAction,
                enabled = primaryEnabled && isCompatible && !isInstalling,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            ) {
                val actionState = when {
                    isInstalling -> "loading"
                    hasUpdate -> "update"
                    isActive -> "active"
                    isInstalled -> "apply"
                    else -> "install"
                }
                AnimatedContent(
                    targetState = actionState,
                    transitionSpec = {
                        (fadeIn(tween(180)) + scaleIn(initialScale = 0.85f, animationSpec = tween(180))) togetherWith
                            (fadeOut(tween(120)) + scaleOut(targetScale = 0.85f, animationSpec = tween(120)))
                    },
                    label = "PrimaryActionAnim"
                ) { state ->
                    if (state == "loading") {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(primaryIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = primaryLabel,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = hasUpdate && !isActive,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                FilledTonalIconButton(
                    onClick = onApply,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.action_apply))
                }
            }

            AnimatedVisibility(
                visible = isInstalled,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                FilledTonalIconButton(
                    onClick = onPreview,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(Icons.Rounded.Visibility, contentDescription = stringResource(R.string.action_preview))
                }
            }
            AnimatedVisibility(
                visible = isInstalled,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                OutlinedIconButton(
                    onClick = onDelete,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.error
                    )
            }
            }
        }
    }
}

private fun Color.toCssHex(): String = String.format("#%06X", 0xFFFFFF and toArgb())

private fun buildReadmeDocument(
    body: String,
    colors: androidx.compose.material3.ColorScheme
): String = """
    <!DOCTYPE html>
    <html>
    <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <style>
            :root {
                --fg: ${colors.onSurface.toCssHex()};
                --muted: ${colors.onSurfaceVariant.toCssHex()};
                --accent: ${colors.primary.toCssHex()};
                --code-bg: ${colors.surfaceContainerHigh.toCssHex()};
                --code-fg: ${colors.onSurfaceVariant.toCssHex()};
                --rule: ${colors.outlineVariant.toCssHex()};
            }
            html, body { background: transparent; }
            body {
                font-family: -apple-system, Roboto, sans-serif;
                color: var(--fg);
                margin: 0;
                padding: 18px 20px 28px 20px;
                line-height: 1.6;
                font-size: 14px;
                word-wrap: break-word;
            }
            h1, h2, h3 { color: var(--accent); line-height: 1.3; margin: 20px 0 8px 0; font-weight: 700; }
            h1 { font-size: 20px; }
            h2 { font-size: 17px; }
            h3 { font-size: 15px; }
            p { margin: 0 0 12px 0; }
            ul { margin: 0 0 12px 0; padding-left: 20px; }
            li { margin: 4px 0; }
            code {
                background: var(--code-bg);
                padding: 2px 6px;
                border-radius: 6px;
                color: var(--code-fg);
                font-size: 13px;
            }
            pre {
                background: var(--code-bg);
                padding: 12px 14px;
                border-radius: 14px;
                overflow-x: auto;
            }
            pre code { background: transparent; padding: 0; }
            blockquote {
                margin: 0 0 12px 0;
                padding: 2px 0 2px 14px;
                border-left: 3px solid var(--rule);
                color: var(--muted);
            }
            hr { border: none; border-top: 1px solid var(--rule); margin: 20px 0; }
            img { max-width: 100%; height: auto; border-radius: 14px; margin: 8px 0; }
            a { color: var(--accent); }
            .empty { color: var(--muted); font-style: italic; }
        </style>
    </head>
    <body>
        $body
    </body>
    </html>
""".trimIndent()

private suspend fun fetchReadmeHtml(item: RepoWallpaperItem): String? = withContext(Dispatchers.IO) {
    try {
        val url = URL(RepositoryFetcher.readmeUrl(item))
        val conn = url.openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 6000
        conn.readTimeout = 6000
        if (conn.responseCode == HttpURLConnection.HTTP_OK) {
            val mdText = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            return@withContext simpleMarkdownToHtml(mdText)
        }
        conn.disconnect()
    } catch (e: Exception) {
        e.printStackTrace()
    }
    if (item.description.isBlank()) null else "<p>${escapeHtml(item.description)}</p>"
}

private fun escapeHtml(raw: String): String = raw
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")

private fun safeUrl(url: String): String? {
    val trimmed = url.trim()
    return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else null
}

private fun safeResourceUrl(url: String): String? {
    val trimmed = url.trim()
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
    if (trimmed.startsWith("/") || trimmed.startsWith("./") || trimmed.startsWith("../")) return trimmed
    return trimmed.takeIf {
        it.isNotBlank() && ':' !in it && !it.startsWith("#")
    }
}

internal fun simpleMarkdownToHtml(md: String): String {
    val escaped = escapeHtml(md)

    val out = StringBuilder()
    var inList = false
    var inCode = false

    fun closeList() {
        if (inList) {
            out.append("</ul>")
            inList = false
        }
    }

    for (rawLine in escaped.lines()) {
        val line = rawLine.trimEnd()

        if (line.trimStart().startsWith("```")) {
            closeList()
            out.append(if (inCode) "</code></pre>" else "<pre><code>")
            inCode = !inCode
            continue
        }
        if (inCode) {
            out.append(line).append('\n')
            continue
        }

        val trimmed = line.trim()
        when {
            trimmed.isEmpty() -> closeList()

            trimmed == "---" || trimmed == "***" -> {
                closeList()
                out.append("<hr/>")
            }

            trimmed.startsWith("### ") -> {
                closeList()
                out.append("<h3>${inlineMd(trimmed.removePrefix("### "))}</h3>")
            }
            trimmed.startsWith("## ") -> {
                closeList()
                out.append("<h2>${inlineMd(trimmed.removePrefix("## "))}</h2>")
            }
            trimmed.startsWith("# ") -> {
                closeList()
                out.append("<h1>${inlineMd(trimmed.removePrefix("# "))}</h1>")
            }

            trimmed.startsWith("&gt; ") -> {
                closeList()
                out.append("<blockquote>${inlineMd(trimmed.removePrefix("&gt; "))}</blockquote>")
            }

            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                if (!inList) {
                    out.append("<ul>")
                    inList = true
                }
                out.append("<li>${inlineMd(trimmed.substring(2))}</li>")
            }

            else -> {
                closeList()
                out.append("<p>${inlineMd(trimmed)}</p>")
            }
        }
    }
    closeList()
    if (inCode) out.append("</code></pre>")

    return out.toString()
}

private val imageRegex = "!\\[([^\\]]*)]\\(([^)\\s]+)\\)".toRegex()
private val linkRegex = "\\[([^\\]]+)]\\(([^)\\s]+)\\)".toRegex()

private fun inlineMd(text: String): String = text
    .replace(imageRegex) { m ->
        val url = safeResourceUrl(m.groupValues[2]) ?: return@replace m.groupValues[1]
        "<img src=\"$url\" alt=\"${m.groupValues[1]}\"/>"
    }
    .replace(linkRegex) { m ->
        val url = safeUrl(m.groupValues[2]) ?: return@replace m.groupValues[1]
        "<a href=\"$url\">${m.groupValues[1]}</a>"
    }
    .replace("`([^`]+)`".toRegex(), "<code>$1</code>")
    .replace("\\*\\*(.*?)\\*\\*".toRegex(), "<strong>$1</strong>")
