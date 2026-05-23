package com.github.k1rakishou.composecustomtextselection.lib

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.text.BreakIterator
import java.util.Locale

class TextSelectionState {
  private val _focusEventFlow = MutableSharedFlow<Unit>(extraBufferCapacity = Channel.RENDEZVOUS)
  val focusEventFlow: SharedFlow<Unit>
    get() = _focusEventFlow.asSharedFlow()

  private val _textLayoutResultState = mutableStateOf<TextLayoutResult?>(null)

  private var _extendingSelectionMode = false
  private var _pointerPosition = Offset.Unspecified

  private val _selectionPath = mutableStateOf<Path>(Path())
  val selectionPath: State<Path>
    get() = _selectionPath
  private val _leftSelectionHandle = mutableStateOf<SelectionHandle>(SelectionHandle())
  val leftSelectionHandle: State<SelectionHandle>
    get() = _leftSelectionHandle
  private val _rightSelectionHandle = mutableStateOf<SelectionHandle>(SelectionHandle())
  val rightSelectionHandle: State<SelectionHandle>
    get() = _rightSelectionHandle

  fun onPointerDown(point: Offset) {
    Snapshot.withMutableSnapshot {
      resetEverything()
    }
  }

  fun onDragStart(startPoint: Offset, initialSelectionMode: InitialSelectionMode) {
    val textLayoutResult = _textLayoutResultState.value
      ?: return

    _focusEventFlow.tryEmit(Unit)

    val fullText = textLayoutResult.layoutInput.text.text
    val charOffset = textLayoutResult.getOffsetForPosition(startPoint)

    var textRange = when (initialSelectionMode) {
      InitialSelectionMode.Word -> textLayoutResult.getWordBoundary(charOffset)
      InitialSelectionMode.Sentence -> getSentenceBoundary(fullText, textLayoutResult, charOffset)
    }
    textRange = removeWhitespaces(fullText, textRange)

    val selectionPath = textLayoutResult.getPathForRange(textRange.start, textRange.end)

    Snapshot.withMutableSnapshot {
      _selectionPath.value = selectionPath

      _leftSelectionHandle.value = SelectionHandle(
        textOffset = textRange.start,
        center = textLayoutResult.getBoundingBox(textRange.start).centerLeft
      )

      _rightSelectionHandle.value = SelectionHandle(
        textOffset = textRange.end,
        center = textLayoutResult.getBoundingBox(textRange.end - 1).centerRight
      )

      _extendingSelectionMode = true
      _pointerPosition = startPoint
    }
  }

  fun onDragProgress(delta: Offset) {
    val textLayoutResult = _textLayoutResultState.value
      ?: return
    val selectionPath = _selectionPath.value
      .takeIf { path -> !path.isEmpty }
      ?: return
    val leftSelectionHandle = _leftSelectionHandle.value
      .takeIf { selectionHandle -> !selectionHandle.isEmpty }
      ?: return
    val rightSelectionHandle = _rightSelectionHandle.value
      .takeIf { selectionHandle -> !selectionHandle.isEmpty }
      ?: return
    val selectionPosition = _pointerPosition
      .takeIf { offset -> offset.isSpecified }
      ?: return

    val text = textLayoutResult.layoutInput.text

    val prevLeftTextOffset = _leftSelectionHandle.value.textOffset
    val prevRightTextOffset = _rightSelectionHandle.value.textOffset

    val currentPointerPosition = _pointerPosition + delta
    _pointerPosition = currentPointerPosition

    if (_extendingSelectionMode) {
      val currentTextOffset = textLayoutResult.getOffsetForPosition(currentPointerPosition)
        .coerceIn(text.indices.first, text.indices.last + 1)

      if (currentTextOffset < leftSelectionHandle.textOffset) {
        _leftSelectionHandle.value = _leftSelectionHandle.value.copy(
          textOffset = currentTextOffset,
          center = textLayoutResult.getBoundingBox(currentTextOffset).centerLeft
        )
      } else if (currentTextOffset > rightSelectionHandle.textOffset) {
        _rightSelectionHandle.value = _rightSelectionHandle.value.copy(
          textOffset = currentTextOffset,
          center = textLayoutResult.getBoundingBox(currentTextOffset - 1).centerRight
        )
      }
    }

    if (
      prevLeftTextOffset != _leftSelectionHandle.value.textOffset ||
      prevRightTextOffset != _rightSelectionHandle.value.textOffset
    ) {
      _selectionPath.value = textLayoutResult.getPathForRange(
        _leftSelectionHandle.value.textOffset,
        _rightSelectionHandle.value.textOffset
      )
    }
  }

  fun onDragStop(stoppedNormally: Boolean) {
    _extendingSelectionMode = false
    _pointerPosition = Offset.Unspecified
  }

  fun onPointerUp() {
  }

  fun updateTextLayoutResult(textLayoutResult: TextLayoutResult) {
    Snapshot.withMutableSnapshot {
      resetEverything()
      _textLayoutResultState.value = textLayoutResult
    }
  }

  fun resetEverything() {
    Snapshot.withMutableSnapshot {
      _extendingSelectionMode = false
      _pointerPosition = Offset.Unspecified
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

  data class SelectionHandle(
    val textOffset: Int = -1,
    val center: Offset = Offset.Unspecified
  ) {
    val isEmpty: Boolean
      get() = textOffset < 0 || center.isUnspecified
  }
}

enum class InitialSelectionMode {
  // Double-tapping
  Word,

  // Trippe-tapping
  Sentence
}

@Composable
fun rememberTextSelectionState(): TextSelectionState {
  return remember { TextSelectionState() }
}