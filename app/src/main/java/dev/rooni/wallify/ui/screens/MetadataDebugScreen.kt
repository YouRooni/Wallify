package dev.rooni.wallify.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.GsonBuilder
import dev.rooni.wallify.R
import dev.rooni.wallify.bridge.SystemMetadataProvider
import dev.rooni.wallify.ui.components.PreferenceCategory
import dev.rooni.wallify.ui.components.PreferenceItem
import dev.rooni.wallify.ui.components.PreferenceType

@Composable
fun MetadataDebugScreen() {
    val context = LocalContext.current
    val metadata = SystemMetadataProvider.getMetadata(context)
    val gson = GsonBuilder().setPrettyPrinting().create()
    val jsonStr = gson.toJson(metadata)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        PreferenceCategory(stringResource(R.string.debug_section_metadata))

        PreferenceItem(
            title = stringResource(R.string.debug_theme),
            summary = metadata.theme.uppercase(),
            onClick = {},
            type = PreferenceType.TOP,
            icon = Icons.Rounded.Palette
        )

        PreferenceItem(
            title = stringResource(R.string.debug_screen_state),
            summary = if (metadata.isUnlocked) stringResource(R.string.debug_unlocked)
            else stringResource(R.string.debug_locked),
            onClick = {},
            type = PreferenceType.MID,
            icon = if (metadata.isUnlocked) Icons.Rounded.LockOpen else Icons.Rounded.Lock
        )

        PreferenceItem(
            title = stringResource(R.string.debug_device),
            summary = "${metadata.device.manufacturer} ${metadata.device.model} (Android ${metadata.device.androidVersion})",
            onClick = {},
            type = PreferenceType.BOTTOM,
            icon = Icons.Rounded.PhoneAndroid
        )

        PreferenceCategory(stringResource(R.string.debug_section_colors))

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                ColorRow("Primary Accent", metadata.accentColors.primary)
                ColorRow("Secondary Accent", metadata.accentColors.secondary)
                ColorRow("Tertiary Accent", metadata.accentColors.tertiary)
                ColorRow("Surface Color", metadata.accentColors.surface)
                ColorRow("Background Color", metadata.accentColors.background)
                ColorRow("Surface Container", metadata.accentColors.surfaceContainer)
            }
        }

        PreferenceCategory(stringResource(R.string.debug_section_json))

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp)
        ) {
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(
                    text = jsonStr,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Composable
fun ColorRow(label: String, hexColor: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val parsedColor = try {
            Color(android.graphics.Color.parseColor(hexColor))
        } catch (e: Exception) {
            MaterialTheme.colorScheme.primary
        }

        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(parsedColor)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = hexColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
