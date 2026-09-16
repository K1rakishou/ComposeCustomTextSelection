package com.github.k1rakishou.composecustomtextselection.lib

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.text.BreakIterator
import java.util.Locale

class SelectableTextState(
  val handleSize: Size,
  selectionColor: Color,
  val debugMode: Boolean
) {
  private val _uiEventFlow = MutableSharedFlow<UiEvent>(
    extraBufferCapacity = 16,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )
  internal val uiEventFlow: SharedFlow<UiEvent>
    get() = _uiEventFlow.asSharedFlow()

  private val _textLayoutResultState = mutableStateOf<TextLayoutResult?>(null)
  val textLayoutResult: TextLayoutResult?
    get() = _textLayoutResultState.value
  private val _selectionColor = mutableStateOf(selectionColor)
  val selectionColor: State<Color>
    get() = _selectionColor
  private val _localPointerPosition = mutableStateOf<Offset?>(null)
  val localPointerPosition: State<Offset?>
    get() = _localPointerPosition
  private var _dragMode = mutableStateOf<DragMode?>(null)
  val dragMode: State<DragMode?>
    get() = _dragMode

  private val _selectableTextLayoutCoordinates = mutableStateOf<LayoutCoordinates?>(null)
  val selectableTextLayoutCoordinates: LayoutCoordinates?
    get() = _selectableTextLayoutCoordinates.value

  private var _grabOffset = Offset.Zero

  internal val leftSelectionHandle = SelectionHandle(handleSize)
  internal val rightSelectionHandle = SelectionHandle(handleSize)

  val handlesCrossed: Boolean
    get() = leftSelectionHandle.textOffset > rightSelectionHandle.textOffset

  val startSelectionHandle: SelectionHandle
    get() {
      return if (!handlesCrossed) {
        leftSelectionHandle
      } else {
        rightSelectionHandle
      }
    }

  val endSelectionHandle: SelectionHandle
    get() {
      return if (!handlesCrossed) {
        rightSelectionHandle
      } else {
        leftSelectionHandle
      }
    }

  val hasSelection: Boolean
    get() = _dragMode.value != null && leftSelectionHandle.isInitialized && rightSelectionHandle.isInitialized

  val selectedTextRange: TextRange?
    get() {
      if (!hasSelection) {
        return null
      }

      val start = startSelectionHandle.textOffset
      val end = endSelectionHandle.textOffset
      if (start >= end) {
        return null
      }

      return TextRange(start, end)
    }

  val selectedText: AnnotatedString?
    get() {
      val textRange = selectedTextRange ?: return null
      val text = textLayoutResult?.layoutInput?.text ?: return null

      return text.subSequence(
        startIndex = textRange.start.coerceIn(0, text.length),
        endIndex = textRange.end.coerceIn(0, text.length)
      )
    }

  fun isStartHandle(handle: SelectionHandle): Boolean {
    return handle === startSelectionHandle
  }

  fun isEndHandle(handle: SelectionHandle): Boolean {
    return handle === endSelectionHandle
  }

  fun updateTextLayoutResult(textLayoutResult: TextLayoutResult) {
    Snapshot.withMutableSnapshot {
      _textLayoutResultState.value = textLayoutResult
    }
  }

  fun updateSelectableTextLayoutCoordinates(layoutCoordinates: LayoutCoordinates) {
    _selectableTextLayoutCoordinates.value = layoutCoordinates
  }

  fun onDragStart(fingerPosition: Offset, dragMode: DragMode) {
    val textLayoutResult = _textLayoutResultState.value ?: return
    val fullText = textLayoutResult.layoutInput.text.text

    when (dragMode) {
      is DragMode.DraggingHandle -> {
        if (!leftSelectionHandle.isInitialized || !rightSelectionHandle.isInitialized) {
          return
        }

        val isStartHandle = isStartHandle(dragMode.dragged)
        val handleBBox = dragMode.dragged.textRelativeHandleBBox(isStartHandle = isStartHandle)
          ?: return

        // The handle hangs below the character it is attached to, on its left side for the start handle and on
        // its right side for the end handle.
        _grabOffset = Offset(
          x = if (isStartHandle) handleBBox.width / 2f else -handleBBox.width / 2f,
          y = -handleBBox.height
        )
      }
      is DragMode.ExtendingSelection -> {
        val charOffset = textLayoutResult.getOffsetForPosition(fingerPosition)

        val newTextRange = when (dragMode.initialSelectionMode) {
          InitialSelectionMode.Word -> textLayoutResult.getWordBoundary(charOffset)
          InitialSelectionMode.Sentence -> getSentenceBoundary(fullText, textLayoutResult, charOffset)
        }

        val textRange = removeWhitespaces(fullText, newTextRange)
        if (textRange.reversed || textRange.collapsed) {
          return
        }

        leftSelectionHandle.update(
          textOffset = textRange.start,
          charBBox = textLayoutResult.getBoundingBox(textRange.start),
        )

        rightSelectionHandle.update(
          textOffset = textRange.end,
          charBBox = textLayoutResult.getBoundingBox(textRange.end - 1),
        )

        _grabOffset = Offset.Zero
        _uiEventFlow.tryEmit(UiEvent.PerformHapticFeedback(HapticFeedbackType.LongPress))
      }
    }

    _dragMode.value = dragMode
    _localPointerPosition.value = fingerPosition + _grabOffset
    _uiEventFlow.tryEmit(UiEvent.RequestFocus)
  }

  fun onDragProgress(fingerPosition: Offset) {
    if (!startSelectionHandle.isInitialized || !endSelectionHandle.isInitialized) {
      return
    }

    val textLayoutResult = _textLayoutResultState.value ?: return
    val dragMode = _dragMode.value ?: return
    if (_localPointerPosition.value == null) {
      return
    }

    val text = textLayoutResult.layoutInput.text

    val newPointerPosition = fingerPosition + _grabOffset
    _localPointerPosition.value = newPointerPosition

    val newTextOffset = textLayoutResult.getOffsetForPosition(newPointerPosition)
      .coerceIn(0, text.lastIndex)

    val prevLeftOffset = leftSelectionHandle.textOffset
    val prevRightOffset = rightSelectionHandle.textOffset

    when (dragMode) {
      is DragMode.ExtendingSelection -> {
        if (newTextOffset < startSelectionHandle.textOffset) {
          startSelectionHandle.update(
            textOffset = newTextOffset,
            charBBox = textLayoutResult.getBoundingBox(newTextOffset)
          )
        } else if (newTextOffset > endSelectionHandle.textOffset) {
          endSelectionHandle.update(
            textOffset = newTextOffset,
            charBBox = textLayoutResult.getBoundingBox(newTextOffset - 1)
          )
        }
      }
      is DragMode.DraggingHandle -> {
        val dragged = dragMode.dragged
        val other = if (dragged === leftSelectionHandle) {
          rightSelectionHandle
        } else {
          leftSelectionHandle
        }

        val safeOffset = when {
          newTextOffset < other.textOffset -> newTextOffset
          newTextOffset > other.textOffset -> newTextOffset
          else -> {
            if (dragged.textOffset < other.textOffset) {
              other.textOffset - 1
            } else {
              other.textOffset + 1
            }
          }
        }.coerceIn(0, text.lastIndex)

        val bboxOffset = if (safeOffset < other.textOffset) {
          safeOffset
        } else {
          safeOffset - 1
        }

        dragged.update(
          textOffset = safeOffset,
          charBBox = textLayoutResult.getBoundingBox(bboxOffset.coerceAtLeast(0))
        )
      }
    }

    if (prevLeftOffset != leftSelectionHandle.textOffset || prevRightOffset != rightSelectionHandle.textOffset) {
      _uiEventFlow.tryEmit(UiEvent.PerformHapticFeedback(HapticFeedbackType.TextHandleMove))
    }
  }

  fun onDragStop() {
    _localPointerPosition.value = null
  }

  fun resetEverything() {
    Snapshot.withMutableSnapshot {
      _localPointerPosition.value = null
      _dragMode.value = null
      _grabOffset = Offset.Zero
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

  internal sealed interface UiEvent {
    data object RequestFocus : UiEvent
    data class PerformHapticFeedback(val type: HapticFeedbackType) : UiEvent
  }
}

sealed interface DragMode {
  data class DraggingHandle(val dragged: SelectionHandle) : DragMode
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
  handleSize: Dp,
  debugMode: Boolean = false,
): SelectableTextState {
  val density = LocalDensity.current

  return remember {
    SelectableTextState(
      handleSize = with(density) { Size(handleSize.toPx(), handleSize.toPx()) },
      selectionColor = selectionColor,
      debugMode = debugMode
    )
  }
}
