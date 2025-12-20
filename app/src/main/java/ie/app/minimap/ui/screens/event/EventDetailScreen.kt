package ie.app.minimap.ui.screens.event

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ie.app.minimap.R
import ie.app.minimap.ui.theme.MiniMapTheme
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ie.app.minimap.data.local.dto.EventWithBuildingAndFloor
import ie.app.minimap.data.local.entity.Event
import ie.app.minimap.data.local.entity.Venue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ie.app.minimap.data.local.entity.Booth
import ie.app.minimap.ui.screens.home.DeleteEventDialog
import kotlinx.coroutines.launch


// --- 1. Data Models ---
data class Vendors(
    val name: String,
    val logoRes: Int
)

data class EventDetail(
    val name: String,
    val time: String,
    val location: String,
    val description: String? = null
)

fun EventWithBuildingAndFloor.toDetail(): EventDetail {
    return EventDetail(
        name = event.name,
        time = event.endTime,
        location = this.boothName ?: "N/A",
        description = event.description
    )
}

// --- 2. Main Screen Composable ---
@Composable
fun EventDetailScreen(
    venueId: Long,
    onBackClicked: () -> Unit,
    onMapClicked: (Long) -> Unit,
    viewModel: EventDetailViewModel = hiltViewModel()
) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    var isOpenDialog by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<Event?>(null) }
    var isOpenDeleteDialog by remember { mutableStateOf(false) }
    var eventToDelete by remember { mutableStateOf<Event?>(null) }

    LaunchedEffect(venueId) {
        viewModel.loadVenueDetails(venueId)
        viewModel.loadEvents(venueId)
        viewModel.loadVendors(venueId)
        viewModel.loadBooths(venueId)
    }

    val venue by viewModel.venue.collectAsState()
    val events by viewModel.uiState.collectAsState()
    val vendors by viewModel.vendors.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val eventsState by viewModel.uiState.collectAsState()
    val allBooths by viewModel.booths.collectAsState()


    if (venue == null) {
        SideEffect {
            println("Debug: No venue with venueId = $venueId")
        }
    }
    if (isOpenDialog) {
        EventDialog(
            idEvent = editingEvent?.id ?: 0L,
            nameInitial = editingEvent?.name ?: "",
            startTimeInitial = editingEvent?.startTime ?: "",
            endTimeInitial = editingEvent?.endTime ?: "",
            descriptionInitial = editingEvent?.description ?: "",
            boothIdInitial = editingEvent?.boothId ?: 0L,
            venueId = venueId,
            booths = allBooths,
            onDismiss = {
                isOpenDialog = false
                editingEvent = null
            },
            onSave = { newEvent ->
                coroutineScope.launch {
                    viewModel.createEvent(newEvent)
                }
            },
            onUpdate = { updatedEvent ->
                coroutineScope.launch {
                    viewModel.createEvent(updatedEvent)
                }
            }
        )
    }
    if (isOpenDeleteDialog && eventToDelete != null) {
        DeleteEventDialog(
            eventName = eventToDelete!!.name,
            onDismiss = {
                isOpenDeleteDialog = false
                eventToDelete = null
            },
            onConfirm = {
                coroutineScope.launch {
                    viewModel.deleteEvent(eventToDelete!!)
                    isOpenDeleteDialog = false
                    eventToDelete = null
                }
            }
        )
    }
    // Fakedata
//    val stands = remember {
//        listOf(
//            Vendors("TechCorp", R.drawable.event_live),
//            Vendors("InnovateX", R.drawable.event_live),
//            Vendors("CloudSphere", R.drawable.event_live),
//            Vendors("CyberGuard", R.drawable.event_live)
//        )
//    }

//    val schedules= remember {
//        listOf(
//            EventDetail("Keynote: Tương lai của AI", "14:00 - 14:45", "Sảnh chính A"),
//            EventDetail(
//                "Workshop: Xây dựng ứng dụng AR",
//                "15:00 - 16:00",
//                "Phòng hội thảo 1",
//                "Tham gia workshop để học cách xây dựng ứng dụng AR từ đầu với các chuyên gia hàng đầu."
//            ),
//            EventDetail("Panel: Blockchain và ứng dụng", "16:15 - 17:00", "Sảnh chính B")
//        )
//    }
    val stands = remember(vendors) {
        vendors.map { vendor ->
            Vendors(
                name = vendor.name,
                logoRes = R.drawable.event_live
            )
        }
    }
    val schedules = remember(events.events) {
        events.events.map { it.toDetail() } ?: emptyList()
    }

    Scaffold(

    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF6F8F8))
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                // 1. Banner Section
                EventBannerNew(venue, onBackClicked)

                Column(modifier = Modifier.padding(16.dp)) {
                    // 2. Thông tin nhanh (Icon màu sắc)
                    EventQuickInfoNew(venue, onMapClicked = { onMapClicked(venueId) })

                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(20.dp))

                    // 3. Giới thiệu
                    Text(
                        text = "Giới thiệu",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Text(
                        text = venue?.description ?: "Thông tin mô tả đang được cập nhật...",
                        color = Color(0xFF475569),
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // 4. Gian hàng nổi bật (LazyRow - Giữ nguyên logic cũ)
                    SectionHeaderNew("Gian hàng nổi bật")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(stands) { StandItemNew(it) }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // 5. Lịch trình sự kiện (Timeline)
                    ScheduleTimelineNew(
                        events = eventsState.events,
                        onAddClick = {
                            editingEvent = null // Reset để dialog hiểu là thêm mới
                            isOpenDialog = true
                        },
                        onEditClick = { eventWithFloor ->
                            editingEvent = eventWithFloor.event // Gán dữ liệu để sửa
                            isOpenDialog = true
                        },
                        onDeleteClick = { eventWithFloor ->
                            eventToDelete = eventWithFloor.event
                            isOpenDeleteDialog = true
                        }
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

// --- 3. Sub-components ---

@Composable
fun EventBannerNew(venue: Venue?, onBack: () -> Unit) {
    Box(modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp) // Thêm padding để thấy rõ bo góc
        .aspectRatio(16f / 9f)
        .clip(RoundedCornerShape(24.dp)) // BO GÓC ẢNH TẠI ĐÂY
    ) {
        Image(
            painter = painterResource(id = R.drawable.event_live),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.8f)
                    )
                )
            ))

        // Nút Back
        Surface(
            onClick = onBack,
            color = Color.Black.copy(alpha = 0.3f),
            shape = CircleShape,
            modifier = Modifier
                .padding(12.dp)
                .size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.ArrowBack, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }

        Column(modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(16.dp)) {
            Surface(color = Color(0xFF13C8EC), shape = RoundedCornerShape(6.dp)) {
                Text("Đang diễn ra", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
            Text(venue?.name ?: "Hội thảo Tech Summit 2024", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun EventQuickInfoNew(venue: Venue?, onMapClicked: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Hàng thời gian
        InfoRowItem(
            icon = Icons.Outlined.CalendarToday,
            bg = Color(0xFFDBEAFE),
            tint = Color(0xFF2563EB),
            title = "Thứ Bảy, 25/12/2024",
            subtitle = "14:00 - 18:00"
        )
        // Hàng địa điểm
        InfoRowItem(
            icon = Icons.Outlined.LocationOn,
            bg = Color(0xFFFEE2E2),
            tint = Color(0xFFDC2626),
            title = venue?.address ?: "Địa điểm đang cập nhật",
            subtitle = "Tòa nhà chính, Tầng 3",
            trailing = {
                TextButton(onClick = onMapClicked) {
                    Text("Bản đồ", color = Color(0xFF13C8EC), fontWeight = FontWeight.Bold)
                    Icon(Icons.Default.ArrowForward, null, Modifier.size(16.dp), tint = Color(0xFF13C8EC))
                }
            }
        )
    }
}

@Composable
fun InfoRowItem(icon: ImageVector, bg: Color, tint: Color, title: String, subtitle: String, trailing: @Composable (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .size(42.dp)
                .background(bg, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(20.dp), tint = tint)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = if (isSystemInDarkTheme()) Color.White else Color.Black
            )
            Text(subtitle, fontSize = 13.sp, color = Color.Gray)
        }
        trailing?.invoke()
    }
}

@Composable
fun StandItemNew(stand: Vendors) {
    Card(
        modifier = Modifier.width(150.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSystemInDarkTheme()) Color(0xFF1E293B) else Color.White),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = stand.logoRes),
                contentDescription = null,
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Text(
                text = stand.name,
                fontWeight = FontWeight.Bold,
                color = if (isSystemInDarkTheme()) Color.White else Color.Black,
                modifier = Modifier.padding(top = 12.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {},
                modifier = Modifier
                    .height(32.dp)
                    .fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF13C8EC).copy(alpha = 0.1f)),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("Chi tiết", color = Color(0xFF13C8EC), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ScheduleTimelineNew(
    events: List<EventWithBuildingAndFloor>,
    onAddClick: () -> Unit,
    onEditClick: (EventWithBuildingAndFloor) -> Unit,
    onDeleteClick: (EventWithBuildingAndFloor) -> Unit
) {
    val isDark = isSystemInDarkTheme()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // --- Header giữ nguyên nút Thêm ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Lịch trình sự kiện",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White else Color.Black
            )
            IconButton(onClick = onAddClick) {
                Icon(Icons.Default.AddCircle, contentDescription = "Thêm", tint = Color(0xFF13C8EC))
            }
        }

        events.forEachIndexed { index, item ->
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                // --- Timeline trang trí (Đường kẻ và dấu chấm) ---
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (index == 0) Color(0xFF13C8EC) else Color.Gray)
                    )
                    if (index != events.size - 1) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .width(2.dp)
                                .background(Color.Gray.copy(alpha = 0.3f))
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                // --- Card nội dung kết hợp UI chi tiết ---
                Card(
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) Color(0xFF1E293B) else Color.White
                    ),
                    border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Dòng tiêu đề: Tên sự kiện + Nút thao tác
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = item.event.name,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color.White else Color.Black,
                                modifier = Modifier.weight(1f)
                            )

                            // Cụm nút Edit/Delete (Giữ lại theo yêu cầu)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(
                                    Icons.Default.Edit,
                                    null,
                                    Modifier
                                        .size(18.dp)
                                        .clickable { onEditClick(item) },
                                    tint = Color.Gray
                                )
                                Icon(
                                    Icons.Default.Delete,
                                    null,
                                    Modifier
                                        .size(18.dp)
                                        .clickable { onDeleteClick(item) },
                                    tint = Color(0xFFEF4444)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Sử dụng DetailRow để hiển thị Time và Location
                        DetailRow(
                            icon = Icons.Default.Schedule,
                            text = "${item.event.startTime} - ${item.event.endTime}",
                            tint = Color(0xFF13C8EC) // Làm nổi bật thời gian
                        )
                        DetailRow(
                            icon = Icons.Default.LocationOn,
                            text = item.boothName ?: "Vị trí đang cập nhật"
                        )

                        // Hiển thị mô tả và đường kẻ Divider nếu có description
                        if (!item.event.description.isNullOrBlank()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = if (isDark) Color.DarkGray else Color.LightGray.copy(alpha = 0.5f)
                            )
                            Text(
                                text = item.event.description!!,
                                fontSize = 13.sp,
                                color = if (isDark) Color.LightGray else Color.Gray,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// Hàm phụ trợ để vẽ dòng chi tiết (Icon + Text)
@Composable
fun DetailRow(icon: ImageVector, text: String, tint: Color = Color.Gray) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(16.dp), tint = tint)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, fontSize = 14.sp, color = if(isSystemInDarkTheme()) Color.LightGray else Color.DarkGray)
    }
}

@Composable
fun SectionHeaderNew(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (isSystemInDarkTheme()) Color.White else Color.Black
        )
        Text("Xem tất cả", color = Color(0xFF13C8EC), fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDialog(
    idEvent: Long = 0,
    nameInitial: String = "",
    startTimeInitial: String = "",
    endTimeInitial: String = "",
    descriptionInitial: String = "",
    boothIdInitial: Long = 0,
    venueId: Long,
    booths: List<Booth>,
    onDismiss: () -> Unit = {},
    onUpdate: (event: Event) -> Unit = {},
    onSave: (event: Event) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf(nameInitial) }
    var startTime by remember { mutableStateOf(startTimeInitial) }
    var endTime by remember { mutableStateOf(endTimeInitial) }
    var description by remember { mutableStateOf(descriptionInitial) }

    var selectedBooth by remember {
        mutableStateOf(booths.find { it.id == boothIdInitial })
    }
    var expanded by remember { mutableStateOf(false) }

    val primaryViolet = Color(0xFF8B5CF6)
    val primaryCyan = Color(0xFF13C8EC)
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
                        text = if (idEvent == 0L) "Thêm Lịch trình" else "Sửa Lịch trình",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1F2937)
                    )
                    Text(
                        text = "Điền thông tin chi tiết cho sự kiện dưới đây.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                CustomDialogTextField(name, { name = it }, "Tên sự kiện", Icons.Default.Badge, primaryCyan)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Chọn vị trí (Booth)",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Gray,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                    )

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedBooth?.name ?: "Chọn gian hàng",
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            leadingIcon = { Icon(Icons.Default.Storefront, null, tint = primaryCyan) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primaryCyan,
                                unfocusedContainerColor = Color(0xFFF3F4F6),
                                focusedContainerColor = Color.White,
                                unfocusedBorderColor = Color.Transparent
                            )
                        )

                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.background(Color.White)
                        ) {
                            booths.forEach { booth ->
                                DropdownMenuItem(
                                    text = { Text(booth.name) },
                                    onClick = {
                                        selectedBooth = booth
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        CustomDialogTextField(startTime, { startTime = it }, "Bắt đầu", Icons.Default.Schedule, primaryCyan)
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        CustomDialogTextField(endTime, { endTime = it }, "Kết thúc", Icons.Default.Timer, primaryCyan)
                    }
                }
                CustomDialogTextField(description, { description = it }, "Mô tả", Icons.Default.Edit, primaryCyan, true)

                Spacer(modifier = Modifier.height(12.dp))

                val isEnabled = name.isNotBlank() && startTime.isNotBlank() && selectedBooth != null
                val scale by animateFloatAsState(if (isEnabled) 1f else 0.97f, label = "ButtonScale")

                val buttonBrush = if (isEnabled) {
                    gradientBrush
                } else {
                    SolidColor(Color.LightGray.copy(alpha = 0.4f))
                }

                Button(
                    onClick = {
                        val eventData = Event(
                            id = idEvent,
                            boothId = selectedBooth?.id ?: 0L,
                            venueId = venueId,
                            name = name,
                            description = description,
                            startTime = startTime,
                            endTime = endTime,
                            createdAt = System.currentTimeMillis()
                        )
                        if (idEvent == 0L) onSave(eventData) else onUpdate(eventData)
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
                        text = if (idEvent == 0L) "Tạo sự kiện" else "Lưu thay đổi",
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

@Preview(showBackground = true, backgroundColor = 0xFF101F22) // Thêm màu nền tối cho khớp thiết kế
@Composable
fun EventDetailScreenPreview() {
    MiniMapTheme {
        EventDetailScreen(
            venueId = 1L, // Truyền ID giả
            onBackClicked = { /* Không làm gì trong preview */ },
            onMapClicked = { /* Không làm gì trong preview */ }
        )
    }
}