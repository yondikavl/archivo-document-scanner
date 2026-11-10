package com.yondex.archivo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex

/** Shared ink for outline + doodle shadow. */
val SketchInk = Color.Black

@Deprecated("Use SketchInk", ReplaceWith("SketchInk"))
val SketchShadowBlack = SketchInk

val SketchStroke = 1.5.dp
val SketchShadowOffset = 3.dp

fun Modifier.sketchBorder(
    shape: Shape = RectangleShape,
    color: Color = SketchInk,
    width: Dp = SketchStroke,
): Modifier = border(width = width, color = color, shape = shape)

@Composable
fun SketchCard(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    ink: Color = SketchInk,
    paper: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    // Always use ink for the outline so filled/primary cards keep a visible border.
    val stroke = ink
    val strokeWidth = if (selected) 2.dp else SketchStroke

    Box(modifier = modifier.padding(end = SketchShadowOffset, bottom = SketchShadowOffset)) {
        Box(
            Modifier
                .matchParentSize()
                .offset(x = SketchShadowOffset, y = SketchShadowOffset)
                .clip(shape)
                .background(ink)
                .zIndex(0f),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(1f)
                .clip(shape)
                .background(paper)
                .sketchBorder(shape, stroke, strokeWidth)
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
}

@Composable
fun SketchIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    shape: Shape = RectangleShape,
    ink: Color = SketchInk,
) {
    Box(modifier = modifier.size(width = size + SketchShadowOffset, height = size + SketchShadowOffset)) {
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(size)
                .clip(shape)
                .background(ink)
                .zIndex(0f),
        )
        Surface(
            onClick = onClick,
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(size)
                .zIndex(1f)
                .sketchBorder(shape, ink),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(icon, contentDescription = contentDescription, tint = MaterialTheme.colorScheme.onSurface)
            }
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
) {
    val paper = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }
    val content = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Surface(
        onClick = onClick,
        shape = RectangleShape,
        color = paper,
        contentColor = content,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        modifier = modifier.sketchBorder(RectangleShape, SketchInk, if (selected) 2.dp else SketchStroke),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
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
    Box(modifier = modifier.size(56.dp + SketchShadowOffset)) {
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(56.dp)
                .background(SketchInk, RectangleShape),
        )
        FloatingActionButton(
            onClick = onClick,
            shape = RectangleShape,
            containerColor = containerColor,
            contentColor = contentColor,
            elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(56.dp)
                .sketchBorder(RectangleShape, SketchInk, 2.dp),
            content = content,
        )
    }
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
    Box(modifier = modifier.padding(end = SketchShadowOffset, bottom = SketchShadowOffset)) {
        Box(
            Modifier
                .matchParentSize()
                .offset(SketchShadowOffset, SketchShadowOffset)
                .background(SketchInk, RectangleShape),
        )
        Surface(
            onClick = onClick,
            shape = RectangleShape,
            color = containerColor,
            contentColor = contentColor,
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
            modifier = Modifier.sketchBorder(RectangleShape, SketchInk, 2.dp),
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
}

@Composable
fun SketchFilledButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RectangleShape,
        colors = ButtonDefaults.buttonColors(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        modifier = modifier.sketchBorder(RectangleShape, SketchInk, SketchStroke),
        content = content,
    )
}

@Composable
fun SketchOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RectangleShape,
        border = null,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        modifier = modifier.sketchBorder(RectangleShape, SketchInk, SketchStroke),
        content = content,
    )
}

@Composable
fun SketchBottomBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        shape = RectangleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .sketchBorder(RectangleShape, SketchInk, SketchStroke),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
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
        shape = RectangleShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(SketchStroke, SketchInk),
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
        Box(
            modifier = modifier
                .widthIn(min = 280.dp, max = 560.dp)
                .padding(end = SketchShadowOffset, bottom = SketchShadowOffset),
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .offset(SketchShadowOffset, SketchShadowOffset)
                    .background(SketchInk, RectangleShape),
            )
            Surface(
                shape = RectangleShape,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                contentColor = AlertDialogDefaults.textContentColor,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .sketchBorder(RectangleShape, SketchInk, SketchStroke),
            ) {
                Column(Modifier.padding(top = 20.dp, bottom = 12.dp)) {
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
                            .padding(horizontal = 12.dp),
                    ) {
                        dismissButton?.invoke()
                        confirmButton()
                    }
                }
            }
        }
    }
}
