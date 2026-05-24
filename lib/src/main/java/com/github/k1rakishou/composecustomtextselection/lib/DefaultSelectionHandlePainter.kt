package com.github.k1rakishou.composecustomtextselection.lib

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.painter.Painter

class DefaultSelectionHandlePainter(
  private val color: Color,
  private val isLeftHandle: Boolean,
  private val size: Size
) : Painter() {
  private var _cached: ImageBitmap? = null

  override fun DrawScope.onDraw() {
    val localCached = _cached
    if (localCached == null) {
      _cached = createSelectionHandleBitmap()
    }

    if (isLeftHandle) {
      drawImage(_cached!!)
    } else {
      scale(scaleX = -1f, scaleY = 1f) {
        drawImage(_cached!!)
      }
    }
  }

  override val intrinsicSize: Size
    get() = size

  // Same as Compose's default selection handle (circle + rectangle on top of it).
  private fun DrawScope.createSelectionHandleBitmap(): ImageBitmap {
    val imageBitmap = ImageBitmap(
      width = size.width.toInt(),
      height = size.height.toInt(),
      config = ImageBitmapConfig.Argb8888
    )

    val canvas = Canvas(imageBitmap)

    val path = Path()
    path.addOval(Rect(0f, 0f, size.width, size.height))
    path.addRect(Rect(size.width / 2f, 0f, size.width, size.height / 2f))

    val paint = Paint()
    paint.color = color
    canvas.drawPath(path, paint)

    return imageBitmap
  }
}