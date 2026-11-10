package com.yondex.archivo.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.yondex.archivo.ui.crop.CropScreen
import com.yondex.archivo.ui.document.DocumentScreen
import com.yondex.archivo.ui.home.HomeScreen
import com.yondex.archivo.ui.page.PageViewerScreen
import com.yondex.archivo.ui.reader.PdfReaderScreen
import com.yondex.archivo.ui.review.ReviewScreen
import com.yondex.archivo.ui.scanner.ScannerScreen
import com.yondex.archivo.ui.search.SearchScreen
import com.yondex.archivo.ui.settings.SettingsScreen
import com.yondex.archivo.ui.tags.TagsScreen

private const val TRANSITION_MS = 220

@Composable
fun ArchivoNavHost() {
    val navController = rememberNavController()
    val back: () -> Unit = { navController.popBackStack() }

    NavHost(
        navController = navController,
        startDestination = HomeRoute(),
        enterTransition = { fadeIn(tween(TRANSITION_MS)) },
        exitTransition = { fadeOut(tween(TRANSITION_MS)) },
        popEnterTransition = { fadeIn(tween(TRANSITION_MS)) },
        popExitTransition = { fadeOut(tween(TRANSITION_MS)) },
    ) {
        composable<HomeRoute> { entry ->
            val route = entry.toRoute<HomeRoute>()
            HomeScreen(
                onBack = if (route.folderId != NO_ID) dropUnlessResumed(block = back) else null,
                onOpenFolder = { navController.navigate(HomeRoute(it)) },
                onOpenDocument = { navController.navigate(DocumentRoute(it)) },
                onOpenPdf = { navController.navigate(PdfReaderRoute(it)) },
                onScan = { folderId -> navController.navigate(ScannerRoute(folderId = folderId ?: NO_ID)) },
                onSearch = { navController.navigate(SearchRoute) },
                onManageTags = { navController.navigate(TagsRoute) },
                onSettings = { navController.navigate(SettingsRoute) },
            )
        }
        composable<ScannerRoute> {
            ScannerScreen(
                onClose = dropUnlessResumed(block = back),
                onReview = dropUnlessResumed { navController.navigate(ReviewRoute) },
            )
        }
        composable<ReviewRoute> {
            ReviewScreen(
                onBack = dropUnlessResumed(block = back),
                onCrop = { navController.navigate(CropRoute(sessionPageId = it)) },
                onSaved = { documentId, isNew ->
                    if (isNew) {
                        navController.navigate(DocumentRoute(documentId)) {
                            popUpTo<ScannerRoute> { inclusive = true }
                        }
                    } else {
                        navController.popBackStack<ScannerRoute>(inclusive = true)
                    }
                },
            )
        }
        composable<CropRoute> {
            CropScreen(onBack = dropUnlessResumed(block = back))
        }
        composable<DocumentRoute> { entry ->
            val documentId = entry.toRoute<DocumentRoute>().documentId
            DocumentScreen(
                onBack = dropUnlessResumed(block = back),
                onOpenPage = { navController.navigate(PageViewerRoute(documentId, it)) },
                onCropPage = { navController.navigate(CropRoute(pageId = it)) },
                onAddPages = { navController.navigate(ScannerRoute(documentId = it)) },
            )
        }
        composable<PageViewerRoute> {
            PageViewerScreen(
                onBack = dropUnlessResumed(block = back),
                onCrop = { navController.navigate(CropRoute(pageId = it)) },
            )
        }
        composable<PdfReaderRoute> {
            PdfReaderScreen(onBack = dropUnlessResumed(block = back))
        }
        composable<SearchRoute> {
            SearchScreen(
                onBack = dropUnlessResumed(block = back),
                onOpenDocument = { navController.navigate(DocumentRoute(it)) },
            )
        }
        composable<TagsRoute> { TagsScreen(onBack = dropUnlessResumed(block = back)) }
        composable<SettingsRoute> { SettingsScreen(onBack = dropUnlessResumed(block = back)) }
    }
}
