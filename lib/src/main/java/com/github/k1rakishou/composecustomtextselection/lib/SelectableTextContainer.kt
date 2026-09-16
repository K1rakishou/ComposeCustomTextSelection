package com.github.k1rakishou.composecustomtextselection.lib

import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.focusable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.magnifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.platform.LocalHapticFeedback
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
  toolbarColors: SelectableTextToolbarColors = SelectableTextToolbarDefaults.colors(),
  toolbar: (SelectableTextToolbarScope.() -> Unit)? = null,
  textContent: @Composable (onTextLayout: (TextLayoutResult) -> Unit) -> Unit
) {
  val focusRequester = remember { FocusRequester() }
  val hapticFeedback = LocalHapticFeedback.current

  val onClickedUpdated by rememberUpdatedState(onClicked)
  val onLongClickedUpdated by rememberUpdatedState(onLongClicked)

  val toolbarItems = toolbar?.let { builder -> buildSelectableTextToolbarItems(builder) }.orEmpty()

  val selectionColor by selectableTextState.selectionColor
  val pointerPositionMut by selectableTextState.localPointerPosition
  val pointerPosition = pointerPositionMut
  val dragModeMut by selectableTextState.dragMode
  val dragMode = dragModeMut

  LaunchedEffect(key1 = selectableTextState, key2 = hapticFeedback) {
    selectableTextState.uiEventFlow
      .collect { uiEvent ->
        when (uiEvent) {
          SelectableTextState.UiEvent.RequestFocus -> focusRequester.requestFocus()
          is SelectableTextState.UiEvent.PerformHapticFeedback -> hapticFeedback.performHapticFeedback(uiEvent.type)
        }
      }
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

  val magnifierModifier = if (dragMode is DragMode.DraggingHandle) {
    Modifier.magnifier(
      sourceCenter = { selectableTextState.localPointerPosition.value ?: Offset.Unspecified },
    )
  } else {
    Modifier
  }

  Box(
    modifier = modifier
      .then(
        Modifier
          .onFocusChanged { focusState ->
            if (!focusState.isFocused && selectableTextState.hasSelection) {
              selectableTextState.resetEverything()
            }
          }
          .focusRequester(focusRequester)
          .focusable()
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
                onClicked = { onClickedUpdated?.invoke() },
                onLongClicked = { onLongClickedUpdated?.invoke() },
                interactionSource = interactionSource,
                selectableTextState = selectableTextState
              )
            }
          )
          .then(magnifierModifier)
      )
  ) {
    textContent { textLayoutResult ->
      selectableTextState.updateTextLayoutResult(textLayoutResult)
    }

    Spacer(
      modifier = Modifier
        .matchParentSize()
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

    if (toolbarItems.isNotEmpty() && dragMode != null && pointerPosition == null && selectableTextState.hasSelection) {
      SelectableTextToolbar(
        selectableTextState = selectableTextState,
        items = toolbarItems,
        colors = toolbarColors
      )
    }

    SelectionHandleElement(
      handleSize = handleSize,
      selectableTextState = selectableTextState,
      selectionHandle = selectableTextState.leftSelectionHandle,
      startHandlePainter = startHandlePainter,
      endHandlePainter = endHandlePainter
    )

    SelectionHandleElement(
      handleSize = handleSize,
      selectableTextState = selectableTextState,
      selectionHandle = selectableTextState.rightSelectionHandle,
      startHandlePainter = startHandlePainter,
      endHandlePainter = endHandlePainter
    )
  }
}

@Composable
private fun SelectionHandleElement(
  handleSize: Dp,
  selectableTextState: SelectableTextState,
  selectionHandle: SelectionHandle,
  startHandlePainter: Painter,
  endHandlePainter: Painter
) {
  if (!selectionHandle.isInitialized) {
    return
  }

  val isStartHandle = selectableTextState.isStartHandle(selectionHandle)
  val handlePainter = if (isStartHandle) startHandlePainter else endHandlePainter

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

        val selectableTextLayoutCoordinates = selectableTextState.selectableTextLayoutCoordinates
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
          selectionHandle.popupLayoutCoordinates = layoutCoordinates
        }
        .pointerInput(selectionHandle, selectableTextState) {
          textSelectionAfterHandleDrag(
            selectionHandle = selectionHandle,
            selectableTextState = selectableTextState
          )
        }
        .drawWithCache {
          onDrawWithContent {
            drawContent()

            with(handlePainter) {
              draw(size)
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