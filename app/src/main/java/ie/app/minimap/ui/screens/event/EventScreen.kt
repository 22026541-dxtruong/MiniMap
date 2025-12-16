package ie.app.minimap.ui.screens.event

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ie.app.minimap.R
import ie.app.minimap.ui.screens.home.VenueCard

@Preview
@Composable
fun EventScreen(
    venueId: Long = 0,
    viewModel: EventViewModel = hiltViewModel(),
    onBackClicked: () -> Unit = { },
    onMapClicked: (Long) -> Unit = { },
    onClickEvent: (Long) -> Unit = { },
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(venueId) {
        viewModel.loadEvents(venueId)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            EventTopBar(
                title = "Events",
                onBackClicked = onBackClicked,
                onSearchClicked = { /*TODO*/ }
            )
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            // --- Nội dung chính ---
            when {
                uiState.events.isEmpty() && !uiState.isLoading && uiState.error == null -> {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Text("Chưa có địa điểm nào")
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(PaddingValues(
                                top = innerPadding.calculateTopPadding() + 16.dp,
                                bottom = innerPadding.calculateBottomPadding()
                            )),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(uiState.events, key = { it.event.id }) { event ->
                            EventCard(
                                name = event.event.name,
                                building = event.buildingName,
                                floor = event.floorName,
                                onClick = { onClickEvent(event.event.id) }
                            )
                        }
                    }
                }
            }

//             --- LOADING overlay ---
            if (uiState.isLoading) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),  // optional dim
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            // --- ERROR overlay ---
            uiState.error?.let { error ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)), // optional
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Error: $error", color = Color.Red)
//                        Spacer(Modifier.height(8.dp))
//                        Button(onClick = { onRetry() }) {
//                            Text("Thử lại")
//                        }
                    }
                }
            }
            EventBottomBar(
                onMapClicked = { onMapClicked(venueId) },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

    }

}

//@Preview
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventTopBar(
    title: String = "Events",
    onSearchClicked: () -> Unit = {},
    onBackClicked: () -> Unit = {}
) {
    CenterAlignedTopAppBar(
        title = { Text(text = title) },
        actions = {
            IconButton(onClick = onSearchClicked) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search"
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBackClicked) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "QR Scan"
                )
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

//@Preview
@Composable
fun EventBottomBar(
    onMapClicked: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Button(
        onClick = { onMapClicked() },
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(8.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.baseline_view_in_ar_24),
                contentDescription = "AR Navigation",
                tint = Color.White
            )
            Text(text = "Start AR Navigation")
        }
    }
}

@Composable
fun EventCard(
    name: String? = "Annual Gala Dinner",
    building: String? = "Some address",
    floor: String? = "A description of the venue",
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    painter = painterResource(R.drawable.outline_calendar_month_24),
                    contentDescription = "Date",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.Center)
                )
            }

            // Texts
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(text = name.orEmpty(), style = MaterialTheme.typography.titleMedium)
                Text(text = building.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                Text(text = floor.orEmpty(), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
