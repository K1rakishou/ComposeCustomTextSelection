package com.github.k1rakishou.composecustomtextselection.lib

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.painter.Painter

data class SelectionHandle(
  val textOffset: Int = -1,
  val charBBox: Rect = Rect.Zero,
  val painter: Painter? = null
) {
  fun leftHandleBBox(): Rect? {
    val bottomLeft = charBBox.bottomLeft
    if (bottomLeft.isUnspecified) {
      return null
    }

    val size = painter?.intrinsicSize
      ?: return null

    val left = bottomLeft.x - size.width
    val top = bottomLeft.y

    return Rect(
      left = left,
      top = top,
      right = left + size.width,
      bottom = top + size.height
    )
  }

  fun rightHandleBBox(): Rect? {
    val bottomRight = charBBox.bottomRight
    if (bottomRight.isUnspecified) {
      return null
    }

    val size = painter?.intrinsicSize
      ?: return null

    val left = bottomRight.x
    val top = bottomRight.y

    return Rect(
      left = left,
      top = top,
      right = left + size.width,
      bottom = top + size.height
    )
  }
}