package ie.app.minimap.ui.screens.home

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ie.app.minimap.R
import ie.app.minimap.data.local.entity.Venue
import kotlinx.coroutines.launch

//@Preview(showSystemUi = true)
@Composable
fun HomeScreen(
    onClickVenue: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    var isOpenDialog by remember { mutableStateOf(false) }
    var editingVenue by remember { mutableStateOf<Venue?>(null) }
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    if (isOpenDialog) {
        VenueDialog(
            idVenue = editingVenue?.id ?: 0,
            nameVenue = editingVenue?.name ?: "",
            addressVenue = editingVenue?.address ?: "",
            descriptionVenue = editingVenue?.description ?: "",
            onDismiss = { isOpenDialog = false },
            onSave = { name, address, description ->
                coroutineScope.launch {
                    val newVenue = viewModel.createVenue(name, address, description)
                    onClickVenue(newVenue?.id ?: 0)
                }
            },
            onUpdateVenue = { id, name, address, description ->
                // UPDATE
                viewModel.updateVenue(Venue(id, name, address, description))
            }
        )
    }
    Scaffold(
        topBar = {
            HomeTopBar(
                onSearchClicked = {},
                onQrScanClicked = {}
            )
        },
        floatingActionButton = {
            AddVenueFAB(onClick = {
                editingVenue = null      // null = create mode
                isOpenDialog = true
            })
        },
        modifier = modifier
    ) { innerPadding ->

        Box(Modifier
            .fillMaxSize()
            .padding(innerPadding)) {

            // --- Nội dung chính ---
            when {
                uiState.venues.isEmpty() && !uiState.isLoading && uiState.error == null -> {
                    Box(
                        Modifier.fillMaxSize()
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
                            .padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(uiState.venues, key = { it.id }) { venue ->
                            VenueCard(
                                name = venue.name,
                                address = venue.address,
                                description = venue.description,
                                onClick = { onClickVenue(venue.id) },
                                onEdit = {
                                    editingVenue = venue
                                    isOpenDialog = true
                                },
                                onDelete = {
                                    viewModel.deleteVenue(venue)
                                }
                            )
                        }
                    }
                }
            }

            // --- LOADING overlay ---
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
        }
    }

}

@Composable
fun AddVenueFAB(onClick: () -> Unit = {}) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primary
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Add Venue"
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopBar(
    title: String = "MiniMap",
    onQrScanClicked: () -> Unit = {},
    onSearchClicked: () -> Unit = {},
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
            IconButton(onClick = onQrScanClicked) {
                Icon(
                    painter = painterResource(id = R.drawable.qr_code_scanner_24dp),
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

@Preview
@Composable
fun VenueCard(
    name: String = "Annual Gala Dinneraweferfregregregwegr",
    address: String = "Some address",
    description: String = "A description of the venue",
    state: String = "Draft", // Published, Draft, Archived
    onClick: () -> Unit = {},
    onDelete: () -> Unit = {},
    onEdit: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = modifier
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        onClick = { expanded = !expanded }
    ) {
        Column {
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

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                    )
                    Text(
                        text = address,
                        style = MaterialTheme.typography.bodyMedium,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 2,
                    )
                }

                FilterChip(
                    selected = true,
                    onClick = {},
                    label = {
                        Text(
                            "Published",
                            textAlign = TextAlign.Center,
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                )
            }
            AnimatedVisibility(
                visible = expanded,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = description, style = MaterialTheme.typography.bodyMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        AssistChip(
                            onClick = onEdit,
                            label = { Text("Edit") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit"
                                )
                            }
                        )
                        AssistChip(
                            onClick = onDelete,
                            label = { Text("Delete") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete"
                                )
                            },
                        )
                        AssistChip(
                            onClick = onClick,
                            label = { Text("Go") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.ExitToApp,
                                    contentDescription = "Exit"
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

//@Preview
@Composable
fun VenueDialog(
    idVenue: Long = 0,
    nameVenue: String = "",
    addressVenue: String = "",
    descriptionVenue: String = "",
    onDismiss: () -> Unit = {},
    onUpdateVenue: (id: Long, name: String, address: String, description: String) -> Unit = { _, _, _, _ -> },
    onSave: (name: String, address: String, description: String) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf(nameVenue) }
    var address by remember { mutableStateOf(addressVenue) }
    var description by remember { mutableStateOf(descriptionVenue) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(24.dp),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title
                Text(
                    text = "Venue",
                    style = MaterialTheme.typography.headlineSmall
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(id = R.drawable.outline_id_card_24),
                            contentDescription = null
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Action buttons
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            if (idVenue == 0L) {
                                onSave(name, address, description)   // CREATE
                            } else {
                                onUpdateVenue(idVenue, name, address, description) // EDIT
                            }
                            onDismiss()
                        },
                        enabled = name.isNotBlank() && address.isNotBlank()
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
