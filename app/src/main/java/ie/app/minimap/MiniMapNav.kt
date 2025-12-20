package ie.app.minimap

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import ie.app.minimap.ui.screens.event.EventDetailScreen
import ie.app.minimap.ui.screens.home.HomeScreen
import ie.app.minimap.ui.screens.event.EventScreen
import ie.app.minimap.ui.screens.home.EventListScreen
import ie.app.minimap.ui.screens.map.MapEditorScreen
import ie.app.minimap.ui.screens.map.MapViewerScreen
import ie.app.minimap.ui.screens.qrscanner.QrScannerScreen
import kotlinx.serialization.Serializable

@Serializable
data object Home : NavKey

@Serializable
data object QrScanner : NavKey

@Serializable
data object EventListing : NavKey

@Serializable
data class VenueDetails(val venueId: Long) : NavKey

@Serializable
data class EventDetail(val venueId: Long) : NavKey

@Serializable
data class MapEditor(val venueId: Long) : NavKey

@Serializable
data class MapViewer(val venueId: Long) : NavKey

@Composable
fun MiniMapNav(
    modifier: Modifier = Modifier
) {
    val backStack = rememberNavBackStack(EventListing)

    NavDisplay(
        modifier = modifier,
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider {
            entry<Home> {
                HomeScreen(
                    onClickVenue = { backStack.add(VenueDetails(it)) },
                    onQrScanClicked = { backStack.add(QrScanner) }
                )
            }
            entry<EventListing> {
                EventListScreen(
                    onVenueClick = { id ->
                        backStack.add(EventDetail(venueId = id))
                    }
                )
            }
            entry<EventDetail> { key ->
                EventDetailScreen(
                    venueId = key.venueId,
                    onBackClicked = {
                        backStack.removeLastOrNull()
                    },
                    onMapClicked = { id ->
                        backStack.add(MapViewer(venueId = id))
                    }
                )
            }
            entry<QrScanner> {
                QrScannerScreen(
                    onScannedSuccess = {
                        backStack.removeLastOrNull()
                        backStack.add(VenueDetails(it))
                    },
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<VenueDetails> { key ->
                EventScreen(
                    key.venueId,
                    onMapEditClicked = { backStack.add(MapEditor(it)) },
                    onMapViewClicked = { backStack.add(MapViewer(it)) },
                    onBackClicked = { backStack.removeLastOrNull() }
                )
            }
            entry<MapEditor> { key ->
                MapEditorScreen(
                    venueId = key.venueId,
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<MapViewer> { key ->
                MapViewerScreen(
                    venueId = key.venueId,
                    onBack = { backStack.removeLastOrNull() }
                )
            }
        }
    )
}