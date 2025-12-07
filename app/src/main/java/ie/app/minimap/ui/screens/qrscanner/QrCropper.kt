package ie.app.minimap.ui.screens.qrscanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

private enum class DragHandle {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
    TOP, BOTTOM, LEFT, RIGHT,
    CENTER, NONE
}

@Composable
fun QrCropper(
    imageUri: Uri,
    onCropSuccess: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    // 1. Load ảnh
    LaunchedEffect(imageUri) {
        withContext(Dispatchers.IO) {
            val stream = context.contentResolver.openInputStream(imageUri)
            bitmap = BitmapFactory.decodeStream(stream)
        }
    }

    if (bitmap != null) {
        CropContent(
            bitmap = bitmap!!,
            onCropSuccess = onCropSuccess,
            onCancel = onCancel
        )
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun CropContent(
    bitmap: Bitmap,
    onCropSuccess: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    var containerSize by remember { mutableStateOf(Size.Zero) }
    var cropRect by remember { mutableStateOf(Rect.Zero) }
    var imageDisplayRect by remember { mutableStateOf(Rect.Zero) }
    var currentHandle by remember { mutableStateOf(DragHandle.NONE) }

    val density = LocalDensity.current
    val touchRadius = with(density) { 30.dp.toPx() } // Vùng cảm ứng

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onGloballyPositioned {
                containerSize = it.size.toSize()
                if (imageDisplayRect == Rect.Zero && containerSize != Size.Zero) {
                    // Logic tính toán hiển thị ảnh Fit Center (giữ nguyên như cũ)
                    val bitmapRatio = bitmap.width.toFloat() / bitmap.height
                    val containerRatio = containerSize.width / containerSize.height
                    val width: Float
                    val height: Float
                    if (bitmapRatio > containerRatio) {
                        width = containerSize.width
                        height = width / bitmapRatio
                    } else {
                        height = containerSize.height
                        width = height * bitmapRatio
                    }
                    val left = (containerSize.width - width) / 2
                    val top = (containerSize.height - height) / 2
                    imageDisplayRect = Rect(left, top, left + width, top + height)

                    // Default crop 80%
                    val insetX = width * 0.1f
                    val insetY = height * 0.1f
                    cropRect = Rect(left + insetX, top + insetY, left + width - insetX, top + height - insetY)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        currentHandle = getHitHandle(offset, cropRect, touchRadius)
                    },
                    onDragEnd = { currentHandle = DragHandle.NONE },
                    onDragCancel = { currentHandle = DragHandle.NONE },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        cropRect = updateCropRect(cropRect, imageDisplayRect, currentHandle, dragAmount)
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (imageDisplayRect != Rect.Zero) {
                drawImage(
                    image = bitmap.asImageBitmap(),
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(bitmap.width, bitmap.height),
                    dstOffset = IntOffset(imageDisplayRect.left.toInt(), imageDisplayRect.top.toInt()),
                    dstSize = IntSize(imageDisplayRect.width.toInt(), imageDisplayRect.height.toInt())
                )
            }

            // Vẽ Scrim (vùng tối)
            val overlayPath = Path().apply { addRect(Rect(0f, 0f, size.width, size.height)) }
            val cropPath = Path().apply { addRect(cropRect) }
            clipPath(Path.combine(PathOperation.Difference, overlayPath, cropPath)) {
                drawRect(Color.Black.copy(alpha = 0.6f))
            }

            // Vẽ viền
            drawRect(
                color = Color.Green,
                topLeft = cropRect.topLeft,
                size = cropRect.size,
                style = Stroke(width = 2.dp.toPx())
            )

            // Vẽ 8 điểm Handle (4 Góc + 4 Cạnh)
            val handleColor = Color.White
            val handleRadius = 8.dp.toPx()

            // 4 Góc
            drawCircle(handleColor, handleRadius, cropRect.topLeft)
            drawCircle(handleColor, handleRadius, cropRect.topRight)
            drawCircle(handleColor, handleRadius, cropRect.bottomLeft)
            drawCircle(handleColor, handleRadius, cropRect.bottomRight)

            // 4 Cạnh (Trung điểm)
            drawCircle(handleColor, handleRadius, Offset(cropRect.left, cropRect.center.y))   // Trái
            drawCircle(handleColor, handleRadius, Offset(cropRect.right, cropRect.center.y))  // Phải
            drawCircle(handleColor, handleRadius, Offset(cropRect.center.x, cropRect.top))    // Trên
            drawCircle(handleColor, handleRadius, Offset(cropRect.center.x, cropRect.bottom)) // Dưới
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 30.dp, start = 20.dp, end = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(onClick = onCancel, colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) {
                Icon(Icons.Default.Close, null); Spacer(Modifier.width(8.dp)); Text("Hủy")
            }
            Button(onClick = {
                val result = cropBitmapFinal(bitmap, cropRect, imageDisplayRect)
                onCropSuccess(result)
            }, colors = ButtonDefaults.buttonColors(containerColor = Color.Green)) {
                Icon(Icons.Default.Check, null); Spacer(Modifier.width(8.dp)); Text("Cắt")
            }
        }
    }
}

// === LOGIC NÂNG CẤP ===

// 1. Logic Hit Test (Ưu tiên: Góc -> Cạnh -> Giữa)
private fun getHitHandle(offset: Offset, rect: Rect, radius: Float): DragHandle {
    // A. Kiểm tra 4 Góc trước (Khoảng cách điểm)
    if ((offset - rect.topLeft).getDistance() <= radius) return DragHandle.TOP_LEFT
    if ((offset - rect.topRight).getDistance() <= radius) return DragHandle.TOP_RIGHT
    if ((offset - rect.bottomLeft).getDistance() <= radius) return DragHandle.BOTTOM_LEFT
    if ((offset - rect.bottomRight).getDistance() <= radius) return DragHandle.BOTTOM_RIGHT

    // B. Kiểm tra 4 Cạnh (Khoảng cách tới đoạn thẳng)
    // Cạnh Trái: X gần left, Y nằm trong khoảng top-bottom
    val inYRange = offset.y >= rect.top - radius && offset.y <= rect.bottom + radius
    val inXRange = offset.x >= rect.left - radius && offset.x <= rect.right + radius

    if (abs(offset.x - rect.left) <= radius && inYRange) return DragHandle.LEFT
    if (abs(offset.x - rect.right) <= radius && inYRange) return DragHandle.RIGHT
    if (abs(offset.y - rect.top) <= radius && inXRange) return DragHandle.TOP
    if (abs(offset.y - rect.bottom) <= radius && inXRange) return DragHandle.BOTTOM

    // C. Kiểm tra Giữa
    if (rect.contains(offset)) return DragHandle.CENTER

    return DragHandle.NONE
}

// 2. Logic Cập nhật Tọa độ
private fun updateCropRect(
    current: Rect,
    bounds: Rect,
    handle: DragHandle,
    delta: Offset
): Rect {
    val minSize = 50f
    var l = current.left
    var t = current.top
    var r = current.right
    var b = current.bottom

    when (handle) {
        // Góc
        DragHandle.TOP_LEFT -> {
            l = (l + delta.x).coerceIn(bounds.left, r - minSize)
            t = (t + delta.y).coerceIn(bounds.top, b - minSize)
        }
        DragHandle.TOP_RIGHT -> {
            r = (r + delta.x).coerceIn(l + minSize, bounds.right)
            t = (t + delta.y).coerceIn(bounds.top, b - minSize)
        }
        DragHandle.BOTTOM_LEFT -> {
            l = (l + delta.x).coerceIn(bounds.left, r - minSize)
            b = (b + delta.y).coerceIn(t + minSize, bounds.bottom)
        }
        DragHandle.BOTTOM_RIGHT -> {
            r = (r + delta.x).coerceIn(l + minSize, bounds.right)
            b = (b + delta.y).coerceIn(t + minSize, bounds.bottom)
        }

        // Cạnh (Chỉ thay đổi 1 chiều)
        DragHandle.LEFT -> {
            l = (l + delta.x).coerceIn(bounds.left, r - minSize)
        }
        DragHandle.RIGHT -> {
            r = (r + delta.x).coerceIn(l + minSize, bounds.right)
        }
        DragHandle.TOP -> {
            t = (t + delta.y).coerceIn(bounds.top, b - minSize)
        }
        DragHandle.BOTTOM -> {
            b = (b + delta.y).coerceIn(t + minSize, bounds.bottom)
        }

        // Di chuyển cả khung
        DragHandle.CENTER -> {
            val newL = (l + delta.x).coerceIn(bounds.left, bounds.right - current.width)
            val newT = (t + delta.y).coerceIn(bounds.top, bounds.bottom - current.height)
            l = newL
            t = newT
            r = newL + current.width
            b = newT + current.height
        }
        DragHandle.NONE -> {}
    }

    return Rect(l, t, r, b)
}

// 3. Hàm map to Bitmap (Giữ nguyên như cũ)
private fun cropBitmapFinal(original: Bitmap, cropRect: Rect, imageRect: Rect): Bitmap {
    val scaleX = original.width / imageRect.width
    val scaleY = original.height / imageRect.height
    val relativeLeft = cropRect.left - imageRect.left
    val relativeTop = cropRect.top - imageRect.top
    var x = (relativeLeft * scaleX).toInt()
    var y = (relativeTop * scaleY).toInt()
    var w = (cropRect.width * scaleX).toInt()
    var h = (cropRect.height * scaleY).toInt()

    if (x < 0) x = 0; if (y < 0) y = 0
    if (x + w > original.width) w = original.width - x
    if (y + h > original.height) h = original.height - y
    if (w <= 0) w = 1; if (h <= 0) h = 1

    return Bitmap.createBitmap(original, x, y, w, h)
}
