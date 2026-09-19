package com.cococue.omnisnap.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

data class QuadCorners(
    val topLeft: Offset,
    val topRight: Offset,
    val bottomRight: Offset,
    val bottomLeft: Offset
)

@Composable
fun DocumentCropView(
    bitmap: Bitmap,
    modifier: Modifier = Modifier,
    onCornersChanged: (QuadCorners) -> Unit
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }

    var corners by remember(bitmap) {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val marginX = w * 0.08f
        val marginY = h * 0.08f
        mutableStateOf(
            QuadCorners(
                topLeft = Offset(marginX, marginY),
                topRight = Offset(w - marginX, marginY),
                bottomRight = Offset(w - marginX, h - marginY),
                bottomLeft = Offset(marginX, h - marginY)
            )
        )
    }

    var selectedCornerIndex by remember { mutableStateOf(-1) }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(bitmap) {
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            val list = listOf(
                                corners.topLeft,
                                corners.topRight,
                                corners.bottomRight,
                                corners.bottomLeft
                            )
                            val threshold = 80f
                            val index = list.indexOfFirst { (it - startOffset).getDistance() < threshold }
                            selectedCornerIndex = index
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (selectedCornerIndex != -1) {
                                val current = when (selectedCornerIndex) {
                                    0 -> corners.topLeft
                                    1 -> corners.topRight
                                    2 -> corners.bottomRight
                                    else -> corners.bottomLeft
                                }
                                val newOffset = current + dragAmount
                                val newCorners = when (selectedCornerIndex) {
                                    0 -> corners.copy(topLeft = newOffset)
                                    1 -> corners.copy(topRight = newOffset)
                                    2 -> corners.copy(bottomRight = newOffset)
                                    else -> corners.copy(bottomLeft = newOffset)
                                }
                                corners = newCorners
                                onCornersChanged(newCorners)
                            }
                        },
                        onDragEnd = {
                            selectedCornerIndex = -1
                        }
                    )
                }
        ) {
            // Draw original bitmap scaled to canvas
            drawImage(imageBitmap)

            // Draw quadrilateral connecting path
            val quadPath = Path().apply {
                moveTo(corners.topLeft.x, corners.topLeft.y)
                lineTo(corners.topRight.x, corners.topRight.y)
                lineTo(corners.bottomRight.x, corners.bottomRight.y)
                lineTo(corners.bottomLeft.x, corners.bottomLeft.y)
                close()
            }

            drawPath(
                path = quadPath,
                color = Color(0xFF2563EB),
                style = Stroke(width = 4.dp.toPx())
            )

            // Draw corner drag handle circles
            val handleRadius = 14.dp.toPx()
            listOf(
                corners.topLeft,
                corners.topRight,
                corners.bottomRight,
                corners.bottomLeft
            ).forEach { point ->
                drawCircle(
                    color = Color.White,
                    radius = handleRadius,
                    center = point
                )
                drawCircle(
                    color = Color(0xFF2563EB),
                    radius = handleRadius,
                    center = point,
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }
    }
}

object PerspectiveTransformUtils {

    /**
     * Perform perspective crop warping using Matrix.setPolyToPoly
     */
    fun cropPerspective(
        srcBitmap: Bitmap,
        corners: QuadCorners
    ): Bitmap {
        val widthA = (corners.topRight - corners.topLeft).getDistance()
        val widthB = (corners.bottomRight - corners.bottomLeft).getDistance()
        val targetWidth = maxOf(widthA, widthB).coerceAtLeast(100f)

        val heightA = (corners.bottomLeft - corners.topLeft).getDistance()
        val heightB = (corners.bottomRight - corners.topRight).getDistance()
        val targetHeight = maxOf(heightA, heightB).coerceAtLeast(100f)

        val srcPoints = floatArrayOf(
            corners.topLeft.x, corners.topLeft.y,
            corners.topRight.x, corners.topRight.y,
            corners.bottomRight.x, corners.bottomRight.y,
            corners.bottomLeft.x, corners.bottomLeft.y
        )

        val dstPoints = floatArrayOf(
            0f, 0f,
            targetWidth, 0f,
            targetWidth, targetHeight,
            0f, targetHeight
        )

        val matrix = android.graphics.Matrix()
        matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)

        val destBitmap = Bitmap.createBitmap(
            targetWidth.toInt(),
            targetHeight.toInt(),
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(destBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(srcBitmap, matrix, paint)

        return destBitmap
    }
}
