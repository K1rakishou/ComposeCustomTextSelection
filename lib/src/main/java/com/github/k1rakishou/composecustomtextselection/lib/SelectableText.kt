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
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest

@Composable
fun SelectableTextContainer(
  modifier: Modifier = Modifier,
  textSelectionState: TextSelectionState,
  indication: Indication? = LocalIndication.current,
  interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
  onClicked: (() -> Unit)? = null,
  onLongClicked: (() -> Unit)? = null,
  onEnteredSelection: (() -> Unit)? = null,
  onExitedSelection: (() -> Unit)? = null,
  textContent: @Composable (onTextLayout: (TextLayoutResult) -> Unit) -> Unit
) {
  val focusRequester = remember { FocusRequester() }

  val selectionPath by textSelectionState.selectionPath
  val leftSelectionHandle by textSelectionState.leftSelectionHandle
  val rightSelectionHandle by textSelectionState.rightSelectionHandle

  LaunchedEffect(key1 = textSelectionState) {
    textSelectionState.focusEventFlow
      .collectLatest { focusRequester.requestFocus() }
  }

  Box(
    modifier = modifier
      .then(
        Modifier
          .focusRequester(focusRequester)
          .focusable()
          .onFocusChanged { focusState ->
            if (!focusState.isFocused) {
              textSelectionState.resetEverything()
            }
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
                textSelectionState = textSelectionState
              )
            }
          )
      )
  ) {
    textContent { textLayoutResult ->
      textSelectionState.updateTextLayoutResult(textLayoutResult)
    }

    Canvas(modifier = Modifier.matchParentSize()) {
      if (!selectionPath.isEmpty) {
        drawPath(selectionPath, Color.Magenta.copy(alpha = 0.6f))
      }

      if (leftSelectionHandle.center.isSpecified) {
        drawCircle(
          color = Color.Green.copy(alpha = 0.6f),
          radius = 2.dp.toPx(),
          center = leftSelectionHandle.center
        )
      }

      if (rightSelectionHandle.center.isSpecified) {
        drawCircle(
          color = Color.Green.copy(alpha = 0.6f),
          radius = 2.dp.toPx(),
          center = rightSelectionHandle.center
        )
      }
    }
  }
}