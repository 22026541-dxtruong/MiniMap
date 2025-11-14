package ie.app.minimap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ie.app.minimap.data.local.entity.Edge
import ie.app.minimap.data.local.entity.Floor
import ie.app.minimap.data.local.entity.FloorConnection
import ie.app.minimap.data.local.entity.Node
import ie.app.minimap.data.local.repository.EdgeRepository
import ie.app.minimap.data.local.repository.FloorConnectionRepository
import ie.app.minimap.data.local.repository.FloorRepository
import ie.app.minimap.data.local.repository.NodeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NodeMapViewModel @Inject constructor(
    private val nodeRepository: NodeRepository,
    private val floorRepository: FloorRepository,
    private val edgeRepository: EdgeRepository,
    private val floorConnectionRepository: FloorConnectionRepository
) : ViewModel() {

    // ---------------- UI STATE ------------------

    private val _selectedFloor = MutableStateFlow<Floor?>(null)
    val selectedFloor: StateFlow<Floor?> = _selectedFloor.asStateFlow()

    private val _nodes = MutableStateFlow<List<Node>>(emptyList())
    val nodes: StateFlow<List<Node>> = _nodes.asStateFlow()

    private val _edges = MutableStateFlow<List<Edge>>(emptyList())
    val edges: StateFlow<List<Edge>> = _edges.asStateFlow()

    private val _connections = MutableStateFlow<List<FloorConnection>>(emptyList())
    val connections: StateFlow<List<FloorConnection>> = _connections.asStateFlow()

    // ---------------- FLOOR ----------------------

    fun loadFloor(floorId: Long) {
        viewModelScope.launch {
            floorRepository.getFloorById(floorId)
                .collect { floor ->
                    _selectedFloor.value = floor
                }
        }

        loadNodes(floorId)
        loadEdges(floorId)
        loadFloorConnections(floorId)
    }

    // ---------------- NODE ----------------------

    fun loadNodes(floorId: Long) {
        viewModelScope.launch {
            nodeRepository.getNodeByFloor(floorId)
                .collect { _nodes.value = it }
        }
    }

    fun insertNode(node: Node) = viewModelScope.launch {
        nodeRepository.insertNode(node)
    }

    fun updateNode(node: Node) = viewModelScope.launch {
        nodeRepository.updateNode(node)
    }

    fun deleteNode(node: Node) = viewModelScope.launch {
        nodeRepository.deleteNode(node)
    }

    // ---------------- EDGE ----------------------

    fun loadEdges(floorId: Long) {
        viewModelScope.launch {
            edgeRepository.getEdgesByFloor(floorId)
                .collect { _edges.value = it }
        }
    }

    fun insertEdge(edge: Edge) = viewModelScope.launch {
        edgeRepository.insert(edge)
    }

    fun updateEdge(edge: Edge) = viewModelScope.launch {
        edgeRepository.update(edge)
    }

    fun deleteEdge(edge: Edge) = viewModelScope.launch {
        edgeRepository.delete(edge)
    }


    // ---------------- FLOOR CONNECTION ----------------------

    fun loadFloorConnections(floorId: Long) {
        viewModelScope.launch {
            floorConnectionRepository.getConnectionsByFloor(floorId)
                .collect { _connections.value = it }
        }
    }

    fun insertFloorConnection(connection: FloorConnection) =
        viewModelScope.launch {
            floorConnectionRepository.insertFloorConnection(connection)
        }

    fun updateFloorConnection(connection: FloorConnection) =
        viewModelScope.launch {
            floorConnectionRepository.updateFloorConnection(connection)
        }

    fun deleteFloorConnection(connection: FloorConnection) =
        viewModelScope.launch {
            floorConnectionRepository.deleteFloorConnection(connection)
        }
}