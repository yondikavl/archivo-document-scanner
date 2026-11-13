package com.yondex.archivo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.annotation.DrawableRes
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Modern borderless surfaces with squircle corners. */
val SketchShape = SquircleShape(28.dp, smoothing = 0.82f)
val SketchShapeLarge = SquircleShape(36.dp, smoothing = 0.82f)
val SketchShapeTop = SquircleShape(topStart = 28.dp, topEnd = 28.dp, smoothing = 0.82f)
val SketchShapePill = SquircleShape(percent = 50, smoothing = 0.88f)
val SketchInk = Color.Black
@Deprecated("Use SketchInk", ReplaceWith("SketchInk"))
val SketchShadowBlack = SketchInk

/** Kept for call-site compatibility; borders are unused. */
val SketchStroke = 0.dp
val SketchStrokeSelected = 0.dp
val SketchShadowOffset = 0.dp
val SketchItemGap = 12.dp
val SketchSectionGap = 16.dp
val SketchChipGap = 10.dp

/** Drag indicator for modal bottom sheets. */
@Composable
fun SketchSheetDragHandle(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
    ) {
        Box(
            Modifier
                .size(width = 40.dp, height = 4.dp)
                .background(MaterialTheme.colorScheme.outlineVariant, SketchShapePill),
        )
    }
}

/** No-op — modern UI is borderless. */
@Suppress("UNUSED_PARAMETER")
fun Modifier.sketchBorder(
    shape: Shape = SketchShape,
    color: Color = SketchInk,
    width: Dp = SketchStroke,
): Modifier = this

@Composable
fun SketchCard(
    modifier: Modifier = Modifier,
    shape: Shape = SketchShape,
    ink: Color = Color.Transparent,
    paper: Color = MaterialTheme.colorScheme.surface,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    @Suppress("UNUSED_VARIABLE")
    val unusedInk = ink
    val interaction = remember { MutableInteractionSource() }
    val container = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        paper
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(container)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = ripple(),
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
        content = content,
    )
}

@Composable
fun SketchIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    shape: Shape = SketchShape,
    ink: Color = Color.Transparent,
) {
    SketchIconButton(
        painter = rememberVectorPainter(icon),
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        size = size,
        shape = shape,
        ink = ink,
    )
}

@Composable
fun SketchIconButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    shape: Shape = SketchShape,
    ink: Color = Color.Transparent,
) {
    SketchIconButton(
        painter = painterResource(iconRes),
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        size = size,
        shape = shape,
        ink = ink,
    )
}

@Composable
fun SketchIconButton(
    painter: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    shape: Shape = SketchShape,
    ink: Color = Color.Transparent,
) {
    val accented = ink != Color.Transparent
    Surface(
        onClick = onClick,
        shape = shape,
        color = if (accented) ink else MaterialTheme.colorScheme.surface,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        modifier = modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                painter = painter,
                contentDescription = contentDescription,
                tint = if (accented) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
fun SketchChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    /** Ignored — kept for call-site compatibility. */
    shadowed: Boolean = true,
) {
    @Suppress("UNUSED_VARIABLE")
    val unusedShadowed = shadowed
    val paper = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Surface(
        onClick = onClick,
        shape = SketchShapePill,
        color = paper,
        contentColor = content,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            leadingIcon?.invoke()
            label()
            trailingIcon?.invoke()
        }
    }
}

@Composable
fun SketchFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    content: @Composable () -> Unit,
) {
    FloatingActionButton(
        onClick = onClick,
        shape = SketchShape,
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = FloatingActionButtonDefaults.elevation(2.dp, 4.dp, 2.dp, 2.dp),
        modifier = modifier.size(56.dp),
        content = content,
    )
}

@Composable
fun SketchExtendedFab(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    text: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    Surface(
        onClick = onClick,
        shape = SketchShape,
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = 2.dp,
        tonalElevation = 0.dp,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            icon()
            Spacer(Modifier.width(10.dp))
            text()
        }
    }
}

@Composable
fun SketchFilledButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fillWidth: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = SketchShape,
        colors = ButtonDefaults.buttonColors(),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        modifier = modifier.then(if (fillWidth) Modifier.fillMaxWidth() else Modifier),
        content = content,
    )
}

@Composable
fun SketchOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fillWidth: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = SketchShape,
        border = null,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        modifier = modifier.then(if (fillWidth) Modifier.fillMaxWidth() else Modifier),
        content = content,
    )
}

@Composable
fun SketchBottomBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        shape = SketchShapeTop,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = SketchSectionGap, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(SketchChipGap),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
fun RowScope.SketchBottomNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    @DrawableRes iconRes: Int,
    label: String,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .weight(1f)
            .fillMaxSize()
            .clip(SketchShape)
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = onClick,
            )
            .padding(horizontal = 4.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
fun SketchDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        shape = SketchShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
        border = null,
        content = content,
    )
}

@Composable
fun SketchAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties,
    ) {
        Surface(
            shape = SketchShapeLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = AlertDialogDefaults.textContentColor,
            tonalElevation = 3.dp,
            shadowElevation = 0.dp,
            modifier = modifier.widthIn(min = 280.dp, max = 560.dp),
        ) {
            Column(Modifier.padding(top = 24.dp, bottom = 16.dp)) {
                if (title != null) {
                    Box(Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp)) {
                        CompositionLocalProvider(
                            LocalContentColor provides AlertDialogDefaults.titleContentColor,
                            LocalTextStyle provides MaterialTheme.typography.headlineSmall,
                            content = title,
                        )
                    }
                }
                if (text != null) {
                    Box(Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp)) {
                        CompositionLocalProvider(
                            LocalContentColor provides AlertDialogDefaults.textContentColor,
                            LocalTextStyle provides MaterialTheme.typography.bodyMedium,
                            content = text,
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}
