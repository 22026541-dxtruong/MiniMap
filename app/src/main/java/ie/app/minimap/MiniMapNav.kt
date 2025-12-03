package ie.app.minimap

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import ie.app.minimap.ui.screens.home.HomeScreen
import ie.app.minimap.ui.screens.event.EventScreen
import ie.app.minimap.ui.screens.map.MapScreen
import ie.app.minimap.ui.screens.qrscanner.QrScannerScreen
import kotlinx.serialization.Serializable

@Serializable
data object Home : NavKey

@Serializable
data object QrScanner : NavKey

@Serializable
data class VenueDetails(val venueId: Long) : NavKey

@Serializable
data class Map(val venueId: Long) : NavKey

@Composable
fun MiniMapNav(
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
                    onQrScanClicked = { backStack.add(QrScanner) }
                )
            }
            entry<QrScanner> {
                QrScannerScreen(
                    onScannedSuccess = {
                        backStack.removeLast()
                        backStack.add(VenueDetails(it))
                    },
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<VenueDetails> { key ->
                EventScreen(
                    key.venueId,
                    onMapClicked = { backStack.add(Map(it)) },
                    onBackClicked = { backStack.removeLastOrNull() }
                )
            }
            entry<Map> { key ->
                MapScreen(
                    venueId = key.venueId,
                    onBack = { backStack.removeLastOrNull() }
                )
            }
        }
    )
}