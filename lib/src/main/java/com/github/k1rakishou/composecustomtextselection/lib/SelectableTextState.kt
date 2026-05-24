package com.github.k1rakishou.composecustomtextselection.lib

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Density
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
  val debugMode: Boolean
) {
  private val _focusEventFlow = MutableSharedFlow<Unit>(extraBufferCapacity = Channel.RENDEZVOUS)
  val focusEventFlow: SharedFlow<Unit>
    get() = _focusEventFlow.asSharedFlow()

  private val _textLayoutResultState = mutableStateOf<TextLayoutResult?>(null)

  private val _selectionPath = mutableStateOf<Path?>(null)
  val selectionPath: State<Path?>
    get() = _selectionPath
  private val _leftSelectionHandle = mutableStateOf<SelectionHandle?>(null)
  val leftSelectionHandle: State<SelectionHandle?>
    get() = _leftSelectionHandle
  private val _rightSelectionHandle = mutableStateOf<SelectionHandle?>(null)
  val rightSelectionHandle: State<SelectionHandle?>
    get() = _rightSelectionHandle
  private val _selectionColor = mutableStateOf(selectionColor)
  val selectionColor: State<Color>
    get() = _selectionColor
  private val _pointerPosition = mutableStateOf<Offset?>(null)
  val pointerPosition: State<Offset?>
    get() = _pointerPosition

  private var _dragMode: DragMode? = null
  val dragMode: DragMode?
    get() = _dragMode

  fun onDragStart(startPoint: Offset, dragMode: DragMode) {
    val textLayoutResult = _textLayoutResultState.value
      ?: return

    _dragMode = dragMode
    _pointerPosition.value = run {
      if (dragMode !is DragMode.DraggingHandle) {
        return@run startPoint
      }

      val handleBBox = if (dragMode.isLeftHandle) {
        checkNotNull(_leftSelectionHandle.value?.leftHandleBBox())
      } else {
        checkNotNull(_rightSelectionHandle.value?.rightHandleBBox())
      }

      var updatedPosition = startPoint
      updatedPosition -= Offset(x = 0f, y = handleBBox.height)

      if (dragMode.isLeftHandle) {
        updatedPosition += Offset(x = handleBBox.width / 2f, y = 0f)
      } else {
        updatedPosition -= Offset(x = handleBBox.width / 2f, y = 0f)
      }

      return@run updatedPosition
    }

    val fullText = textLayoutResult.layoutInput.text.text

    when (dragMode) {
      is DragMode.DraggingHandle -> {
        checkNotNull(_selectionPath.value) { "selection path is null" }
        checkNotNull(_leftSelectionHandle.value) { "leftSelectionHandle is null" }
        checkNotNull(_rightSelectionHandle.value) { "rightSelectionHandle is null" }
      }
      is DragMode.ExtendingSelection -> {
        _focusEventFlow.tryEmit(Unit)
        val charOffset = textLayoutResult.getOffsetForPosition(startPoint)

        val newTextRange = when (dragMode.initialSelectionMode) {
          InitialSelectionMode.Word -> textLayoutResult.getWordBoundary(charOffset)
          InitialSelectionMode.Sentence -> getSentenceBoundary(fullText, textLayoutResult, charOffset)
        }

        val textRange = removeWhitespaces(fullText, newTextRange)
        val selectionPath = textLayoutResult.getPathForRange(textRange.start, textRange.end)

        Snapshot.withMutableSnapshot {
          _selectionPath.value = selectionPath

          _leftSelectionHandle.value = SelectionHandle(
            textOffset = textRange.start,
            charBBox = textLayoutResult.getBoundingBox(textRange.start),
            painter = leftPainter
          )

          _rightSelectionHandle.value = SelectionHandle(
            textOffset = textRange.end,
            charBBox = textLayoutResult.getBoundingBox(textRange.end - 1),
            painter = rightPainter
          )
        }
      }
    }
  }

  fun onDragProgress(delta: Offset) {
    val textLayoutResult = _textLayoutResultState.value
      ?: return
    val prevLeftSelectionHandle = _leftSelectionHandle.value
      ?: return
    val prevRightSelectionHandle = _rightSelectionHandle.value
      ?: return
    val dragMode = _dragMode
      ?: return
    val prevPointerPosition = _pointerPosition.value
      ?: return

    val text = textLayoutResult.layoutInput.text

    val newPointerPosition = prevPointerPosition + delta
    _pointerPosition.value = newPointerPosition

    val newTextOffset = textLayoutResult.getOffsetForPosition(newPointerPosition)
      .coerceIn(0, text.lastIndex)

    var newLeftTextSelectionHandle = prevLeftSelectionHandle
    var newRightTextSelectionHandle = prevRightSelectionHandle

    run {
      when (dragMode) {
        is DragMode.ExtendingSelection -> {
          if (newTextOffset < prevLeftSelectionHandle.textOffset) {
            newLeftTextSelectionHandle = prevLeftSelectionHandle.copy(
              textOffset = newTextOffset,
              charBBox = textLayoutResult.getBoundingBox(newTextOffset)
            )
          } else if (newTextOffset > prevRightSelectionHandle.textOffset) {
            newRightTextSelectionHandle = prevRightSelectionHandle.copy(
              textOffset = newTextOffset,
              charBBox = textLayoutResult.getBoundingBox(newTextOffset - 1)
            )
          }
        }
        is DragMode.DraggingHandle -> {
          if (dragMode.isLeftHandle) {
            val prevTextOffset = prevRightSelectionHandle.textOffset

            // Do not allow shrinking the text selection to have less than 1 selected character.
            val adjustedTextOffset = if (newTextOffset > prevTextOffset - 1) {
              prevTextOffset - 1
            } else {
              newTextOffset
            }

            newLeftTextSelectionHandle = prevLeftSelectionHandle.copy(
              textOffset = adjustedTextOffset,
              charBBox = textLayoutResult.getBoundingBox(adjustedTextOffset)
            )
          } else {
            val prevTextOffset = prevLeftSelectionHandle.textOffset

            // Do not allow shrinking the text selection to have less than 1 selected character.
            val adjustedTextOffset = if (newTextOffset < prevTextOffset + 1) {
              prevTextOffset + 1
            } else {
              newTextOffset
            }

            newRightTextSelectionHandle = prevRightSelectionHandle.copy(
              textOffset = adjustedTextOffset,
              charBBox = textLayoutResult.getBoundingBox(adjustedTextOffset - 1)
            )
          }
        }
      }
    }

    if (
      prevLeftSelectionHandle != newLeftTextSelectionHandle ||
      prevRightSelectionHandle != newRightTextSelectionHandle
    ) {
      Snapshot.withMutableSnapshot {
        _selectionPath.value = textLayoutResult.getPathForRange(
          start = newLeftTextSelectionHandle.textOffset,
          end = newRightTextSelectionHandle.textOffset
        )

        _leftSelectionHandle.value = newLeftTextSelectionHandle
        _rightSelectionHandle.value = newRightTextSelectionHandle
      }
    }
  }

  fun onDragStop(stoppedNormally: Boolean) {
    _pointerPosition.value = null
  }

  fun updateTextLayoutResult(textLayoutResult: TextLayoutResult) {
    Snapshot.withMutableSnapshot {
      resetEverything()
      _textLayoutResultState.value = textLayoutResult
    }
  }

  fun resetEverything() {
    Snapshot.withMutableSnapshot {
      _pointerPosition.value = null
      _dragMode = null
      _selectionPath.value = Path()
      _leftSelectionHandle.value = SelectionHandle()
      _rightSelectionHandle.value = SelectionHandle()
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

  // Trippe-tapping
  Sentence
}

private fun Density.defaultSize(): Size = Size(28.dp.toPx(), 28.dp.toPx())

@Composable
fun rememberTextSelectionState(
  selectionColor: Color = Color(0x804FCEF7L),
  cursorColor: Color = Color(0xFF0BB7EFL),
  size: Size = with(LocalDensity.current) { defaultSize() },
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
      size = size,
      color = cursorColor
    )
  }

  val rightPainter = remember {
    if (rightSelectionHandlePainter != null) {
      return@remember rightSelectionHandlePainter
    }

    return@remember DefaultSelectionHandlePainter(
      isLeftHandle = false,
      size = size,
      color = cursorColor
    )
  }

  return remember {
    SelectableTextState(
      selectionColor = selectionColor,
      leftPainter = leftPainter,
      rightPainter = rightPainter,
      debugMode = debugMode
    )
  }
}