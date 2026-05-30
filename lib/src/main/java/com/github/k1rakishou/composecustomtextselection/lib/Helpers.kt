package com.github.k1rakishou.composecustomtextselection.lib

import androidx.compose.ui.geometry.Offset

internal fun Offset.popupToLocal(
  selectionHandle: SelectionHandle
): Offset? {
  val selectableTextLayoutCoordinates = selectionHandle.selectableTextLayoutCoordinates
    ?: return null
  val popupLayoutCoordinates = selectionHandle.popupLayoutCoordinates
    ?: return null

  val screenOffset = popupLayoutCoordinates.localToScreen(this)
  val localOffset = selectableTextLayoutCoordinates.screenToLocal(screenOffset)

  return localOffset
}

internal fun Offset.localToPopup(
  selectionHandle: SelectionHandle
): Offset? {
  val selectableTextLayoutCoordinates = selectionHandle.selectableTextLayoutCoordinates
    ?: return null
  val popupLayoutCoordinates = selectionHandle.popupLayoutCoordinates
    ?: return null

  val screenOffset = selectableTextLayoutCoordinates.localToScreen(this)
  val localOffset = popupLayoutCoordinates.screenToLocal(screenOffset)

  return localOffset
}