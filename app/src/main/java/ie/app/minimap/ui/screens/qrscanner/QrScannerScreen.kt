package ie.app.minimap.ui.screens.qrscanner

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import ie.app.minimap.R

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun QrScannerScreen(
    viewModel: QrScannerViewModel = hiltViewModel(),
    onScannedSuccess: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)
    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted)
            cameraPermissionState.launchPermissionRequest()
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val surfaceRequest by viewModel.surfaceRequest.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val cropImageLauncher = rememberLauncherForActivityResult(
        contract = CropImageContract()
    ) { result ->
        if (result.isSuccessful) {
            // Cắt xong -> Gửi ảnh đã cắt cho ViewModel xử lý ngay lập tức
            viewModel.onImageCropped(context, result.uriContent)
        }
    }
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            // Chọn xong -> Mở màn hình Cắt ảnh
            val cropOptions = CropImageContractOptions(
                uri,
                CropImageOptions(
                    imageSourceIncludeGallery = false,
                    imageSourceIncludeCamera = false,
                    cropShape = CropImageView.CropShape.RECTANGLE,
                    // Cấu hình UI đẹp
                    borderLineColor = android.graphics.Color.GREEN,
                    guidelinesColor = android.graphics.Color.GREEN,
                    activityTitle = "Cắt mã QR",
                    toolbarColor = android.graphics.Color.BLACK,
                    activityMenuIconColor = android.graphics.Color.WHITE,
                    backgroundColor = android.graphics.Color.BLACK
                )
            )
            cropImageLauncher.launch(cropOptions)
        }
    }
    var isFlashOn by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.venueId) {
        if (uiState.venueId != 0L) {
            onScannedSuccess(uiState.venueId)
        }
    }

    LaunchedEffect(lifecycleOwner) {
        viewModel.bindToCamera(context.applicationContext, lifecycleOwner)
    }

    Scaffold { innerPadding ->
        Box(modifier = modifier.fillMaxSize().padding(innerPadding)) {
            surfaceRequest?.let { request ->
                CameraXViewfinder(
                    surfaceRequest = request,
                    modifier = modifier
                )
            }

            QrScannerOverlay(
                modifier = Modifier.align(Alignment.Center),
                scanWindowSize = 280f
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                    .align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {

                // Nút Flashlight
                IconButton(
                    onClick = {
                        isFlashOn = !isFlashOn
                         viewModel.toggleFlash(isFlashOn)
                    },
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.2f), CircleShape)
                        .size(56.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.outline_flashlight_on_24),
                        contentDescription = "Flash",
                        tint = if (isFlashOn) Color.Blue else Color.White
                    )
                }

                IconButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.2f), CircleShape)
                        .size(56.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.outline_imagesmode_24),
                        contentDescription = "Image source",
                        tint = Color.White
                    )
                }
            }

            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
fun QrScannerOverlay(
    modifier: Modifier = Modifier,
    scanWindowSize: Float = 250f, // Kích thước vùng quét vuông
    verticalOffset: Dp = (-50).dp
) {
    // Animation cho đường quét chạy lên xuống
    val infiniteTransition = rememberInfiniteTransition(label = "scan_line")
    val animatedY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "scan_line_y"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val centerX = size.width / 2
        val centerY = (size.height / 2) + verticalOffset.toPx()
        val windowSizePx = scanWindowSize.dp.toPx()
        val cornerLength = 20.dp.toPx()
        val cornerRadius = 12.dp.toPx()

        // 1. Vẽ vùng tối xung quanh (Background Scrim)
        // Sử dụng Path để đục lỗ ở giữa
        val overlayPath = Path().apply {
            addRect(Rect(0f, 0f, size.width, size.height))
        }
        val cutoutPath = Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    left = centerX - windowSizePx / 2,
                    top = centerY - windowSizePx / 2,
                    right = centerX + windowSizePx / 2,
                    bottom = centerY + windowSizePx / 2,
                    cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                )
            )
        }

        // Cắt vùng giữa ra khỏi vùng tối
        val finalPath = Path.combine(
            operation = androidx.compose.ui.graphics.PathOperation.Difference,
            path1 = overlayPath,
            path2 = cutoutPath
        )

        drawPath(
            path = finalPath,
            color = Color.Black.copy(alpha = 0.6f) // Màu nền tối bán trong suốt
        )

        // 2. Vẽ 4 góc bao quanh (Borders)
        val strokeWidth = 4.dp.toPx()
        val halfWindow = windowSizePx / 2
        val boxRect = Rect(
            centerX - halfWindow,
            centerY - halfWindow,
            centerX + halfWindow,
            centerY + halfWindow
        )

        // Hàm vẽ góc
        fun drawCorner(start: Offset, end: Offset) {
            drawLine(
                color = Color.Green, // Hoặc màu primary của App
                start = start,
                end = end,
                strokeWidth = strokeWidth,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }

        // Top Left
        drawCorner(boxRect.topLeft, boxRect.topLeft + Offset(cornerLength, 0f))
        drawCorner(boxRect.topLeft, boxRect.topLeft + Offset(0f, cornerLength))
        // Top Right
        drawCorner(boxRect.topRight, boxRect.topRight + Offset(-cornerLength, 0f))
        drawCorner(boxRect.topRight, boxRect.topRight + Offset(0f, cornerLength))
        // Bottom Left
        drawCorner(boxRect.bottomLeft, boxRect.bottomLeft + Offset(cornerLength, 0f))
        drawCorner(boxRect.bottomLeft, boxRect.bottomLeft + Offset(0f, -cornerLength))
        // Bottom Right
        drawCorner(boxRect.bottomRight, boxRect.bottomRight + Offset(-cornerLength, 0f))
        drawCorner(boxRect.bottomRight, boxRect.bottomRight + Offset(0f, -cornerLength))

        // 3. Vẽ đường quét (Scan Line)
        val lineY = boxRect.top + (boxRect.height * animatedY)
        drawLine(
            color = Color.Red,
            start = Offset(boxRect.left + 10f, lineY),
            end = Offset(boxRect.right - 10f, lineY),
            strokeWidth = 2.dp.toPx()
        )
    }
}
