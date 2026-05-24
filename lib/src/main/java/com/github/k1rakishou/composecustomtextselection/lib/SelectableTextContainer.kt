package com.github.k1rakishou.composecustomtextselection.lib

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.focusable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
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
  val leftHandleMut by selectableTextState.leftSelectionHandle
  val leftHandle = leftHandleMut
  val rightHandleMut by selectableTextState.rightSelectionHandle
  val rightHandle = rightHandleMut
  val pointerPositionMut by selectableTextState.pointerPosition
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

      run {
        val painter = leftHandle?.painter
        if (painter != null) {
          val bbox = leftHandle.leftHandleBBox()
          if (bbox != null) {
            translate(
              left = bbox.left,
              top = bbox.top
            ) {
              with(painter) {
                draw(painter.intrinsicSize)
              }
            }

            if (selectableTextState.debugMode) {
              drawRect(
                color = Color.DarkGray,
                topLeft = bbox.topLeft,
                size = bbox.size,
                style = Stroke(width = 1.dp.toPx())
              )
            }
          }
        }
      }

      run {
        val painter = rightHandle?.painter
        if (painter != null) {
          val bbox = rightHandle.rightHandleBBox()
          if (bbox != null) {
            translate(
              left = bbox.left,
              top = bbox.top
            ) {
              with(painter) {
                draw(painter.intrinsicSize)
              }
            }

            if (selectableTextState.debugMode) {
              drawRect(
                color = Color.DarkGray,
                topLeft = bbox.topLeft,
                size = bbox.size,
                style = Stroke(width = 1.dp.toPx())
              )
            }
          }
        }
      }

      if (selectableTextState.debugMode && pointerPosition != null) {
        drawCircle(color = Color.Red, radius = 2.dp.toPx(), center = pointerPosition)
      }
    }
  }
}