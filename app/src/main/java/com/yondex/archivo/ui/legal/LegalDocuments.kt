package com.yondex.archivo.ui.legal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yondex.archivo.R
import com.yondex.archivo.ui.components.SketchShapeLarge
import com.yondex.archivo.ui.components.SketchSheetDragHandle

enum class LegalDocument {
    Privacy,
    Terms,
}

@Composable
fun LegalDocument.title(): String = stringResource(
    when (this) {
        LegalDocument.Privacy -> R.string.privacy_policy
        LegalDocument.Terms -> R.string.terms_of_use
    },
)

@Composable
fun LegalDocument.body(): String = stringResource(
    when (this) {
        LegalDocument.Privacy -> R.string.privacy_policy_body
        LegalDocument.Terms -> R.string.terms_of_use_body
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalDocumentSheet(
    document: LegalDocument,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = SketchShapeLarge,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        dragHandle = { SketchSheetDragHandle() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(document.title(), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    document.body(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
