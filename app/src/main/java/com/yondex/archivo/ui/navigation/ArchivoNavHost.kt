package com.yondex.archivo.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.yondex.archivo.ui.crop.CropScreen
import com.yondex.archivo.ui.document.DocumentScreen
import com.yondex.archivo.ui.home.HomeScreen
import com.yondex.archivo.ui.merge.MergeScreen
import com.yondex.archivo.ui.page.PageViewerScreen
import com.yondex.archivo.ui.qr.QrScannerScreen
import com.yondex.archivo.ui.reader.PdfReaderScreen
import com.yondex.archivo.ui.review.ReviewScreen
import com.yondex.archivo.ui.scanner.ScannerScreen
import com.yondex.archivo.ui.search.SearchScreen
import com.yondex.archivo.ui.settings.SettingsScreen
import com.yondex.archivo.ui.split.SplitResult
import com.yondex.archivo.ui.split.SplitScreen
import com.yondex.archivo.ui.tags.TagsScreen

private const val TRANSITION_MS = 220

@Composable
fun ArchivoNavHost() {
    val navController = rememberNavController()
    val back: () -> Unit = { navController.popBackStack() }
    val entry by navController.currentBackStackEntryAsState()
    val destination = entry?.destination

    val selectedTab = when {
        destination?.hasRoute<SettingsRoute>() == true -> MainTab.Settings
        destination?.hasRoute<QrScannerRoute>() == true -> MainTab.Qr
        destination?.hasRoute<ScannerRoute>() == true -> MainTab.Scan
        else -> MainTab.Home
    }
    val showBottomBar = destination?.hasRoute<HomeRoute>() == true ||
        destination?.hasRoute<SettingsRoute>() == true ||
        destination?.hasRoute<QrScannerRoute>() == true

    fun currentHomeFolderId(): Long {
        val homeEntry = navController.currentBackStack.value
            .asReversed()
            .firstOrNull { it.destination.hasRoute<HomeRoute>() }
            ?: return NO_ID
        val folderId = homeEntry.toRoute<HomeRoute>().folderId
        return if (folderId == NO_ID) NO_ID else folderId
    }

    fun openTab(tab: MainTab) {
        when (tab) {
            MainTab.Home -> {
                // Clear QR/Settings/etc. and land on root Home every time.
                navController.navigate(HomeRoute()) {
                    popUpTo(navController.graph.id) {
                        inclusive = true
                    }
                    launchSingleTop = true
                }
            }
            MainTab.Settings -> {
                if (selectedTab == MainTab.Settings) return
                navController.navigate(SettingsRoute) {
                    launchSingleTop = true
                }
            }
            MainTab.Qr -> {
                if (selectedTab == MainTab.Qr) return
                navController.navigate(QrScannerRoute) {
                    launchSingleTop = true
                }
            }
            MainTab.Scan -> navController.navigate(ScannerRoute(folderId = currentHomeFolderId()))
        }
    }

    val bottomNavInset = bottomNavInset(showBottomBar)
    val layoutDirection = LocalLayoutDirection.current

    CompositionLocalProvider(LocalBottomNavInset provides bottomNavInset) {
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomBar) {
                ArchivoBottomBar(
                    selected = selectedTab,
                    onSelect = { tab -> openTab(tab) },
                )
            }
        },
    ) { padding ->
        // Let content draw under the rounded bottom nav so corners aren't a hard rect cut.
        val contentPadding = PaddingValues(
            start = padding.calculateLeftPadding(layoutDirection),
            top = padding.calculateTopPadding(),
            end = padding.calculateRightPadding(layoutDirection),
            bottom = 0.dp,
        )
        NavHost(
            navController = navController,
            startDestination = HomeRoute(),
            modifier = Modifier.padding(contentPadding),
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
                    onSearch = { navController.navigate(SearchRoute) },
                    onManageTags = { navController.navigate(TagsRoute) },
                    onScan = {
                        navController.navigate(ScannerRoute(folderId = currentHomeFolderId()))
                    },
                    onMerge = { documentIds, libraryFileIds ->
                        navController.navigate(
                            MergeRoute(documentIds = documentIds, libraryFileIds = libraryFileIds),
                        )
                    },
                    onSplit = { libraryFileId ->
                        navController.navigate(SplitRoute(libraryFileId = libraryFileId))
                    },
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
                    onSplit = { navController.navigate(SplitRoute(documentId = it)) },
                )
            }
            composable<PageViewerRoute> {
                PageViewerScreen(
                    onBack = dropUnlessResumed(block = back),
                    onCrop = { navController.navigate(CropRoute(pageId = it)) },
                )
            }
            composable<PdfReaderRoute> {
                PdfReaderScreen(
                    onBack = dropUnlessResumed(block = back),
                    onSplit = { navController.navigate(SplitRoute(libraryFileId = it)) },
                )
            }
            composable<MergeRoute> {
                MergeScreen(
                    onBack = dropUnlessResumed(block = back),
                    onDone = { documentId ->
                        navController.navigate(DocumentRoute(documentId)) {
                            popUpTo<MergeRoute> { inclusive = true }
                        }
                    },
                )
            }
            composable<SplitRoute> {
                SplitScreen(
                    onBack = dropUnlessResumed(block = back),
                    onDone = { result ->
                        when (result) {
                            is SplitResult.Document -> {
                                navController.navigate(DocumentRoute(result.id)) {
                                    popUpTo<SplitRoute> { inclusive = true }
                                }
                            }
                            is SplitResult.LibraryPdf -> {
                                navController.navigate(PdfReaderRoute(result.id)) {
                                    popUpTo<SplitRoute> { inclusive = true }
                                }
                            }
                        }
                    },
                )
            }
            composable<SearchRoute> {
                SearchScreen(
                    onBack = dropUnlessResumed(block = back),
                    onOpenDocument = { navController.navigate(DocumentRoute(it)) },
                    onOpenPdf = { navController.navigate(PdfReaderRoute(it)) },
                )
            }
            composable<TagsRoute> {
                TagsScreen(onBack = dropUnlessResumed(block = back))
            }
            composable<SettingsRoute> {
                SettingsScreen()
            }
            composable<QrScannerRoute> {
                QrScannerScreen(onClose = dropUnlessResumed { openTab(MainTab.Home) })
            }
        }
    }
    }
}
