package kat.android.tvprogramm.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kat.android.tvprogramm.data.model.TvShow
import kat.android.tvprogramm.ui.TvViewModel
import kat.android.tvprogramm.ui.components.ChannelBadge
import kat.android.tvprogramm.ui.components.ShowCard

@Composable
fun ChannelOverviewScreen(
    viewModel: TvViewModel,
    onShowClick: (TvShow) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val channels by viewModel.channels.collectAsState()
    val showsByChannel by viewModel.showsByChannel.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()

    val selectedChannelId = uiState.selectedChannelId ?: "ard"
    val channelShows = showsByChannel[selectedChannelId]?.sortedBy { it.startTime } ?: emptyList()
    val selectedChannelObj = channels.find { it.id == selectedChannelId }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Title
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Tv,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Senderübersicht",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Horizontal Channel Selector
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(channels, key = { it.id }) { channel ->
                val isSelected = channel.id == selectedChannelId
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.selectChannel(channel.id) },
                    label = {
                        Text(channel.name)
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Selected Channel Header Card
        if (selectedChannelObj != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(12.dp).fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ChannelBadge(channelName = selectedChannelObj.name)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = selectedChannelObj.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${selectedChannelObj.category} • Senderplatz #${selectedChannelObj.channelNumber}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = "${channelShows.size} Sendungen",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        if (channelShows.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Keine Sendungen für diesen Sender verfügbar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(channelShows, key = { it.id }) { show ->
                    ShowCard(
                        show = show,
                        isFavorite = favoriteIds.contains(show.id),
                        onClick = { onShowClick(show) },
                        onFavoriteToggle = { viewModel.toggleFavorite(show.id) }
                    )
                }
            }
        }
    }
}
