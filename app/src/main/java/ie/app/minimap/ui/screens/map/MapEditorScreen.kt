package ie.app.minimap.ui.screens.map

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ie.app.minimap.R
import ie.app.minimap.data.local.entity.Booth
import ie.app.minimap.data.local.entity.Building
import ie.app.minimap.data.local.entity.Floor
import ie.app.minimap.data.local.entity.Node
import ie.app.minimap.data.local.entity.Vendor
import ie.app.minimap.data.local.relations.BoothWithVendor
import ie.app.minimap.data.local.relations.NodeWithShape
import ie.app.minimap.ui.ar.ArViewEditor
import ie.app.minimap.ui.graph.GraphEditor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("ContextCastToActivity")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapEditorScreen(
    venueId: Long,
    onBack: () -> Unit = {},
    viewModel: MapViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    var expandedSearch by remember { mutableStateOf(false) }
    var centerNode by remember { mutableStateOf<NodeWithShape?>(null) }
    var selectionNode by remember { mutableStateOf<NodeWithShape?>(null) }

    val uiState by viewModel.uiState.collectAsState()
    val buildings by viewModel.buildings.collectAsState()
    val floors by viewModel.floors.collectAsState()
    val nodes by viewModel.nodes.collectAsState()
    val userPosition = viewModel.userPosition.collectAsState()
    val searchResult by viewModel.searchResult.collectAsState()
    val boothWithVendor by viewModel.boothWithVendor.collectAsState()
    val allVendors by viewModel.allVendors.collectAsState()

    val scaffoldState = rememberBottomSheetScaffoldState()

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val bottomExpanded by remember {
        derivedStateOf { scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded }
    }

    var showBuildingDialog by remember { mutableStateOf(false) }
    var editingBuilding by remember { mutableStateOf<Building?>(null) }

    var showFloorDialog by remember { mutableStateOf(false) }
    var editingFloor by remember { mutableStateOf<Floor?>(null) }

    var showBoothDialog by remember { mutableStateOf(false) }
    var showRoomDialog by remember { mutableStateOf(false) }

    var showDeleteConfirm by remember { mutableStateOf<Any?>(null) }

    LaunchedEffect(venueId) {
        viewModel.loadMap(venueId)
    }

    LaunchedEffect(expandedSearch) {
        if (expandedSearch) scaffoldState.bottomSheetState.partialExpand()
    }

    LaunchedEffect(selectionNode?.node?.id) {
        selectionNode?.node?.id?.let {
            viewModel.loadBoothWithVendor(it)
        }
    }

    BottomSheetScaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState)
        },
        scaffoldState = scaffoldState,
        sheetPeekHeight = 360.dp,
        sheetSwipeEnabled = !expandedSearch,
        sheetDragHandle = {
            Surface(
                modifier =
                    Modifier.padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Box(Modifier.size(width = 32.dp, height = 4.dp))
            }
        },
        sheetContent = {
            Box(
                modifier = modifier
                    .fillMaxHeight(0.78f)
                    .navigationBarsPadding()
            ) {
                if (uiState.selectedFloor.id != 0L) {
                    GraphEditor(
                        venueId = venueId,
                        floorId = uiState.selectedFloor.id,
                        userPosition = userPosition,
                        centerNode = centerNode,
                        onCenterConsumed = { centerNode = null },
                        onSelectionConsumed = {
                            selectionNode = it
                        },
                        onEditNode = { node ->
                            selectionNode = node
                            if (node.node.type == Node.BOOTH) {
                                showBoothDialog = true
                            } else if (node.node.type == Node.ROOM) {
                                showRoomDialog = true
                            }
                        },
                        modifier = if (bottomExpanded) Modifier.fillMaxSize() else Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .align(Alignment.TopCenter)
                    )
                }
                FloorsDropDownMenu(
                    modifier = Modifier.align(Alignment.TopStart),
                    floors = floors,
                    selectedFloor = uiState.selectedFloor,
                    onFloorSelected = viewModel::onFloorSelected
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            editingFloor = null
                            showFloorDialog = true
                        },
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add Floor",
                            modifier = Modifier
                                .size(24.dp)
                                .padding(4.dp)
                        )
                    }
                    FilledTonalIconButton(
                        onClick = {
                            if (uiState.selectedFloor.id != 0L) {
                                editingFloor = uiState.selectedFloor
                                showFloorDialog = true
                            }
                        },
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit Floor",
                            modifier = Modifier
                                .size(24.dp)
                                .padding(4.dp)
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ArViewEditor(
                venueId = venueId,
                building = uiState.selectedBuilding,
                floor = uiState.selectedFloor,
                nodes = nodes,
                onMessage = { message ->
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(message)
                    }
                },
                updateUserLocation = { viewModel.updateUserLocation(it) },
                modifier = Modifier.fillMaxSize()
            )

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding(),
                horizontalAlignment = Alignment.Start
            ) {
                MapTopBar(
                    expandedSearch = expandedSearch,
                    onBack = onBack,
                    onSearch = {
                        viewModel.getNodesByLabel(it, venueId)
                    },
                    onClickNode = { building, floor, node ->
                        viewModel.onBuildingSelected(building)
                        viewModel.onFloorSelected(floor)
                        centerNode = node
                    },
                    onExpandedChange = { expandedSearch = it },
                    searchResults = searchResult,
                )
                BuildingsDropDownMenu(
                    buildings = buildings,
                    selectedBuilding = uiState.selectedBuilding,
                    onBuildingSelected = viewModel::onBuildingSelected
                ) {

                    FilledTonalIconButton(
                        onClick = {
                            editingBuilding = null
                            showBuildingDialog = true
                        },
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add Building",
                            modifier = Modifier
                                .size(24.dp)
                                .padding(4.dp)
                        )
                    }
                    FilledTonalIconButton(
                        onClick = {
                            if (uiState.selectedBuilding.id != 0L) {
                                editingBuilding = uiState.selectedBuilding
                                showBuildingDialog = true
                            }
                        },
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit Building",
                            modifier = Modifier
                                .size(24.dp)
                                .padding(4.dp)
                        )
                    }
                }
            }
        }
    }

    // Dialogs
    if (showBuildingDialog) {
        BuildingDialog(
            building = editingBuilding,
            canDelete = buildings.size > 1,
            onDismiss = { showBuildingDialog = false },
            onConfirm = { building ->
                viewModel.upsertBuilding(building.copy(venueId = venueId))
                showBuildingDialog = false
            },
            onDelete = { building ->
                if (buildings.size <= 1) {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Phải có ít nhất 2 tòa nhà mới có thể xóa.")
                    }
                } else {
                    showDeleteConfirm = building
                }
                showBuildingDialog = false
            }
        )
    }

    if (showFloorDialog) {
        FloorDialog(
            floor = editingFloor,
            canDelete = floors.size > 1,
            onDismiss = { showFloorDialog = false },
            onConfirm = { floor ->
                viewModel.upsertFloor(
                    floor.copy(
                        venueId = venueId,
                        buildingId = uiState.selectedBuilding.id
                    )
                )
                showFloorDialog = false
            },
            onDelete = { floor ->
                if (floors.size <= 1) {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Phải có ít nhất 2 tầng mới có thể xóa.")
                    }
                } else {
                    showDeleteConfirm = floor
                }
                showFloorDialog = false
            }
        )
    }

    if (showBoothDialog && selectionNode != null && boothWithVendor != null) {
        BoothEditDialog(
            boothWithVendor = boothWithVendor!!,
            allVendors = allVendors,
            onDismiss = { showBoothDialog = false },
            onConfirm = { booth, vendor ->
                viewModel.upsertBoothWithVendor(booth, vendor)
                viewModel.updateNodeLabel(selectionNode!!, booth.name)
                showBoothDialog = false
            }
        )
    }

    if (showRoomDialog && selectionNode != null) {
        RoomEditDialog(
            nodeWithShape = selectionNode!!,
            onDismiss = { showRoomDialog = false },
            onConfirm = { label ->
                viewModel.updateNodeLabel(selectionNode!!, label)
                selectionNode =
                    selectionNode!!.copy(shape = selectionNode!!.shape?.copy(label = label))
                showRoomDialog = false
            }
        )
    }

    if (showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Confirm Delete") },
            text = {
                val name = when (val item = showDeleteConfirm) {
                    is Building -> item.name
                    is Floor -> item.name
                    else -> ""
                }
                Text("Are you sure you want to delete '$name'?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (val item = showDeleteConfirm) {
                            is Building -> viewModel.deleteBuilding(item)
                            is Floor -> viewModel.deleteFloor(item)
                        }
                        showDeleteConfirm = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoothEditDialog(
    boothWithVendor: BoothWithVendor,
    allVendors: List<Vendor>,
    onDismiss: () -> Unit,
    onConfirm: (Booth, Vendor) -> Unit
) {
    var boothName by remember { mutableStateOf(boothWithVendor.booth.name) }
    var boothDescription by remember { mutableStateOf(boothWithVendor.booth.description) }
    var boothCategory by remember { mutableStateOf(boothWithVendor.booth.category) }

    var vendorId by remember { mutableLongStateOf(boothWithVendor.vendor.id) }
    var vendorName by remember { mutableStateOf(boothWithVendor.vendor.name) }
    var vendorDescription by remember { mutableStateOf(boothWithVendor.vendor.description) }
    var vendorContact by remember { mutableStateOf(boothWithVendor.vendor.contactInfo) }

    var vendorExpanded by remember { mutableStateOf(false) }
    val filteredVendors = remember(vendorName, allVendors) {
        if (vendorName.isBlank()) allVendors
        else allVendors.filter { it.name.contains(vendorName, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Booth & Vendor") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
            ) {
                Text("Booth Info", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = boothName,
                    onValueChange = { boothName = it },
                    label = { Text("Booth Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = boothDescription,
                    onValueChange = { boothDescription = it },
                    label = { Text("Booth Description") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = boothCategory,
                    onValueChange = { boothCategory = it },
                    label = { Text("Category") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text("Vendor Info", style = MaterialTheme.typography.titleSmall)

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = vendorName,
                        onValueChange = {
                            vendorName = it
                            vendorExpanded = true
                            if (it != boothWithVendor.vendor.name) {
                                vendorId = 0L
                            }
                        },
                        label = { Text("Vendor Name") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = vendorExpanded) }
                    )

                    DropdownMenu(
                        expanded = vendorExpanded && filteredVendors.isNotEmpty(),
                        onDismissRequest = { vendorExpanded = false },
                        properties = PopupProperties(focusable = false),
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        filteredVendors.forEach { vendor ->
                            DropdownMenuItem(
                                text = { Text(vendor.name) },
                                onClick = {
                                    vendorId = vendor.id
                                    vendorName = vendor.name
                                    vendorDescription = vendor.description
                                    vendorContact = vendor.contactInfo
                                    vendorExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = vendorDescription,
                    onValueChange = { vendorDescription = it },
                    label = { Text("Vendor Description") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = vendorContact,
                    onValueChange = { vendorContact = it },
                    label = { Text("Contact Info") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val updatedVendor = Vendor(
                        id = vendorId,
                        venueId = boothWithVendor.vendor.venueId,
                        name = vendorName,
                        description = vendorDescription,
                        contactInfo = vendorContact
                    )
                    val updatedBooth = boothWithVendor.booth.copy(
                        name = boothName,
                        description = boothDescription,
                        category = boothCategory
                    )
                    onConfirm(updatedBooth, updatedVendor)
                },
                enabled = boothName.isNotBlank() && vendorName.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun RoomEditDialog(
    nodeWithShape: NodeWithShape,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var label by remember { mutableStateOf(nodeWithShape.shape?.label ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Room") },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Room Name / Label") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(label) },
                enabled = label.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun BuildingDialog(
    building: Building?,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Building) -> Unit,
    onDelete: (Building) -> Unit
) {
    var name by remember { mutableStateOf(building?.name ?: "") }
    var description by remember { mutableStateOf(building?.description ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (building == null) "Add Building" else "Edit Building") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm((building ?: Building()).copy(name = name, description = description))
                },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                if (building != null) {
                    TextButton(
                        onClick = { onDelete(building) },
                        enabled = canDelete
                    ) {
                        Text(
                            "Delete",
                            color = if (canDelete) MaterialTheme.colorScheme.error else Color.Gray
                        )
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
fun FloorDialog(
    floor: Floor?,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Floor) -> Unit,
    onDelete: (Floor) -> Unit
) {
    var name by remember { mutableStateOf(floor?.name ?: "") }
    var level by remember { mutableStateOf(floor?.level?.toString() ?: "1") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (floor == null) "Add Floor" else "Edit Floor") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Floor Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = level,
                    onValueChange = {
                        if (it.all { char -> char.isDigit() || char == '-' }) level = it
                    },
                    label = { Text("Level") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        (floor ?: Floor()).copy(
                            name = name,
                            level = level.toIntOrNull() ?: 1
                        )
                    )
                },
                enabled = name.isNotBlank() && level.toIntOrNull() != null
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                if (floor != null) {
                    TextButton(
                        onClick = { onDelete(floor) },
                        enabled = canDelete
                    ) {
                        Text(
                            "Delete",
                            color = if (canDelete) MaterialTheme.colorScheme.error else Color.Gray
                        )
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapTopBar(
    expandedSearch: Boolean = false,
    onExpandedChange: (Boolean) -> Unit = { _ -> },
    onBack: () -> Unit = {},
    onClickNode: (Building, Floor, NodeWithShape) -> Unit = { _, _, _ -> },
    onSearch: (String) -> Unit = { _ -> },
    searchResults: List<SearchResult> = emptyList(),
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            isLoading = false
            return@LaunchedEffect
        }
        delay(300)
        isLoading = true
        onSearch(query)
        isLoading = false
    }

    DockedSearchBar(
        inputField = {
            SearchBarDefaults.InputField(
                query = query,
                onQueryChange = {
                    query = it
                }, // Cập nhật query
                onSearch = { onExpandedChange(false) },
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
                        if (query.isNotBlank()) Icons.Default.Clear else Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.clickable {
                            if (query.isNotBlank()) {
                                query = ""
                                onExpandedChange(false)
                            }
                        }
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
        if (isLoading || (searchResults.isEmpty() && query.isNotBlank())) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoading -> CircularProgressIndicator()
                    else -> Text(
                        text = "No Results",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(searchResults) { result ->
                ListItem(
                    headlineContent = { Text(result.node.shape!!.label) },
                    supportingContent = { Text("${result.node.node.type}, ${result.building.name}, ${result.floor.name}") },
                    leadingContent = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onExpandedChange(false)
                            query = result.node.shape!!.label // Đặt query thành kết quả được chọn
                            onClickNode(
                                result.building,
                                result.floor,
                                result.node
                            ) // Thực hiện tìm kiếm
                            // Không cần cập nhật `expanded` vì nó tự động false khi `query` không rỗng
                        }
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
    content: @Composable (RowScope.() -> Unit) = { }
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
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surface)
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
    content: @Composable (RowScope.() -> Unit) = { }
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
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface)
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
