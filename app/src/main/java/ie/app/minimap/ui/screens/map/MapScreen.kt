package ie.app.minimap.ui.screens.map

import android.graphics.Paint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ie.app.minimap.R
import ie.app.minimap.data.local.entity.Building
import ie.app.minimap.data.local.entity.Edge
import ie.app.minimap.data.local.entity.Floor
import ie.app.minimap.data.local.entity.Node
import ie.app.minimap.ui.ar.ArView
import kotlinx.coroutines.launch
import kotlin.collections.forEach

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    venueId: Long,
    viewModel: MapViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var editing by remember { mutableStateOf(false) }
    var expandedSearch by remember { mutableStateOf(false) }

    val uiState by viewModel.uiState.collectAsState()
    val userPosition by viewModel.userPosition.collectAsState()
    val buildings by viewModel.buildings.collectAsState()
    val floors by viewModel.floors.collectAsState()
    val nodes by viewModel.nodes.collectAsState()
    val edges by viewModel.edges.collectAsState()
    val draggingState by viewModel.draggingState.collectAsState()
    val scale by viewModel.scale.collectAsState()
    val offset by viewModel.offset.collectAsState()
    val rotation by viewModel.rotation.collectAsState()
    val selection by viewModel.selection.collectAsState() // Theo dõi lựa chọn
    val pathOffsetAndNode by viewModel.pathOffsetAndNode.collectAsState()
    val searchResult by viewModel.searchResult.collectAsState()

    val textPaint = remember {
        Paint().apply {
            color = Color.White.toArgb()
            textSize = 30f
            textAlign = Paint.Align.CENTER
        }
    }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val scaffoldState = rememberBottomSheetScaffoldState()
    val bottomExpanded by remember {
        derivedStateOf { scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded }
    }
    val sheetState = scaffoldState.bottomSheetState

    var sheetOffsetPx by remember { mutableFloatStateOf(0f) }

    val sheetOffsetDp = with(LocalDensity.current) { sheetOffsetPx.toDp() }

    LaunchedEffect(sheetState) {
        snapshotFlow {
            runCatching { -sheetState.requireOffset() }.getOrDefault(0f)
        }.collect { offset ->
            sheetOffsetPx = offset
        }
    }
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    LaunchedEffect(venueId) {
        viewModel.loadMap(venueId)
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState)
        }
    ) { innerPadding ->
        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = 80.dp + innerPadding.calculateBottomPadding(),
            sheetContainerColor = if (bottomExpanded) Color(0xFFF0F0F0) else BottomSheetDefaults.ContainerColor,
            sheetDragHandle = {
                Surface(
                    modifier =
                        modifier.padding(top = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Box(Modifier.size(width = 32.dp, height = 4.dp))
                }
            },
            sheetContent = {
                Box(
                    Modifier
                        .padding(bottom = innerPadding.calculateBottomPadding())
                        .fillMaxWidth()
                        .fillMaxHeight(0.4f)
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = bottomExpanded,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFFF0F0F0))
                                .onSizeChanged { size ->
                                    viewModel.setScreenSize(size)
                                }
                                .pointerInput(Unit) {
                                    detectTransformGestures { centroid, pan, zoom, rotation ->
                                        viewModel.onTransform(
                                            centroid,
                                            pan,
                                            zoom,
                                            rotation
                                        )
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectTapGestures { viewModel.onTap(it) }
                                }
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = viewModel::onDragStart,
                                        onDrag = { change, dragAmount ->
                                            viewModel.onDragGesture(change, dragAmount)
                                            change.consume()
                                        },
                                        onDragEnd = viewModel::onDragEnd,
                                        onDragCancel = viewModel::onDragEnd
                                    )
                                }
                        ) {
                            withTransform({
                                translate(offset.x, offset.y)
                                rotate(degrees = rotation, pivot = Offset.Zero)
                                scale(scale = scale, pivot = Offset.Zero)
                            }) {
                                drawEdges(edges, nodes, selection, scale)
                                drawDraggingLine(draggingState, scale)
                                if (pathOffsetAndNode != null) {
                                    drawPath(pathOffsetAndNode!!.first, scale)
                                }
                                drawNodes(nodes, textPaint, draggingState, selection, scale)
                                if (userPosition != null) {
                                    drawUserPosition(userPosition, scale)
                                }
                            }
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = bottomExpanded,
                        modifier = Modifier
                            .align(Alignment.TopEnd),
                    ) {
                        Row(
                            Modifier
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (selection != Selection.None) {
                                FilledTonalIconButton(onClick = viewModel::deleteSelection) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = Color.Red
                                    )
                                }
                            }
                            FilledTonalIconButton(onClick = { viewModel.zoom(1.2f) }) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Zoom in"
                                )
                            }
                            FilledTonalIconButton(onClick = { viewModel.zoom(0.8f) }) {
                                Icon(
                                    painter = painterResource(R.drawable.outline_check_indeterminate_small_24),
                                    contentDescription = "Zoom out"
                                )
                            }
                        }
                    }

                    FloorsDropDownMenu(
                        modifier = Modifier.align(Alignment.TopStart),
                        floors = floors,
                        selectedFloor = uiState.selectedFloor,
                        onFloorSelected = viewModel::onFloorSelected
                    ) {
                        AnimatedContent(
                            targetState = bottomExpanded,
                        ) {
                            when (it) {
                                true -> androidx.compose.animation.AnimatedVisibility(editing) {
                                    FilledTonalIconButton(
                                        onClick = { },
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.stacks_add_24dp),
                                            contentDescription = "Show Floors",
                                            modifier = Modifier
                                                .size(24.dp)
                                                .padding(4.dp)
                                        )
                                    }
                                }

                                else -> BuildingsDropDownMenu(
                                    buildings = buildings,
                                    selectedBuilding = uiState.selectedBuilding,
                                    onBuildingSelected = viewModel::onBuildingSelected
                                )
                            }
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = bottomExpanded,
                        modifier = Modifier.align(Alignment.BottomStart),
                    ) {
                        BuildingsDropDownMenu(
                            buildings = buildings,
                            selectedBuilding = uiState.selectedBuilding,
                            onBuildingSelected = viewModel::onBuildingSelected
                        ) {
                            androidx.compose.animation.AnimatedVisibility(editing) {
                                FilledTonalIconButton(
                                    onClick = { },
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.outline_domain_add_24),
                                        contentDescription = "Show Floors",
                                        modifier = Modifier
                                            .size(24.dp)
                                            .padding(4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },

        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                ArView(
                    editing = editing,
                    venueId = venueId,
                    building = uiState.selectedBuilding,
                    floor = uiState.selectedFloor,
                    nodes = nodes,
                    pathNode = pathOffsetAndNode?.second,
                    onMessage = { message ->
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(message)
                        }
                    },
                    updateUserLocation = { viewModel.updateUserLocation(it) },
                    modifier = Modifier.fillMaxSize()
                )

                FloatingActionButton(
                    onClick = {
                        editing = !editing
                        expandedSearch = false
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(y = -sheetOffsetDp - screenHeight - 28.dp)
                        .padding(16.dp)
                ) {
                    AnimatedContent(
                        editing,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            when (it) {
                                true -> {
                                    Icon(
                                        painterResource(R.drawable.baseline_visibility_24),
                                        "View"
                                    )
                                    Text("View")
                                }

                                else -> {
                                    Icon(Icons.Default.Edit, "Edit")
                                    Text("Edit")
                                }
                            }
                        }
                    }
                }
                MapTopBar(
                    expandedSearch = expandedSearch,
                    onBack = onBack,
                    onSearch = {
                        coroutineScope.launch {
                            viewModel.getNodesByLabel(it)
                        }
                    },
                    onClickNode = {
                        viewModel.findPathToNode(it)
                    },
                    onExpandedChange = { expandedSearch = it },
                    searchResults = searchResult,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloorsDropDownMenu(
    floors: List<Floor>,
    selectedFloor: Floor,
    onFloorSelected: (Floor) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .padding(vertical = 4.dp)
            .height(50.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier
                .width(100.dp)
                .height(50.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxSize()
                    .border(
                        width = 2.dp,
                        color = Color.Gray, // Hoặc dùng màu Material
                        shape = RoundedCornerShape(4.dp) // Hình bo góc
                    )
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Icon(
                    painter = painterResource(R.drawable.outline_stacks_24),
                    contentDescription = "Floors",
                    modifier = Modifier
                        .size(24.dp)
                        .padding(4.dp)
                )
                Text(
                    selectedFloor.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                ExposedDropdownMenuDefaults.TrailingIcon(
                    expanded = expanded,
                )
            }

            // ExposedDropdownMenu có khả năng xử lý danh sách cuộn tốt hơn
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                floors.forEach { selectionOption ->
                    DropdownMenuItem(
                        text = { Text(selectionOption.name) },
                        onClick = {
                            onFloorSelected(selectionOption)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildingsDropDownMenu(
    buildings: List<Building> = (1..10).toList()
        .map { Building(it.toLong(), name = "Tòa nhà $it") },
    selectedBuilding: Building = Building(1, name = "Tòa nhà 1"),
    onBuildingSelected: (Building) -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .height(50.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxHeight()
                .width(100.dp)
                .border(
                    width = 2.dp,
                    color = Color.Gray, // Hoặc dùng màu Material
                    shape = RoundedCornerShape(4.dp) // Hình bo góc
                )
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Icon(
                painter = painterResource(R.drawable.outline_domain_24),
                contentDescription = "Buildings",
                modifier = Modifier
                    .size(24.dp)
                    .padding(4.dp)
            )
            Text(
                selectedBuilding.name,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
            ExposedDropdownMenuDefaults.TrailingIcon(
                expanded = expanded,
                modifier = Modifier.rotate(if (expanded) 90f else -270f)
            )
        }
        // ExposedDropdownMenu có khả năng xử lý danh sách cuộn tốt hơn
        AnimatedVisibility(
            visible = expanded,
            enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
        ) {
            // LazyRow để hiển thị danh sách các building
            LazyRow(
                contentPadding = PaddingValues(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(buildings, key = { it.id }) { building ->
                    FilledTonalButton(
                        onClick = {
                            onBuildingSelected(building)
                            expanded = false // Khi chọn 1 item, đóng dropdown
                        },
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(
                            building.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
fun MapTopBar(
    expandedSearch: Boolean = false,
    onExpandedChange: (Boolean) -> Unit = { _ -> },
    onBack: () -> Unit = {},
    onClickNode: (Node) -> Unit = {},
    onSearch: (String) -> Unit = { _ -> },
    searchResults: List<Node> =  emptyList(),
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }

    DockedSearchBar(
        inputField = {
            SearchBarDefaults.InputField(
                query = query,
                onQueryChange = {
                    query = it
                    onExpandedChange(it.isNotBlank())
                    onSearch(it)
                }, // Cập nhật query
                onSearch = { onSearch(it) }, // Thực hiện tìm kiếm
                expanded = expandedSearch,
                onExpandedChange = onExpandedChange,
                placeholder = { Text("Search") },
                leadingIcon = {
                    IconButton(
                        onClick = onBack
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                trailingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null
                    )
                },
                // Sử dụng màu mặc định cho InputField
                colors = SearchBarDefaults.colors().inputFieldColors
            )
        },
        expanded = expandedSearch,
        onExpandedChange = onExpandedChange,
        modifier = modifier.padding(16.dp)
    ) {
        if (searchResults.isEmpty()) {
            // ➡️ Hiển thị thông báo "Không có kết quả"
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No Results",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(searchResults) { result ->
                ListItem(
                    headlineContent = { Text(result.label) },
                    leadingContent = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                        .clickable {
                            onExpandedChange(false)
                            query = result.label // Đặt query thành kết quả được chọn
                            onClickNode(result) // Thực hiện tìm kiếm
                            // Không cần cập nhật `expanded` vì nó tự động false khi `query` không rỗng
                        }
                )
            }
        }
    }
}

//@Preview
@Composable
fun AddEditDeleteDialog(
    title: String = "Building",
    id: Long = 0,
    name: String = "",
    descriptionVenue: String = "",
    onDismiss: () -> Unit = {},
    onDeleted: (id: Long) -> Unit = {},
    onSave: (name: String, description: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf(name) }
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
                    text = title,
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

                if (title == "Building") {
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
                }

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
                            onSave(name, description)
                            onDismiss()
                        },
                        enabled = name.isNotBlank()
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

// --- Các hàm trợ giúp vẽ ---

private fun DrawScope.drawEdges(
    edges: List<Edge>,
    nodes: List<Node>,
    selection: Selection,
    scale: Float
) {
    val baseStrokeWidth = 5f
    val selectedStrokeWidth = 8f
    edges.forEach { edge ->
        val startNode = nodes.find { it.id == edge.fromNode }
        val endNode = nodes.find { it.id == edge.toNode }

        if (startNode != null && endNode != null) {
            val isSelected = (selection as? Selection.EdgeSelected)?.edge?.id == edge.id
            val currentStrokeWidth =
                (if (isSelected) selectedStrokeWidth else baseStrokeWidth) / scale

            drawLine(
                color = if (isSelected) Color.Red else Color.Black,
                start = Offset(startNode.x, startNode.y),
                end = Offset(endNode.x, endNode.y),
                strokeWidth = currentStrokeWidth
            )
        }
    }
}

private fun DrawScope.drawPath(
    points: List<Offset>,
    scale: Float
) {
    if (points.size < 2) return

    // 1. Cấu hình giao diện (Style)
    val pathColor = Color(0xFF4285F4) // Xanh dương kiểu Google Maps

    // Tính toán độ dày nét vẽ dựa trên scale
    // Mục đích: Khi zoom map to lên, nét vẽ không bị "phình" ra, mà giữ nguyên độ mảnh
    val baseWidth = 4.dp.toPx()
    val adjustedStrokeWidth = (baseWidth / scale).coerceAtLeast(1f)

    // Tạo hiệu ứng nét đứt (Dashed Line)
    // Intervals cũng phải chia cho scale để độ dài vạch không bị phóng to
    val dashedEffect = PathEffect.dashPathEffect(
        intervals = floatArrayOf(20f / scale, 10f / scale),
        phase = 0f
    )

    // 2. Tạo đối tượng Path từ List<Offset>
    val navigationPath = Path().apply {
        // Điểm bắt đầu (thường là User Position)
        moveTo(points.first().x, points.first().y)

        // Nối lần lượt qua các điểm trung gian đến đích
        // drop(1) để bỏ qua điểm đầu vì đã moveTo rồi
        points.drop(1).forEach { offset ->
            lineTo(offset.x, offset.y)
        }
    }

    // 3. Thực hiện vẽ đường đi
    drawPath(
        path = navigationPath,
        color = pathColor,
        style = Stroke(
            width = adjustedStrokeWidth,
            cap = StrokeCap.Round,  // Bo tròn đầu mút
            join = StrokeJoin.Round, // Bo tròn các khúc cua
            pathEffect = dashedEffect // Xóa dòng này nếu muốn vẽ nét liền
        )
    )

    // 4. (Tùy chọn) Vẽ điểm chốt ở đầu và cuối để map sinh động hơn
    val dotRadius = (5.dp.toPx() / scale)

    // Vẽ điểm đầu (Vị trí User)
    drawCircle(
        color = Color.Blue,
        radius = dotRadius,
        center = points.first()
    )

    // Vẽ điểm đích (Target)
    drawCircle(
        color = Color.Red,
        radius = dotRadius,
        center = points.last()
    )
}

private fun DrawScope.drawDraggingLine(draggingState: DraggingState?, scale: Float) {
    draggingState?.let { drag ->
        val startPos = Offset(drag.startNode.x, drag.startNode.y)
        // Nếu "dính" thì vẽ đến tâm nút, nếu không thì vẽ đến ngón tay
        val endPos = Offset(
            drag.snapTargetNode?.x ?: drag.currentPosition.x,
            drag.snapTargetNode?.y ?: drag.currentPosition.y
        )

        drawLine(
            color = Color.Blue.copy(alpha = 0.7f),
            start = startPos,
            end = endPos,
            strokeWidth = 6f,
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(20f / scale, 10f / scale), // Dash cũng phải scale
                0f
            )
        )
    }
}

private fun DrawScope.drawNodes(
    nodes: List<Node>,
    textPaint: Paint,
    draggingState: DraggingState?,
    selection: Selection,
    scale: Float
) {
    textPaint.textSize = 30f / scale
    nodes.forEach { node ->
        // Kiểm tra xem nút này có đang được chọn không
        val isSelected = (selection as? Selection.NodeSelected)?.node?.id == node.id

        val strokeWidth = (if (isSelected) 8f else 3f) / scale

        // Vẽ vòng tròn chính của nút
        drawCircle(
            color = Color(0xFF3B82F6), // Màu xanh dương
            center = Offset(node.x, node.y),
            radius = MapViewModel.RADIUS / scale
        )

        // Vẽ viền
        drawCircle(
            color = if (isSelected) Color.Red else Color.Black, // Màu viền đỏ nếu được chọn
            center = Offset(node.x, node.y),
            radius = MapViewModel.RADIUS / scale,
            style = Stroke(width = strokeWidth) // Viền dày hơn nếu được chọn
        )

        // Vẽ nhãn (label)
        drawContext.canvas.nativeCanvas.drawText(
            node.label,
            node.x,
            node.y - (textPaint.descent() + textPaint.ascent()) / 2, // Căn giữa
            textPaint
        )

        // Vẽ vòng "dính" (snap ring) nếu nút này là mục tiêu
        if (draggingState?.snapTargetNode?.id == node.id) {
            val snapRadius = MapViewModel.SNAP_THRESHOLD / scale
            drawCircle(
                color = Color.Green.copy(alpha = 0.5f),
                center = Offset(node.x, node.y),
                radius = snapRadius,
                style = Stroke(
                    width = 5f / scale,
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(15f / scale, 15f / scale),
                        0f
                    )
                )
            )
        }
    }
}

private fun DrawScope.drawUserPosition(position: Offset?, scale: Float) {
    if (position == null) return

    val pulseRadius = 50f / scale // Bán kính vòng tỏa
    val dotRadius = 25f / scale  // Bán kính chấm người dùng

    // Vẽ vòng tròn mờ xung quanh (mô phỏng độ chính xác)
    drawCircle(
        color = Color.Magenta.copy(alpha = 0.3f),
        center = position,
        radius = pulseRadius
    )

    // Vẽ viền trắng cho nổi bật
    drawCircle(
        color = Color.White,
        center = position,
        radius = dotRadius + 5f
    )

    // Vẽ chấm chính (Màu Tím hồng để khác biệt với Node xanh dương)
    drawCircle(
        color = Color.Magenta,
        center = position,
        radius = dotRadius
    )
}
