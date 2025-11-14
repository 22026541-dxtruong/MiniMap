package ie.app.minimap.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun HomeScreen(
    onClickMapEditor: () -> Unit,
    onClickMapViewer: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Xóa đi làm homescreen như trong figma
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxSize()
    ) {
        Button(onClick = onClickMapEditor) {
            Text(text = "Map Editor")
        }
        Button(onClick = onClickMapViewer) {
            Text(text = "Map Viewer")
        }
    }
}