package ie.app.minimap.ui.screens.map

import android.annotation.SuppressLint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ie.app.minimap.R
import ie.app.minimap.data.local.relations.NodeWithShape
import ie.app.minimap.ui.ar.ArViewViewer
import ie.app.minimap.ui.graph.GraphViewer
import kotlinx.coroutines.launch

@SuppressLint("ContextCastToActivity")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapViewerScreen(
    venueId: Long,
    onBack: () -> Unit = {},
    viewModel: MapViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    var expandedSearch by remember { mutableStateOf(false) }
    var centerNode by remember { mutableStateOf<NodeWithShape?>(null) }
    var selectionNode by remember { mutableStateOf<NodeWithShape?>(null) }

    val animWidth = remember { Animatable(400f) }
    val animHeight = remember { Animatable(400f) }

    val uiState by viewModel.uiState.collectAsState()
    val buildings by viewModel.buildings.collectAsState()
    val floors by viewModel.floors.collectAsState()
    val nodes by viewModel.nodes.collectAsState()
    val userPosition by viewModel.userPosition.collectAsState()
    val searchResult by viewModel.searchResult.collectAsState()
    val boothWithVendor by viewModel.boothWithVendor.collectAsState()
    val pathOffsetAndNode by viewModel.pathOffsetAndNode.collectAsState()

    val scaffoldState = rememberBottomSheetScaffoldState()

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
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

    LaunchedEffect(expandedSearch) {
        if (expandedSearch) scaffoldState.bottomSheetState.partialExpand()
    }

    LaunchedEffect(selectionNode?.node?.id) {
        selectionNode?.node?.id?.let {
            viewModel.loadBoothWithVendor(it)
        }
    }

    LaunchedEffect(boothWithVendor) {
        boothWithVendor?.let {
            scaffoldState.bottomSheetState.expand()
        }
    }

    BottomSheetScaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState)
        },
        scaffoldState = scaffoldState,
        sheetPeekHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        sheetSwipeEnabled = !expandedSearch,
        sheetDragHandle = {
            boothWithVendor?.let {
                Surface(
                    modifier =
                        Modifier.padding(vertical = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Box(Modifier.size(width = 32.dp, height = 4.dp))
                }
            }
        },
        sheetContent = {
            boothWithVendor?.let { (booth, vendor) ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.4f)
                        .padding(horizontal = 16.dp)
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(booth.name, style = MaterialTheme.typography.titleLarge)
                    Text(booth.description, style = MaterialTheme.typography.bodyLarge)
                    Text(vendor.name, style = MaterialTheme.typography.titleMedium)
                    Text(vendor.description, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ArViewViewer(
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

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding(),
                horizontalAlignment = Alignment.End
            ) {
                MapTopBar(
                    expandedSearch = expandedSearch,
                    onBack = onBack,
                    onSearch = {
                        viewModel.getNodesByLabel(it)
                    },
                    onClickNode = { building, floor, node ->
                        viewModel.onBuildingSelected(building)
                        viewModel.onFloorSelected(floor)
                        centerNode = node
                        coroutineScope.launch {
                            scaffoldState.bottomSheetState.expand()
                        }
                    },
                    onExpandedChange = { expandedSearch = it },
                    searchResults = searchResult,
                )
                    if (!expandedSearch) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(if (bottomExpanded) 0.4f else 0.9f)
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.TopEnd // Khóa góc trên bên phải
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(
                                        width = with(LocalDensity.current) { animWidth.value.toDp() },
                                        height = with(LocalDensity.current) { animHeight.value.toDp() }
                                    )
                                    .clip(RoundedCornerShape(8.dp))
                            ) {
                                if (uiState.selectedFloor.id != 0L) {
                                    GraphViewer(
                                        floorId = uiState.selectedFloor.id,
                                        userPosition = userPosition,
                                        wayOffset = pathOffsetAndNode?.first,
                                        centerNode = centerNode,
                                        onCenterConsumed = { centerNode = null },
                                        onSelectionConsumed = { selectionNode = it },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                // Handle tại góc dưới bên trái
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .align(Alignment.BottomStart)
                                        .pointerInput(Unit) {
                                            detectDragGestures(
                                                onDragEnd = {
                                                    // Ví dụ: Thả tay ra thì tự động "nảy" về kích thước chẵn
                                                    coroutineScope.launch {
                                                        animWidth.animateTo(
                                                            animWidth.value,
                                                            spring(Spring.DampingRatioLowBouncy)
                                                        )
                                                    }
                                                }
                                            ) { change, dragAmount ->
                                                change.consume()
                                                coroutineScope.launch {
                                                    // Dùng snapTo để bám sát ngón tay (không trễ)
                                                    val newW =
                                                        (animWidth.value - dragAmount.x).coerceAtLeast(
                                                            400f
                                                        )
                                                    val newH =
                                                        (animHeight.value + dragAmount.y).coerceAtLeast(
                                                            400f
                                                        )

                                                    // Chạy song song để cập nhật UI
                                                    launch { animWidth.snapTo(newW) }
                                                    launch { animHeight.snapTo(newH) }
                                                }
                                            }
                                        }
                                ) {
                                    // Icon gợi ý kéo
                                    Icon(
                                        painter = painterResource(R.drawable.outline_open_in_full_24), // Thay bằng icon kéo chéo nếu có
                                        contentDescription = null,
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                }
                            }

                    }
                }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(y = -sheetOffsetDp - screenHeight - 28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FloorsDropDownMenu(
                    floors = floors,
                    selectedFloor = uiState.selectedFloor,
                    onFloorSelected = viewModel::onFloorSelected
                )
                BuildingsDropDownMenu(
                    buildings = buildings,
                    selectedBuilding = uiState.selectedBuilding,
                    onBuildingSelected = viewModel::onBuildingSelected,
                    modifier = Modifier.weight(1f)
                )
                selectionNode?.shape?.let {
                    userPosition?.let {
                        Button(onClick = {
                            viewModel.findPathToNode(selectionNode!!.node)
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.outline_directions_24),
                                contentDescription = "Way"
                            )
                            Text("Chi duong")
                        }
                    }
                }
            }
        }
    }
}
