package com.yondex.archivo.ui.crop

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.sharp.ArrowBack
import androidx.compose.material.icons.sharp.Check
import androidx.compose.material.icons.sharp.AutoFixHigh
import androidx.compose.material.icons.sharp.CropFree
import androidx.compose.material.icons.sharp.Edit
import androidx.compose.material.icons.sharp.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yondex.archivo.R
import com.yondex.archivo.domain.model.PageFilter
import com.yondex.archivo.ui.components.EmptyState
import com.yondex.archivo.ui.components.SketchChip
import com.yondex.archivo.ui.components.SketchInk
import com.yondex.archivo.ui.components.SketchStroke
import com.yondex.archivo.ui.components.sketchBorder
import com.yondex.archivo.ui.label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(
    onBack: () -> Unit,
    viewModel: CropViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.done.collect { onBack() } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.crop_and_filter)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Sharp.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (state.isWorking) {
                        CircularProgressIndicator(Modifier.padding(12.dp))
                    } else {
                        IconButton(onClick = viewModel::apply, enabled = state.image != null) {
                            Icon(Icons.Sharp.Check, contentDescription = stringResource(R.string.action_apply))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val image = state.image
                when {
                    state.loadFailed -> EmptyState(
                        icon = Icons.Sharp.CropFree,
                        title = stringResource(R.string.crop_load_error_title),
                        message = stringResource(R.string.crop_load_error_message),
                    )
                    image == null -> CircularProgressIndicator()
                    state.showPreview -> {
                        val preview = state.preview
                        if (preview == null) {
                            CircularProgressIndicator()
                        } else {
                            val bitmap = remember(preview) { preview.asImageBitmap() }
                            Image(
                                bitmap = bitmap,
                                contentDescription = stringResource(R.string.preview),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().padding(24.dp),
                            )
                        }
                    }
                    else -> {
                        val bitmap = remember(image) { image.asImageBitmap() }
                        CropEditor(
                            image = bitmap,
                            quad = state.quad,
                            accent = MaterialTheme.colorScheme.primary,
                            onCornerMoved = viewModel::moveCorner,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            Surface(
                shape = RectangleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 0.dp,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .sketchBorder(RectangleShape, SketchInk, SketchStroke),
            ) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 8.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        TextButton(onClick = viewModel::autoDetect) {
                            Icon(Icons.Sharp.AutoFixHigh, contentDescription = null)
                            Text(" ${stringResource(R.string.detect)}")
                        }
                        TextButton(onClick = viewModel::selectFull) {
                            Icon(Icons.Sharp.CropFree, contentDescription = null)
                            Text(" ${stringResource(R.string.full_page)}")
                        }
                        TextButton(onClick = { viewModel.setPreview(!state.showPreview) }) {
                            Icon(if (state.showPreview) Icons.Sharp.Edit else Icons.Sharp.Visibility, contentDescription = null)
                            Text(
                                " ${stringResource(if (state.showPreview) R.string.corners else R.string.preview)}",
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                    ) {
                        PageFilter.entries.forEach { filter ->
                            SketchChip(
                                selected = state.filter == filter,
                                onClick = { viewModel.setFilter(filter) },
                                label = { Text(filter.label()) },
                            )
                        }
                    }
                }
            }
        }
    }
}
