package ie.app.minimap

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import ie.app.minimap.ui.screens.home.HomeScreen
import ie.app.minimap.ui.screens.editor.MapEditorScreen
import ie.app.minimap.ui.screens.event.EventScreen
import kotlinx.serialization.Serializable

@Serializable
data object Home : NavKey

@Serializable
data class MapEditor(val venueId: Long) : NavKey

@Serializable
data object MapViewer : NavKey

@Serializable
data class VenueDetails(val venueId: Long) : NavKey

@Composable
fun MiniMapNav(
    innerPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val backStack = rememberNavBackStack(Home)

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
//                    onClickMapEditor = { backStack.add(MapEditor) },
//                    onClickMapViewer = { backStack.add(MapViewer) },
                )
            }
            entry<VenueDetails> { key ->
                EventScreen(
                    key.venueId,
                    onMapClicked = { backStack.add(MapEditor(it)) },
                    onBackClicked = { backStack.removeLastOrNull() }
                )
            }
            entry<MapEditor> { key ->
                MapEditorScreen(
                    venueId = key.venueId,
                )
            }
            entry(MapViewer) {
//                MapViewerScreen()
            }
        }
    )
}