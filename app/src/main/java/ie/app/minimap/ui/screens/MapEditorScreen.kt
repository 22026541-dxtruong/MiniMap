package ie.app.minimap.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ie.app.minimap.ui.components.ArEditor

@Composable
fun MapEditorScreen(
    modifier: Modifier = Modifier
) {
    ArEditor(modifier = modifier.fillMaxSize())
}