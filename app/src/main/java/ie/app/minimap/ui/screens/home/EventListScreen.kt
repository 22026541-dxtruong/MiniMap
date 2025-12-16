package ie.app.minimap.ui.screens.home

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ie.app.minimap.R
import ie.app.minimap.ui.theme.MiniMapTheme
import ie.app.minimap.ui.theme.PrimaryColor
import ie.app.minimap.ui.theme.Red500
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

// Dữ liệu giả định
data class Event(
    val title: String,
    val time: String,
    val location: String,
    val isLive: Boolean = false,
    val imageUrl: Int // Dùng Resource ID tạm thời
)

@Composable
fun EventListScreen(

//    viewModel: EventListViewModel = hiltViewModel()
) {
//    val venues by viewModel.venues.collectAsState()

    // Dữ liệu giả định
    val liveEvents = listOf(
        Event("Hội thảo Tech Summit 2024", "14:00 - 25/12/2024", "Tòa nhà A, Tầng 3", true,R.drawable.event_live) // Thêm ID tài nguyên giả định
    )
    val upcomingEvents = listOf(
        Event("Workshop Thiết kế Giao diện AR", "09:00 - 28/12/2024", "Tòa nhà B, Tầng 5", false,R.drawable.event_live),
        Event("Gala Dinner cuối năm", "18:00 - 31/12/2024", "Tòa nhà C, Sảnh chính", false,R.drawable.event_live)
    )
    val showNoResults = false // Đặt thành true để xem màn hình rỗng



    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { /* TODO: Xử lý thêm sự kiện */ },
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
                onSearchChange = { /* TODO: Xử lý tìm kiếm */ }
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                if (!showNoResults) {
                    // Danh sách Đang diễn ra
                    item {
                        EventSection(
                            title = "Đang diễn ra",
                            events = liveEvents,
                            isLiveSection = true
                        )
                    }

                    // Danh sách Sắp diễn ra
                    item {
                        EventSection(
                            title = "Sắp diễn ra",
                            events = upcomingEvents,
                            isLiveSection = false
                        )
                    }
                } else {
                    // Màn hình Không tìm thấy
                    item {
                        NoResultsView(Modifier.fillParentMaxSize())
                    }
                }

                // Khoảng trống cuối cùng để tránh bị che bởi FAB
                item { Spacer(modifier = Modifier.height(30.dp)) }
            }
        }
    }
}

@Composable
fun EventListHeader(onSearchChange: (String) -> Unit) {
    var searchText by remember { mutableStateOf("") }

    // Sử dụng Column để tạo thanh cố định (sticky header)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(bottom = 8.dp)
    ) {
        // Top Bar
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

        // Thanh tìm kiếm
        SearchBar(
            searchText = searchText,
            onSearchChange = {
                searchText = it
                onSearchChange(it)
            },
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // Các Bộ lọc
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
fun EventSection(title: String, events: List<Event>, isLiveSection: Boolean) {
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
                EventCard(event = event)
            }
        }
    }
}

@Composable
fun EventCard(event: Event) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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

                // Nút Map
                ButtonMap(onClick = { /* TODO: Xử lý hiển thị trên bản đồ */ })
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

@Preview(showBackground = true)
@Composable
fun EventListScreenPreview() {
    MiniMapTheme {
        EventListScreen()
    }
}