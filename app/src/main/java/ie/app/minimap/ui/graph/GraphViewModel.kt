package ie.app.minimap.ui.graph

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ie.app.minimap.data.local.entity.Edge
import ie.app.minimap.data.local.entity.Node
import ie.app.minimap.data.local.entity.Shape
import ie.app.minimap.data.local.relations.BoothWithVendor
import ie.app.minimap.data.local.relations.NodeWithShape
import ie.app.minimap.data.local.repository.InfoRepository
import ie.app.minimap.data.local.repository.MapRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

// --- LỚP DỮ LIỆU ---

enum class ResizeHandle {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
    TOP, BOTTOM, LEFT, RIGHT
}

enum class EditorMode {
    MOVE,    // Chế độ di chuyển Node
    CONNECT,  // Chế độ nối dây
    SCALE,
    DELETE,
    NONE
}

data class AlignmentGuide(
    val isVertical: Boolean,
    val coordinate: Float,
    val start: Float,
    val end: Float
)

data class DraggingState(
    val startNode: Node,
    val currentPosition: Offset,
    val snapTargetNode: Node? = null
)

data class ResizingState(
    val handle: ResizeHandle,
    val nodeWithShape: NodeWithShape,
    val startDragPosition: Offset
)

data class MovingState(
    val nodeWithShape: NodeWithShape,
    val initialPosition: Offset,
    val startDragPosition: Offset
)

sealed class Selection {
    data object None : Selection()
    data class NodeSelected(val nodeWithShape: NodeWithShape) : Selection()
    data class EdgeSelected(val edge: Edge) : Selection()
}

data class GraphUiState(
    val isLoading: Boolean = false,
    val error: String? = null
)

// --- VIEWMODEL QUẢN LÝ TRẠNG THÁI ---

@HiltViewModel
class GraphViewModel @Inject constructor(
    private val mapRepository: MapRepository,
    private val infoRepository: InfoRepository
) : ViewModel() {

    companion object {
        const val EDGE_TAP_THRESHOLD = 30f
        const val HANDLE_RADIUS = 30f
        const val MIN_SIZE = 20f
        const val ALIGNMENT_THRESHOLD = 15f
        const val DOT_RADIUS = 5f
        const val DOT_CONNECTION_RADIUS = 50f // Tăng bán kính vùng chạm để dễ nối dây hơn
    }

    private val _uiState = MutableStateFlow(GraphUiState(isLoading = true))
    val uiState: StateFlow<GraphUiState> = _uiState.asStateFlow()

    private val _nodes = MutableStateFlow<List<NodeWithShape>>(emptyList())
    val nodes: StateFlow<List<NodeWithShape>> = _nodes.asStateFlow()

    private val _edges = MutableStateFlow<List<Edge>>(emptyList())
    val edges: StateFlow<List<Edge>> = _edges.asStateFlow()

    private val _editorMode = MutableStateFlow(EditorMode.NONE)
    val editorMode: StateFlow<EditorMode> = _editorMode.asStateFlow()

    private val _draggingState = MutableStateFlow<DraggingState?>(null)
    val draggingState: StateFlow<DraggingState?> = _draggingState.asStateFlow()

    private val _resizingState = MutableStateFlow<ResizingState?>(null)
    private val _movingState = MutableStateFlow<MovingState?>(null)

    private val _alignmentGuides = MutableStateFlow<List<AlignmentGuide>>(emptyList())
    val alignmentGuides: StateFlow<List<AlignmentGuide>> = _alignmentGuides.asStateFlow()

    private val _scale = MutableStateFlow(1f)
    val scale: StateFlow<Float> = _scale.asStateFlow()

    private val _offset = MutableStateFlow(Offset.Zero)
    val offset: StateFlow<Offset> = _offset.asStateFlow()

    private val _rotation = MutableStateFlow(0f)
    val rotation: StateFlow<Float> = _rotation.asStateFlow()

    private val _isPanning = MutableStateFlow(false)

    private val _selection = MutableStateFlow<Selection>(Selection.None)
    val selection: StateFlow<Selection> = _selection.asStateFlow()

    private var _viewSize = IntSize.Zero
    private var _isInitialized = false

    // Job để quản lý animation di chuyển camera
    private var centerAnimationJob: Job? = null

    val availableColors = listOf(
        0xFF3B82F6, 0xFFEF4444, 0xFF10B981,
        0xFFF59E0B, 0xFF8B5CF6, 0xFFEC4899, 0xFF6B7280
    )

    fun loadGraph(floorId: Long) {
        viewModelScope.launch {
            _uiState.value = GraphUiState(isLoading = true)
            mapRepository.getFloorWithNodesAndEdgeByFloorId(floorId).collect {
                _nodes.value = it.nodeWithShapes
                _edges.value = it.edges
                _uiState.value = GraphUiState(isLoading = false)
            }
        }
    }

    // --- Quản lý Khởi tạo ---

    fun setScreenSize(size: IntSize) {
        if (_viewSize == size) return
        _viewSize = size
        if (!_isInitialized && size.width > 0 && size.height > 0) {
            _offset.value = Offset(size.width / 2f, size.height / 2f)
            _isInitialized = true
//            if (_nodes.value.isEmpty()) {
//                addNode(Offset.Zero, NodeShape.DOT) // Mặc định thêm DOT ở tâm
//            }
        }
    }

    fun toggleMode(editorMode: EditorMode) {
        _editorMode.update { editorMode }
//        clearSelection()
    }

    fun centerOnNode(offset: Offset) {
        val viewWidth = _viewSize.width.toFloat()
        val viewHeight = _viewSize.height.toFloat()
        if (viewWidth <= 0 || viewHeight <= 0) return

        // Huỷ animation cũ nếu đang chạy
        centerAnimationJob?.cancel()

        centerAnimationJob = viewModelScope.launch {
            val startOffset = _offset.value
            val screenCenter = Offset(viewWidth / 2, viewHeight / 2)

            // Tính toán Offset mục tiêu
            // Công thức: Màn hình = Rotate(World * Scale) + Offset
            // => Offset = Màn hình - Rotate(World * Scale)
            // Ta muốn Node nằm ở giữa màn hình (Màn hình = screenCenter)

            val scaledNodePos = offset * _scale.value
            val rotatedNodePos = rotatePoint(scaledNodePos, _rotation.value)
            val targetOffset = screenCenter - rotatedNodePos

            val duration = 400L // 400ms animation
            val startTime = System.currentTimeMillis()

            while (isActive) {
                val currentTime = System.currentTimeMillis()
                val elapsed = currentTime - startTime
                val rawProgress = (elapsed / duration.toFloat()).coerceIn(0f, 1f)

                // Hàm easing (Ease Out Cubic) cho mượt
                val t = 1 - (1 - rawProgress).pow(3)

                // Nội suy tuyến tính (Lerp)
                _offset.value = startOffset + (targetOffset - startOffset) * t

                if (rawProgress >= 1f) break
                delay(16) // ~60fps frame time
            }
        }
    }

    // --- Quản lý Nút (Node) ---
    fun addShapeNearNode() {
        val node = _selection.value as? Selection.NodeSelected ?: return
        if (node.nodeWithShape.shape != null) return
        val newShape = Shape(
            nodeId = node.nodeWithShape.node.id,
            centerX = node.nodeWithShape.node.x,
            centerY = node.nodeWithShape.node.y,
            width = 120f,
            height = 80f,
            shape = Shape.Companion.ShapeType.RECTANGLE,
            color = availableColors[0]
        )

        viewModelScope.launch {
            mapRepository.upsertShape(newShape)
        }

        // 🔥 QUAN TRỌNG
        _selection.value = Selection.NodeSelected(
            node.nodeWithShape.copy(shape = newShape)
        )
    }

    fun deleteShapeNearNode() {
        val node = _selection.value as? Selection.NodeSelected ?: return
        if (node.nodeWithShape.shape == null) return
        viewModelScope.launch {
            mapRepository.deleteShape(node.nodeWithShape.shape)
        }

        clearSelection()
    }

    fun updateSelection(selection: Selection) {
        _selection.value = selection
    }

    fun updateSelectedNodeShape(shape: Shape.Companion.ShapeType) {
        val selection = _selection.value as? Selection.NodeSelected ?: return
        _nodes.value.forEach {
            if (it.node.id == selection.nodeWithShape.node.id && it.shape != null) {
                viewModelScope.launch {
                    mapRepository.upsertShape(
                        it.shape.copy(
                            shape = shape,
                        )
                    )
                }
                _selection.value = Selection.NodeSelected(
                    it.copy(
                        shape = it.shape.copy(
                            shape = shape,
                        )
                    )
                )
            }
        }
    }

    fun updateSelectedNodeColor(color: Long) {
        val selection = _selection.value as? Selection.NodeSelected ?: return
        _nodes.value.forEach {
            if (it.node.id == selection.nodeWithShape.node.id && it.shape != null) {
                viewModelScope.launch {
                    mapRepository.upsertShape(
                        it.shape.copy(
                            color = color
                        )
                    )
                }
                _selection.value = Selection.NodeSelected(
                    it.copy(
                        shape = it.shape.copy(
                            color = color
                        )
                    )
                )
            }
        }
    }

    suspend fun getBoothWithVendor(nodeId: Long) = infoRepository.getBoothWithVendorByNodeId(nodeId)

    fun getNodeById(id: Long): NodeWithShape? = _nodes.value.find { it.node.id == id }

    // --- Quản lý Cạnh & Xoá ---

    fun deleteSelection() {
        when (val currentSelection = _selection.value) {
            is Selection.EdgeSelected -> {
                viewModelScope.launch {
                    mapRepository.deleteEdge(currentSelection.edge)
                }
            }

            is Selection.NodeSelected -> {
                viewModelScope.launch {
                    mapRepository.deleteNode(currentSelection.nodeWithShape.node)
//                    mapRepository.deleteEdgesByNodeId(currentSelection.node.id)
                }
            }

            Selection.None -> {
                // Không làm gì
            }
        }
        // Bỏ chọn sau khi xoá
        _selection.value = Selection.None
    }

    private fun clearSelection() {
        _selection.value = Selection.None
        _editorMode.value = EditorMode.NONE
    }

    // --- Xử lý Transform (Zoom/Pan/Rotate) ---

    private fun rotatePoint(point: Offset, degrees: Float): Offset {
        val radians = Math.toRadians(degrees.toDouble())
        val cos = cos(radians)
        val sin = sin(radians)
        val newX = point.x * cos - point.y * sin
        val newY = point.x * sin + point.y * cos
        return Offset(newX.toFloat(), newY.toFloat())
    }

    private fun screenToWorld(screenPos: Offset): Offset {
        val translated = screenPos - _offset.value
        val rotated = rotatePoint(translated, -_rotation.value)
        return rotated / _scale.value
    }

    fun onTransform(centroid: Offset, pan: Offset, zoom: Float, rotationChange: Float) {
        if (_resizingState.value != null || _movingState.value != null || _draggingState.value != null) {
            return
        }
        centerAnimationJob?.cancel()

        val oldScreenPos = centroid - pan
        val pivotWorldPos = screenToWorld(oldScreenPos)
        val newScale = (_scale.value * zoom).coerceIn(0.1f, 10f)
        val newRotation = _rotation.value + rotationChange
        _scale.value = newScale
        _rotation.value = newRotation
        val scaledWorldVector = pivotWorldPos * newScale
        val rotatedScaledWorldVector = rotatePoint(scaledWorldVector, newRotation)
        _offset.value = centroid - rotatedScaledWorldVector
    }

    fun zoom(factor: Float) {
        centerAnimationJob?.cancel()

        val centerX = _viewSize.width / 2f
        val centerY = _viewSize.height / 2f
        onTransform(
            centroid = Offset(centerX, centerY),
            pan = Offset.Zero,
            zoom = factor,
            rotationChange = 0f
        )
    }

    // --- Xử lý Cử chỉ (Tap & Drag) ---
    fun onTap(screenOffset: Offset): Selection {
        val worldOffset = screenToWorld(screenOffset)

        val currentSelection = _selection.value
        if (currentSelection is Selection.NodeSelected) {
            val selectedNode = getNodeById(currentSelection.nodeWithShape.node.id)
            if (selectedNode != null) {
                if (getTappedHandle(worldOffset, selectedNode) != null)
                    return Selection.NodeSelected(selectedNode)
            }
        }

        // Ưu tiên chọn dot để nối dây nếu ở chế độ Connect, nhưng ở đây chỉ select
        val tappedNode = findTappedNode(worldOffset)
        if (tappedNode != null) {
            _selection.value = Selection.NodeSelected(tappedNode)
            if (tappedNode.shape == null) {
                _editorMode.value = EditorMode.CONNECT
            }
            return Selection.NodeSelected(tappedNode)
        }
        val tappedEdge = findTappedEdge(worldOffset)
        if (tappedEdge != null) {
            _selection.value = Selection.EdgeSelected(tappedEdge)
            return Selection.EdgeSelected(tappedEdge)
        }
        clearSelection()
        return Selection.None
    }

    fun onDragStart(screenOffset: Offset) {
        centerAnimationJob?.cancel()

        val worldOffset = screenToWorld(screenOffset)

        // 1. Kiểm tra Resize (Ưu tiên cao nhất nếu đang chọn và ở chế độ Move)
        if (_editorMode.value == EditorMode.SCALE) {
            val currentSelection = _selection.value
            if (currentSelection is Selection.NodeSelected) {
                val selectedNode = getNodeById(currentSelection.nodeWithShape.node.id)
                if (selectedNode != null) {
                    val handle = getTappedHandle(worldOffset, selectedNode)
                    if (handle != null) {
                        _resizingState.value = ResizingState(handle, selectedNode, worldOffset)
                        _isPanning.value = false
                        return
                    }
                }
            }
        }

        // 2. Tìm Node để Move hoặc Connect
        // Logic mới:
        // - Chế độ CONNECT: Phải chạm vào DOT (tâm) mới bắt đầu kéo.
        // - Chế độ MOVE: Chạm vào body (toàn bộ hình) để di chuyển.

        val targetNode = if (_editorMode.value == EditorMode.CONNECT) {
            // Tìm node mà người dùng chạm vào DOT của nó
            _nodes.value.findLast { isPointInNode(worldOffset, it) }
        } else {
            // Move mode: Tìm node chạm vào body
            findTappedNode(worldOffset)
        }

        if (targetNode != null) {
            if (_editorMode.value == EditorMode.CONNECT) {
                _draggingState.value = DraggingState(targetNode.node, worldOffset)
            } else if (_editorMode.value == EditorMode.MOVE) {
                if (targetNode.shape != null) {
                    _movingState.value = MovingState(
                        targetNode,
                        Offset(targetNode.shape.centerX, targetNode.shape.centerY),
                        worldOffset
                    )
                }
                _selection.value = Selection.NodeSelected(targetNode)
            }
            _isPanning.value = false
        } else {
            _isPanning.value = true
            clearSelection()
        }
    }

    fun onDragGesture(change: PointerInputChange, dragAmount: Offset) {
        if (_resizingState.value != null) {
            onResize(change.position)
        } else if (_movingState.value != null) {
            onMoveNode(change.position)
        } else if (_isPanning.value) {
            _offset.update { it + dragAmount }
        } else if (_draggingState.value != null) {
            onDragEdge(change.position)
        }
    }

    fun onDragEnd(venueId: Long, floorId: Long) {
        if (_resizingState.value != null) onResizeEnd()
        if (_movingState.value != null) onMoveNodeEnd()
        _isPanning.value = false
        _alignmentGuides.value = emptyList()
        if (_draggingState.value != null) {
            onDragEdgeEnd(venueId, floorId)
        }
    }

    // --- LOGIC SNAP ALIGNMENT ---
    private fun findSnap(
        value: Float,
        targets: List<Float>,
        threshold: Float
    ): Pair<Float, Float>? {
        var minDelta = Float.MAX_VALUE
        var bestTarget = 0f
        var found = false
        for (target in targets) {
            val delta = target - value
            if (abs(delta) < threshold && abs(delta) < abs(minDelta)) {
                minDelta = delta
                bestTarget = target
                found = true
            }
        }
        return if (found) minDelta to bestTarget else null
    }

    private fun onMoveNode(screenPos: Offset) {
        val state = _movingState.value ?: return

        val movingNode = state.nodeWithShape.shape ?: return

        val currentWorldPos = screenToWorld(screenPos)
        val delta = currentWorldPos - state.startDragPosition
        val rawPosition = state.initialPosition + delta

        val scale = _scale.value
        val effectiveW = movingNode.width
        val effectiveH = movingNode.height

        val rawRect = Rect(
            offset = Offset(
                rawPosition.x - effectiveW / 2f,
                rawPosition.y - effectiveH / 2f
            ),
            size = Size(effectiveW, effectiveH)
        )
        val threshold = ALIGNMENT_THRESHOLD / scale
        val otherNodes = _nodes.value.filterNot { it.node.id != state.nodeWithShape.node.id }
        val xTargets = otherNodes.flatMap {
            if (it.shape == null) {
                listOf(
                    it.node.x,
                    it.node.x - DOT_RADIUS / scale,
                    it.node.x + DOT_RADIUS / scale
                )
            } else {
                val w = it.shape.width
                listOf(it.shape.centerX, it.shape.centerX - w / 2, it.shape.centerX + w / 2)
            }
        }
        val yTargets = otherNodes.flatMap {
            if (it.shape == null) {
                listOf(
                    it.node.y,
                    it.node.y - DOT_RADIUS / scale,
                    it.node.y + DOT_RADIUS / scale
                )
            } else {
                val h = it.shape.height
                listOf(it.shape.centerY, it.shape.centerY - h / 2, it.shape.centerY + h / 2)
            }
        }

        var snapX = 0f
        val guides = mutableListOf<AlignmentGuide>()
        val xCandidates = listOf(rawRect.center.x, rawRect.left, rawRect.right)
        var bestSnapX: Pair<Float, Float>? = null
        for (candidate in xCandidates) {
            val res = findSnap(candidate, xTargets, threshold)
            if (res != null && (bestSnapX == null || abs(res.first) < abs(bestSnapX.first))) bestSnapX =
                res
        }
        if (bestSnapX != null) {
            snapX = bestSnapX.first
            guides.add(AlignmentGuide(true, bestSnapX.second, -10000f, 10000f))
        }

        var snapY = 0f
        val yCandidates = listOf(rawRect.center.y, rawRect.top, rawRect.bottom)
        var bestSnapY: Pair<Float, Float>? = null
        for (candidate in yCandidates) {
            val res = findSnap(candidate, yTargets, threshold)
            if (res != null && (bestSnapY == null || abs(res.first) < abs(bestSnapY.first))) bestSnapY =
                res
        }
        if (bestSnapY != null) {
            snapY = bestSnapY.first
            guides.add(AlignmentGuide(false, bestSnapY.second, -10000f, 10000f))
        }

        val finalPosition = rawPosition + Offset(snapX, snapY)
        _nodes.update { nodes ->
            nodes.map {
                if (it.node.id == state.nodeWithShape.node.id) {
                    it.copy(
                        shape = it.shape!!.copy(
                            centerX = finalPosition.x,
                            centerY = finalPosition.y
                        )
                    )
                } else it
            }
        }
        _alignmentGuides.value = guides
    }

    // --- LOGIC RESIZE ---
    private fun getTappedHandle(worldPos: Offset, nodeWithShape: NodeWithShape): ResizeHandle? {
        // KHÔNG cho phép resize DOT
        if (nodeWithShape.shape == null) return null

        val scale = _scale.value
        val w = nodeWithShape.shape.width // MAP SHAPE
        val h = nodeWithShape.shape.height
        val halfW = w / 2
        val halfH = h / 2
        val hitRadiusSq = (HANDLE_RADIUS / scale).pow(2)
        val handles = mapOf(
            ResizeHandle.TOP_LEFT to Offset(
                nodeWithShape.shape.centerX - halfW,
                nodeWithShape.shape.centerY - halfH
            ),
            ResizeHandle.TOP_RIGHT to Offset(
                nodeWithShape.shape.centerX + halfW,
                nodeWithShape.shape.centerY - halfH
            ),
            ResizeHandle.BOTTOM_LEFT to Offset(
                nodeWithShape.shape.centerX - halfW,
                nodeWithShape.shape.centerY + halfH
            ),
            ResizeHandle.BOTTOM_RIGHT to Offset(
                nodeWithShape.shape.centerX + halfW,
                nodeWithShape.shape.centerY + halfH
            ),
            ResizeHandle.TOP to Offset(
                nodeWithShape.shape.centerX,
                nodeWithShape.shape.centerY - halfH
            ),
            ResizeHandle.BOTTOM to Offset(
                nodeWithShape.shape.centerX,
                nodeWithShape.shape.centerY + halfH
            ),
            ResizeHandle.LEFT to Offset(
                nodeWithShape.shape.centerX - halfW,
                nodeWithShape.shape.centerY
            ),
            ResizeHandle.RIGHT to Offset(
                nodeWithShape.shape.centerX + halfW,
                nodeWithShape.shape.centerY
            )
        )
        for ((handle, pos) in handles) {
            if ((pos - worldPos).getDistanceSquared() <= hitRadiusSq) return handle
        }
        return null
    }

    private fun onResize(screenPos: Offset) {
        val state = _resizingState.value ?: return
        val initial = state.nodeWithShape
        if (initial.shape == null) return

        val currentWorldPos = screenToWorld(screenPos)
        val scale = _scale.value
        val threshold = ALIGNMENT_THRESHOLD / scale
        val delta = currentWorldPos - state.startDragPosition

        val dWorldX = delta.x
        val dWorldY = delta.y

        val initialWorldW = initial.shape.width
        val initialWorldH = initial.shape.height
        val halfW = initialWorldW / 2f
        val halfH = initialWorldH / 2f

        val initialWorldRect = Rect(
            left = initial.shape.centerX - halfW,
            top = initial.shape.centerY - halfH,
            right = initial.shape.centerX + halfW,
            bottom = initial.shape.centerY + halfH
        )

        var newLeft = initialWorldRect.left
        var newTop = initialWorldRect.top
        var newRight = initialWorldRect.right
        var newBottom = initialWorldRect.bottom

        when (state.handle) {
            ResizeHandle.RIGHT, ResizeHandle.TOP_RIGHT, ResizeHandle.BOTTOM_RIGHT -> newRight += dWorldX
            ResizeHandle.LEFT, ResizeHandle.TOP_LEFT, ResizeHandle.BOTTOM_LEFT -> newLeft += dWorldX
            else -> {}
        }
        when (state.handle) {
            ResizeHandle.BOTTOM, ResizeHandle.BOTTOM_LEFT, ResizeHandle.BOTTOM_RIGHT -> newBottom += dWorldY
            ResizeHandle.TOP, ResizeHandle.TOP_LEFT, ResizeHandle.TOP_RIGHT -> newTop += dWorldY
            else -> {}
        }

        val otherNodes = _nodes.value.filterNot { it.node.id != initial.node.id }
        val otherXTargets = otherNodes.flatMap {
            if (it.shape == null) {
                listOf(
                    it.node.x,
                    it.node.x - DOT_RADIUS / scale,
                    it.node.x + DOT_RADIUS / scale
                )
            } else {
                val w = it.shape.width
                listOf(it.shape.centerX, it.shape.centerX - w / 2, it.shape.centerX + w / 2)
            }
        }
        val otherYTargets = otherNodes.flatMap {
            if (it.shape == null) {
                listOf(
                    it.node.y,
                    it.node.y - DOT_RADIUS / scale,
                    it.node.y + DOT_RADIUS / scale
                )
            } else {
                val h = it.shape.height
                listOf(it.shape.centerY, it.shape.centerY - h / 2, it.shape.centerY + h / 2)
            }
        }
        val guides = mutableListOf<AlignmentGuide>()
        val isMovingLeft = state.handle in listOf(
            ResizeHandle.LEFT,
            ResizeHandle.TOP_LEFT,
            ResizeHandle.BOTTOM_LEFT
        )
        val isMovingRight = state.handle in listOf(
            ResizeHandle.RIGHT,
            ResizeHandle.TOP_RIGHT,
            ResizeHandle.BOTTOM_RIGHT
        )
        val isMovingTop =
            state.handle in listOf(ResizeHandle.TOP, ResizeHandle.TOP_LEFT, ResizeHandle.TOP_RIGHT)
        val isMovingBottom = state.handle in listOf(
            ResizeHandle.BOTTOM,
            ResizeHandle.BOTTOM_LEFT,
            ResizeHandle.BOTTOM_RIGHT
        )

        if (isMovingLeft) findSnap(
            newLeft,
            otherXTargets,
            threshold
        )?.let { (d, t) -> newLeft += d; guides.add(AlignmentGuide(true, t, -10000f, 10000f)) }
        if (isMovingRight) findSnap(
            newRight,
            otherXTargets,
            threshold
        )?.let { (d, t) -> newRight += d; guides.add(AlignmentGuide(true, t, -10000f, 10000f)) }
        if (isMovingTop) findSnap(
            newTop,
            otherYTargets,
            threshold
        )?.let { (d, t) -> newTop += d; guides.add(AlignmentGuide(false, t, -10000f, 10000f)) }
        if (isMovingBottom) findSnap(
            newBottom,
            otherYTargets,
            threshold
        )?.let { (d, t) -> newBottom += d; guides.add(AlignmentGuide(false, t, -10000f, 10000f)) }

        val minWorldSize = MIN_SIZE / scale
        if (newRight - newLeft < minWorldSize) {
            if (isMovingLeft) newLeft = newRight - minWorldSize else newRight =
                newLeft + minWorldSize
        }
        if (newBottom - newTop < minWorldSize) {
            if (isMovingTop) newTop = newBottom - minWorldSize else newBottom =
                newTop + minWorldSize
        }

        val newWorldW = newRight - newLeft
        val newWorldH = newBottom - newTop
        val newWorldCenterX = newLeft + newWorldW / 2
        val newWorldCenterY = newTop + newWorldH / 2

        val finalPos = Offset(newWorldCenterX, newWorldCenterY)

//        _nodes.update { nodes ->
//            nodes.map { if (it.node.id == state.nodeId) it.copy(width = newWorldW,
//                height = newWorldH, position = finalPos) else it }
//        }
        _nodes.update { nodes ->
            nodes.map {
                if (it.node.id == initial.node.id) {
                    it.copy(
                        shape = it.shape!!.copy(
                            centerX = finalPos.x,
                            centerY = finalPos.y,
                            width = newWorldW,
                            height = newWorldH
                        )
                    )
                } else it
            }
        }
        _alignmentGuides.value = guides
    }

    // --- LOGIC DRAG EDGE ---
    private fun onDragEdge(newScreenPosition: Offset) {
        val currentDrag = _draggingState.value ?: return
        val newWorldPosition = screenToWorld(newScreenPosition)
        // Khi kéo dây, snap vào BODY của node đích để dễ trúng, nhưng logic vẽ sẽ về tâm
        val snapTarget = _nodes.value.find { node ->
            node.node.id != currentDrag.startNode.id && isPointInNode(
                newWorldPosition,
                node
            )
        }
        _draggingState.value =
            currentDrag.copy(currentPosition = newWorldPosition, snapTargetNode = snapTarget?.node)
    }

    private fun onMoveNodeEnd() {
        val state = _movingState.value ?: return
        val latestNodeWithShape = _nodes.value.find { it.node.id == state.nodeWithShape.node.id }
        val latestShape = latestNodeWithShape?.shape ?: return
        viewModelScope.launch {
            mapRepository.upsertShape(latestShape)
        }
        _movingState.value = null
    }

    private fun onResizeEnd() {
        val state = _resizingState.value ?: return
        val latestNodeWithShape = _nodes.value.find { it.node.id == state.nodeWithShape.node.id }
        val latestShape = latestNodeWithShape?.shape ?: return

        if (state.nodeWithShape.shape != null) {
            viewModelScope.launch {
                mapRepository.upsertShape(latestShape)
            }
        }
        _resizingState.value = null
    }

    private fun onDragEdgeEnd(venueId: Long, floorId: Long) {
        val currentDrag = _draggingState.value ?: return
        val targetNode = currentDrag.snapTargetNode
        if (targetNode != null) {
            val edgeExists =
                _edges.value.any { (it.fromNode == currentDrag.startNode.id && it.toNode == targetNode.id) || (it.fromNode == targetNode.id && it.toNode == currentDrag.startNode.id) }
            if (!edgeExists) {
                viewModelScope.launch {
                    mapRepository.upsertEdge(
                        Edge(
                            venueId = venueId,
                            fromNode = currentDrag.startNode.id,
                            toNode = targetNode.id,
                            floorId = floorId,
                            weight = (Offset(
                                currentDrag.startNode.x,
                                currentDrag.startNode.y
                            ) - Offset(targetNode.x, targetNode.y)).getDistance()
                        )
                    )
                }
            }
        }
        _draggingState.value = null
    }

    // --- HIT TEST ---

    // Kiểm tra chạm vào thân (Body) - Dùng cho Move/Resize/Select
    private fun isPointInShape(point: Offset, nodeWithShape: NodeWithShape): Boolean {
        if (nodeWithShape.shape == null) return false

        val w = nodeWithShape.shape.width
        val h = nodeWithShape.shape.height

        val halfW = w / 2
        val halfH = h / 2
        val localX = point.x - nodeWithShape.shape.centerX
        val localY = point.y - nodeWithShape.shape.centerY

        return when (nodeWithShape.shape.shape) {
            Shape.Companion.ShapeType.RECTANGLE -> abs(localX) <= halfW && abs(localY) <= halfH
            // Update DOT logic: Bắt buộc vùng chạm tối thiểu 40px (trên màn hình)
            //            Shape.DOT -> {
            //                val hitRadius = max(halfW, 40f / scale)
            //                (localX * localX) + (localY * localY) <= hitRadius * hitRadius
            //            }
//                Shape.Companion.ShapeType.CAPSULE -> {
//                    val radius = min(w, h) / 2
//                    if (w > h) {
//                        val distX = abs(localX) - (halfW - radius)
//                        if (distX <= 0) return abs(localY) <= radius
//                        return (distX * distX + localY * localY) <= radius * radius
//                    } else {
//                        val distY = abs(localY) - (halfH - radius)
//                        if (distY <= 0) return abs(localX) <= radius
//                        return (localX * localX + distY * distY) <= radius * radius
//                    }
//                }

            Shape.Companion.ShapeType.CIRCLE -> (localX * localX) / (halfW * halfW) + (localY * localY) / (halfH * halfH) <= 1
            Shape.Companion.ShapeType.TRIANGLE -> isPointInPolygon(
                Offset(
                    localX,
                    localY
                ), listOf(Offset(0f, -halfH), Offset(-halfW, halfH), Offset(halfW, halfH))
            )

            Shape.Companion.ShapeType.DIAMOND -> isPointInPolygon(
                Offset(
                    localX,
                    localY
                ),
                listOf(
                    Offset(0f, -halfH),
                    Offset(halfW, 0f),
                    Offset(0f, halfH),
                    Offset(-halfW, 0f)
                )
            )

            Shape.Companion.ShapeType.PENTAGON -> isPointInPolygon(
                Offset(
                    localX,
                    localY
                ), calculatePolygonVertices(5, w, h)
            )

            Shape.Companion.ShapeType.HEXAGON -> isPointInPolygon(
                Offset(
                    localX,
                    localY
                ), calculatePolygonVertices(6, w, h)
            )

        }
    }

    // Kiểm tra chạm vào DOT trung tâm (Dùng cho Connect)
    private fun isPointInNode(point: Offset, nodeWithShape: NodeWithShape): Boolean {
//        if (nodeWithShape.shape != null) return false
        val scale = _scale.value
        // Vùng chạm của Dot cố định trên màn hình (~30px), chuyển sang World Unit
        val hitRadius = DOT_CONNECTION_RADIUS / scale
        return (point - Offset(
            nodeWithShape.node.x,
            nodeWithShape.node.y
        )).getDistanceSquared() <= hitRadius.pow(2)
    }

    private fun isPointInPolygon(point: Offset, vertices: List<Offset>): Boolean {
        var intersectCount = 0
        for (i in vertices.indices) {
            val j = (i + 1) % vertices.size
            val v1 = vertices[i]
            val v2 = vertices[j]
            if ((v1.y > point.y) != (v2.y > point.y) && point.x < (v2.x - v1.x) * (point.y - v1.y) / (v2.y - v1.y) + v1.x) intersectCount++
        }
        return intersectCount % 2 == 1
    }

    private fun calculatePolygonVertices(sides: Int, w: Float, h: Float): List<Offset> {
        val vertices = mutableListOf<Offset>()
        val radiusX = w / 2
        val radiusY = h / 2
        val startAngle = -PI / 2
        for (i in 0 until sides) {
            val angle = startAngle + 2 * PI * i / sides
            vertices.add(Offset((radiusX * cos(angle)).toFloat(), (radiusY * sin(angle)).toFloat()))
        }
        return vertices
    }

    private fun findTappedNode(worldOffset: Offset): NodeWithShape? {
        return _nodes.value.findLast {
            isPointInNode(worldOffset, it) || isPointInShape(
                worldOffset,
                it
            )
        }
    }

    private fun findTappedEdge(worldOffset: Offset): Edge? {
        val currentScale = _scale.value
        val worldThresholdSq = (EDGE_TAP_THRESHOLD / currentScale).pow(2)
        return _edges.value.find { edge ->
            val fromNode = getNodeById(edge.fromNode)?.node ?: return@find false
            val p1 = Offset(fromNode.x, fromNode.y)
            val toNode = getNodeById(edge.toNode)?.node ?: return@find false
            val p2 = Offset(toNode.x, toNode.y)
            distanceToSegmentSquared(worldOffset, p1, p2) <= worldThresholdSq
        }
    }

    private fun distanceToSegmentSquared(p: Offset, v: Offset, w: Offset): Float {
        val l2 = (v - w).getDistanceSquared()
        if (l2 == 0f) return (p - v).getDistanceSquared()
        val t = ((p.x - v.x) * (w.x - v.x) + (p.y - v.y) * (w.y - v.y)) / l2
        val tClamped = t.coerceIn(0f, 1f)
        val projection = Offset(
            v.x + tClamped * (w.x - v.x),
            v.y + tClamped * (w.y - v.y)
        )
        return (p - projection).getDistanceSquared()
    }
}
