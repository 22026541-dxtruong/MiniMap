package ie.app.minimap.ui.screens.event

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    LaunchedEffect(venueId) {
        viewModel.loadVenueDetails(venueId)
        viewModel.loadEvents(venueId)
        viewModel.loadVendors(venueId)
    }

    val venue by viewModel.venue.collectAsState()
    val events by viewModel.uiState.collectAsState()
    val vendors by viewModel.vendors.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    if (venue == null) {
        SideEffect {
            println("Debug: No venue with venueId = $venueId")
        }
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

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFF101F22))) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // --- Header Image & Overlays ---
            Box(modifier = Modifier
                .height(260.dp)
                .fillMaxWidth()) {
                Image(
                    painter = painterResource(id = R.drawable.event_live), // Ảnh bìa
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Gradient mờ dần phía dưới
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xFF101F22)),
                                startY = 300f
                            )
                        )
                )

                // Top Bar Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp, start = 16.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircleIconButton(Icons.Default.ArrowBackIosNew, onClick = onBackClicked)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircleIconButton(Icons.Default.BookmarkBorder)
                        CircleIconButton(Icons.Default.Share)
                    }
                }
            }

            // --- Content ---
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {

                // Event Info Card (Đè lên ảnh một chút)
                EventMainInfoCard(venue)

                Spacer(modifier = Modifier.height(24.dp))

                // Mô tả
                Text(
                    text = "Mô tả",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = venue?.description ?: "Đang tải mô tả...",
                    color = Color.LightGray,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Gian hàng nổi bật
                SectionHeader("Gian hàng nổi bật")
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(stands) { StandItem(it) }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Lịch trình
                SectionHeader("Lịch trình sự kiện")
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    schedules.forEach { ScheduleCard(it) }
                }

                Spacer(modifier = Modifier.height(100.dp)) // Khoảng trống cuối trang
            }
        }

        // Nút Map nổi (FAB)
        FloatingActionButton(
            onClick = { onMapClicked(venueId) },
            containerColor = Color(0xFF13C8EC),
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .size(56.dp)
        ) {
            Icon(Icons.Default.Map, contentDescription = "Xem bản đồ", tint = Color.White)
        }
    }
}

// --- 3. Sub-components ---

@Composable
fun EventMainInfoCard(venue: Venue?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = (-40).dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Live Badge
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Red)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier
                    .size(6.dp)
                    .background(Color.White, CircleShape))
                Text(" Đang diễn ra", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            Text(
                venue?.name ?: "Hội thảo Tech Summit 2024",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            val addressDisplay: String = venue?.address ?: "Địa chỉ đang cập nhật"
            DetailRow(Icons.Default.CalendarToday, "14:00 - 17:00, Thứ Bảy, 25/12/2024")
            DetailRow(Icons.Default.LocationOn, addressDisplay)
        }
    }
}

@Composable
fun ScheduleCard(item: EventDetail) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(item.name, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                Icon(Icons.Default.ExpandMore, contentDescription = null, tint = Color.Gray)
            }
            Spacer(modifier = Modifier.height(8.dp))
            DetailRow(Icons.Default.Schedule, item.time)
            DetailRow(Icons.Default.LocationOn, item.location)

            if (item.description != null) {
                Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color.DarkGray)
                Text(item.description, fontSize = 13.sp, color = Color.LightGray)
            }
        }
    }
}

@Composable
fun StandItem(stand: Vendors) {
    Card(
        modifier = Modifier.width(150.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(16.dp)
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
            Text(stand.name, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(top = 12.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {},
                modifier = Modifier
                    .height(32.dp)
                    .fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF13C8EC).copy(alpha = 0.1f)),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("Xem chi tiết", color = Color(0xFF13C8EC), fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun DetailRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Icon(icon, null, modifier = Modifier.size(16.dp), tint = Color.Gray)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, fontSize = 14.sp, color = Color.LightGray)
    }
}

@Composable
fun SectionHeader(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Xem tất cả", color = Color(0xFF13C8EC), fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CircleIconButton(icon: ImageVector, onClick: () -> Unit = {}) {
    Surface(
        onClick = onClick,
        color = Color.Black.copy(alpha = 0.3f),
        shape = CircleShape,
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
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