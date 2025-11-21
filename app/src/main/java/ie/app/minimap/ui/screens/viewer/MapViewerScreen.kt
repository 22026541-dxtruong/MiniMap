package ie.app.minimap.ui.screens.viewer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ie.app.minimap.ui.components.ArViewer

@Composable
fun MapViewerScreen(
    modifier: Modifier = Modifier
) {
    ArViewer(modifier = modifier.fillMaxSize())
}