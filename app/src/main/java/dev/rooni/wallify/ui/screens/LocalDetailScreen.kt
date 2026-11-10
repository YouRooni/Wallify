package dev.rooni.wallify.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.rooni.wallify.R
import dev.rooni.wallify.model.WallpaperItem
import dev.rooni.wallify.service.WallpaperLocalServer
import dev.rooni.wallify.util.StorageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun localCoverKey(id: String) = "local-cover-$id"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.LocalDetailScreen(
    item: WallpaperItem,
    isActive: Boolean,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onApply: () -> Unit,
    onAddShortcut: () -> Unit,
    onPreview: () -> Unit,
    onSettings: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var readmeHtml by remember(item.folderUriString) { mutableStateOf<String?>(null) }
    var readmeBaseUrl by remember(item.folderUriString) { mutableStateOf<String?>(null) }
    var loading by remember(item.folderUriString) { mutableStateOf(true) }
    val backProgress = remember { Animatable(0f) }
    var backFromLeft by remember { mutableStateOf(true) }

    androidx.activity.compose.PredictiveBackHandler {
        try {
            it.collect { event ->
                backFromLeft = event.swipeEdge == androidx.activity.BackEventCompat.EDGE_LEFT
                backProgress.snapTo(event.progress)
            }
            onBack()
        } catch (_: kotlinx.coroutines.CancellationException) {
            backProgress.animateTo(
                0f,
                spring(dampingRatio = Spring.DampingRatioLowBouncy)
            )
        }
    }

    LaunchedEffect(item.folderUriString) {
        val markdown = withContext(Dispatchers.IO) {
            StorageUtils.readWallpaperText(context, item, "README.md")
        }
        readmeBaseUrl = withContext(Dispatchers.IO) {
            WallpaperLocalServer.getInstance(context)
                .urlFor(Uri.parse(item.folderUriString), "")
        }
        readmeHtml = markdown?.let(::simpleMarkdownToHtml)
            ?: item.manifest.description?.let(::simpleMarkdownToHtml)
        loading = false
    }

    Scaffold(
        modifier = Modifier.graphicsLayer {
            val progress = backProgress.value
            val scale = 1f - 0.12f * progress
            scaleX = scale
            scaleY = scale
            translationX = (if (backFromLeft) 1f else -1f) * progress * 28.dp.toPx()
            transformOrigin = TransformOrigin(if (backFromLeft) 1f else 0f, 0.5f)
            shape = RoundedCornerShape(32.dp * progress)
            clip = progress > 0f
        },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    IconButton(onClick = onAddShortcut) {
                        Icon(
                            Icons.Rounded.AddToHomeScreen,
                            contentDescription = stringResource(R.string.shortcut_add)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Button(
                        onClick = onApply,
                        enabled = !isActive && item.isCompatible,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(if (isActive) R.string.btn_active else R.string.action_apply))
                    }
                    FilledTonalIconButton(
                        onClick = onPreview,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(Icons.Rounded.Visibility, contentDescription = stringResource(R.string.action_preview))
                    }
                    if (item.hasSettings) {
                        FilledTonalIconButton(
                            onClick = onSettings,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.size(52.dp)
                        ) {
                            Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.btn_settings))
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .sharedElement(
                            rememberSharedContentState(localCoverKey(item.id)),
                            animatedVisibilityScope,
                            renderInOverlayDuringTransition = false
                        )
                        .size(76.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    val cover = item.iconUriString ?: item.coverUriString
                    if (cover != null) {
                        AsyncImage(
                            model = remember(cover) { Uri.parse(cover) },
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(Icons.Rounded.Wallpaper, null, modifier = Modifier.size(34.dp))
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.manifest.name ?: item.folderName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 21.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    item.manifest.author?.let {
                        Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        stringResource(
                            R.string.repo_version,
                            item.manifest.version ?: "1.0.0"
                        ),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                LocalDetailBadge(if (item.manifest.isLite) "Lite" else "Full")
                if (item.manifest.useGyroscope) LocalDetailBadge("Гироскоп")
                LocalDetailBadge(item.manifest.main ?: "index.html")
            }
            Text(
                stringResource(R.string.repo_readme_title),
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)
            )
            ReadmeView(
                html = readmeHtml,
                isLoading = loading,
                modifier = Modifier.fillMaxWidth().weight(1f),
                baseUrl = readmeBaseUrl
            )
        }
    }
}

@Composable
private fun LocalDetailBadge(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}
