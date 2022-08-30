package com.github.k1rakishou.composecustomtextselection.lib

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InternalFoundationTextApi
import androidx.compose.foundation.text.TextDelegate
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection

private val DefaultSelectionHandleContentFunc = @Composable { position: Offset,
                                                              isStartHandle: Boolean,
                                                              direction: ResolvedTextDirection,
                                                              handlesCrossed: Boolean,
                                                              modifier: Modifier ->
  DefaultSelectionHandleContent(
    position = position,
    isStartHandle = isStartHandle,
    direction = direction,
    handlesCrossed = handlesCrossed,
    modifier = modifier
  )
}

@Composable
fun SelectableTextContainer(
  modifier: Modifier = Modifier,
  selectionState: SelectionState,
  configurableTextToolbar: ConfigurableTextToolbar,
  onEnteredSelection: (() -> Unit)? = null,
  onExitedSelection: (() -> Unit)? = null,
  selectionHandleContent: @Composable ((Offset, Boolean, ResolvedTextDirection, Boolean, Modifier) -> Unit) = DefaultSelectionHandleContentFunc,
  textContent: @Composable (modifier: Modifier, onTextLayout: (TextLayoutResult) -> Unit) -> Unit
) {
  val selectionRegistrar = remember { SelectionRegistrarImpl() }
  val selectableId = rememberSaveable(selectionRegistrar, saver = selectionIdSaver(selectionRegistrar)) {
    selectionRegistrar.nextSelectableId()
  }

  val textState = remember(key1 = selectionRegistrar) { TextState(selectableId) }
  var layoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

  var isInSelectionMode by remember { mutableStateOf(false) }
  var selection by remember { mutableStateOf<Selection?>(null) }

  val textDragObserver = remember(
    key1 = layoutCoordinates,
    key2 = textState,
    key3 = selectionRegistrar
  ) {
    CustomTextDragObserver(
      layoutCoordinates = layoutCoordinates,
      textState = textState,
      selectionRegistrar = selectionRegistrar
    )
  }

  selectionState.updateTextDragObserver(textDragObserver)

  DisposableEffect(
    key1 = Unit,
    effect = {
      textState.selectable = selectionRegistrar.subscribe(
        MultiWidgetSelectionDelegate(
          selectableId = textState.selectableId,
          coordinatesCallback = { textState.layoutCoordinates },
          layoutResultCallback = { textState.layoutResult }
        )
      )

      onDispose {
        textState.selectable?.let { selectionRegistrar.unsubscribe(it) }
      }
    })

  SelectionContainer(
    modifier = modifier,
    selection = selection,
    selectionRegistrar = selectionRegistrar,
    configurableTextToolbar = configurableTextToolbar,
    onSelectionChange = { newSelection ->
      val wasInSelectionMode = isInSelectionMode
      val nowInSelectionMode = newSelection != null

      if (!wasInSelectionMode && nowInSelectionMode) {
        onEnteredSelection?.invoke()
      } else if (wasInSelectionMode && !nowInSelectionMode) {
        onExitedSelection?.invoke()
      }

      isInSelectionMode = nowInSelectionMode
      selection = newSelection
    },
    selectionHandleContent = selectionHandleContent
  ) {
    val selectionBackgroundColor = LocalTextSelectionColors.current.backgroundColor

    textContent(
      modifier = Modifier
        .drawTextAndSelectionBehind(
          selectionBackgroundColor = selectionBackgroundColor,
          selectionRegistrar = selectionRegistrar,
          textState = textState
        )
        .onGloballyPositioned { coordinates ->
          layoutCoordinates = coordinates
          textState.layoutCoordinates = coordinates

          if (selectionRegistrar.hasSelection(textState.selectableId)) {
            val newGlobalPosition = coordinates.positionInWindow()
            if (newGlobalPosition != textState.previousGlobalPosition) {
              selectionRegistrar.notifyPositionChange(textState.selectableId)
            }

            textState.previousGlobalPosition = newGlobalPosition
          }
        },
      onTextLayout = { textLayoutResult ->
        textState.layoutResult = textLayoutResult
      }
    )
  }
}

@OptIn(InternalFoundationTextApi::class)
@Stable
private fun Modifier.drawTextAndSelectionBehind(
  selectionBackgroundColor: Color,
  selectionRegistrar: SelectionRegistrar,
  textState: TextState
): Modifier {
  return this
    .graphicsLayer()
    .drawBehind {
      textState.layoutResult?.let {
        textState.drawScopeInvalidation
        val selection = selectionRegistrar.subselections.get(textState.selectableId)

        if (selection != null) {
          val start = if (!selection.handlesCrossed) {
            selection.start.offset
          } else {
            selection.end.offset
          }
          val end = if (!selection.handlesCrossed) {
            selection.end.offset
          } else {
            selection.start.offset
          }

          if (start != end) {
            val selectionPath = it.multiParagraph.getPathForRange(start, end)
            drawPath(selectionPath, selectionBackgroundColor)
          }
        }
        drawIntoCanvas { canvas ->
          TextDelegate.paint(canvas, it)
        }
      }
    }
}

private fun outOfBoundary(textState: TextState, start: Offset, end: Offset): Boolean {
  textState.layoutResult?.let {
    val lastOffset = it.layoutInput.text.text.length
    val rawStartOffset = it.getOffsetForPosition(start)
    val rawEndOffset = it.getOffsetForPosition(end)

    return rawStartOffset >= lastOffset - 1 && rawEndOffset >= lastOffset - 1 ||
      rawStartOffset < 0 && rawEndOffset < 0
  }

  return false
}

private fun selectionIdSaver(selectionRegistrar: SelectionRegistrar?) = Saver<Long, Long>(
  save = { if (selectionRegistrar.hasSelection(it)) it else null },
  restore = { it }
)

class SelectionState {
  private var _textDragObserver: TextDragObserver? = null
  val textDragObserver: TextDragObserver?
    get() = _textDragObserver

  fun stopSelection() {
    textDragObserver?.onStop()
  }

  fun updateTextDragObserver(textDragObserver: TextDragObserver) {
    _textDragObserver = textDragObserver
  }
}

@Composable
fun rememberSelectionState(): SelectionState {
  return remember { SelectionState() }
}

private class TextState(
  val selectableId: Long
) {

  /** The [Selectable] associated with this [BasicText]. */
  var selectable: Selectable? = null

  /** The last layout coordinates for the Text's layout, used by selection */
  var layoutCoordinates: LayoutCoordinates? = null

  /** The latest TextLayoutResult calculated in the measure block.*/
  var layoutResult: TextLayoutResult? = null
    set(value) {
      drawScopeInvalidation = Unit
      field = value
    }

  /** The global position calculated during the last notifyPosition callback */
  var previousGlobalPosition: Offset = Offset.Zero

  /** Read in draw scopes to invalidate when layoutResult  */
  var drawScopeInvalidation by mutableStateOf(Unit, neverEqualPolicy())
    private set
}

private class CustomTextDragObserver(
  private val layoutCoordinates: LayoutCoordinates?,
  private val textState: TextState,
  private val selectionRegistrar: SelectionRegistrar
) : TextDragObserver {
  /**
   * The beginning position of the drag gesture. Every time a new drag gesture starts, it wil be
   * recalculated.
   */
  var lastPosition = Offset.Zero

  /**
   * The total distance being dragged of the drag gesture. Every time a new drag gesture starts,
   * it will be zeroed out.
   */
  var dragTotalDistance = Offset.Zero

  override fun onDown(point: Offset) {
    // no-op
  }

  override fun onUp() {
    // no-op
  }

  override fun onStart(startPoint: Offset) {
    layoutCoordinates?.let {
      if (!it.isAttached) {
        return
      }

      if (outOfBoundary(textState, startPoint, startPoint)) {
        selectionRegistrar.notifySelectionUpdateSelectAll(
          selectableId = textState.selectableId
        )
      } else {
        selectionRegistrar.notifySelectionUpdateStart(
          layoutCoordinates = it,
          startPosition = startPoint,
          adjustment = SelectionAdjustment.Word
        )
      }

      lastPosition = startPoint
    }

    // selection never started
    if (!selectionRegistrar.hasSelection(textState.selectableId)) {
      return
    }

    // Zero out the total distance that being dragged.
    dragTotalDistance = Offset.Zero
  }

  override fun onDrag(delta: Offset) {
    layoutCoordinates?.let {
      if (!it.isAttached) {
        return
      }

      // selection never started, did not consume any drag
      if (!selectionRegistrar.hasSelection(textState.selectableId)) {
        return
      }

      dragTotalDistance += delta
      val newPosition = lastPosition + dragTotalDistance

      if (!outOfBoundary(textState, lastPosition, newPosition)) {
        // Notice that only the end position needs to be updated here.
        // Start position is left unchanged. This is typically important when
        // long-press is using SelectionAdjustment.WORD or
        // SelectionAdjustment.PARAGRAPH that updates the start handle position from
        // the dragBeginPosition.
        val consumed = selectionRegistrar.notifySelectionUpdate(
          layoutCoordinates = it,
          previousPosition = lastPosition,
          newPosition = newPosition,
          isStartHandle = false,
          adjustment = SelectionAdjustment.CharacterWithWordAccelerate
        )

        if (consumed) {
          lastPosition = newPosition
          dragTotalDistance = Offset.Zero
        }
      }
    }
  }

  override fun onStop() {
    if (selectionRegistrar.hasSelection(textState.selectableId)) {
      selectionRegistrar.notifySelectionUpdateEnd()
    }
  }

  override fun onCancel() {
    if (selectionRegistrar.hasSelection(textState.selectableId)) {
      selectionRegistrar.notifySelectionUpdateEnd()
    }
  }

}