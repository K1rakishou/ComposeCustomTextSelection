package com.github.k1rakishou.composecustomtextselection.lib

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextLayoutResult
import kotlin.math.max

/**
 * This method returns the graphical position where the selection handle should be based on the
 * offset and other information.
 *
 * @param textLayoutResult a result of the text layout.
 * @param offset character offset to be calculated
 * @param isStart true if called for selection start handle
 * @param areHandlesCrossed true if the selection handles are crossed
 *
 * @return the graphical position where the selection handle should be.
 */
internal fun getSelectionHandleCoordinates(
  textLayoutResult: TextLayoutResult,
  offset: Int,
  isStart: Boolean,
  areHandlesCrossed: Boolean
): Offset {
  val line = textLayoutResult.getLineForOffset(offset)
  val x = textLayoutResult.getHorizontalPosition(offset, isStart, areHandlesCrossed)
  val y = textLayoutResult.getLineBottom(line)

  return Offset(x, y)
}

internal fun TextLayoutResult.getHorizontalPosition(
  offset: Int,
  isStart: Boolean,
  areHandlesCrossed: Boolean
): Float {
  val offsetToCheck =
    if (isStart && !areHandlesCrossed || !isStart && areHandlesCrossed) offset
    else max(offset - 1, 0)
  val bidiRunDirection = getBidiRunDirection(offsetToCheck)
  val paragraphDirection = getParagraphDirection(offset)

  return getHorizontalPosition(
    offset = offset,
    usePrimaryDirection = bidiRunDirection == paragraphDirection
  )
}
