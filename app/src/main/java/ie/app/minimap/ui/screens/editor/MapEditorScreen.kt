package ie.app.minimap.ui.screens.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ie.app.minimap.ui.components.ArEditor
import ie.app.minimap.ui.components.MapEditor

@Composable
fun MapEditorScreen(
    viewModel: MapEditorViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val nodes by viewModel.nodes.collectAsState()
    val edges by viewModel.edges.collectAsState()
    val draggingState by viewModel.draggingState.collectAsState()
    val scale by viewModel.scale.collectAsState()
    val offset by viewModel.offset.collectAsState()
    val selection by viewModel.selection.collectAsState() // Theo dõi lựa chọn

    Box(modifier = modifier.fillMaxSize()) {
//        ArEditor(modifier = modifier.fillMaxSize())
        MapEditor(
            nodes = nodes,
            edges = edges,
            draggingState = draggingState,
            scale = scale,
            offset = offset,
            selection = selection,
            addNewNodeNearScreenCenter = viewModel::addNewNodeNearScreenCenter,
            onZoom = viewModel::onZoom,
            onTap = viewModel::onTap,
            onDragStart = viewModel::onDragStart,
            onDrag = viewModel::onDragGesture,
            onDragEnd = viewModel::onDragEnd,
            onDelete = viewModel::deleteSelection,
            onZoomButton = viewModel::zoom
        )
    }
}