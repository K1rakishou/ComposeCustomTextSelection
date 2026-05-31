package com.github.k1rakishou.composecustomtextselection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class AnimatedSelectionHandlePainter(
  val isLeftHandle: Boolean,
  private val bitmap: ImageBitmap,
  private val frameCount: Int,
  private val frameSize: Int,
  private val size: Int,
  private val frameDurationMs: Long,
) : Painter() {

  private var currentFrame by mutableIntStateOf(0)

  override val intrinsicSize = Size(size.toFloat(), size.toFloat())

  override fun DrawScope.onDraw() {
    drawIntoCanvas { canvas ->
      val srcLeft = currentFrame * frameSize
      val scaleX = if (isLeftHandle) -1f else 1f

      scale(scaleX = scaleX, scaleY = 1f) {
        canvas.drawImageRect(
          image = bitmap,
          srcOffset = IntOffset(srcLeft, 0),
          srcSize = IntSize(frameSize, frameSize),
          dstOffset = IntOffset(0, 0),
          dstSize = IntSize(size.width.toInt(), size.height.toInt()),
          paint = Paint()
        )
      }
    }
  }

  suspend fun animate() {
    coroutineScope {
      while (isActive) {
        delay(frameDurationMs)
        currentFrame = (currentFrame + 1) % frameCount
      }
    }
  }
}