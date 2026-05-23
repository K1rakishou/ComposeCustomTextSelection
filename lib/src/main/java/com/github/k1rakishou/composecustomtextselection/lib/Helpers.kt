package com.github.k1rakishou.composecustomtextselection.lib

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal suspend fun PointerInputScope.textSelectionAfterDoubleTapOrTapWithLongTap(
  onClicked: (() -> Unit)?,
  onLongClicked: (() -> Unit)?,
  interactionSource: MutableInteractionSource,
  textSelectionState: TextSelectionState
) {
  coroutineScope {
    launch(start = CoroutineStart.UNDISPATCHED) {
      val pressRef = AtomicReference<PressInteraction.Press?>(null)

      detectPreDragGesturesWithObserver(
        onDown = { offset ->
          val press = PressInteraction.Press(offset)
          interactionSource.tryEmit(press)
          pressRef.store(press)

          textSelectionState.onPointerDown(offset)
        },
        onUp = {
          pressRef.exchange(null)
            ?.let { press -> interactionSource.tryEmit(PressInteraction.Release(press)) }

          textSelectionState.onPointerUp()
        },
      )
    }

    launch(start = CoroutineStart.UNDISPATCHED) {
      awaitEachGesture {
        val textSelectionGesture = detectTextSelectionGesture(
          onClicked = onClicked,
          onLongClicked = onLongClicked
        )
        if (textSelectionGesture == null) {
          return@awaitEachGesture
        }

        processDragEvents(
          textSelectionState = textSelectionState,
          textSelectionGesture = textSelectionGesture,
        )
      }
    }
  }
}

private suspend fun AwaitPointerEventScope.processDragEvents(
  textSelectionState: TextSelectionState,
  textSelectionGesture: TextSelectionGesture,
) {
  textSelectionState.onDragStart(
    startPoint = textSelectionGesture.position,
    initialSelectionMode = textSelectionGesture.initialSelectionMode
  )

  val stoppedNormally = drag(textSelectionGesture.id) { change ->
    textSelectionState.onDragProgress(change.positionChange())
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

  textSelectionState.onDragStop(stoppedNormally = stoppedNormally)
}

private suspend fun AwaitPointerEventScope.detectTextSelectionGesture(
  onClicked: (() -> Unit)?,
  onLongClicked: (() -> Unit)?,
): TextSelectionGesture? {
  val firstDown = awaitFirstDown()

  val longPressTimeout = viewConfiguration.longPressTimeoutMillis
  val doubleTapTimeout = viewConfiguration.doubleTapMinTimeMillis
  var upOrCancel: PointerInputChange? = null

  // wait for first tap up or long press
  upOrCancel = withTimeoutOrNull(longPressTimeout) {
    waitForUpOrCancellation()
  }

  if (upOrCancel == null) {
    // Long tap
    onLongClicked?.invoke()
    return null
  }

  val secondDown = awaitNextDown(upOrCancel)
  if (secondDown == null) {
    // Tap
    onClicked?.invoke()
    return null
  }

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

  val trippleTap = withTimeoutOrNull(longPressTimeout) {
    val thirdUp = waitForUpOrCancellation(
      minUptime = thirdDown.uptimeMillis + doubleTapTimeout,
      onNewPointerInputChange = { pointerInputChange ->
        if (pointerInputChange.pressed) {
          lastPointerInputChange = pointerInputChange
        }
      }
    )

    if (thirdUp != null) {
      thirdUp.consume()
      return@withTimeoutOrNull thirdUp
    }

    return@withTimeoutOrNull null
  }

  if (trippleTap == null) {
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

  // Tripple tap
  return TextSelectionGesture.SelectSentence(
    position = trippleTap.position,
    id = trippleTap.id
  )
}

private suspend fun AwaitPointerEventScope.waitForUpOrCancellation(
  minUptime: Long,
  onNewPointerInputChange: (PointerInputChange) -> Unit
): PointerInputChange? {
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

private suspend fun PointerInputScope.detectPreDragGesturesWithObserver(
  onDown: (Offset) -> Unit,
  onUp: () -> Unit,
) {
  awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    onDown(down.position)

    // Wait for that pointer to come up.

    try {
      do {
        val event = awaitPointerEvent()
      } while (event.changes.any { it.id == down.id && it.pressed })
    } finally {
      onUp()
    }
  }
}

private sealed interface TextSelectionGesture {
  val position: Offset
  val id: PointerId
  val initialSelectionMode: InitialSelectionMode

  data class SelectWord(
    override val position: Offset,
    override val id: PointerId,
  ) : TextSelectionGesture {
    override val initialSelectionMode: InitialSelectionMode
      get() = InitialSelectionMode.Word
  }

  data class SelectSentence(
    override val position: Offset,
    override val id: PointerId,
  ) : TextSelectionGesture {
    override val initialSelectionMode: InitialSelectionMode
      get() = InitialSelectionMode.Sentence
  }
}