package ie.app.minimap.ui.screens.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ie.app.minimap.data.local.relations.NodeWithShape
import ie.app.minimap.ui.ar.ArView
import ie.app.minimap.ui.graph.BuildingsDropDownMenu
import ie.app.minimap.ui.graph.FloorsDropDownMenu
import ie.app.minimap.ui.graph.Graph
import ie.app.minimap.ui.graph.MapTopBar
import kotlinx.coroutines.launch

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
    val userPosition by viewModel.userPosition.collectAsState()
    val searchResult by viewModel.searchResult.collectAsState()

    val scaffoldState = rememberBottomSheetScaffoldState()

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val bottomExpanded by remember {
        derivedStateOf { scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded }
    }

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
                modifier = Modifier
                    .fillMaxHeight(0.78f)
            ) {
                if (uiState.selectedFloor.id != 0L) {
                    Graph(
                        venueId = venueId,
                        floorId = uiState.selectedFloor.id,
                        userPosition = userPosition,
                        centerNode = centerNode,
                        onCenterConsumed = { centerNode = null },
                        onSelectionConsumed = { selectionNode = it },
                        edit = true,
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
                        onClick = { },
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
                        onClick = { },
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
            ArView(
                editing = true,
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
                        viewModel.getNodesByLabel(it)
                    },
                    onClickNode = {
                        centerNode = it
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
                        onClick = { },
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
                        onClick = { },
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
        }
    }
}
