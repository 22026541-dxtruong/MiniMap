package ie.app.minimap.ui.screens.home

import android.annotation.SuppressLint
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ie.app.minimap.R
import ie.app.minimap.data.local.entity.Venue
import kotlinx.coroutines.launch
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.SolidColor

//@Preview(showSystemUi = true)
@Composable
fun HomeScreen(
    onClickVenue: (Long) -> Unit,
    onQrScanClicked: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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
                onQrScanClicked = onQrScanClicked
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
                        items(uiState.venues.toList(), key = { it.first }) { venue ->
                            VenueCard(
                                bitmap = viewModel.generateQrCode(context, venue.first),
                                name = venue.second.name,
                                address = venue.second.address,
                                description = venue.second.description,
                                onClick = { onClickVenue(venue.second.id) },
                                onEdit = {
                                    editingVenue = venue.second
                                    isOpenDialog = true
                                },
                                onShareImage = {
                                    viewModel.shareImage(
                                        context = context,
                                        bitmap = viewModel.generateQrCode(context, venue.first)
                                    )
                                },
                                onDelete = {
                                    viewModel.deleteVenue(venue.second)
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
    bitmap: Bitmap? = null,
    name: String = "Annual Gala Dinneraweferfregregregwegr",
    address: String = "Some address",
    description: String = "A description of the venue",
    state: String = "Draft", // Published, Draft, Archived
    onClick: () -> Unit = {},
    onDelete: () -> Unit = {},
    onEdit: () -> Unit = {},
    onShareImage: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var openDialog by remember { mutableStateOf(false) }

    if (bitmap != null && openDialog) {
        Dialog(onDismissRequest = { openDialog = false }) {
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
                    Text(
                        text = name,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Qr Code",
                        modifier = Modifier
                            .fillMaxWidth()
                    )
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = { openDialog = false }) {
                            Text("Cancel")
                        }
                        TextButton(onClick = {
                            onShareImage()
                            openDialog = false
                        }) {
                            Text("Share")
                        }
                    }
                }
            }
        }
    }

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
                        .clickable(onClick = { openDialog = if (bitmap != null) true else false })
                ) {
                    if (bitmap == null) {
                        Icon(
                            painter = painterResource(R.drawable.outline_calendar_month_24),
                            contentDescription = "Date",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .size(24.dp)
                                .align(Alignment.Center)
                        )
                    } else {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Qr Code",
                            modifier = Modifier
                                .size(24.dp)
                                .align(Alignment.Center)
                        )
                    }
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

    val primaryViolet = Color(0xFF8B5CF6)
    val secondaryFuchsia = Color(0xFFD946EF)
    val gradientBrush = Brush.horizontalGradient(listOf(primaryViolet, secondaryFuchsia))

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .shadow(elevation = 24.dp, shape = RoundedCornerShape(32.dp))
                .clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
            }

            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Column {
                    Text(
                        text = if (idVenue == 0L) "Add Venue Event" else "Event Details",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1F2937)
                    )
                    Text(
                        text = if (idVenue == 0L) "Enter Event details below." else "Edit the Event details below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                CustomDialogTextField(name, { name = it }, "Venue Name", Icons.Default.Badge, primaryViolet)
                CustomDialogTextField(address, { address = it }, "Address", Icons.Default.LocationOn, primaryViolet)
                CustomDialogTextField(description, { description = it }, "Description", Icons.Default.Edit, primaryViolet, true)

                Spacer(modifier = Modifier.height(12.dp))

                val isEnabled = name.isNotBlank() && address.isNotBlank()
                val scale by animateFloatAsState(if (isEnabled) 1f else 0.97f, label = "ButtonScale")

                val buttonBrush = if (isEnabled) {
                    gradientBrush
                } else {
                    SolidColor(Color.LightGray.copy(alpha = 0.4f))
                }

                Button(
                    onClick = {
                        if (idVenue == 0L) onSave(name, address, description)
                        else onUpdateVenue(idVenue, name, address, description)
                        onDismiss()
                    },
                    enabled = isEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .graphicsLayer(scaleX = scale, scaleY = scale)
                        .clip(CircleShape)
                        .background(buttonBrush), // Truyền Brush thống nhất
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent // Quan trọng: Để background Modifier tự xử lý
                    ),
                    contentPadding = PaddingValues()
                ) {
                    Text(
                        text = if (idVenue == 0L) "Create Venue" else "Save Changes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isEnabled) Color.White else Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun CustomDialogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    primaryColor: Color,
    isMultiline: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val borderColor by animateColorAsState(if (isFocused) primaryColor else Color.Transparent, label = "border")
    val containerColor by animateColorAsState(if (isFocused) Color.White else Color(0xFFF3F4F6), label = "bg")
    val elevation by animateDpAsState(if (isFocused) 6.dp else 0.dp, label = "shadow")

    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation, RoundedCornerShape(16.dp))
            .border(width = 2.dp, color = borderColor, shape = RoundedCornerShape(16.dp)),
        label = {
            Text(
                text = label,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal
            )
        },
        leadingIcon = {
            Icon(icon, null, tint = if (isFocused) primaryColor else Color.Gray)
        },
        interactionSource = interactionSource,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = containerColor,
            unfocusedContainerColor = containerColor,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedLabelColor = primaryColor,
            cursorColor = primaryColor
        ),
        shape = RoundedCornerShape(16.dp),
        singleLine = !isMultiline,
        minLines = if (isMultiline) 3 else 1
    )
}

@Preview(name = "Light Mode - Add New", showBackground = true)
@Composable
fun VenueDialogAddPreview() {
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.LightGray)
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            VenueDialog(
                idVenue = 0L, // Mode thêm mới
                onDismiss = {}
            )
        }
    }
}

@Preview(name = "Edit Mode - Existing Data", showBackground = true)
@Composable
fun VenueDialogEditPreview() {
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF111827)) // Giả lập nền tối
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            VenueDialog(
                idVenue = 1L, // Mode chỉnh sửa
                nameVenue = "Cửa hàng trung tâm",
                addressVenue = "123 Đường Lê Lợi, Quận 1",
                descriptionVenue = "Vị trí lắp đặt thiết bị AR tại sảnh chính.",
                onDismiss = {}
            )
        }
    }
}