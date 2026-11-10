package com.yondex.archivo.ui.navigation

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
import com.yondex.archivo.ui.review.ReviewScreen
import com.yondex.archivo.ui.scanner.ScannerScreen
import com.yondex.archivo.ui.search.SearchScreen
import com.yondex.archivo.ui.settings.SettingsScreen
import com.yondex.archivo.ui.tags.TagsScreen

@Composable
fun ArchivoNavHost() {
    val navController = rememberNavController()
    val back: () -> Unit = { navController.popBackStack() }

    NavHost(navController = navController, startDestination = HomeRoute()) {
        composable<HomeRoute> { entry ->
            val route = entry.toRoute<HomeRoute>()
            HomeScreen(
                onBack = if (route.folderId != NO_ID) dropUnlessResumed(block = back) else null,
                onOpenFolder = { navController.navigate(HomeRoute(it)) },
                onOpenDocument = { navController.navigate(DocumentRoute(it)) },
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
