package com.github.k1rakishou.composecustomtextselection.lib

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.channels.Channel
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
  private val _focusEventFlow = MutableSharedFlow<Unit>(extraBufferCapacity = Channel.RENDEZVOUS)
  val focusEventFlow: SharedFlow<Unit>
    get() = _focusEventFlow.asSharedFlow()

  private val _textLayoutResultState = mutableStateOf<TextLayoutResult?>(null)
  val textLayoutResult: TextLayoutResult?
    get() = _textLayoutResultState.value
  private val _selectionColor = mutableStateOf(selectionColor)
  val selectionColor: State<Color>
    get() = _selectionColor
  private val _localPointerPosition = mutableStateOf<Offset?>(null)
  val localPointerPosition: State<Offset?>
    get() = _localPointerPosition

  private var _dragMode: DragMode? = null
  val dragMode: DragMode?
    get() = _dragMode

  private val _leftSelectionHandle = SelectionHandle(handleSize)
  private val _rightSelectionHandle = SelectionHandle(handleSize)

  val handlesCrossed: Boolean
    get() = _leftSelectionHandle.textOffset > _rightSelectionHandle.textOffset

  val startSelectionHandle: SelectionHandle
    get() {
      return if (!handlesCrossed) {
        _leftSelectionHandle
      } else {
        _rightSelectionHandle
      }
    }

  val endSelectionHandle: SelectionHandle
    get() {
      return if (!handlesCrossed) {
        _rightSelectionHandle
      } else {
        _leftSelectionHandle
      }
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
    startSelectionHandle.updateSelectableTextLayoutCoordinates(layoutCoordinates)
    endSelectionHandle.updateSelectableTextLayoutCoordinates(layoutCoordinates)
  }

  fun updatePopupLayoutCoordinates(isLeftHandle: Boolean, layoutCoordinates: LayoutCoordinates) {
    if (isLeftHandle) {
      startSelectionHandle.updatePopupLayoutCoordinates(layoutCoordinates)
    } else {
      endSelectionHandle.updatePopupLayoutCoordinates(layoutCoordinates)
    }
  }

  fun grabHandleForDragging(isLeftHandle: Boolean): SelectionHandle {
    return if (isLeftHandle) {
      _leftSelectionHandle
    } else {
      _rightSelectionHandle
    }
  }

  fun onDragStart(startPoint: Offset, dragMode: DragMode) {
    val textLayoutResult = _textLayoutResultState.value ?: return
    val fullText = textLayoutResult.layoutInput.text.text

    when (dragMode) {
      is DragMode.DraggingHandle -> {
        check(_leftSelectionHandle.isInitialized) { "leftSelectionHandle is not initialized" }
        check(_rightSelectionHandle.isInitialized) { "rightSelectionHandle is not initialized" }
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

        _leftSelectionHandle.update(
          textOffset = textRange.start,
          charBBox = textLayoutResult.getBoundingBox(textRange.start),
        )

        _rightSelectionHandle.update(
          textOffset = textRange.end,
          charBBox = textLayoutResult.getBoundingBox(textRange.end - 1),
        )
      }
    }

    _dragMode = dragMode
    _localPointerPosition.value = run {
      if (dragMode !is DragMode.DraggingHandle) {
        return@run startPoint
      }

      val isLeftHandle = dragMode.dragged == _leftSelectionHandle
      val handleBBox = checkNotNull(
        startSelectionHandle.textRelativeHandleBBox(left = isLeftHandle)
      )

      var updatedPosition = startPoint
      updatedPosition -= Offset(x = 0f, y = handleBBox.height)

      if (isLeftHandle) {
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
    if (!startSelectionHandle.isInitialized || !endSelectionHandle.isInitialized) {
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

    run {
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
          val other = if (dragged === _leftSelectionHandle) {
            _rightSelectionHandle
          } else {
            _leftSelectionHandle
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
    }
  }

  fun onDragStop() {
    _localPointerPosition.value = null
  }

  fun resetEverything() {
    Snapshot.withMutableSnapshot {
      _localPointerPosition.value = null
      _dragMode = null
      startSelectionHandle.reset()
      endSelectionHandle.reset()
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