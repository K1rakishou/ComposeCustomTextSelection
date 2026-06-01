package com.github.k1rakishou.composecustomtextselection.lib

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import kotlinx.coroutines.coroutineScope
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

internal suspend fun PointerInputScope.textSelectionAfterHandleDrag(
  isLeftHandle: Boolean,
  selectionHandle: SelectionHandle,
  selectableTextState: SelectableTextState
) {
  detectDragGestures(
    onDragStart = { popupRelativeOffset ->
      val textRelativeOffset = popupRelativeOffset.popupToLocal(selectionHandle)
        ?: return@detectDragGestures

      selectableTextState.onDragStart(
        startPoint = textRelativeOffset,
        dragMode = DragMode.DraggingHandle(
          dragged = selectableTextState.grabHandleForDragging(isLeftHandle)
        )
      )
    },
    onDrag = { _, delta -> selectableTextState.onDragProgress(delta) },
    onDragEnd = { selectableTextState.onDragStop() },
    onDragCancel = { selectableTextState.onDragStop() }
  )
}

@OptIn(ExperimentalAtomicApi::class)
internal suspend fun PointerInputScope.textSelectionAfterDoubleTapOrTapWithLongTap(
  onClicked: (() -> Unit)?,
  onLongClicked: (() -> Unit)?,
  interactionSource: MutableInteractionSource,
  selectableTextState: SelectableTextState
) {
  coroutineScope {
    awaitEachGesture {
      val pressRef = AtomicReference<PressInteraction.Press?>(null)

      val textSelectionGesture = detectTextSelectionGesture(
        selectableTextState = selectableTextState,
        onDown = { offset ->
          val press = PressInteraction.Press(offset)
          interactionSource.tryEmit(press)
          pressRef.store(press)
        },
        onUp = {
          pressRef.exchange(null)
            ?.let { press -> interactionSource.tryEmit(PressInteraction.Release(press)) }
        },
        onResetSelection = {
          selectableTextState.resetEverything()
        },
        onClicked = onClicked,
        onLongClicked = onLongClicked
      )
      if (textSelectionGesture == null) {
        return@awaitEachGesture
      }

      selectableTextState.onDragStart(
        startPoint = textSelectionGesture.position,
        dragMode = textSelectionGesture.dragMode,
      )

      val stoppedNormally = drag(textSelectionGesture.id) { change ->
        selectableTextState.onDragProgress(change.positionChange())
        change.consume()
      }

      if (stoppedNormally) {
        // consume up if we quit drag gracefully with the up
        currentEvent.changes.forEach { change ->
          if (change.changedToUp()) {
            change.consume()
          }
        }
      }

      selectableTextState.onDragStop()
    }
  }
}

private suspend fun AwaitPointerEventScope.detectTextSelectionGesture(
  selectableTextState: SelectableTextState,
  onDown: (Offset) -> Unit,
  onUp: () -> Unit,
  onResetSelection: () -> Unit,
  onClicked: (() -> Unit)?,
  onLongClicked: (() -> Unit)?,
): TextSelectionGesture? {
  val firstDown = awaitFirstDown()
  onDown(firstDown.position)

  val longPressTimeout = viewConfiguration.longPressTimeoutMillis
  val doubleTapTimeout = viewConfiguration.doubleTapMinTimeMillis

  var upOrCancel: PointerInputChange? = null
  var slopExceeded = false

  upOrCancel = withTimeoutOrNull(longPressTimeout) {
    waitForUpOrCancellation(
      onTouchSlopExceeded = { slopExceeded = true }
    )
  }

  if (slopExceeded) {
    // Scroll
    onUp()
    return null
  }

  if (selectableTextState.dragMode.value != null) {
    onResetSelection()
  }

  if (upOrCancel == null) {
    // Long tap
    onLongClicked?.invoke()
    onUp()
    return null
  }

  val secondDown = awaitNextDown(upOrCancel)
  if (secondDown == null) {
    // Tap
    onClicked?.invoke()
    onUp()
    return null
  }

  onDown(secondDown.position)

  upOrCancel.consume()
  firstDown.consume()
  secondDown.consume()

  var lastPointerInputChange: PointerInputChange? = null

  val doubleTap = withTimeoutOrNull(longPressTimeout) {
    val secondUp = waitForUpOrCancellation(
      minUptime = secondDown.uptimeMillis + doubleTapTimeout,
      onNewPointerInputChange = { pointerInputChange ->
        if (pointerInputChange.pressed) {
          lastPointerInputChange = pointerInputChange
        }
      }
    )

    onUp()

    if (secondUp != null) {
      secondUp.consume()
      return@withTimeoutOrNull secondUp
    }

    return@withTimeoutOrNull null
  }

  if (doubleTap == null) {
    if (lastPointerInputChange == null) {
      // Tap
      return null
    }

    // Tap + longtap
    return TextSelectionGesture.SelectWord(
      position = lastPointerInputChange.position,
      id = lastPointerInputChange.id
    )
  }

  val thirdDown = awaitNextDown(doubleTap)
  if (thirdDown == null) {
    // Double tap
    return TextSelectionGesture.SelectWord(
      position = doubleTap.position,
      id = doubleTap.id
    )
  }

  lastPointerInputChange = null
  doubleTap.consume()
  onDown(secondDown.position)

  val tripleTap = withTimeoutOrNull(longPressTimeout) {
    val thirdUp = waitForUpOrCancellation(
      minUptime = thirdDown.uptimeMillis + doubleTapTimeout,
      onNewPointerInputChange = { pointerInputChange ->
        if (pointerInputChange.pressed) {
          lastPointerInputChange = pointerInputChange
        }
      }
    )

    onUp()

    if (thirdUp != null) {
      thirdUp.consume()
      return@withTimeoutOrNull thirdUp
    }

    return@withTimeoutOrNull null
  }

  if (tripleTap == null) {
    val localLastPointerInputChange = lastPointerInputChange
    if (localLastPointerInputChange == null) {
      // Double tap
      return TextSelectionGesture.SelectWord(
        position = doubleTap.position,
        id = doubleTap.id
      )
    }

    // Double tap + long tap
    return TextSelectionGesture.SelectSentence(
      position = localLastPointerInputChange.position,
      id = localLastPointerInputChange.id
    )
  }

  // Triple tap
  return TextSelectionGesture.SelectSentence(
    position = tripleTap.position,
    id = tripleTap.id
  )
}

private suspend fun AwaitPointerEventScope.awaitNextDown(
  prevUp: PointerInputChange
): PointerInputChange? {
  return withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
    val minUptime = prevUp.uptimeMillis + viewConfiguration.doubleTapMinTimeMillis
    var change: PointerInputChange
    // The next tap doesn't count if it happens before DoubleTapMinTime of the first tap
    do {
      change = awaitFirstDown()
    } while (change.uptimeMillis < minUptime)
    change
  }
}

private suspend fun AwaitPointerEventScope.waitForUpOrCancellation(
  minUptime: Long = 0,
  onNewPointerInputChange: (PointerInputChange) -> Unit = {},
  onTouchSlopExceeded: () -> Unit = {},
): PointerInputChange? {
  var touchSlopExceeded = false
  val touchSlop = viewConfiguration.touchSlop
  var totalDrag = Offset.Zero

  while (true) {
    val event = awaitPointerEvent(PointerEventPass.Main)
    if (event.changes.fastAll { it.changedToUp() }) {
      val up = event.changes[0]
      if (up.uptimeMillis >= minUptime) {
        return up
      }

      continue
    }

    if (event.changes.fastAny { it.isConsumed || it.isOutOfBounds(size, extendedTouchPadding) }) {
      return null
    }

    // Track drag distance for touch slop
    if (!touchSlopExceeded) {
      val change = event.changes.firstOrNull()
      if (change != null) {
        totalDrag += change.positionChange()
        if (totalDrag.getDistance() > touchSlop) {
          touchSlopExceeded = true
          onTouchSlopExceeded()
        }
      }
    }

    val pointerInputChange = event.changes.firstOrNull()
    if (pointerInputChange != null) {
      onNewPointerInputChange(pointerInputChange)
    }

    val consumeCheck = awaitPointerEvent(PointerEventPass.Final)
    if (consumeCheck.changes.fastAny { it.isConsumed }) {
      return null
    }
  }
}

private sealed interface TextSelectionGesture {
  val position: Offset
  val id: PointerId
  val dragMode: DragMode

  data class SelectWord(
    override val position: Offset,
    override val id: PointerId,
  ) : TextSelectionGesture {
    override val dragMode: DragMode
      get() = DragMode.ExtendingSelection(InitialSelectionMode.Word)
  }

  data class SelectSentence(
    override val position: Offset,
    override val id: PointerId,
  ) : TextSelectionGesture {
    override val dragMode: DragMode
      get() = DragMode.ExtendingSelection(InitialSelectionMode.Sentence)
  }
}