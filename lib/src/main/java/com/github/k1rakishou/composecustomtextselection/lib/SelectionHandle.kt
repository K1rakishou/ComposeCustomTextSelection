package com.github.k1rakishou.composecustomtextselection.lib

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.LayoutCoordinates

@Stable
class SelectionHandle(
  private val handleSize: Size
) {
  private val _textOffset = mutableIntStateOf(-1)
  val textOffset: Int
    get() = _textOffset.intValue

  private val _charBBox = mutableStateOf<Rect?>(null)

  private val _selectableTextLayoutCoordinates = mutableStateOf<LayoutCoordinates?>(null)
  val selectableTextLayoutCoordinates: LayoutCoordinates?
    get() = _selectableTextLayoutCoordinates.value

  private val _popupLayoutCoordinates = mutableStateOf<LayoutCoordinates?>(null)
  val popupLayoutCoordinates: LayoutCoordinates?
    get() = _popupLayoutCoordinates.value

  val isInitialized: Boolean
    get() = _textOffset.intValue >= 0 && _charBBox.value != null

  fun updateSelectableTextLayoutCoordinates(layoutCoordinates: LayoutCoordinates) {
    _selectableTextLayoutCoordinates.value = layoutCoordinates
  }

  fun updatePopupLayoutCoordinates(layoutCoordinates: LayoutCoordinates) {
    _popupLayoutCoordinates.value = layoutCoordinates
  }

  fun update(
    textOffset: Int? = null,
    charBBox: Rect? = null,
  ): Boolean {
    var updated = false

    if (textOffset != null && textOffset != _textOffset.intValue) {
      _textOffset.intValue = textOffset
      updated = true
    }

    if (charBBox != null && charBBox != _charBBox.value) {
      _charBBox.value = charBBox
      updated = true
    }

    return updated
  }

  fun textRelativeHandleBBox(left: Boolean): Rect? {
    if (left) {
      val bottomLeft = _charBBox.value?.bottomLeft
        ?: return null

      val left = bottomLeft.x - handleSize.width
      val top = bottomLeft.y

      return Rect(
        left = left,
        top = top,
        right = left + handleSize.width,
        bottom = top + handleSize.height
      )
    } else {
      val bottomRight = _charBBox.value?.bottomRight
        ?: return null

      val left = bottomRight.x
      val top = bottomRight.y

      return Rect(
        left = left,
        top = top,
        right = left + handleSize.width,
        bottom = top + handleSize.height
      )
    }
  }

  fun reset() {
    _textOffset.intValue = -1
    _charBBox.value = null
  }
}