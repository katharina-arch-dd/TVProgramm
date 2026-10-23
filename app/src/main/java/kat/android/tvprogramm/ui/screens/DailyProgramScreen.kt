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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kat.android.tvprogramm.data.model.TvShow
import kat.android.tvprogramm.ui.TvViewModel
import kat.android.tvprogramm.ui.components.CategoryChipRow
import kat.android.tvprogramm.ui.components.ShowCard
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun DailyProgramScreen(
    viewModel: TvViewModel,
    onShowClick: (TvShow) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val allShows by viewModel.filteredShows.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()

    var selectedTimeSlot by remember { mutableStateOf<String?>(null) } // null = Alle, "20:15", "22:00"

    val today = LocalDate.now()
    val daysList = listOf(
        today.minusDays(1) to "Gestern",
        today to "Heute",
        today.plusDays(1) to "Morgen",
        today.plusDays(2) to "Übermorgen"
    )

    val displayedShows = remember(allShows, selectedTimeSlot) {
        if (selectedTimeSlot == null) {
            allShows
        } else if (selectedTimeSlot == "20:15") {
            allShows.filter { show ->
                val primeStart = LocalTime.of(20, 0)
                val primeEnd = LocalTime.of(21, 30)
                val showTime = show.startTime.toLocalTime()
                showTime >= primeStart && showTime <= primeEnd
            }
        } else if (selectedTimeSlot == "22:00") {
            allShows.filter { show ->
                val lateStart = LocalTime.of(21, 45)
                val lateEnd = LocalTime.of(23, 59)
                val showTime = show.startTime.toLocalTime()
                showTime >= lateStart && showTime <= lateEnd
            }
        } else {
            allShows
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Title
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.CalendarMonth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Tagesprogramm",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Date selection tabs
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(daysList) { (date, label) ->
                val isSelected = uiState.selectedDate == date
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.selectDate(date) },
                    label = {
                        Text("$label (${date.format(DateTimeFormatter.ofPattern("dd.MM."))})")
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Time slot shortcuts
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            AssistChip(
                onClick = { selectedTimeSlot = null },
                label = { Text("Ganzer Tag") },
                colors = if (selectedTimeSlot == null) AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else AssistChipDefaults.assistChipColors()
            )

            AssistChip(
                onClick = { selectedTimeSlot = "20:15" },
                label = { Text("20:15 Prime-Time") },
                leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp)) },
                colors = if (selectedTimeSlot == "20:15") AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else AssistChipDefaults.assistChipColors()
            )

            AssistChip(
                onClick = { selectedTimeSlot = "22:00" },
                label = { Text("22:00 Spät") },
                colors = if (selectedTimeSlot == "22:00") AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else AssistChipDefaults.assistChipColors()
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Category Filter
        CategoryChipRow(
            selectedCategory = uiState.selectedCategory,
            onCategorySelected = { viewModel.selectCategory(it) }
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (displayedShows.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Keine Sendungen für den ausgewählten Filter vorhanden.",
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
                items(displayedShows, key = { it.id }) { show ->
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
