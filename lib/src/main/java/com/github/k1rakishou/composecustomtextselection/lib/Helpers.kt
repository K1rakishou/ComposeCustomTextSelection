package com.github.k1rakishou.composecustomtextselection.lib

import androidx.compose.ui.geometry.Offset

internal fun Offset.popupToLocal(
  selectableTextState: SelectableTextState,
  selectionHandle: SelectionHandle
): Offset? {
  val selectableTextLayoutCoordinates = selectableTextState.selectableTextLayoutCoordinates
    ?.takeIf { it.isAttached }
    ?: return null
  val popupLayoutCoordinates = selectionHandle.popupLayoutCoordinates
    ?.takeIf { it.isAttached }
    ?: return null

  val screenOffset = popupLayoutCoordinates.localToScreen(this)
  val localOffset = selectableTextLayoutCoordinates.screenToLocal(screenOffset)

  return localOffset
}