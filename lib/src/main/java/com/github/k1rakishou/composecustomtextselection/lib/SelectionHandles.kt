package com.github.k1rakishou.composecustomtextselection.lib

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.unit.dp


internal val HandleWidth = 25.dp
internal val HandleHeight = 25.dp

/**
 * [SelectionHandleInfo]s for the nodes representing selection handles. These nodes are in popup
 * windows, and will respond to drag gestures.
 */
internal val SelectionHandleInfoKey =
  SemanticsPropertyKey<SelectionHandleInfo>("SelectionHandleInfo")

/**
 * Information about a single selection handle popup.
 *
 * @param handle Which selection [Handle] this is about.
 * @param position The position that the handle is anchored to relative to the selectable content.
 * This position is not necessarily the position of the popup itself, it's the position that the
 * handle "points" to (so e.g. top-middle for [Handle.Cursor]).
 */
internal data class SelectionHandleInfo(
  val handle: Handle,
  val position: Offset
)

/**
 * Adjust coordinates for given text offset.
 *
 * Currently [android.text.Layout.getLineBottom] returns y coordinates of the next
 * line's top offset, which is not included in current line's hit area. To be able to
 * hit current line, move up this y coordinates by 1 pixel.
 */
internal fun getAdjustedCoordinates(position: Offset): Offset {
  return Offset(position.x, position.y - 1f)
}

internal enum class Handle {
  Cursor,
  SelectionStart,
  SelectionEnd
}