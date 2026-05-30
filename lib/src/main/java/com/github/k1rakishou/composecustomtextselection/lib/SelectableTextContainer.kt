package com.github.k1rakishou.composecustomtextselection.lib

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.focusable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.flow.collectLatest

@Composable
fun SelectableTextContainer(
  modifier: Modifier = Modifier,
  selectableTextState: SelectableTextState,
  indication: Indication? = LocalIndication.current,
  interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
  onClicked: (() -> Unit)? = null,
  onLongClicked: (() -> Unit)? = null,
  textContent: @Composable (onTextLayout: (TextLayoutResult) -> Unit) -> Unit
) {
  val focusRequester = remember { FocusRequester() }

  val selectionColor by selectableTextState.selectionColor
  val selectionPathMut by selectableTextState.selectionPath
  val selectionPath = selectionPathMut
  val pointerPositionMut by selectableTextState.localPointerPosition
  val pointerPosition = pointerPositionMut

  LaunchedEffect(key1 = selectableTextState) {
    selectableTextState.focusEventFlow
      .collectLatest { focusRequester.requestFocus() }
  }

  Box(
    modifier = modifier
      .then(
        Modifier
          .focusRequester(focusRequester)
          .focusable()
          .onFocusChanged { focusState ->
            // TODO: for some reason we lose focus when dragging selection handles sometimes.
            //  Haven't figured out why yet so for now this is disabled.
//            if (!focusState.isFocused) {
//              selectableTextState.resetEverything()
//            }
          }
          .onGloballyPositioned { layoutCoordinates ->
            selectableTextState.updateSelectableTextLayoutCoordinates(layoutCoordinates)
          }
          .indication(
            interactionSource = interactionSource,
            indication = indication
          )
          .pointerInput(
            key1 = Unit,
            block = {
              textSelectionAfterDoubleTapOrTapWithLongTap(
                onClicked = onClicked,
                onLongClicked = onLongClicked,
                interactionSource = interactionSource,
                selectableTextState = selectableTextState
              )
            }
          )
      )
  ) {
    textContent { textLayoutResult ->
      selectableTextState.updateTextLayoutResult(textLayoutResult)
    }

    Canvas(modifier = Modifier.matchParentSize()) {
      if (selectionPath != null) {
        drawPath(selectionPath, selectionColor)
      }

      if (selectableTextState.debugMode && pointerPosition != null) {
        drawCircle(color = Color.Red, radius = 2.dp.toPx(), center = pointerPosition)
      }
    }

    SelectionHandleElement(
      selectableTextState = selectableTextState,
      isLeftHandle = true,
    )

    SelectionHandleElement(
      selectableTextState = selectableTextState,
      isLeftHandle = false,
    )
  }
}

@Composable
private fun SelectionHandleElement(
  selectableTextState: SelectableTextState,
  isLeftHandle: Boolean,
) {
  val selectionHandle = if (isLeftHandle) {
    selectableTextState.leftSelectionHandle
  } else {
    selectableTextState.rightSelectionHandle
  }

  val popupPositionProvider = remember(key1 = selectionHandle, key2 = isLeftHandle) {
    object : PopupPositionProvider {
      private var _prevOffset = IntOffset.Zero

      override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
      ): IntOffset {
        val handleBBox = selectionHandle.textRelativeHandleBBox(isLeftHandle)

        val intOffset = if (handleBBox == null) {
          _prevOffset
        } else {
          IntOffset(
            x = handleBBox.left.toInt(),
            y = handleBBox.top.toInt()
          )
        }
        _prevOffset = intOffset

        val selectableTextLayoutCoordinates = selectionHandle.selectableTextLayoutCoordinates
        if (selectableTextLayoutCoordinates == null) {
          return IntOffset.Zero
        }

        val windowOffset = selectableTextLayoutCoordinates.localToWindow(
          Offset(intOffset.x.toFloat(), intOffset.y.toFloat())
        )

        return IntOffset(
          x = windowOffset.x.toInt(),
          y = windowOffset.y.toInt()
        )
      }
    }
  }

  Popup(
    popupPositionProvider = popupPositionProvider,
    properties = PopupProperties(
      excludeFromSystemGesture = true,
      clippingEnabled = false
    )
  ) {
    Spacer(
      modifier = Modifier
        .onGloballyPositioned { layoutCoordinates ->
          selectableTextState.updatePopupLayoutCoordinates(
            isLeftHandle = isLeftHandle,
            layoutCoordinates = layoutCoordinates
          )
        }
        .pointerInput(isLeftHandle, selectionHandle, selectableTextState) {
          textSelectionAfterHandleDrag(
            isLeftHandle = isLeftHandle,
            selectionHandle = selectionHandle,
            selectableTextState = selectableTextState
          )
        }
        .drawWithCache {
          onDrawWithContent {
            drawContent()

            if (!selectionHandle.isInitialized) {
              return@onDrawWithContent
            }

            val painter = selectionHandle.painter
              ?: return@onDrawWithContent

            with(painter) {
              draw(painter.intrinsicSize)
            }

            if (selectableTextState.debugMode) {
              drawRect(
                color = Color.DarkGray,
                style = Stroke(width = 1.dp.toPx())
              )
            }
          }
        }
        .size(selectableTextState.size)
    )
  }
}