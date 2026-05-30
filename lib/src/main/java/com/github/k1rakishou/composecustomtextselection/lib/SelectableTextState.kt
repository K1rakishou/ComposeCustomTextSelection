package com.github.k1rakishou.composecustomtextselection.lib

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.text.BreakIterator
import java.util.Locale

class SelectableTextState(
  selectionColor: Color,
  private val leftPainter: Painter,
  private val rightPainter: Painter,
  val size: Dp,
  val debugMode: Boolean
) {
  private val _focusEventFlow = MutableSharedFlow<Unit>(extraBufferCapacity = Channel.RENDEZVOUS)
  val focusEventFlow: SharedFlow<Unit>
    get() = _focusEventFlow.asSharedFlow()

  private val _textLayoutResultState = mutableStateOf<TextLayoutResult?>(null)

  private val _selectionPath = mutableStateOf<Path?>(null)
  val selectionPath: State<Path?>
    get() = _selectionPath
  private val _selectionColor = mutableStateOf(selectionColor)
  val selectionColor: State<Color>
    get() = _selectionColor
  private val _localPointerPosition = mutableStateOf<Offset?>(null)
  val localPointerPosition: State<Offset?>
    get() = _localPointerPosition

  private var _dragMode: DragMode? = null
  val dragMode: DragMode?
    get() = _dragMode

  val leftSelectionHandle = SelectionHandle()
  val rightSelectionHandle = SelectionHandle()

  fun updateTextLayoutResult(textLayoutResult: TextLayoutResult) {
    Snapshot.withMutableSnapshot {
      _textLayoutResultState.value = textLayoutResult
    }
  }

  fun updateSelectableTextLayoutCoordinates(layoutCoordinates: LayoutCoordinates) {
    leftSelectionHandle.updateSelectableTextLayoutCoordinates(layoutCoordinates)
    rightSelectionHandle.updateSelectableTextLayoutCoordinates(layoutCoordinates)
  }

  fun updatePopupLayoutCoordinates(isLeftHandle: Boolean, layoutCoordinates: LayoutCoordinates) {
    if (isLeftHandle) {
      leftSelectionHandle.updatePopupLayoutCoordinates(layoutCoordinates)
    } else {
      rightSelectionHandle.updatePopupLayoutCoordinates(layoutCoordinates)
    }
  }

  fun onDragStart(startPoint: Offset, dragMode: DragMode) {
    val textLayoutResult = _textLayoutResultState.value ?: return
    val fullText = textLayoutResult.layoutInput.text.text

    when (dragMode) {
      is DragMode.DraggingHandle -> {
        checkNotNull(_selectionPath.value) { "selection path is null" }
        check(leftSelectionHandle.isInitialized) { "leftSelectionHandle is not initialized" }
        check(rightSelectionHandle.isInitialized) { "rightSelectionHandle is not initialized" }
      }
      is DragMode.ExtendingSelection -> {
        val charOffset = textLayoutResult.getOffsetForPosition(startPoint)

        val newTextRange = when (dragMode.initialSelectionMode) {
          InitialSelectionMode.Word -> textLayoutResult.getWordBoundary(charOffset)
          InitialSelectionMode.Sentence -> getSentenceBoundary(fullText, textLayoutResult, charOffset)
        }

        val textRange = removeWhitespaces(fullText, newTextRange)
        if (textRange.reversed || textRange.collapsed) {
          return
        }

        _selectionPath.value = textLayoutResult.getPathForRange(
          start = textRange.start,
          end = textRange.end
        )

        leftSelectionHandle.update(
          textOffset = textRange.start,
          charBBox = textLayoutResult.getBoundingBox(textRange.start),
          painter = leftPainter
        )

        rightSelectionHandle.update(
          textOffset = textRange.end,
          charBBox = textLayoutResult.getBoundingBox(textRange.end - 1),
          painter = rightPainter
        )
      }
    }

    _dragMode = dragMode
    _localPointerPosition.value = run {
      if (dragMode !is DragMode.DraggingHandle) {
        return@run startPoint
      }

      val handleBBox = checkNotNull(
        leftSelectionHandle.textRelativeHandleBBox(left = dragMode.isLeftHandle)
      )

      var updatedPosition = startPoint
      updatedPosition -= Offset(x = 0f, y = handleBBox.height)

      if (dragMode.isLeftHandle) {
        updatedPosition += Offset(x = handleBBox.width / 2f, y = 0f)
      } else {
        updatedPosition -= Offset(x = handleBBox.width / 2f, y = 0f)
      }

      return@run updatedPosition
    }

    // TODO: haptic feedback
    _focusEventFlow.tryEmit(Unit)
  }

  fun onDragProgress(delta: Offset) {
    if (!leftSelectionHandle.isInitialized || !rightSelectionHandle.isInitialized) {
      return
    }

    val textLayoutResult = _textLayoutResultState.value ?: return
    val dragMode = _dragMode ?: return
    val prevPointerPosition = _localPointerPosition.value ?: return
    val text = textLayoutResult.layoutInput.text

    val newPointerPosition = prevPointerPosition + delta
    _localPointerPosition.value = newPointerPosition

    val newTextOffset = textLayoutResult.getOffsetForPosition(newPointerPosition)
      .coerceIn(0, text.lastIndex)

    var leftSelectionHandleUpdated = false
    var rightSelectionHandleUpdated = false

    run {
      when (dragMode) {
        is DragMode.ExtendingSelection -> {
          if (newTextOffset < leftSelectionHandle.textOffset) {
            leftSelectionHandleUpdated = leftSelectionHandle.update(
              textOffset = newTextOffset,
              charBBox = textLayoutResult.getBoundingBox(newTextOffset)
            )
          } else if (newTextOffset > rightSelectionHandle.textOffset) {
            rightSelectionHandleUpdated = rightSelectionHandle.update(
              textOffset = newTextOffset,
              charBBox = textLayoutResult.getBoundingBox(newTextOffset - 1)
            )
          }
        }
        is DragMode.DraggingHandle -> {
          if (dragMode.isLeftHandle) {
            val prevTextOffset = rightSelectionHandle.textOffset

            // Do not allow shrinking the text selection to have less than 1 selected character.
            val adjustedTextOffset = if (newTextOffset > prevTextOffset - 1) {
              prevTextOffset - 1
            } else {
              newTextOffset
            }

            leftSelectionHandleUpdated = leftSelectionHandle.update(
              textOffset = adjustedTextOffset,
              charBBox = textLayoutResult.getBoundingBox(adjustedTextOffset)
            )
          } else {
            val prevTextOffset = leftSelectionHandle.textOffset

            // Do not allow shrinking the text selection to have less than 1 selected character.
            val adjustedTextOffset = if (newTextOffset < prevTextOffset + 1) {
              prevTextOffset + 1
            } else {
              newTextOffset
            }

            rightSelectionHandleUpdated = rightSelectionHandle.update(
              textOffset = adjustedTextOffset,
              charBBox = textLayoutResult.getBoundingBox(adjustedTextOffset - 1)
            )
          }
        }
      }
    }

    if (leftSelectionHandleUpdated || rightSelectionHandleUpdated) {
      // TODO: haptic feedback

      Snapshot.withMutableSnapshot {
        _selectionPath.value = textLayoutResult.getPathForRange(
          start = leftSelectionHandle.textOffset,
          end = rightSelectionHandle.textOffset
        )
      }
    }
  }

  fun onDragStop(stoppedNormally: Boolean) {
    _localPointerPosition.value = null
  }

  fun resetEverything() {
    Snapshot.withMutableSnapshot {
      _localPointerPosition.value = null
      _dragMode = null
      _selectionPath.value = Path()
      leftSelectionHandle.reset()
      rightSelectionHandle.reset()
    }
  }

  private fun removeWhitespaces(
    fullText: String,
    textRange: TextRange
  ): TextRange {
    var start = textRange.start
    var end = textRange.end

    while (start < end) {
      val ch = fullText.getOrNull(start)
      if (ch == null || !ch.isWhitespace()) {
        break
      }

      ++start
    }

    while (start < end) {
      val ch = fullText.getOrNull(end - 1)
      if (ch == null || !ch.isWhitespace()) {
        break
      }

      --end
    }

    return TextRange(start, end)
  }

  private fun getSentenceBoundary(
    fullText: String,
    textLayoutResult: TextLayoutResult,
    charOffset: Int
  ): TextRange {
    val breakIterator = BreakIterator.getSentenceInstance(Locale.getDefault())
    breakIterator.setText(fullText)

    var start = breakIterator.first().takeIf { it >= 0 } ?: 0
    var end = fullText.length

    while (end != BreakIterator.DONE) {
      end = breakIterator.next()

      if (charOffset in start..end) {
        return TextRange(start, end)
      }

      start = end
    }

    return textLayoutResult.getWordBoundary(charOffset)
  }
}

sealed interface DragMode {
  data class DraggingHandle(val isLeftHandle: Boolean) : DragMode
  data class ExtendingSelection(val initialSelectionMode: InitialSelectionMode) : DragMode
}

enum class InitialSelectionMode {
  // Double-tapping
  Word,

  // Triple-tapping
  Sentence
}

@Composable
fun rememberTextSelectionState(
  selectionColor: Color = Color(0x804FCEF7L),
  cursorColor: Color = Color(0xFF0BB7EFL),
  size: Dp = 28.dp,
  leftSelectionHandlePainter: Painter? = null,
  rightSelectionHandlePainter: Painter? = null,
  debugMode: Boolean = false,
): SelectableTextState {
  val leftPainter = remember {
    if (leftSelectionHandlePainter != null) {
      return@remember leftSelectionHandlePainter
    }

    return@remember DefaultSelectionHandlePainter(
      isLeftHandle = true,
      color = cursorColor
    )
  }

  val rightPainter = remember {
    if (rightSelectionHandlePainter != null) {
      return@remember rightSelectionHandlePainter
    }

    return@remember DefaultSelectionHandlePainter(
      isLeftHandle = false,
      color = cursorColor
    )
  }

  return remember {
    SelectableTextState(
      selectionColor = selectionColor,
      leftPainter = leftPainter,
      rightPainter = rightPainter,
      size = size,
      debugMode = debugMode
    )
  }
}