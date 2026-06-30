package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import java.io.ByteArrayInputStream
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.ComicItem
import com.example.data.ComicPreferences
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

enum class AppTab {
    BROWSER, BOOKMARKS, HISTORY, SETTINGS
}

class MainActivity : ComponentActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                MainAppScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MainAppScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val comicPrefs = remember { ComicPreferences(context) }

    // Persistent active tab state
    var currentTab by remember { mutableStateOf(AppTab.BROWSER) }

    // WebView persistence across tab switching
    val webView = remember {
        WebView(context).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.supportZoom()
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            
            // Safe cookies
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        }
    }

    // App state tracked from WebView
    var currentUrl by remember { mutableStateOf("https://komiktap.info") }
    var currentTitle by remember { mutableStateOf("Komiktap - Baca Komik Online") }
    var webProgress by remember { mutableStateOf(0) }
    var isWebLoading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var isDesktopMode by remember { mutableStateOf(false) }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isAdBlockEnabled by remember { mutableStateOf(true) }

    // Bookmarked and History lists updated dynamically
    var bookmarksList by remember { mutableStateOf(comicPrefs.getBookmarks()) }
    var historyList by remember { mutableStateOf(comicPrefs.getHistory()) }
    var isCurrentlyBookmarked by remember { mutableStateOf(comicPrefs.isBookmarked(currentUrl)) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var showVpnNoticeDialog by remember { mutableStateOf(!comicPrefs.hasShownVpnNotice()) }

    // Handle Android system back button to navigate WebView back
    BackHandler(enabled = currentTab == AppTab.BROWSER && canGoBack) {
        webView.goBack()
    }

    // Setup Custom WebViewClient
    LaunchedEffect(webView, isAdBlockEnabled) {
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url?.toString() ?: return null
                if (isAdBlockEnabled && AdBlocker.isAd(url)) {
                    return AdBlocker.createEmptyResource()
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                isWebLoading = true
                url?.let {
                    currentUrl = it
                    isCurrentlyBookmarked = comicPrefs.isBookmarked(it)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                isWebLoading = false
                webProgress = 0
                url?.let { finishedUrl ->
                    currentUrl = finishedUrl
                    isCurrentlyBookmarked = comicPrefs.isBookmarked(finishedUrl)
                    val title = view?.title ?: "Komiktap"
                    currentTitle = if (title.contains("komiktap", ignoreCase = true)) title else "$title - Komiktap"
                    
                    // Automatically add loaded chapter/comic to History
                    comicPrefs.addHistory(title, finishedUrl)
                    historyList = comicPrefs.getHistory()
                }
                canGoBack = webView.canGoBack()
                canGoForward = webView.canGoForward()
            }

            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url == null) return false
                // Keep load inside WebView if it's Komiktap, else launch external activity for safety
                return if (url.contains("komiktap.info", ignoreCase = true) || url.startsWith("http")) {
                    false
                } else {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                        true
                    } catch (e: Exception) {
                        false
                    }
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                webProgress = newProgress
                if (newProgress == 100) {
                    isWebLoading = false
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                title?.let {
                    currentTitle = if (it.contains("komiktap", ignoreCase = true)) it else "$it - Komiktap"
                }
            }
        }

        // Initial load
        webView.loadUrl("https://komiktap.info")
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (currentTab != AppTab.BROWSER) {
                Column {
                    // Bottom Tab Navigation Bar matching Professional Polish layout specs
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                            ),
                        tonalElevation = 8.dp
                    ) {
                        NavigationBarItem(
                            selected = currentTab == AppTab.BROWSER,
                            onClick = { currentTab = AppTab.BROWSER },
                            icon = {
                                Icon(
                                    imageVector = if (currentTab == AppTab.BROWSER) Icons.Default.Explore else Icons.Outlined.Explore,
                                    contentDescription = "Explore"
                                )
                            },
                            label = { Text("Browser", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.testTag("tab_browser")
                        )
                        
                        NavigationBarItem(
                            selected = currentTab == AppTab.BOOKMARKS,
                            onClick = {
                                bookmarksList = comicPrefs.getBookmarks()
                                currentTab = AppTab.BOOKMARKS
                            },
                            icon = {
                                Icon(
                                    imageVector = if (currentTab == AppTab.BOOKMARKS) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                                    contentDescription = "Bookmarks"
                                )
                            },
                            label = { Text("Favorit", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.testTag("tab_favorit")
                        )

                        NavigationBarItem(
                            selected = currentTab == AppTab.HISTORY,
                            onClick = {
                                historyList = comicPrefs.getHistory()
                                currentTab = AppTab.HISTORY
                            },
                            icon = {
                                Icon(
                                    imageVector = if (currentTab == AppTab.HISTORY) Icons.Default.History else Icons.Outlined.History,
                                    contentDescription = "History"
                                )
                            },
                            label = { Text("Riwayat", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.testTag("tab_history")
                        )

                        NavigationBarItem(
                            selected = currentTab == AppTab.SETTINGS,
                            onClick = { currentTab = AppTab.SETTINGS },
                            icon = {
                                Icon(
                                    imageVector = if (currentTab == AppTab.SETTINGS) Icons.Default.Settings else Icons.Outlined.Settings,
                                    contentDescription = "Settings"
                                )
                            },
                            label = { Text("Pengaturan", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.testTag("tab_settings")
                        )
                    }

                    // Adaptive system bottom bar padding
                    Spacer(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (currentTab) {
                AppTab.BROWSER -> {
                    BrowserScreen(
                        webView = webView,
                        currentUrl = currentUrl,
                        currentTitle = currentTitle,
                        canGoBack = canGoBack,
                        canGoForward = canGoForward,
                        isCurrentlyBookmarked = isCurrentlyBookmarked,
                        onBookmarkToggle = {
                            if (isCurrentlyBookmarked) {
                                comicPrefs.removeBookmark(currentUrl)
                                scope.launch {
                                    snackbarHostState.showSnackbar("Dihapus dari Favorit")
                                }
                            } else {
                                comicPrefs.addBookmark(currentTitle, currentUrl)
                                scope.launch {
                                    snackbarHostState.showSnackbar("Disimpan ke Favorit!")
                                }
                            }
                            isCurrentlyBookmarked = !isCurrentlyBookmarked
                            bookmarksList = comicPrefs.getBookmarks()
                        },
                        onHomeTrigger = {
                            webView.loadUrl("https://komiktap.info")
                        },
                        onMenuClick = {
                            showBottomSheet = true
                        },
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        isSearchExpanded = isSearchExpanded,
                        onSearchExpandedChange = { isSearchExpanded = it },
                        isWebLoading = isWebLoading,
                        webProgress = webProgress
                    )
                }
                AppTab.BOOKMARKS -> {
                    BookmarksScreen(
                        bookmarks = bookmarksList,
                        onBookmarkClick = { targetUrl ->
                            webView.loadUrl(targetUrl)
                            currentTab = AppTab.BROWSER
                        },
                        onDeleteBookmark = { urlToDelete ->
                            comicPrefs.removeBookmark(urlToDelete)
                            bookmarksList = comicPrefs.getBookmarks()
                            isCurrentlyBookmarked = comicPrefs.isBookmarked(currentUrl)
                        }
                    )
                }
                AppTab.HISTORY -> {
                    HistoryScreen(
                        history = historyList,
                        onHistoryClick = { targetUrl ->
                            webView.loadUrl(targetUrl)
                            currentTab = AppTab.BROWSER
                        },
                        onClearAll = {
                            comicPrefs.clearHistory()
                            historyList = emptyList()
                        }
                    )
                }
                AppTab.SETTINGS -> {
                    SettingsScreen(
                        context = context,
                        isDesktopMode = isDesktopMode,
                        onDesktopModeToggle = {
                            isDesktopMode = !isDesktopMode
                            if (isDesktopMode) {
                                webView.settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
                            } else {
                                webView.settings.userAgentString = null
                            }
                            webView.reload()
                        },
                        isAdBlockEnabled = isAdBlockEnabled,
                        onAdBlockToggle = {
                            isAdBlockEnabled = !isAdBlockEnabled
                        },
                        onClearCache = {
                            webView.clearCache(true)
                            webView.clearHistory()
                            Toast.makeText(context, "Cache & Data browser berhasil dibersihkan", Toast.LENGTH_SHORT).show()
                        },
                        onResetHome = {
                            webView.loadUrl("https://komiktap.info")
                            currentTab = AppTab.BROWSER
                        }
                    )
                }
            }
        }
    }

    // Modal Bottom Sheet Navigation & Controls
    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Menu Navigasi Bacakomik",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp, start = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Refresh Action
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable {
                                webView.reload()
                                showBottomSheet = false
                            }
                            .padding(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Muat Ulang",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Muat Ulang", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }

                    // Desktop Toggle Action
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable {
                                isDesktopMode = !isDesktopMode
                                if (isDesktopMode) {
                                    webView.settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
                                } else {
                                    webView.settings.userAgentString = null
                                }
                                webView.reload()
                                showBottomSheet = false
                            }
                            .padding(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(if (isDesktopMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isDesktopMode) Icons.Default.Laptop else Icons.Default.PhoneAndroid,
                                contentDescription = "Mode Desktop",
                                tint = if (isDesktopMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(if (isDesktopMode) "Mode HP" else "Mode Desktop", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }

                    // Share Action
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable {
                                try {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, currentTitle)
                                        putExtra(Intent.EXTRA_TEXT, currentUrl)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Bagikan Komik"))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Gagal membagikan", Toast.LENGTH_SHORT).show()
                                }
                                showBottomSheet = false
                            }
                            .padding(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Bagikan",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Bagikan", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                ListItem(
                    headlineContent = { Text("Komik Favorit Saya", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Kumpulan komik yang kamu simpan") },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = "Favorit",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier.clickable {
                        bookmarksList = comicPrefs.getBookmarks()
                        currentTab = AppTab.BOOKMARKS
                        showBottomSheet = false
                    }
                )

                ListItem(
                    headlineContent = { Text("Riwayat Membaca", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Daftar halaman yang baru kamu kunjungi") },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Riwayat",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier.clickable {
                        historyList = comicPrefs.getHistory()
                        currentTab = AppTab.HISTORY
                        showBottomSheet = false
                    }
                )

                ListItem(
                    headlineContent = { Text("Pengaturan Aplikasi", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Konfigurasi AdBlock, Cache, dan data browser") },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Pengaturan",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier.clickable {
                        currentTab = AppTab.SETTINGS
                        showBottomSheet = false
                    }
                )
            }
        }
    }

    // VPN Warning Dialog
    if (showVpnNoticeDialog) {
        AlertDialog(
            onDismissRequest = { 
                comicPrefs.setVpnNoticeShown()
                showVpnNoticeDialog = false 
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "VPN Required",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Koneksi VPN Diperlukan",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Text(
                    text = "Aplikasi ini memuat situs komiktap.info untuk membaca komik. Agar halaman dapat terbuka dengan lancar, pastikan Anda menggunakan aplikasi VPN yang aktif di ponsel Anda.\n\nTanpa VPN, halaman web mungkin tidak akan bisa terbuka (blank atau error).",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        comicPrefs.setVpnNoticeShown()
                        showVpnNoticeDialog = false
                    },
                    modifier = Modifier.testTag("vpn_confirm_button")
                ) {
                    Text("Saya Mengerti")
                }
            }
        )
    }
}

@Composable
fun BrowserScreen(
    webView: WebView,
    currentUrl: String,
    currentTitle: String,
    canGoBack: Boolean,
    canGoForward: Boolean,
    isCurrentlyBookmarked: Boolean,
    onBookmarkToggle: () -> Unit,
    onHomeTrigger: () -> Unit,
    onMenuClick: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isSearchExpanded: Boolean,
    onSearchExpandedChange: (Boolean) -> Unit,
    isWebLoading: Boolean,
    webProgress: Int
) {
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Main Web View display
            Box(modifier = Modifier.weight(1f)) {
                AndroidView(
                    factory = { webView },
                    modifier = Modifier.fillMaxSize().testTag("webview_komiktap")
                )

                // Thin progress indicator overlaid at the top of the WebView so it doesn't push the layout down
                if (isWebLoading) {
                    LinearProgressIndicator(
                        progress = { webProgress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent
                    )
                }
            }

            // Specialized Single Navigation Bar for Comic Reading Controls
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                IconButton(
                    onClick = { if (canGoBack) webView.goBack() },
                    enabled = canGoBack,
                    modifier = Modifier.testTag("web_back_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Kembali",
                        tint = if (canGoBack) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }

                IconButton(
                    onClick = { if (canGoForward) webView.goForward() },
                    enabled = canGoForward,
                    modifier = Modifier.testTag("web_forward_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Maju",
                        tint = if (canGoForward) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }

                IconButton(
                    onClick = { onSearchExpandedChange(!isSearchExpanded) },
                    modifier = Modifier.testTag("web_search_button")
                ) {
                    Icon(
                        imageVector = if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = "Cari Komik",
                        tint = if (isSearchExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = onBookmarkToggle,
                    modifier = Modifier.testTag("web_bookmark_button")
                ) {
                    Icon(
                        imageVector = if (isCurrentlyBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = "Simpan Favorit",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = onHomeTrigger,
                    modifier = Modifier.testTag("web_home_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Beranda Utama",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier.testTag("web_menu_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu Utama",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Search Bar Overlay at the top of the screen (Floating overlay style, no top bar push)
        AnimatedVisibility(
            visible = isSearchExpanded,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            val keyboardController = LocalSoftwareKeyboardController.current
            Card(
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Cari manga / komik...", fontSize = 14.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("search_input_field"),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (searchQuery.isNotBlank()) {
                                    val searchUrl = "https://komiktap.info/?s=${Uri.encode(searchQuery)}"
                                    webView.loadUrl(searchUrl)
                                    onSearchExpandedChange(false)
                                    keyboardController?.hide()
                                }
                            }
                        ) {
                            Icon(Icons.Default.ArrowForward, contentDescription = "Search Go")
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        if (searchQuery.isNotBlank()) {
                            val searchUrl = "https://komiktap.info/?s=${Uri.encode(searchQuery)}"
                            webView.loadUrl(searchUrl)
                            onSearchExpandedChange(false)
                            keyboardController?.hide()
                        }
                    })
                )
            }
        }
    }
}

@Composable
fun BookmarksScreen(
    bookmarks: List<ComicItem>,
    onBookmarkClick: (String) -> Unit,
    onDeleteBookmark: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Komik Favorit Saya",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Badge(containerColor = MaterialTheme.colorScheme.primary) {
                Text(text = "${bookmarks.size}", color = Color.White, modifier = Modifier.padding(4.dp))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (bookmarks.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.BookmarkBorder,
                        contentDescription = "Favorit Kosong",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Belum Ada Favorit",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Klik ikon bookmark saat membaca komik di tab Browser untuk menyimpan di sini.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(bookmarks) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onBookmarkClick(item.url) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Book,
                                    contentDescription = "Comic Icon",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Buka kembali chapter komik ini",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            IconButton(
                                onClick = { onDeleteBookmark(item.url) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Hapus Favorit",
                                    tint = Color(0xFFEF5350)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(
    history: List<ComicItem>,
    onHistoryClick: (String) -> Unit,
    onClearAll: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Riwayat Membaca",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (history.isNotEmpty()) {
                TextButton(
                    onClick = onClearAll,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF5350))
                ) {
                    Text("Bersihkan Semua", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (history.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Riwayat Kosong",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Riwayat Kosong",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Halaman komik yang kamu kunjungi akan otomatis dicatat di sini.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        } else {
            val dateFormat = remember { SimpleDateFormat("HH:mm - dd MMM", Locale.getDefault()) }
            
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(history) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onHistoryClick(item.url) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = "Waktu",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp)
                            )
                            
                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = dateFormat.format(Date(item.timestamp)),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Buka",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    context: Context,
    isDesktopMode: Boolean,
    onDesktopModeToggle: () -> Unit,
    isAdBlockEnabled: Boolean,
    onAdBlockToggle: () -> Unit,
    onClearCache: () -> Unit,
    onResetHome: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Pengaturan Aplikasi",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Card containing Browser Settings
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Konfigurasi Browser",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Ad Blocker Toggle Row (AdBlock)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAdBlockToggle() }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Blokir Iklan & Redirect (AdBlock)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Mencegah iklan pop-up dan pengalihan otomatis saat membaca komik",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    Switch(
                        checked = isAdBlockEnabled,
                        onCheckedChange = { onAdBlockToggle() }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // Desktop Mode Toggle Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDesktopModeToggle() }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tampilan Desktop Mode",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Memaksa web komiktap dimuat versi komputer",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    Switch(
                        checked = isDesktopMode,
                        onCheckedChange = { onDesktopModeToggle() }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // Reset Home URL Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onResetHome() }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset Home",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Kembali ke Beranda Utama",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Muat ulang browser langsung ke halaman awal komiktap.info",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // Clear Cache Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onClearCache() }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Clear Cache",
                        tint = Color(0xFFEF5350),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Bersihkan Data Cache",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Menghapus sampah web view untuk menghemat memori",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Card containing About & App Info
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Tentang Aplikasi",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Aplikasi ini didesain khusus sebagai browser premium komiktap.info yang menyembunyikan alamat link agar membaca komik lebih lega dan terfokus pada konten.",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Versi Aplikasi", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text(text = "1.0.0 Stable (installDebug)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }
        }
    }
}

object AdBlocker {
    private val AD_KEYWORDS = setOf(
        "googleads", "googlesyndication", "doubleclick", "adservice", "adsystem", 
        "popads", "popunder", "exoclick", "onclickads", "adkeeper", "admaven", 
        "adsterra", "mgid", "propellerads", "monetag", "trafficstars", "juicyads", 
        "plugrush", "histats", "stats.wp.com", "analytics", "disqus.com/next/embed", 
        "mobicow", "adtrue", "dynamic-yield", "outbrain", "taboola", "criteo",
        "adnxs", "pubmatic", "rubiconproject", "openx", "casalemedia", "smartadserver",
        "adcolony", "applovin", "unity3d", "vungle", "inmobi", "fyber", "chartboost"
    )

    private val AD_URL_PATTERNS = setOf(
        "/ads/", "/ad/", "/ad-", "/ads-", "/adbox", "/adbar", "/adbanner", "banner_ad", 
        "popup.js", "pop.js", "nativeads", "adbygoogle", "adsbygoogle", "ad_loader"
    )

    fun isAd(url: String): Boolean {
        val lowerUrl = url.lowercase(Locale.getDefault())
        // Never block the main komiktap core pages
        if (lowerUrl.contains("komiktap.info") && !lowerUrl.contains("/ad/") && !lowerUrl.contains("/ads/")) {
            return false
        }
        
        for (keyword in AD_KEYWORDS) {
            if (lowerUrl.contains(keyword)) {
                return true
            }
        }
        
        for (pattern in AD_URL_PATTERNS) {
            if (lowerUrl.contains(pattern)) {
                return true
            }
        }
        
        return false
    }

    fun createEmptyResource(): WebResourceResponse {
        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream("".toByteArray()))
    }
}
