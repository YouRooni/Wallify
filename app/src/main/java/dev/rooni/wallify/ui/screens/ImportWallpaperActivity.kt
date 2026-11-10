package dev.rooni.wallify.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rooni.wallify.R
import dev.rooni.wallify.service.WallpaperLocalServer
import dev.rooni.wallify.ui.theme.HTMLWallpaperTheme
import dev.rooni.wallify.util.StorageUtils
import dev.rooni.wallify.util.WallpaperManagerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImportWallpaperActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sourceUri = resolveSourceUri(intent)
        if (sourceUri == null) {
            Toast.makeText(this, getString(R.string.import_no_file), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            HTMLWallpaperTheme {
                ImportDialog(
                    sourceUri = sourceUri,
                    onClose = { finish() }
                )
            }
        }
    }

    private fun resolveSourceUri(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
            }
            else -> intent.data
        }
    }
}

private sealed interface ImportState {
    data object Loading : ImportState
    data class Ready(val preview: StorageUtils.ZipPreview, val isInstalled: Boolean) : ImportState
    data class Failed(val messageRes: Int) : ImportState
    data object NoFolder : ImportState
    data class Installing(val name: String) : ImportState
}

@Composable
private fun ImportDialog(sourceUri: Uri, onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<ImportState>(ImportState.Loading) }
    var visible by remember { mutableStateOf(false) }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            StorageUtils.saveRootFolderUri(context, uri)
            state = ImportState.Loading
        }
    }

    LaunchedEffect(sourceUri, state is ImportState.Loading) {
        if (state !is ImportState.Loading) return@LaunchedEffect
        visible = true
        val result = withContext(Dispatchers.IO) {
            if (StorageUtils.getRootFolderUri(context) == null) return@withContext null
            StorageUtils.peekZip(context, sourceUri)
        }
        state = when {
            StorageUtils.getRootFolderUri(context) == null -> ImportState.NoFolder
            result == null -> ImportState.Failed(R.string.import_bad_archive)
            !result.hasEntryPoint -> ImportState.Failed(R.string.import_no_index)
            else -> {
                val installed = StorageUtils.scanWallpapers(context).any {
                    it.id.equals(result.suggestedName, true) ||
                        it.folderName.equals(result.suggestedName, true) ||
                        (it.manifest.name?.equals(result.manifest?.name, true) == true)
                }
                ImportState.Ready(result, installed)
            }
        }
    }

    fun install(apply: Boolean) {
        val ready = state as? ImportState.Ready ?: return
        val name = ready.preview.suggestedName
        state = ImportState.Installing(name)
        scope.launch {
            val folder = withContext(Dispatchers.IO) {
                StorageUtils.importWallpaperFromZip(context, sourceUri, name)
            }
            if (folder == null) {
                Toast.makeText(context, context.getString(R.string.import_failed), Toast.LENGTH_SHORT).show()
                onClose()
                return@launch
            }
            if (apply) {
                withContext(Dispatchers.IO) {
                    val item = StorageUtils.scanWallpapers(context, forceRefresh = true)
                        .find { it.folderName == folder }
                    if (item != null) {
                        StorageUtils.setActiveWallpaperFolder(context, folder)
                        WallpaperLocalServer.getInstance(context)
                            .setWallpaperUri(Uri.parse(item.folderUriString))
                    }
                }
                WallpaperManagerHelper.sendRestartSignal(context)
                if (!WallpaperManagerHelper.isWallpaperActive(context)) {
                    WallpaperManagerHelper.openWallpaperPicker(context)
                } else {
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_wallpaper_applied),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                withContext(Dispatchers.IO) { StorageUtils.scanWallpapers(context, forceRefresh = true) }
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_wallpaper_installed, folder),
                    Toast.LENGTH_SHORT
                ).show()
            }
            onClose()
        }
    }

    val scrimInteraction = remember { MutableInteractionSource() }
    val cardInteraction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = scrimInteraction, indication = null) { onClose() },
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.9f, animationSpec = tween(180)),
            exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.9f, animationSpec = tween(120))
        ) {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
                    .clickable(interactionSource = cardInteraction, indication = null) {}
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    when (val s = state) {
                        is ImportState.Loading -> DialogBody(
                            icon = Icons.Rounded.Archive,
                            title = stringResource(R.string.import_reading),
                            text = null,
                            busy = true
                        )

                        is ImportState.NoFolder -> {
                            DialogBody(
                                icon = Icons.Rounded.FolderOpen,
                                title = stringResource(R.string.import_need_folder_title),
                                text = stringResource(R.string.import_need_folder_desc)
                            )
                            Spacer(Modifier.height(20.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                TextButton(onClick = onClose) { Text(stringResource(R.string.action_cancel)) }
                                Button(
                                    onClick = { folderPicker.launch(null) },
                                    shape = RoundedCornerShape(14.dp)
                                ) { Text(stringResource(R.string.btn_select_folder)) }
                            }
                        }

                        is ImportState.Failed -> {
                            DialogBody(
                                icon = Icons.Rounded.ErrorOutline,
                                title = stringResource(R.string.import_failed_title),
                                text = stringResource(s.messageRes),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.height(20.dp))
                            Row(
                                horizontalArrangement = Arrangement.End,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(onClick = onClose, shape = RoundedCornerShape(14.dp)) {
                                    Text(stringResource(R.string.action_close))
                                }
                            }
                        }

                        is ImportState.Installing -> DialogBody(
                            icon = Icons.Rounded.Archive,
                            title = stringResource(R.string.import_installing, s.name),
                            text = null,
                            busy = true
                        )

                        is ImportState.Ready -> {
                            val p = s.preview
                            val avatar = remember(p.coverBytes) {
                                p.coverBytes?.let {
                                    android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size)
                                }
                            }
                            avatar?.let {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .padding(bottom = 14.dp)
                                        .size(72.dp)
                                        .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(18.dp))
                                )
                            }
                            DialogBody(
                                icon = Icons.Rounded.Archive,
                                title = p.manifest?.name ?: p.suggestedName,
                                text = buildString {
                                    p.manifest?.author?.takeIf { it.isNotBlank() }?.let {
                                        append(context.getString(R.string.import_author, it))
                                        append('\n')
                                    }
                                    p.manifest?.version?.takeIf { it.isNotBlank() }?.let {
                                        append(context.getString(R.string.import_version, it))
                                        append('\n')
                                    }
                                    append(context.getString(R.string.import_files, p.fileCount))
                                }
                            )

                            p.manifest?.description?.takeIf { it.isNotBlank() }?.let { desc ->
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = desc,
                                    fontSize = 13.sp,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            p.readme?.takeIf { it.isNotBlank() }?.let { readme ->
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = readme,
                                    fontSize = 12.sp,
                                    maxLines = 8,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(Modifier.height(20.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { install(apply = true) },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                ) {
                                    Text(
                                        stringResource(
                                            if (s.isInstalled) R.string.import_update_and_apply
                                            else R.string.import_install_and_apply
                                        )
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    TextButton(onClick = onClose) {
                                        Text(stringResource(R.string.action_cancel))
                                    }
                                    TextButton(onClick = { install(apply = false) }) {
                                        Text(
                                            stringResource(
                                                if (s.isInstalled) R.string.import_update_only
                                                else R.string.import_install_only
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogBody(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    text: String?,
    busy: Boolean = false,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(tint.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = tint)
            } else {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (text != null) {
                Text(
                    text = text,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
