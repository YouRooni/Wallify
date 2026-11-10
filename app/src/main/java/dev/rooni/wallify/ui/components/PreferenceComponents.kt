package dev.rooni.wallify.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class PreferenceType {
    TOP, MID, BOTTOM, ROUND
}

@Composable
fun getChildColor(): Color {
    return if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        lerp(MaterialTheme.colorScheme.surfaceVariant, Color.Black, 0.03f)
    }
}

@Composable
fun PreferenceCategory(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
fun PreferenceItem(
    title: String,
    summary: String,
    onClick: () -> Unit,
    type: PreferenceType = PreferenceType.MID,
    isChild: Boolean = false,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val contentAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.38f,
        animationSpec = tween(220),
        label = "PreferenceEnabledAlpha"
    )

    val shape = when (type) {
        PreferenceType.TOP -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
        PreferenceType.MID -> RoundedCornerShape(4.dp)
        PreferenceType.BOTTOM -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        PreferenceType.ROUND -> RoundedCornerShape(24.dp)
    }

    Card(
        onClick = onClick,
        enabled = enabled,
        shape = if (!isChild) shape else RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isChild) getChildColor() else MaterialTheme.colorScheme.surfaceContainerHighest,
            disabledContainerColor = if (isChild) getChildColor() else MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .graphicsLayer { alpha = contentAlpha },
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .size(48.dp)
                        .padding(12.dp)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (icon != null) 16.dp else 0.dp)
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (summary.isNotEmpty()) {
                    Text(
                        text = summary,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            if (trailingContent != null) {
                trailingContent()
            }
        }
    }
}

@Composable
fun PreferenceSwitch(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    type: PreferenceType = PreferenceType.MID,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    PreferenceItem(
        title = title,
        summary = summary,
        onClick = { onCheckedChange(!checked) },
        type = type,
        icon = icon,
        enabled = enabled,
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled
            )
        }
    )
}
