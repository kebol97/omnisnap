package com.cococue.omnisnap.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

data class QuadCorners(
    val topLeft: Offset = Offset(0.05f, 0.05f),
    val topRight: Offset = Offset(0.95f, 0.05f),
    val bottomRight: Offset = Offset(0.95f, 0.95f),
    val bottomLeft: Offset = Offset(0.05f, 0.95f)
)

@Composable
fun DocumentCropView(
    bitmap: Bitmap,
    modifier: Modifier = Modifier,
    initialCorners: QuadCorners = QuadCorners(),
    onCornersChanged: (QuadCorners) -> Unit
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }

    // Normalized corners (0.0f .. 1.0f relative to bitmap dimensions)
    var corners by remember(bitmap) { mutableStateOf(initialCorners) }
    var selectedCornerIndex by remember { mutableStateOf(-1) }

    // Notify initial corners on load
    LaunchedEffect(corners) {
        onCornersChanged(corners)
    }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(bitmap) {
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            val canvasWidth = size.width.toFloat()
                            val canvasHeight = size.height.toFloat()
                            val bmpWidth = bitmap.width.toFloat()
                            val bmpHeight = bitmap.height.toFloat()

                            val scale = minOf(canvasWidth / bmpWidth, canvasHeight / bmpHeight)
                            val drawWidth = bmpWidth * scale
                            val drawHeight = bmpHeight * scale

                            val offsetX = (canvasWidth - drawWidth) / 2f
                            val offsetY = (canvasHeight - drawHeight) / 2f

                            fun normToScreen(norm: Offset) = Offset(
                                x = offsetX + norm.x * drawWidth,
                                y = offsetY + norm.y * drawHeight
                            )

                            val list = listOf(
                                normToScreen(corners.topLeft),
                                normToScreen(corners.topRight),
                                normToScreen(corners.bottomRight),
                                normToScreen(corners.bottomLeft)
                            )
                            val threshold = 90f
                            selectedCornerIndex = list.indexOfFirst { (it - startOffset).getDistance() < threshold }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (selectedCornerIndex != -1) {
                                val canvasWidth = size.width.toFloat()
                                val canvasHeight = size.height.toFloat()
                                val bmpWidth = bitmap.width.toFloat()
                                val bmpHeight = bitmap.height.toFloat()

                                val scale = minOf(canvasWidth / bmpWidth, canvasHeight / bmpHeight)
                                val drawWidth = bmpWidth * scale
                                val drawHeight = bmpHeight * scale

                                val offsetX = (canvasWidth - drawWidth) / 2f
                                val offsetY = (canvasHeight - drawHeight) / 2f

                                fun normToScreen(norm: Offset) = Offset(
                                    x = offsetX + norm.x * drawWidth,
                                    y = offsetY + norm.y * drawHeight
                                )

                                fun screenToNorm(screen: Offset) = Offset(
                                    x = ((screen.x - offsetX) / drawWidth).coerceIn(0.0f, 1.0f),
                                    y = ((screen.y - offsetY) / drawHeight).coerceIn(0.0f, 1.0f)
                                )

                                val currentNorm = when (selectedCornerIndex) {
                                    0 -> corners.topLeft
                                    1 -> corners.topRight
                                    2 -> corners.bottomRight
                                    else -> corners.bottomLeft
                                }
                                val currentScreen = normToScreen(currentNorm)
                                val newScreen = currentScreen + dragAmount
                                val newNorm = screenToNorm(newScreen)

                                val updated = when (selectedCornerIndex) {
                                    0 -> corners.copy(topLeft = newNorm)
                                    1 -> corners.copy(topRight = newNorm)
                                    2 -> corners.copy(bottomRight = newNorm)
                                    else -> corners.copy(bottomLeft = newNorm)
                                }
                                corners = updated
                                onCornersChanged(updated)
                            }
                        },
                        onDragEnd = {
                            selectedCornerIndex = -1
                        }
                    )
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val bmpWidth = bitmap.width.toFloat()
            val bmpHeight = bitmap.height.toFloat()

            val scale = minOf(canvasWidth / bmpWidth, canvasHeight / bmpHeight)
            val drawWidth = bmpWidth * scale
            val drawHeight = bmpHeight * scale

            val offsetX = (canvasWidth - drawWidth) / 2f
            val offsetY = (canvasHeight - drawHeight) / 2f

            fun normToScreen(norm: Offset) = Offset(
                x = offsetX + norm.x * drawWidth,
                y = offsetY + norm.y * drawHeight
            )

            // Draw original bitmap centered with aspect ratio preserved
            drawImage(
                image = imageBitmap,
                dstOffset = IntOffset(offsetX.toInt(), offsetY.toInt()),
                dstSize = IntSize(drawWidth.toInt(), drawHeight.toInt())
            )

            val sTL = normToScreen(corners.topLeft)
            val sTR = normToScreen(corners.topRight)
            val sBR = normToScreen(corners.bottomRight)
            val sBL = normToScreen(corners.bottomLeft)

            // Draw dark dimming overlay outside document quad
            val quadPath = Path().apply {
                moveTo(sTL.x, sTL.y)
                lineTo(sTR.x, sTR.y)
                lineTo(sBR.x, sBR.y)
                lineTo(sBL.x, sBL.y)
                close()
            }

            val fullCanvasPath = Path().apply {
                addRect(androidx.compose.ui.geometry.Rect(0f, 0f, canvasWidth, canvasHeight))
            }

            val dimPath = Path().apply {
                op(fullCanvasPath, quadPath, PathOperation.Difference)
            }

            drawPath(
                path = dimPath,
                color = Color.Black.copy(alpha = 0.5f)
            )

            // Draw cyan boundary quad line
            drawPath(
                path = quadPath,
                color = Color(0xFF00E5FF),
                style = Stroke(width = 3.dp.toPx())
            )

            // Draw corner drag handle circles
            val handleRadius = 14.dp.toPx()
            val handlePoints = listOf(sTL, sTR, sBR, sBL)

            handlePoints.forEachIndexed { index, point ->
                val isSelected = index == selectedCornerIndex
                val ringColor = if (isSelected) Color(0xFFFFD600) else Color(0xFF00E5FF)

                // Outer Ring
                drawCircle(
                    color = ringColor,
                    radius = handleRadius + 4.dp.toPx(),
                    center = point
                )
                // Inner White Core
                drawCircle(
                    color = Color.White,
                    radius = handleRadius,
                    center = point
                )
                // Inner Accent Dot
                drawCircle(
                    color = ringColor,
                    radius = handleRadius - 6.dp.toPx(),
                    center = point
                )
            }
        }
    }
}

object PerspectiveTransformUtils {

    /**
     * Perform perspective crop warping using Matrix.setPolyToPoly
     * with normalized corners mapped to true source bitmap pixels.
     */
    fun cropPerspective(
        srcBitmap: Bitmap,
        corners: QuadCorners
    ): Bitmap {
        val w = srcBitmap.width.toFloat()
        val h = srcBitmap.height.toFloat()

        // Map normalized coordinates (0.0 .. 1.0) to true bitmap pixel coordinates
        val pTL = Offset(corners.topLeft.x * w, corners.topLeft.y * h)
        val pTR = Offset(corners.topRight.x * w, corners.topRight.y * h)
        val pBR = Offset(corners.bottomRight.x * w, corners.bottomRight.y * h)
        val pBL = Offset(corners.bottomLeft.x * w, corners.bottomLeft.y * h)

        val widthTop = (pTR - pTL).getDistance()
        val widthBottom = (pBR - pBL).getDistance()
        val targetWidth = maxOf(widthTop, widthBottom).toInt().coerceAtLeast(200)

        val heightLeft = (pBL - pTL).getDistance()
        val heightRight = (pBR - pTR).getDistance()
        val targetHeight = maxOf(heightLeft, heightRight).toInt().coerceAtLeast(200)

        val srcPoints = floatArrayOf(
            pTL.x, pTL.y,
            pTR.x, pTR.y,
            pBR.x, pBR.y,
            pBL.x, pBL.y
        )

        val dstPoints = floatArrayOf(
            0f, 0f,
            targetWidth.toFloat(), 0f,
            targetWidth.toFloat(), targetHeight.toFloat(),
            0f, targetHeight.toFloat()
        )

        val matrix = android.graphics.Matrix()
        matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)

        val destBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(destBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(srcBitmap, matrix, paint)

        return destBitmap
    }
}
