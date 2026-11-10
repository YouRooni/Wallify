package dev.rooni.wallify.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.BackEventCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import dev.rooni.wallify.BuildConfig
import dev.rooni.wallify.R
import dev.rooni.wallify.bridge.VersionEvaluator
import dev.rooni.wallify.model.RepoWallpaperItem
import dev.rooni.wallify.model.WallpaperItem
import dev.rooni.wallify.service.WallpaperLocalServer
import dev.rooni.wallify.ui.components.PreferenceCategory
import dev.rooni.wallify.ui.components.PreferenceItem
import dev.rooni.wallify.ui.components.PreferenceSwitch
import dev.rooni.wallify.ui.components.PreferenceType
import dev.rooni.wallify.ui.components.WallpaperCard
import dev.rooni.wallify.util.RepositoryFetcher
import dev.rooni.wallify.util.StorageUtils
import dev.rooni.wallify.util.WallpaperManagerHelper
import dev.rooni.wallify.util.WallpaperShortcutHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 4 })

    var rootUri by remember { mutableStateOf(StorageUtils.getRootFolderUri(context)) }
    var wallpapers by remember { mutableStateOf<List<WallpaperItem>>(emptyList()) }
    var activeFolder by remember { mutableStateOf(StorageUtils.getActiveWallpaperFolder(context)) }
    var isTouchEnabled by remember { mutableStateOf(StorageUtils.isTouchEnabled(context)) }
    var isPaused by remember { mutableStateOf(false) }

    var catalogItems by remember { mutableStateOf<List<RepoWallpaperItem>>(emptyList()) }
    var catalogDownloads by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var isCatalogLoading by remember { mutableStateOf(false) }

    var selectedRepoItem by remember { mutableStateOf<RepoWallpaperItem?>(null) }
    var selectedLocalItem by remember { mutableStateOf<WallpaperItem?>(null) }
    var showDebugSheet by remember { mutableStateOf(false) }

    var globalLoadingMessage by remember { mutableStateOf<String?>(null) }

    val expandedCards = remember { mutableStateMapOf<String, Boolean>() }
    var wallpaperSort by remember { mutableStateOf(WallpaperSort.RECENT) }

    fun refreshWallpapers(forceRefresh: Boolean = false) {
        coroutineScope.launch(Dispatchers.IO) {
            val list = StorageUtils.scanWallpapers(context, forceRefresh = forceRefresh)
            val currentActive = StorageUtils.getActiveWallpaperFolder(context)
            withContext(Dispatchers.Main) {
                wallpapers = list
                activeFolder = currentActive
            }
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshWallpapers(forceRefresh = false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    suspend fun loadCatalog() {
        isCatalogLoading = true
        try {
            val catalog = withContext(Dispatchers.IO) { RepositoryFetcher.fetchCatalog() }
            catalogItems = catalog.wallpapers.orEmpty()
            catalogDownloads = withContext(Dispatchers.IO) { RepositoryFetcher.fetchDownloadCounts() }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isCatalogLoading = false
        }
    }

    LaunchedEffect(rootUri) {
        if (rootUri != null) {
            refreshWallpapers()
        }
    }

    LaunchedEffect(Unit) {
        if (catalogItems.isEmpty()) {
            loadCatalog()
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            StorageUtils.saveRootFolderUri(context, uri)
            rootUri = uri
            refreshWallpapers(forceRefresh = true)
        }
    }

    val zipImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty() && rootUri != null) {
            globalLoadingMessage = context.getString(R.string.loading_importing)
            coroutineScope.launch(Dispatchers.IO) {
                val installed = uris.mapNotNull { StorageUtils.importWallpaperFromZip(context, it) }
                withContext(Dispatchers.Main) {
                    if (installed.isNotEmpty()) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.toast_wallpapers_installed, installed.size),
                            Toast.LENGTH_SHORT
                        ).show()
                        refreshWallpapers(forceRefresh = true)
                    } else {
                        Toast.makeText(context, R.string.import_failed, Toast.LENGTH_SHORT).show()
                    }
                    globalLoadingMessage = null
                }
            }
        }
    }

    fun applyWallpaper(item: WallpaperItem) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        globalLoadingMessage = context.getString(R.string.loading_applying)
        coroutineScope.launch(Dispatchers.IO) {
            val folderUri = Uri.parse(item.folderUriString)
            StorageUtils.setActiveWallpaperFolder(context, item.folderName)
            WallpaperLocalServer.getInstance(context).setWallpaperUri(folderUri)
            withContext(Dispatchers.Main) {
                activeFolder = item.folderName
                WallpaperManagerHelper.sendRestartSignal(context)
                if (!WallpaperManagerHelper.isWallpaperActive(context)) {
                    WallpaperManagerHelper.openWallpaperPicker(context)
                } else {
                    Toast.makeText(context, R.string.toast_wallpaper_applied, Toast.LENGTH_SHORT).show()
                }
                globalLoadingMessage = null
            }
        }
    }

    SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    WallifyTabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                coroutineScope.launch {
                                    if (kotlin.math.abs(pagerState.currentPage - index) > 1) {
                                        pagerState.scrollToPage(index)
                                    } else {
                                        pagerState.animateScrollToPage(index)
                                    }
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.labelRes)) }
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())
            ) {
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        0 -> HomeScreen(
                            isPaused = isPaused,
                            activeWallpaper = wallpapers.find { it.folderName == activeFolder },
                            onTogglePause = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                isPaused = !isPaused
                                if (isPaused) {
                                    WallpaperManagerHelper.sendPauseSignal(context)
                                } else {
                                    WallpaperManagerHelper.sendResumeSignal(context)
                                }
                            }
                        )
                        1 -> WallpapersTabContent(
                            rootUri = rootUri,
                            wallpapers = wallpapers,
                            activeFolder = activeFolder,
                            sort = wallpaperSort,
                            onSortChange = { wallpaperSort = it },
                            expandedCards = expandedCards,
                            openedLocalItemId = selectedLocalItem?.id,
                            onRefresh = { refreshWallpapers(forceRefresh = true) },
                            onSelectFolder = { folderPickerLauncher.launch(null) },
                            onImportZip = { zipImportLauncher.launch(StorageUtils.IMPORT_MIME_TYPES) },
                            onSelectWallpaper = { item -> applyWallpaper(item) },
                            onOpenDetails = { item -> selectedLocalItem = item },
                            onPreview = { item ->
                                val intent = Intent(context, WallpaperPreviewActivity::class.java).apply {
                                    putExtra(WallpaperPreviewActivity.EXTRA_FOLDER_URI, item.folderUriString)
                                    putExtra(WallpaperPreviewActivity.EXTRA_FOLDER_NAME, item.folderName)
                                    putExtra(WallpaperPreviewActivity.EXTRA_MAIN, item.manifest.main ?: "index.html")
                                }
                                context.startActivity(intent)
                            },
                            onOpenSettings = { item ->
                                globalLoadingMessage = context.getString(R.string.loading_opening_settings)
                                val intent = Intent(context, WallpaperSettingsActivity::class.java).apply {
                                    putExtra("FOLDER_URI", item.folderUriString)
                                    putExtra("FOLDER_NAME", item.folderName)
                                    putExtra("SETTINGS_PATH", item.manifest.settingsPath ?: "settings/index.html")
                                }
                                context.startActivity(intent)
                                coroutineScope.launch {
                                    kotlinx.coroutines.delay(400L)
                                    globalLoadingMessage = null
                                }
                            },
                            onExportZip = { item ->
                                globalLoadingMessage = context.getString(R.string.loading_preparing_share)
                                coroutineScope.launch(Dispatchers.IO) {
                                    val sharedDir = java.io.File(context.cacheDir, "shared_wallpapers").apply { mkdirs() }
                                    val zipFile = java.io.File(
                                        sharedDir,
                                        "${item.folderName}.${StorageUtils.WALLPAPER_EXTENSION}"
                                    )
                                    val zipUri = androidx.core.content.FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        zipFile
                                    )
                                    val success = StorageUtils.exportWallpaperToZip(context, item, Uri.fromFile(zipFile))
                                    withContext(Dispatchers.Main) {
                                        if (success) {
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = StorageUtils.WALLPAPER_MIME_TYPE
                                                putExtra(Intent.EXTRA_STREAM, zipUri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(
                                                Intent.createChooser(
                                                    shareIntent,
                                                    context.getString(R.string.action_share)
                                                )
                                            )
                                        } else {
                                            Toast.makeText(context, R.string.toast_export_failed, Toast.LENGTH_SHORT).show()
                                        }
                                        globalLoadingMessage = null
                                    }
                                }
                            },
                            onDeleteWallpaper = { item ->
                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        val doc = DocumentFile.fromSingleUri(context, Uri.parse(item.folderUriString))
                                            ?: DocumentFile.fromTreeUri(context, Uri.parse(item.folderUriString))
                                        doc?.delete()
                                        StorageUtils.invalidateCache()
                                        withContext(Dispatchers.Main) {
                                            refreshWallpapers(forceRefresh = true)
                                            Toast.makeText(context, R.string.toast_wallpaper_deleted, Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                        )
                        2 -> if (rootUri == null) {
                            RepositoryNeedsFolderScreen(
                                onSelectFolder = { folderPickerLauncher.launch(null) }
                            )
                        } else {
                            RepositoryTabContent(
                                catalogItems = catalogItems,
                                downloadCounts = catalogDownloads,
                                isLoading = isCatalogLoading,
                                openedRepoItemId = selectedRepoItem?.id,
                                onRefreshCatalog = { coroutineScope.launch { loadCatalog() } },
                                onSelectRepoItem = { item -> selectedRepoItem = item }
                            )
                        }
                        3 -> SettingsTabContent(
                            rootUri = rootUri,
                            isTouchEnabled = isTouchEnabled,
                            onToggleTouch = { enabled ->
                                isTouchEnabled = enabled
                                StorageUtils.setTouchEnabled(context, enabled)
                                WallpaperManagerHelper.sendReloadSignal(context)
                            },
                            onChangeFolder = { folderPickerLauncher.launch(null) },
                            onOpenDebug = { showDebugSheet = true }
                        )
                    }
                }

                if (showDebugSheet) {
                    ModalBottomSheet(
                        onDismissRequest = { showDebugSheet = false },
                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                    ) {
                        MetadataDebugScreen()
                    }
                }
            }
        }

        val backProgress = remember { Animatable(0f) }
        var backFromLeftEdge by remember { mutableStateOf(true) }
        androidx.activity.compose.PredictiveBackHandler(enabled = selectedRepoItem != null) { events ->
            try {
                events.collect { event ->
                    backFromLeftEdge = event.swipeEdge == BackEventCompat.EDGE_LEFT
                    backProgress.snapTo(event.progress)
                }
                selectedRepoItem = null
            } catch (e: kotlinx.coroutines.CancellationException) {
                backProgress.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
            }
        }

        var lastSelectedRepoItem by remember { mutableStateOf<RepoWallpaperItem?>(null) }
        LaunchedEffect(selectedRepoItem) {
            if (selectedRepoItem != null) {
                lastSelectedRepoItem = selectedRepoItem
                backProgress.snapTo(0f)
            }
        }

        AnimatedVisibility(
            visible = selectedRepoItem != null,
            enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.94f, animationSpec = tween(260)),
            exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.94f, animationSpec = tween(200)),
            modifier = Modifier.graphicsLayer {
                val p = backProgress.value
                if (p > 0f) {
                    val scale = 1f - 0.12f * p
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(if (backFromLeftEdge) 1f else 0f, 0.5f)
                    translationX = (if (backFromLeftEdge) 1f else -1f) * p * 28.dp.toPx()
                    shape = RoundedCornerShape(32.dp * p)
                    clip = true
                }
            }
        ) {
            val activeItem = selectedRepoItem ?: lastSelectedRepoItem
            if (activeItem != null) {
                val installedMap = remember(wallpapers) {
                    val map = mutableMapOf<String, String>()
                    for (item in wallpapers) {
                        val ver = item.manifest.version ?: "1.0.0"
                        item.manifest.name?.let { map[it] = ver }
                        map[item.folderName] = ver
                        map[item.id] = ver
                    }
                    map
                }
                val currentInstalledVer = installedMap[activeItem.name] ?: installedMap[activeItem.id]

                val isRepoItemActive = remember(activeItem.id, activeFolder, wallpapers) {
                    val local = wallpapers.find {
                        it.manifest.name == activeItem.name || it.folderName == activeItem.id
                    }
                    local != null && local.folderName == activeFolder &&
                        WallpaperManagerHelper.isWallpaperActive(context)
                }

                RepoDetailScreen(
                    item = activeItem,
                    installedVersion = currentInstalledVer,
                    isActive = isRepoItemActive,
                    downloadCount = catalogDownloads[RepositoryFetcher.downloadKey(activeItem)],
                    animatedVisibilityScope = this@AnimatedVisibility,
                    onBack = { selectedRepoItem = null },
                    onInstalledOrUpdated = {
                        refreshWallpapers(forceRefresh = true)
                    },
                    onApplied = {
                        activeFolder = StorageUtils.getActiveWallpaperFolder(context)
                    }
                )
            }
        }

        AnimatedVisibility(
            visible = selectedLocalItem != null,
            enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.94f, animationSpec = tween(260)),
            exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.94f, animationSpec = tween(200))
        ) {
            selectedLocalItem?.let { item ->
                LocalDetailScreen(
                    item = item,
                    isActive = item.folderName == activeFolder &&
                        WallpaperManagerHelper.isWallpaperActive(context),
                    animatedVisibilityScope = this@AnimatedVisibility,
                    onBack = { selectedLocalItem = null },
                    onApply = { applyWallpaper(item) },
                    onAddShortcut = {
                        WallpaperShortcutHelper.requestPinnedShortcut(context, item)
                    },
                    onPreview = {
                        context.startActivity(Intent(context, WallpaperPreviewActivity::class.java).apply {
                            putExtra(WallpaperPreviewActivity.EXTRA_FOLDER_URI, item.folderUriString)
                            putExtra(WallpaperPreviewActivity.EXTRA_FOLDER_NAME, item.folderName)
                            putExtra(WallpaperPreviewActivity.EXTRA_MAIN, item.manifest.main ?: "index.html")
                        })
                    },
                    onSettings = {
                        context.startActivity(Intent(context, WallpaperSettingsActivity::class.java).apply {
                            putExtra("FOLDER_URI", item.folderUriString)
                            putExtra("FOLDER_NAME", item.folderName)
                            putExtra("SETTINGS_PATH", item.manifest.settingsPath ?: "settings/index.html")
                        })
                    }
                )
            }
        }

        val currentLoadingMessage = globalLoadingMessage
        AnimatedVisibility(
            visible = currentLoadingMessage != null,
            enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.92f, animationSpec = tween(150)),
            exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.92f, animationSpec = tween(150))
        ) {
            if (currentLoadingMessage != null) {
                PrettyLoadingOverlay(message = currentLoadingMessage)
            }
        }
    }
    }
}

private data class WallifyTab(val labelRes: Int, val icon: ImageVector)

private val WallifyTabs = listOf(
    WallifyTab(R.string.tab_home, Icons.Rounded.Home),
    WallifyTab(R.string.tab_wallpapers, Icons.Rounded.Wallpaper),
    WallifyTab(R.string.tab_repository, Icons.Rounded.Explore),
    WallifyTab(R.string.tab_settings, Icons.Rounded.Settings)
)

@Composable
fun PrettyLoadingOverlay(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier
                .padding(32.dp)
                .wrapContentSize()
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 4.dp,
                    strokeCap = StrokeCap.Round
                )
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun SkeletonCard() {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "skeletonAlpha"
    )
    val base = MaterialTheme.colorScheme.surfaceContainerHighest

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = base.copy(alpha = alpha)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .height(14.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(11.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), RoundedCornerShape(6.dp))
                )
            }
        }
    }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, description: String? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (description != null) {
            Text(
                text = description,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

private data class StatusStateInfo(
    val icon: ImageVector,
    val tint: Color,
    val title: String,
    val desc: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    isPaused: Boolean,
    activeWallpaper: WallpaperItem?,
    onTogglePause: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var isSystemWallpaper by remember { mutableStateOf(WallpaperManagerHelper.isWallpaperActive(context)) }
    val homeLifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(homeLifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isSystemWallpaper = WallpaperManagerHelper.isWallpaperActive(context)
            }
        }
        homeLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { homeLifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()

    val topAppBarColors = TopAppBarDefaults.largeTopAppBarColors(
        containerColor = Color.Transparent,
        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.tab_home), fontWeight = FontWeight.Bold) },
                colors = topAppBarColors,
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            contentPadding = innerPadding,
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                PreferenceCategory(stringResource(R.string.status_title))

                val statusBgColor = MaterialTheme.colorScheme.surfaceContainerHigh
                val statusTitleColor = MaterialTheme.colorScheme.onSurface
                val statusDescColor = MaterialTheme.colorScheme.onSurfaceVariant

                val statusInfo = when {
                    !isSystemWallpaper -> StatusStateInfo(
                        Icons.Rounded.Cancel,
                        MaterialTheme.colorScheme.error,
                        stringResource(R.string.status_not_installed),
                        stringResource(R.string.status_not_installed_desc)
                    )
                    isPaused -> StatusStateInfo(
                        Icons.Rounded.PauseCircle,
                        MaterialTheme.colorScheme.tertiary,
                        stringResource(R.string.status_paused),
                        stringResource(R.string.status_paused_desc)
                    )
                    else -> StatusStateInfo(
                        Icons.Rounded.CheckCircle,
                        MaterialTheme.colorScheme.primary,
                        stringResource(R.string.status_installed),
                        stringResource(R.string.status_installed_desc)
                    )
                }

                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = statusBgColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                        .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        AnimatedContent(
                            targetState = statusInfo,
                            transitionSpec = {
                                (slideInVertically { it / 2 } + fadeIn(tween(220))) togetherWith
                                (slideOutVertically { -it / 2 } + fadeOut(tween(180)))
                            },
                            label = "StatusCardAnim"
                        ) { info ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(info.tint.copy(alpha = 0.15f), shape = CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = info.icon,
                                        contentDescription = null,
                                        tint = info.tint,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = info.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = statusTitleColor
                                    )
                                    Text(
                                        text = info.desc,
                                        fontSize = 13.sp,
                                        color = statusDescColor,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = activeWallpaper?.let {
                                stringResource(
                                    R.string.status_active_pack,
                                    it.manifest.name ?: it.folderName
                                )
                            } ?: stringResource(R.string.status_no_pack),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = statusDescColor
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { WallpaperManagerHelper.openWallpaperPicker(context) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(stringResource(R.string.btn_set_wallpaper))
                            }

                            IconButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    WallpaperManagerHelper.sendRestartSignal(context)
                                    Toast.makeText(context, R.string.toast_wallpaper_restarted, Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(14.dp))
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.btn_restart_wallpaper))
                            }

                            IconButton(
                                onClick = onTogglePause,
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(14.dp))
                            ) {
                                AnimatedContent(targetState = isPaused, label = "PauseIconAnim") { paused ->
                                    Icon(
                                        imageVector = if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                        contentDescription = stringResource(R.string.btn_pause_resume)
                                    )
                                }
                            }
                        }
                    }
                }

                PreferenceCategory(stringResource(R.string.info_title))

                PreferenceItem(
                    title = stringResource(R.string.app_version_title),
                    summary = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    onClick = {},
                    type = PreferenceType.TOP,
                    icon = Icons.Rounded.Info
                )

                PreferenceItem(
                    title = stringResource(R.string.docs_title),
                    summary = stringResource(R.string.docs_summary),
                    onClick = {
                        try {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/YouRooni/Wallify-Repo/blob/main/WALLPAPER_API.md")
                            )
                            context.startActivity(intent)
                        } catch (e: Exception) { e.printStackTrace() }
                    },
                    type = PreferenceType.BOTTOM,
                    icon = Icons.Rounded.MenuBook
                )

                PreferenceCategory(stringResource(R.string.author_title))

                PreferenceItem(
                    title = stringResource(R.string.author_name_title),
                    summary = stringResource(R.string.author_name_summary),
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://rooni.dev"))
                            context.startActivity(intent)
                        } catch (e: Exception) { e.printStackTrace() }
                    },
                    type = PreferenceType.TOP,
                    icon = Icons.Rounded.Person
                )

                PreferenceItem(
                    title = stringResource(R.string.tg_channel_title),
                    summary = stringResource(R.string.tg_channel_summary),
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/RnPlugins"))
                            context.startActivity(intent)
                        } catch (e: Exception) { e.printStackTrace() }
                    },
                    type = PreferenceType.BOTTOM,
                    icon = Icons.Rounded.Code
                )
            }
        }
    }
}

enum class WallpaperSort(val labelRes: Int, val icon: ImageVector) {
    RECENT(R.string.sort_recent, Icons.Rounded.History),
    NEWEST(R.string.sort_newest, Icons.Rounded.ArrowDownward),
    OLDEST(R.string.sort_oldest, Icons.Rounded.ArrowUpward),
    NAME_ASC(R.string.sort_name_asc, Icons.Rounded.SortByAlpha),
    NAME_DESC(R.string.sort_name_desc, Icons.Rounded.SortByAlpha)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedTransitionScope.WallpapersTabContent(
    rootUri: Uri?,
    wallpapers: List<WallpaperItem>,
    activeFolder: String?,
    sort: WallpaperSort,
    onSortChange: (WallpaperSort) -> Unit,
    expandedCards: MutableMap<String, Boolean>,
    openedLocalItemId: String?,
    onRefresh: () -> Unit,
    onSelectFolder: () -> Unit,
    onImportZip: () -> Unit,
    onSelectWallpaper: (WallpaperItem) -> Unit,
    onOpenDetails: (WallpaperItem) -> Unit,
    onPreview: (WallpaperItem) -> Unit,
    onOpenSettings: (WallpaperItem) -> Unit,
    onExportZip: (WallpaperItem) -> Unit,
    onDeleteWallpaper: (WallpaperItem) -> Unit
) {
    val context = LocalContext.current
    var isRefreshing by remember { mutableStateOf(false) }
    val refreshScope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showSortSheet by remember { mutableStateOf(false) }

    val topAppBarColors = TopAppBarDefaults.largeTopAppBarColors(
        containerColor = Color.Transparent,
        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )

    val isTopBarExpanded by remember {
        derivedStateOf { scrollBehavior.state.heightOffset >= -1f }
    }

    val lastUsed = remember(wallpapers, activeFolder) { StorageUtils.getLastUsedMap(context) }

    val visibleWallpapers = remember(wallpapers, searchQuery, sort, lastUsed) {
        val filtered = if (searchQuery.isBlank()) wallpapers else wallpapers.filter { item ->
            val name = item.manifest.name ?: item.folderName
            name.contains(searchQuery, true) ||
                item.folderName.contains(searchQuery, true) ||
                (item.manifest.author ?: "").contains(searchQuery, true)
        }

        fun displayName(item: WallpaperItem) = (item.manifest.name ?: item.folderName).lowercase()

        when (sort) {
            WallpaperSort.RECENT -> filtered.sortedWith(
                compareByDescending<WallpaperItem> { lastUsed[it.folderName] ?: 0L }
                    .thenBy { displayName(it) }
            )
            WallpaperSort.NEWEST -> filtered.sortedWith(
                compareByDescending<WallpaperItem> { it.lastModified }.thenBy { displayName(it) }
            )
            WallpaperSort.OLDEST -> filtered.sortedWith(
                compareBy<WallpaperItem> { it.lastModified }.thenBy { displayName(it) }
            )
            WallpaperSort.NAME_ASC -> filtered.sortedBy { displayName(it) }
            WallpaperSort.NAME_DESC -> filtered.sortedByDescending { displayName(it) }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            AnimatedContent(
                targetState = isSearchActive,
                transitionSpec = {
                    (fadeIn(tween(220)) + expandVertically()) togetherWith
                        (fadeOut(tween(180)) + shrinkVertically())
                },
                label = "WallpapersTopBarAnim"
            ) { searching ->
                if (searching) {
                    androidx.compose.material3.TopAppBar(
                        title = {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text(stringResource(R.string.search_wallpapers_hint)) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_clear))
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        navigationIcon = {
                            IconButton(onClick = {
                                isSearchActive = false
                                searchQuery = ""
                            }) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_close))
                            }
                        }
                    )
                } else {
                    LargeTopAppBar(
                        title = { Text(stringResource(R.string.tab_wallpapers), fontWeight = FontWeight.Bold) },
                        colors = topAppBarColors,
                        actions = {
                            if (rootUri != null) {
                                IconButton(onClick = { isSearchActive = true }) {
                                    Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.action_search))
                                }
                                IconButton(onClick = { showSortSheet = true }) {
                                    Icon(
                                        imageVector = Icons.Rounded.FilterList,
                                        contentDescription = stringResource(R.string.sort_title)
                                    )
                                }
                                IconButton(onClick = onImportZip) {
                                    Icon(
                                        imageVector = Icons.Rounded.FileUpload,
                                        contentDescription = stringResource(R.string.btn_import_zip)
                                    )
                                }
                            }
                        },
                        scrollBehavior = scrollBehavior
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (rootUri == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.height(56.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.no_folder_selected_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = stringResource(R.string.no_folder_selected_desc),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                            )
                            Button(
                                onClick = onSelectFolder,
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(stringResource(R.string.btn_select_folder))
                            }
                        }
                    }
                }
            } else {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        if (!isRefreshing) {
                            isRefreshing = true
                            refreshScope.launch {
                                onRefresh()
                                kotlinx.coroutines.delay(600)
                                isRefreshing = false
                            }
                        }
                    },
                    enabled = isTopBarExpanded && !isSearchActive,
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        state = listState,
                        contentPadding = innerPadding,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            PreferenceCategory(stringResource(R.string.wallpapers_count, visibleWallpapers.size))
                        }

                        if (visibleWallpapers.isEmpty()) {
                            item {
                                if (searchQuery.isNotBlank()) {
                                    EmptyState(
                                        icon = Icons.Rounded.Search,
                                        title = stringResource(R.string.no_wallpapers_match)
                                    )
                                } else {
                                    EmptyState(
                                        icon = Icons.Rounded.Wallpaper,
                                        title = stringResource(R.string.no_wallpapers_found)
                                    )
                                }
                            }
                        }

                        items(visibleWallpapers, key = { it.folderUriString }) { item ->
                            val stableKey = item.folderUriString
                            WallpaperCard(
                                wallpaper = item,
                                isActive = item.folderName == activeFolder,
                                expanded = expandedCards[stableKey] == true,
                                modifier = Modifier.animateItem(),
                                coverModifier = Modifier.sharedElementWithCallerManagedVisibility(
                                    sharedContentState = rememberSharedContentState(localCoverKey(item.id)),
                                    visible = openedLocalItemId != item.id,
                                    renderInOverlayDuringTransition = false
                                ),
                                onExpandedChange = { expandedCards[stableKey] = it },
                                onCoverClick = { onOpenDetails(item) },
                                onSelect = { onSelectWallpaper(item) },
                                onPreview = { onPreview(item) },
                                onOpenSettings = { onOpenSettings(item) },
                                onExportZip = { onExportZip(item) },
                                onDelete = { onDeleteWallpaper(item) }
                            )
                        }
                    }
                }
            }

            if (showSortSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showSortSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp, start = 16.dp, end = 16.dp, top = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.sort_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        WallpaperSort.entries.forEach { option ->
                            SortRow(
                                icon = option.icon,
                                label = stringResource(option.labelRes),
                                selected = sort == option
                            ) {
                                onSortChange(option)
                                showSortSheet = false
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepositoryNeedsFolderScreen(onSelectFolder: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(stringResource(R.string.tab_repository), fontWeight = FontWeight.Bold)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Rounded.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.height(56.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.no_folder_selected_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        stringResource(R.string.repo_folder_required_desc),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
                    )
                    Button(onClick = onSelectFolder, shape = RoundedCornerShape(14.dp)) {
                        Text(stringResource(R.string.btn_select_folder))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.RepositoryTabContent(
    catalogItems: List<RepoWallpaperItem>,
    downloadCounts: Map<String, Int>,
    isLoading: Boolean,
    openedRepoItemId: String?,
    onRefreshCatalog: () -> Unit,
    onSelectRepoItem: (RepoWallpaperItem) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()

    val topAppBarColors = TopAppBarDefaults.largeTopAppBarColors(
        containerColor = Color.Transparent,
        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )

    val isTopBarExpanded by remember {
        derivedStateOf { scrollBehavior.state.heightOffset >= -1f }
    }

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var sortType by remember { mutableStateOf("newest") }
    var showSortSheet by remember { mutableStateOf(false) }

    var selectedModes by remember { mutableStateOf(setOf("lite", "full")) }
    var showUnsupported by remember { mutableStateOf(false) }
    var landscapeOnly by remember { mutableStateOf(false) }
    var adaptiveThemeOnly by remember { mutableStateOf(false) }
    var monetOnly by remember { mutableStateOf(false) }

    var isNoticeVisible by remember { mutableStateOf(!StorageUtils.isRepoNoticeDismissed(context)) }
    var isRefreshing by remember { mutableStateOf(false) }
    var installedVersionsMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    suspend fun refreshLocalInstalled() {
        val map = withContext(Dispatchers.IO) {
            val local = StorageUtils.scanWallpapers(context)
            val result = mutableMapOf<String, String>()
            for (item in local) {
                val ver = item.manifest.version ?: "1.0.0"
                item.manifest.name?.let { result[it] = ver }
                result[item.folderName] = ver
                result[item.id] = ver
            }
            result
        }
        installedVersionsMap = map
    }

    LaunchedEffect(Unit) {
        refreshLocalInstalled()
    }

    val filteredItems = remember(
        searchQuery,
        sortType,
        catalogItems,
        installedVersionsMap,
        selectedModes,
        showUnsupported,
        landscapeOnly,
        adaptiveThemeOnly,
        monetOnly,
        downloadCounts
    ) {
        val terms = searchQuery.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val hashtagSearch = terms.any { it.startsWith("#") }
        var result = catalogItems.filter { item ->
            val searchableText = listOf(item.name, item.author, item.description)
            val matchesSearch = terms.all { rawTerm ->
                if (rawTerm.startsWith("#")) {
                    val tag = rawTerm.removePrefix("#")
                    item.tags.orEmpty().any { it.equals(tag, ignoreCase = true) }
                } else {
                    searchableText.any { it.contains(rawTerm, ignoreCase = true) } ||
                        item.tags.orEmpty().any { it.contains(rawTerm, ignoreCase = true) }
                }
            }
            val matchesMode = (item.isLite && "lite" in selectedModes) || (!item.isLite && "full" in selectedModes)
            val compatible = VersionEvaluator
                .isCompatible(BuildConfig.VERSION_NAME, item.minAppVersion).first
            val matchesLandscape = !landscapeOnly ||
                item.orientations.orEmpty().any { it.equals("landscape", ignoreCase = true) }
            val themes = item.themeSupport.orEmpty().map(String::lowercase)
            val matchesTheme = !adaptiveThemeOnly || ("light" in themes && "dark" in themes)
            val matchesMonet = !monetOnly || item.supportsMonet
            matchesSearch && matchesMode && matchesLandscape && matchesTheme &&
                matchesMonet && (compatible || showUnsupported)
        }

        fun checkHasUpdate(item: RepoWallpaperItem): Boolean {
            val instVer = installedVersionsMap[item.name] ?: installedVersionsMap[item.id]
            return instVer != null && VersionEvaluator.isNewer(item.version, instVer)
        }

        result = when {
            hashtagSearch || sortType == "downloads" -> result.sortedByDescending {
                downloadCounts[RepositoryFetcher.downloadKey(it)] ?: 0
            }
            sortType == "newest" -> result.reversed()
            else -> result.sortedBy { it.name.lowercase() }
        }

        result.sortedByDescending { checkHasUpdate(it) }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            AnimatedContent(
                targetState = isSearchActive,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(220)) + expandVertically()) togetherWith
                    (fadeOut(animationSpec = tween(180)) + shrinkVertically())
                },
                label = "SearchTopAppBarAnim"
            ) { searching ->
                if (searching) {
                    androidx.compose.material3.TopAppBar(
                        title = {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text(stringResource(R.string.repo_search_hint)) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_clear))
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        navigationIcon = {
                            IconButton(onClick = {
                                isSearchActive = false
                                searchQuery = ""
                            }) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_close))
                            }
                        }
                    )
                } else {
                    LargeTopAppBar(
                        title = { Text(stringResource(R.string.tab_repository), fontWeight = FontWeight.Bold) },
                        colors = topAppBarColors,
                        actions = {
                            IconButton(
                                onClick = {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse(RepositoryFetcher.REPOSITORY_URL)
                                        )
                                    )
                                }
                            ) {
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(R.drawable.ic_github),
                                    contentDescription = stringResource(R.string.open_on_github)
                                )
                            }
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.action_search))
                            }
                            IconButton(onClick = { showSortSheet = true }) {
                                Icon(Icons.Rounded.FilterList, contentDescription = stringResource(R.string.repo_sort_title))
                            }
                        },
                        scrollBehavior = scrollBehavior
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    if (!isRefreshing) {
                        isRefreshing = true
                        coroutineScope.launch {
                            refreshLocalInstalled()
                            onRefreshCatalog()
                            kotlinx.coroutines.delay(600)
                            isRefreshing = false
                        }
                    }
                },
                enabled = isTopBarExpanded && !isSearchActive,
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = innerPadding,
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        AnimatedVisibility(
                            visible = isNoticeVisible,
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Card(
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.repo_notice_title),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = stringResource(R.string.repo_notice_desc),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            isNoticeVisible = false
                                            StorageUtils.setRepoNoticeDismissed(context, true)
                                        }
                                    ) {
                                        Icon(
                                            Icons.Rounded.Close,
                                            contentDescription = stringResource(R.string.repo_notice_dismiss)
                                        )
                                    }
                                }
                            }
                        }

                        PreferenceCategory(stringResource(R.string.repo_count, filteredItems.size))
                    }

                    if (isLoading && catalogItems.isEmpty()) {
                        items(4) { SkeletonCard() }
                    } else if (filteredItems.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Rounded.Explore,
                                title = stringResource(R.string.repo_empty)
                            )
                        }
                    } else {
                        items(filteredItems, key = { it.id }) { item ->
                            val instVer = installedVersionsMap[item.name] ?: installedVersionsMap[item.id]
                            val isInstalled = instVer != null
                            val hasUpdate = isInstalled && instVer != null &&
                                VersionEvaluator.isNewer(item.version, instVer)
                            val compatible = VersionEvaluator
                                .isCompatible(BuildConfig.VERSION_NAME, item.minAppVersion).first

                            Card(
                                onClick = { onSelectRepoItem(item) },
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (hasUpdate) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                    else MaterialTheme.colorScheme.surfaceContainer
                                ),
                                modifier = Modifier
                                    .animateItem()
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 5.dp)
                            ) {
                                RepoBanner(
                                    item = item,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 9f)
                                )
                                Row(
                                    modifier = Modifier.padding(14.dp)
                                ) {
                                    RepoCover(
                                        item = item,
                                        cornerRadius = 14.dp,
                                        iconSize = 24.dp,
                                        highlighted = hasUpdate,
                                        modifier = Modifier
                                            .sharedElementWithCallerManagedVisibility(
                                                sharedContentState = rememberSharedContentState(
                                                    key = repoCoverKey(item.id)
                                                ),
                                                visible = openedRepoItemId != item.id,
                                                renderInOverlayDuringTransition = false
                                            )
                                            .size(52.dp)
                                    )

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = item.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )

                                            if (hasUpdate) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Badge(
                                                    text = stringResource(R.string.repo_badge_update),
                                                    background = MaterialTheme.colorScheme.primary,
                                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                                )
                                            } else if (isInstalled) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Badge(
                                                    text = stringResource(R.string.repo_badge_installed),
                                                    background = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                    contentColor = MaterialTheme.colorScheme.outline
                                                )
                                            }
                                            if (!compatible) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Badge(
                                                    text = stringResource(
                                                        R.string.repo_requires_version,
                                                        item.minAppVersion ?: "?"
                                                    ),
                                                    background = MaterialTheme.colorScheme.errorContainer,
                                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                            }
                                            if (item.orientations.orEmpty().any { it.equals("landscape", true) }) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Badge(
                                                    text = stringResource(R.string.repo_badge_landscape),
                                                    background = MaterialTheme.colorScheme.secondaryContainer,
                                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                            }
                                        }

                                        Text(
                                            text = stringResource(R.string.wallpaper_by, item.author, item.version),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )

                                        Text(
                                            text = item.description,
                                            fontSize = 13.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (showSortSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showSortSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp, start = 16.dp, end = 16.dp, top = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.repo_sort_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        SortRow(
                            icon = Icons.Rounded.SortByAlpha,
                            label = stringResource(R.string.repo_sort_alpha),
                            selected = sortType == "alphabet"
                        ) {
                            sortType = "alphabet"
                            showSortSheet = false
                        }
                        SortRow(
                            icon = Icons.Rounded.Download,
                            label = stringResource(R.string.repo_sort_downloads),
                            selected = sortType == "downloads"
                        ) {
                            sortType = "downloads"
                            showSortSheet = false
                        }
                        SortRow(
                            icon = Icons.Rounded.Schedule,
                            label = stringResource(R.string.repo_sort_new),
                            selected = sortType == "newest"
                        ) {
                            sortType = "newest"
                            showSortSheet = false
                        }

                        Text(
                            text = stringResource(R.string.repo_type_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            FilterChip(
                                selected = "lite" in selectedModes,
                                onClick = {
                                    selectedModes = if ("lite" in selectedModes) {
                                        if (selectedModes.size > 1) selectedModes - "lite" else selectedModes
                                    } else {
                                        selectedModes + "lite"
                                    }
                                },
                                label = { Text(stringResource(R.string.repo_type_lite)) },
                                leadingIcon = if ("lite" in selectedModes) {
                                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )

                            FilterChip(
                                selected = "full" in selectedModes,
                                onClick = {
                                    selectedModes = if ("full" in selectedModes) {
                                        if (selectedModes.size > 1) selectedModes - "full" else selectedModes
                                    } else {
                                        selectedModes + "full"
                                    }
                                },
                                label = { Text(stringResource(R.string.repo_type_full)) },
                                leadingIcon = if ("full" in selectedModes) {
                                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                        FilterChip(
                            selected = showUnsupported,
                            onClick = { showUnsupported = !showUnsupported },
                            label = { Text(stringResource(R.string.repo_type_unsupported)) },
                            leadingIcon = if (showUnsupported) {
                                {
                                    Icon(
                                        Icons.Rounded.Warning,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null
                        )

                        Text(
                            text = stringResource(R.string.repo_support_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )
                        FilterChip(
                            selected = landscapeOnly,
                            onClick = { landscapeOnly = !landscapeOnly },
                            label = { Text(stringResource(R.string.repo_filter_landscape)) },
                            leadingIcon = if (landscapeOnly) {
                                { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                            } else null
                        )
                        FilterChip(
                            selected = adaptiveThemeOnly,
                            onClick = { adaptiveThemeOnly = !adaptiveThemeOnly },
                            label = { Text(stringResource(R.string.repo_filter_themes)) },
                            leadingIcon = if (adaptiveThemeOnly) {
                                { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                            } else null
                        )
                        FilterChip(
                            selected = monetOnly,
                            onClick = { monetOnly = !monetOnly },
                            label = { Text(stringResource(R.string.repo_filter_monet)) },
                            leadingIcon = if (monetOnly) {
                                { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String, background: Color, contentColor: Color) {
    Box(
        modifier = Modifier
            .background(background, shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}

@Composable
private fun SortRow(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(16.dp))
        Text(label, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTabContent(
    rootUri: Uri?,
    isTouchEnabled: Boolean,
    onToggleTouch: (Boolean) -> Unit,
    onChangeFolder: () -> Unit,
    onOpenDebug: () -> Unit
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()

    val topAppBarColors = TopAppBarDefaults.largeTopAppBarColors(
        containerColor = Color.Transparent,
        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )

    val maxRefreshRate = remember { StorageUtils.getMaxRefreshRate(context) }

    val fpsOptions = remember(maxRefreshRate) {
        (1..12)
            .map { (maxRefreshRate.toFloat() / it).roundToInt() }
            .filter { it >= 15 }
            .distinct()
            .sorted()
            .ifEmpty { listOf(maxRefreshRate) }
    }
    var fpsIndex by remember {
        val saved = StorageUtils.getTargetFps(context)
        mutableIntStateOf(
            fpsOptions.indices.minByOrNull { kotlin.math.abs(fpsOptions[it] - saved) }
                ?: fpsOptions.lastIndex
        )
    }
    var resSliderValue by remember { mutableFloatStateOf(StorageUtils.getResolutionScale(context).toFloat()) }
    var autoPausePowerSave by remember { mutableStateOf(StorageUtils.isAutoPausePowerSave(context)) }
    fun isBatteryOptimizationIgnored(): Boolean {
        val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    }
    var batteryOptimizationIgnored by remember { mutableStateOf(isBatteryOptimizationIgnored()) }
    var bypassIframe by remember { mutableStateOf(StorageUtils.isBypassIframeEnabled(context)) }
    var simpleRender by remember { mutableStateOf(StorageUtils.isSimpleRenderMode(context)) }

    val settingsLifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(settingsLifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                simpleRender = StorageUtils.isSimpleRenderMode(context)
                batteryOptimizationIgnored = isBatteryOptimizationIgnored()
            }
        }
        settingsLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { settingsLifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.tab_settings), fontWeight = FontWeight.Bold) },
                colors = topAppBarColors,
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            contentPadding = innerPadding,
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                PreferenceCategory(stringResource(R.string.settings_general))

                PreferenceItem(
                    title = stringResource(
                        if (batteryOptimizationIgnored) {
                            R.string.battery_optimization_done
                        } else {
                            R.string.battery_optimization_title
                        }
                    ),
                    summary = stringResource(
                        if (batteryOptimizationIgnored) {
                            R.string.battery_optimization_done_summary
                        } else {
                            R.string.battery_optimization_summary
                        }
                    ),
                    onClick = {
                        if (!batteryOptimizationIgnored) {
                            val directIntent = Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${context.packageName}")
                            )
                            try {
                                context.startActivity(directIntent)
                            } catch (_: Exception) {
                                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            }
                        }
                    },
                    type = PreferenceType.TOP,
                    icon = Icons.Rounded.BatterySaver
                )

                PreferenceSwitch(
                    title = stringResource(R.string.touch_title),
                    summary = stringResource(R.string.touch_summary),
                    checked = isTouchEnabled,
                    onCheckedChange = onToggleTouch,
                    type = PreferenceType.MID,
                    icon = Icons.Rounded.TouchApp
                )

                PreferenceSwitch(
                    title = stringResource(R.string.auto_pause_title),
                    summary = stringResource(R.string.auto_pause_summary),
                    checked = autoPausePowerSave,
                    onCheckedChange = { enabled ->
                        autoPausePowerSave = enabled
                        StorageUtils.setAutoPausePowerSave(context, enabled)
                        WallpaperManagerHelper.sendReloadSignal(context)
                    },
                    type = PreferenceType.MID,
                    icon = Icons.Rounded.Speed
                )

                PreferenceItem(
                    title = stringResource(R.string.change_folder_title),
                    summary = rootUri?.lastPathSegment ?: stringResource(R.string.change_folder_empty),
                    onClick = onChangeFolder,
                    type = PreferenceType.BOTTOM,
                    icon = Icons.Rounded.FolderOpen
                )

                PreferenceCategory(stringResource(R.string.settings_experimental))

                PreferenceSwitch(
                    title = stringResource(R.string.bypass_iframe_title),
                    summary = stringResource(R.string.bypass_iframe_summary),
                    checked = bypassIframe,
                    onCheckedChange = { enabled ->
                        bypassIframe = enabled
                        StorageUtils.setBypassIframeEnabled(context, enabled)
                        WallpaperManagerHelper.sendReloadSignal(context)
                    },
                    type = PreferenceType.TOP,
                    icon = Icons.Rounded.Security
                )

                PreferenceSwitch(
                    title = stringResource(R.string.simple_render_title),
                    summary = stringResource(R.string.simple_render_summary),
                    checked = simpleRender,
                    onCheckedChange = { enabled ->
                        simpleRender = enabled
                        StorageUtils.setSimpleRenderMode(context, enabled)
                        WallpaperManagerHelper.sendReloadSignal(context)
                    },
                    type = PreferenceType.BOTTOM,
                    icon = Icons.Rounded.Memory
                )

                PreferenceCategory(stringResource(R.string.settings_perf))

                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Speed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.fps_title, fpsOptions[fpsIndex]),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = stringResource(R.string.fps_max_summary, maxRefreshRate),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                        )

                        Slider(
                            value = fpsIndex.toFloat(),
                            onValueChange = { fpsIndex = it.roundToInt().coerceIn(fpsOptions.indices) },
                            onValueChangeFinished = {
                                StorageUtils.setTargetFps(context, fpsOptions[fpsIndex])
                                WallpaperManagerHelper.sendReloadSignal(context)
                            },
                            valueRange = 0f..(fpsOptions.size - 1).toFloat(),
                            steps = (fpsOptions.size - 2).coerceAtLeast(0)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.res_title, resSliderValue.roundToInt()),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = stringResource(R.string.res_summary),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                        )

                        Slider(
                            value = resSliderValue,
                            onValueChange = { resSliderValue = it },
                            onValueChangeFinished = {
                                val selectedRes = resSliderValue.roundToInt()
                                StorageUtils.setResolutionScale(context, selectedRes)
                                WallpaperManagerHelper.sendReloadSignal(context)
                            },
                            valueRange = 50f..100f,
                            steps = 4
                        )
                    }
                }

                PreferenceCategory(stringResource(R.string.settings_dev))

                PreferenceItem(
                    title = stringResource(R.string.debug_title),
                    summary = stringResource(R.string.debug_summary),
                    onClick = onOpenDebug,
                    type = PreferenceType.ROUND,
                    icon = Icons.Rounded.Code
                )
            }
        }
    }
}
