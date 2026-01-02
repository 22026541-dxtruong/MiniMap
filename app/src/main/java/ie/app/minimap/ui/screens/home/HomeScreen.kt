package ie.app.minimap.ui.screens.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ie.app.minimap.R
import ie.app.minimap.ui.theme.PrimaryColor
import ie.app.minimap.ui.theme.Red500
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ie.app.minimap.data.local.entity.Venue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewModelScope

data class Event(
    val id: Long,
    val title: String,
    val time: String,
    val location: String,
    val isLive: Boolean = false,
    val imageUrl: Int // Dùng Resource ID tạm thời
)

fun Venue.toEvent(isLiveEvent: Boolean = false, imageId: Int = R.drawable.event_live): Event {
    return Event(
        id = this.id,
        title = this.name,
        time = "Chưa xác định thời gian",
        location = this.address,
        isLive = isLiveEvent,
        imageUrl = imageId
    )
}

@Composable
fun HomeScreen(
    onVenueClick: (Long) -> Unit,
    onQrClick: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val uiState by viewModel.uiState.collectAsState()
    var isOpenDialog by remember { mutableStateOf(false) }
    var editingVenue by remember { mutableStateOf<Venue?>(null) }
    val coroutineScope = rememberCoroutineScope()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var venueToDelete by remember { mutableStateOf<Venue?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.viewModelScope.launch {
                viewModel.importData(context, it)?.let { venueId ->
                    onVenueClick(venueId)
                }
            }
        }
    }


    val allEvents = uiState.venues.map { venue ->
        venue.toEvent(isLiveEvent = false, imageId = R.drawable.event_live)
    }

    val liveEvents = if (allEvents.isNotEmpty()) {
        listOf(allEvents.first().copy(isLive = true, time = "ĐANG DIỄN RA"))
    } else {
        emptyList()
    }

    val upcomingEvents = if (allEvents.size > 1) {
        allEvents.drop(1)
    } else {
        emptyList()
    }

    val showNoResults = liveEvents.isEmpty() && upcomingEvents.isEmpty()

    if (isOpenDialog) {
        VenueDialog(
            idVenue = editingVenue?.id ?: 0,
            nameVenue = editingVenue?.name ?: "",
            addressVenue = editingVenue?.address ?: "",
            descriptionVenue = editingVenue?.description ?: "",
            onDismiss = {
                isOpenDialog = false
                editingVenue = null
            },
            onSave = { name, address, description ->
                coroutineScope.launch {
                    viewModel.createVenue(name, address, description)
                    isOpenDialog = false
                }
            },
            onUpdateVenue = { id, name, address, description ->
                viewModel.updateVenue(Venue(id, name, address, description))
                isOpenDialog = false
            }
        )
    }

    if (showDeleteDialog && venueToDelete != null) {
        DeleteEventDialog(
            eventName = venueToDelete?.name ?: "",
            onDismiss = {
                showDeleteDialog = false
                venueToDelete = null
            },
            onConfirm = {
                coroutineScope.launch {
                    viewModel.deleteVenue(venueToDelete!!)
                    showDeleteDialog = false
                    venueToDelete = null
                }
            }
        )
    }

    LaunchedEffect(uiState.isOnline) {
        if (!uiState.isOnline) {
            snackbarHostState.showSnackbar("Không có kết nối mạng", duration = SnackbarDuration.Indefinite)
        } else {
            snackbarHostState.currentSnackbarData?.dismiss()
        }
    }


    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingVenue = null
                    isOpenDialog = true
                },
                containerColor = PrimaryColor,
                shape = CircleShape,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Thêm sự kiện", tint = Color.White)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            EventListHeader(
                onSearchChange = { /* TODO: Xử lý tìm kiếm */ },
                onQrClick = onQrClick,
                onUpload = { launcher.launch("text/plain") }
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                if (!showNoResults) {
                    item {
                        EventSection(
                            title = "Đang diễn ra",
                            events = liveEvents,
                            isLiveSection = true,
                            onEventClick = onVenueClick,
                            onEditEvent = { id ->
                                editingVenue = uiState.venues.find { it.id == id }
                                isOpenDialog = true
                            },
                            onDeleteEvent = { id ->
                                venueToDelete = uiState.venues.find { it.id == id }
                                showDeleteDialog = true
                            },
                            onShareEvent = { event ->
                                showDeleteDialog = true
                                viewModel.viewModelScope.launch {
                                    viewModel.shareFile(context, event.id, event.title)
                                }
                            }
                        )
                    }

                    item {
                        EventSection(
                            title = "Sắp diễn ra",
                            events = upcomingEvents,
                            isLiveSection = false,
                            onEventClick = onVenueClick,
                            onEditEvent = { id ->
                                editingVenue = uiState.venues.find { it.id == id }
                                isOpenDialog = true
                            },
                            onDeleteEvent = { id ->
                                venueToDelete = uiState.venues.find { it.id == id }
                                showDeleteDialog = true
                            },
                            onShareEvent = { event ->
                                showDeleteDialog = true
                                viewModel.viewModelScope.launch {
                                    viewModel.shareFile(context, event.id, event.title)
                                }
                            }
                        )
                    }
                } else {
                    item {
                        NoResultsView(Modifier.fillParentMaxSize())
                    }
                }

                item { Spacer(modifier = Modifier.height(30.dp)) }
            }
        }
    }
}

@Composable
fun EventListHeader(
    onSearchChange: (String) -> Unit,
    onQrClick: () -> Unit = {},
    onUpload: () -> Unit = {}
) {
    var searchText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.size(48.dp)) // Placeholder cho biểu tượng bên trái
            Text(
                text = "Sự kiện",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.size(48.dp)) // Placeholder cho biểu tượng bên phải
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchBar(
                searchText = searchText,
                onSearchChange = {
                    searchText = it
                    onSearchChange(it)
                },
                modifier = Modifier
                    .weight(1f)
            )

            IconButton(onClick = onQrClick) {
                Icon(
                    painter = painterResource(id = R.drawable.qr_code_scanner_24dp),
                    contentDescription = "Qr Code Scanner"
                )
            }
            IconButton(onClick = onUpload) {
                Icon(
                    painter = painterResource(id = R.drawable.outline_upload_24),
                    contentDescription = "Import"
                )
            }
        }

        FilterChipsRow(
            modifier = Modifier
                .padding(top = 16.dp, bottom = 8.dp)
                .padding(start = 16.dp, end = 16.dp)
        )
    }
}

@Composable
fun SearchBar(searchText: String, onSearchChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_search), // Thay thế bằng resource ID icon
            contentDescription = "Tìm kiếm",
            modifier = Modifier.padding(start = 16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        BasicTextField(
            value = searchText,
            onValueChange = onSearchChange,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            textStyle = LocalTextStyle.current.copy(
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 16.sp
            ),
            singleLine = true,
            decorationBox = { innerTextField ->
                if (searchText.isEmpty()) {
                    Text(
                        text = "Tìm kiếm sự kiện...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                innerTextField()
            }
        )
    }
}

@Composable
fun FilterChip(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(32.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { /* Xử lý lọc */ }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Icon(
            painter = painterResource(id = R.drawable.ic_arrow_drop_down), // Thay thế bằng resource ID icon
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
fun FilterChipsRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FilterChip(text = "Tòa nhà")
        FilterChip(text = "Thời gian")
        FilterChip(text = "Danh mục")
    }
}

@Composable
fun EventSection(
    title: String,
    events: List<Event>,
    isLiveSection: Boolean,
    onEventClick: (Long) -> Unit,
    onEditEvent: (Long) -> Unit,
    onDeleteEvent: (Long) -> Unit,
    onShareEvent: (Event) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isLiveSection) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Red500)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            events.forEach { event ->
                EventCard(
                    event = event,
                    onClick = { onEventClick(event.id) },
                    onEdit = { onEditEvent(event.id) },
                    onDelete = { onDeleteEvent(event.id) },
                    onShare = { onShareEvent(event) }
                )
            }
        }
    }
}

@Composable
fun EventCard(
    event: Event,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            // Ảnh sự kiện
            Image(
                painter = painterResource(id = event.imageUrl),
                contentDescription = event.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp) // Tương đương aspect-[16/10]
            )

            // Chi tiết sự kiện
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Thời gian
                    EventDetailRow(
                        icon = R.drawable.ic_calendar_today, // Thay thế bằng resource ID icon
                        text = event.time
                    )
                    // Địa điểm
                    EventDetailRow(
                        icon = R.drawable.ic_location_on, // Thay thế bằng resource ID icon
                        text = event.location
                    )
                }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = Slate600
                        )
                    }

                    // DropdownMenu xuất hiện khi nhấn MoreVert
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(
                            Color.White
                        )
                    ) {
                        DropdownMenuItem(
                            text = { Text("Chỉnh sửa sự kiện") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                showMenu = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Chia sẻ sự kiện") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                showMenu = false
                                onShare()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Xóa sự kiện", color = Color.Red) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = Color.Red,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )

                    }
                }
            }
        }
    }
}

@Composable
fun EventDetailRow(icon: Int, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

val Slate900 = Color(0xFF0F172A)
val Slate600 = Color(0xFF475569)
val Slate400 = Color(0xFF94A3B8)
val Slate200 = Color(0xFFE2E8F0)
val PrimaryCyan = Color(0xFF13C8EC)

@Composable
fun EventIconInfo(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = Slate400
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Slate600
        )
    }
}

@Composable
fun ButtonMap(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(PrimaryColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_map), // Thay thế bằng resource ID icon
            contentDescription = "Xem bản đồ",
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
fun NoResultsView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_search_off), // Thay thế bằng resource ID icon
            contentDescription = "Không tìm thấy",
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Text(
            text = "Không tìm thấy sự kiện",
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Vui lòng thử lại với từ khóa hoặc bộ lọc khác.",
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

val IosRed = Color(0xFFFF3B30)
val IosGray = Color(0xFFF2F2F7)
val TextDark = Color(0xFF111827)
val TextGray = Color(0xFF6B7280)

@Composable
fun DeleteEventDialog(
    eventName: String,
    onDismiss: () -> Unit = {},
    onConfirm: () -> Unit = {}
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false // Cho phép tràn lề tùy chỉnh
        )
    ) {
        // Khung Dialog chính
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.88f) // max-width tương đương bản web
                .wrapContentHeight()
                .shadow(
                    elevation = 30.dp,
                    shape = RoundedCornerShape(32.dp),
                    spotColor = Color.Black.copy(alpha = 0.2f)
                ),
            shape = RoundedCornerShape(32.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 28.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // --- Icon Thùng rác với hiệu ứng Ring ---
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(Color(0xFFFFEBEB), CircleShape) // Nền đỏ nhạt ngoài cùng
                        .border(
                            4.dp,
                            Color(0xFFFFEBEB).copy(alpha = 0.5f),
                            CircleShape
                        ) // Hiệu ứng Ring
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(1.dp, Color(0xFFFFD6D6), CircleShape) // Viền đỏ nhạt bên trong
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = IosRed,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // --- Tiêu đề ---
                Text(
                    text = "Xóa sự kiện này?",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark,
                    letterSpacing = (-0.5).sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // --- Nội dung mô tả với AnnotatedString ---
                Text(
                    text = buildAnnotatedString {
                        append("Bạn có chắc muốn xóa ")
                        withStyle(
                            style = SpanStyle(
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )
                        ) {
                            append(eventName)
                        }
                        append(" không? Hành động này không thể hoàn tác.")
                    },
                    fontSize = 15.sp,
                    color = TextGray,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(32.dp))

                // --- Cụm nút bấm hành động ---
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Nút Xóa
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .shadow(
                                elevation = 12.dp,
                                shape = RoundedCornerShape(16.dp),
                                spotColor = IosRed.copy(alpha = 0.25f)
                            ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = IosRed,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Text(
                            text = "Xóa sự kiện",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Nút Hủy
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = IosGray,
                            contentColor = TextDark
                        ),
                        shape = RoundedCornerShape(16.dp),
                        elevation = null
                    ) {
                        Text(
                            text = "Hủy",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

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
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
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

                CustomDialogTextField(
                    name,
                    { name = it },
                    "Venue Name",
                    Icons.Default.Badge,
                    primaryViolet
                )
                CustomDialogTextField(
                    address,
                    { address = it },
                    "Address",
                    Icons.Default.LocationOn,
                    primaryViolet
                )
                CustomDialogTextField(
                    description,
                    { description = it },
                    "Description",
                    Icons.Default.Edit,
                    primaryViolet,
                    true
                )

                Spacer(modifier = Modifier.height(12.dp))

                val isEnabled = name.isNotBlank() && address.isNotBlank()
                val scale by animateFloatAsState(
                    if (isEnabled) 1f else 0.97f,
                    label = "ButtonScale"
                )

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

    val borderColor by animateColorAsState(
        if (isFocused) primaryColor else Color.Transparent,
        label = "border"
    )
    val containerColor by animateColorAsState(
        if (isFocused) Color.White else Color(0xFFF3F4F6),
        label = "bg"
    )
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
