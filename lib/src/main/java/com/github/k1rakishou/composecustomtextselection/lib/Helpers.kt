package com.github.k1rakishou.composecustomtextselection.lib

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Detects either a double-tap or a tap + longtap.
 * In case of double-tap you can then move text selection handles to change text selection.
 * In case of tap + longtap you can start selecting text right away without having to lift the finger up
 * by moving it around (Just like in the default Compose text selection but it's tap + longtap instead of just longtap).
 * */
suspend fun PointerInputScope.textSelectionAfterDoubleTapOrTapWithLongTap(
  selectionState: SelectionState
) {
  var stopOrCancelJob: Job? = null

  coroutineScope {
    launch {
      detectPreDragGesturesWithObserver(
        onDown = { offset -> selectionState.textDragObserver?.onDown(offset) },
        onUp = { selectionState.textDragObserver?.onUp() },
      )
    }

    launch {
      forEachGesture {
        awaitPointerEventScope {
          val doubleTapOrTapWithLongTap = detectDoubleTapOrTapWithLongTap()
          if (doubleTapOrTapWithLongTap == null) {
            return@awaitPointerEventScope
          }

          stopOrCancelJob?.cancel()

          selectionState.textDragObserver?.onStart(doubleTapOrTapWithLongTap.position)

          val stoppedNormally = if (
            drag(doubleTapOrTapWithLongTap.id) {
              selectionState.textDragObserver?.onDrag(it.positionChange())
              it.consume()
            }
          ) {
            // consume up if we quit drag gracefully with the up
            currentEvent.changes.forEach {
              if (it.changedToUp()) it.consume()
            }

            true
          } else {
            false
          }

          // HACK! Since we are using double-tap to show text selection, we need to use a slight delay
          // so that the parent and all children have time to get recomposed with the new Selection,
          // otherwise if onStop/onCancel is called right away, the Selection will be set to null,
          // so when showSelectionToolbar() is called the toolbar won't be shown since
          // current selection is null.
          stopOrCancelJob = launch {
            // Let's just assume 2 frames (assuming 60fps refresh rate) is enough for everyone!
            try {
              delay(32)
            } catch (e: CancellationException) {
              selectionState.textDragObserver?.onCancel()
              throw e
            }

            if (stoppedNormally) {
              selectionState.textDragObserver?.onStop()
            } else {
              selectionState.textDragObserver?.onCancel()
            }

            stopOrCancelJob = null
          }
        }
      }
    }
  }
}

suspend fun AwaitPointerEventScope.detectDoubleTapOrTapWithLongTap(): PointerInputChange? {
  val firstDown = awaitFirstDown()

  val firstLongPressTimeout = Long.MAX_VALUE / 2
  var upOrCancel: PointerInputChange? = null

  try {
    // wait for first tap up or long press
    upOrCancel = withTimeout(firstLongPressTimeout) {
      waitForUpOrCancellation()
    }
  } catch (_: PointerEventTimeoutCancellationException) {
    return null
  }

  if (upOrCancel == null) {
    return null
  }

  val secondDown = awaitSecondDown(upOrCancel)
  if (secondDown == null) {
    return null
  }

  upOrCancel.consume()
  firstDown.consume()
  secondDown.consume()

  val secondLongPressTimeout = viewConfiguration.longPressTimeoutMillis
  var lastPointerInputChange: PointerInputChange? = secondDown

  val doubleTap = withTimeoutOrNull(secondLongPressTimeout) {
    val secondUp = waitForUpOrCancellation(
      minUptime = secondDown.uptimeMillis + viewConfiguration.doubleTapMinTimeMillis,
      onNewPointerInputChange = { pointerInputChange ->
        pointerInputChange.consume()
        lastPointerInputChange = pointerInputChange
      }
    )

    // Double tap happened
    if (secondUp != null) {
      secondUp.consume()
      return@withTimeoutOrNull secondUp
    }

    return@withTimeoutOrNull null
  }

  // if doubleTap is null that means a longtap happened instead
  return doubleTap ?: lastPointerInputChange
}

private suspend fun AwaitPointerEventScope.waitForUpOrCancellation(
  minUptime: Long,
  onNewPointerInputChange: (PointerInputChange) -> Unit
): PointerInputChange? {
  while (true) {
    val event = awaitPointerEvent(PointerEventPass.Main)
    if (event.changes.fastAll { it.changedToUp() }) {
      // All pointers are up
      return event.changes[0]
    }

    if (event.changes.fastAny {
        it.isConsumed || it.isOutOfBounds(size, extendedTouchPadding)
      }
    ) {
      return null // Canceled
    }

    val pointerInputChange = event.changes.firstOrNull()
    if (pointerInputChange != null) {
      onNewPointerInputChange(pointerInputChange)

      if (pointerInputChange.uptimeMillis > minUptime) {
        return null
      }
    }

    // Check for cancel by position consumption. We can look on the Final pass of the
    // existing pointer event because it comes after the Main pass we checked above.
    val consumeCheck = awaitPointerEvent(PointerEventPass.Final)
    if (consumeCheck.changes.fastAny { it.isConsumed }) {
      return null
    }
  }
}

private suspend fun AwaitPointerEventScope.awaitSecondDown(
  firstUp: PointerInputChange
): PointerInputChange? = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
  val minUptime = firstUp.uptimeMillis + viewConfiguration.doubleTapMinTimeMillis
  var change: PointerInputChange
  // The second tap doesn't count if it happens before DoubleTapMinTime of the first tap
  do {
    change = awaitFirstDown()
  } while (change.uptimeMillis < minUptime)
  change
}

private suspend fun PointerInputScope.detectPreDragGesturesWithObserver(
  onDown: (Offset) -> Unit,
  onUp: () -> Unit,
) {
  forEachGesture {
    awaitPointerEventScope {
      val down = awaitFirstDown(requireUnconsumed = false)
      onDown(down.position)
      // Wait for that pointer to come up.
      do {
        val event = awaitPointerEvent()
      } while (event.changes.any { it.id == down.id && it.pressed })
      onUp()
    }
  }
}
