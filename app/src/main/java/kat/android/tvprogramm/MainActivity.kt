package kat.android.tvprogramm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import kat.android.tvprogramm.ui.TvViewModel
import kat.android.tvprogramm.ui.components.ApiConfigDialog
import kat.android.tvprogramm.ui.components.ShowDetailBottomSheet
import kat.android.tvprogramm.ui.screens.ChannelOverviewScreen
import kat.android.tvprogramm.ui.screens.DailyProgramScreen
import kat.android.tvprogramm.ui.screens.FavoritesScreen
import kat.android.tvprogramm.ui.screens.NowPlayingScreen
import kat.android.tvprogramm.ui.theme.TVProgrammTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TVProgrammTheme {
                TvProgramApp()
            }
        }
    }
}

enum class NavigationTab(val title: String, val icon: ImageVector) {
    NOW_PLAYING("Jetzt", Icons.Default.LiveTv),
    DAILY_PROGRAM("Tagesprogramm", Icons.Default.CalendarMonth),
    CHANNELS("Sender", Icons.Default.Tv),
    FAVORITES("Merkliste", Icons.Default.Bookmark)
}

@Composable
fun TvProgramApp(viewModel: TvViewModel = viewModel()) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showApiDialog by remember { mutableStateOf(false) }

    val uiState by viewModel.uiState.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val apiStatus by viewModel.apiStatus.collectAsState()

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationTab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) }
                    )
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        val modifier = Modifier.padding(innerPadding)

        when (NavigationTab.entries[selectedTab]) {
            NavigationTab.NOW_PLAYING -> {
                NowPlayingScreen(
                    viewModel = viewModel,
                    onShowClick = { viewModel.openShowDetails(it) },
                    onApiConfigClick = { showApiDialog = true },
                    modifier = modifier
                )
            }
            NavigationTab.DAILY_PROGRAM -> {
                DailyProgramScreen(
                    viewModel = viewModel,
                    onShowClick = { viewModel.openShowDetails(it) },
                    modifier = modifier
                )
            }
            NavigationTab.CHANNELS -> {
                ChannelOverviewScreen(
                    viewModel = viewModel,
                    onShowClick = { viewModel.openShowDetails(it) },
                    modifier = modifier
                )
            }
            NavigationTab.FAVORITES -> {
                FavoritesScreen(
                    viewModel = viewModel,
                    onShowClick = { viewModel.openShowDetails(it) },
                    modifier = modifier
                )
            }
        }

        // Show Details Modal BottomSheet
        uiState.selectedShowForDetail?.let { selectedShow ->
            ShowDetailBottomSheet(
                show = selectedShow,
                isFavorite = favoriteIds.contains(selectedShow.id),
                onDismiss = { viewModel.closeShowDetails() },
                onFavoriteToggle = { viewModel.toggleFavorite(selectedShow.id) }
            )
        }

        // API Configuration / Sync Dialog
        if (showApiDialog) {
            ApiConfigDialog(
                currentUrl = uiState.customApiUrl,
                apiStatus = apiStatus,
                onUrlChange = { viewModel.updateCustomApiUrl(it) },
                onRefreshClick = { viewModel.fetchProgramFromApi() },
                onDismiss = { showApiDialog = false }
            )
        }
    }
}
