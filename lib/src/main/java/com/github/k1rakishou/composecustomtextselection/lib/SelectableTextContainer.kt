package com.github.k1rakishou.composecustomtextselection.lib

import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.focusable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.flow.collectLatest

private val DefaultHandleColor = Color(0xFF0BB7EFL)

@Composable
fun SelectableTextContainer(
  modifier: Modifier = Modifier,
  selectableTextState: SelectableTextState,
  cursorColor: Color = DefaultHandleColor,
  handleSize: Dp,
  startSelectionHandlePainter: Painter? = null,
  endSelectionHandlePainter: Painter? = null,
  indication: Indication? = LocalIndication.current,
  interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
  onClicked: (() -> Unit)? = null,
  onLongClicked: (() -> Unit)? = null,
  textContent: @Composable (onTextLayout: (TextLayoutResult) -> Unit) -> Unit
) {
  val focusRequester = remember { FocusRequester() }

  val selectionColor by selectableTextState.selectionColor
  val pointerPositionMut by selectableTextState.localPointerPosition
  val pointerPosition = pointerPositionMut

  LaunchedEffect(key1 = selectableTextState) {
    selectableTextState.focusEventFlow
      .collectLatest { focusRequester.requestFocus() }
  }

  val startHandlePainter = remember(cursorColor, startSelectionHandlePainter) {
    if (startSelectionHandlePainter != null) {
      return@remember startSelectionHandlePainter
    }

    return@remember DefaultSelectionHandlePainter(
      isLeftHandle = true,
      color = cursorColor
    )
  }

  val endHandlePainter = remember(cursorColor, endSelectionHandlePainter) {
    if (endSelectionHandlePainter != null) {
      return@remember endSelectionHandlePainter
    }

    return@remember DefaultSelectionHandlePainter(
      isLeftHandle = false,
      color = cursorColor
    )
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

    Spacer(
      modifier = Modifier
        .fillMaxSize()
        .drawWithCache {
          onDrawWithContent {
            drawContent()

            val textLayoutResult = selectableTextState.textLayoutResult
            if (textLayoutResult != null) {
              val start = selectableTextState.startSelectionHandle.textOffset
              val end = selectableTextState.endSelectionHandle.textOffset

              if (start >= 0 && end >= 0 && start <= end) {
                val path = textLayoutResult.getPathForRange(start, end)
                if (!path.isEmpty) {
                  drawPath(path, selectionColor)
                }
              }
            }

            if (selectableTextState.debugMode && pointerPosition != null) {
              drawCircle(color = Color.Red, radius = 2.dp.toPx(), center = pointerPosition)
            }
          }
        }
    )

    SelectionHandleElement(
      handleSize = handleSize,
      selectableTextState = selectableTextState,
      selectionHandle = selectableTextState.startSelectionHandle,
      handlePainter = startHandlePainter
    )

    SelectionHandleElement(
      handleSize = handleSize,
      selectableTextState = selectableTextState,
      selectionHandle = selectableTextState.endSelectionHandle,
      handlePainter = endHandlePainter
    )
  }
}

@Composable
private fun SelectionHandleElement(
  handleSize: Dp,
  selectableTextState: SelectableTextState,
  selectionHandle: SelectionHandle,
  handlePainter: Painter
) {
  val isStartHandle = selectableTextState.isStartHandle(selectionHandle)

  val popupPositionProvider = remember(isStartHandle, selectionHandle) {
    object : PopupPositionProvider {
      private var _prevOffset = IntOffset.Zero

      override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
      ): IntOffset {
        val handleBBox = selectionHandle.textRelativeHandleBBox(isStartHandle)

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
            isLeftHandle = isStartHandle,
            layoutCoordinates = layoutCoordinates
          )
        }
        .pointerInput(isStartHandle, selectableTextState) {
          textSelectionAfterHandleDrag(
            isLeftHandle = isStartHandle,
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

            with(handlePainter) {
              draw(handlePainter.intrinsicSize)
            }

            if (selectableTextState.debugMode) {
              drawRect(
                color = Color.DarkGray,
                style = Stroke(width = 1.dp.toPx())
              )
            }
          }
        }
        .size(handleSize)
    )
  }
}