package com.github.k1rakishou.composecustomtextselection.lib

import android.app.Instrumentation
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.TextRange
import org.junit.Assert.fail
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Injects touch events through [android.app.UiAutomation] using screen coordinates, the same way a real finger
 * would. This matters for selection handles because they are rendered inside separate popup windows.
 */
internal class TouchInjector(instrumentation: Instrumentation) {
  private val uiAutomation = instrumentation.uiAutomation

  fun tap(position: Offset, pressDurationMs: Long = 80) {
    val downTime = SystemClock.uptimeMillis()
    inject(downTime, MotionEvent.ACTION_DOWN, position)
    SystemClock.sleep(pressDurationMs)
    inject(downTime, MotionEvent.ACTION_UP, position)
  }

  fun multiTap(position: Offset, count: Int) {
    repeat(count) { index ->
      tap(position)

      if (index < count - 1) {
        SystemClock.sleep(120)
      }
    }
  }

  fun drag(from: Offset, to: Offset, durationMs: Long = 400) {
    val downTime = SystemClock.uptimeMillis()
    inject(downTime, MotionEvent.ACTION_DOWN, from)
    SystemClock.sleep(50)

    val steps = (durationMs / 16).coerceAtLeast(1)
    for (step in 1..steps) {
      val fraction = step.toFloat() / steps
      inject(downTime, MotionEvent.ACTION_MOVE, from + (to - from) * fraction)
      SystemClock.sleep(16)
    }

    SystemClock.sleep(50)
    inject(downTime, MotionEvent.ACTION_UP, to)
  }

  private fun inject(downTime: Long, action: Int, position: Offset) {
    val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, position.x, position.y, 0)
    event.source = InputDevice.SOURCE_TOUCHSCREEN

    try {
      check(uiAutomation.injectInputEvent(event, true)) { "Failed to inject $event" }
    } finally {
      event.recycle()
    }
  }
}

internal class RecordingHapticFeedback : HapticFeedback {
  val events = CopyOnWriteArrayList<HapticFeedbackType>()

  override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
    events += hapticFeedbackType
  }
}

internal fun Instrumentation.waitUntil(
  description: String,
  timeoutMs: Long = 3_000,
  currentState: (() -> Any?)? = null,
  condition: () -> Boolean
) {
  val deadline = SystemClock.uptimeMillis() + timeoutMs

  while (SystemClock.uptimeMillis() < deadline) {
    if (onMain(condition)) {
      return
    }

    SystemClock.sleep(16)
  }

  saveFailureScreenshot(description)

  val stateDescription = currentState?.let { ", current state: ${onMain(it)}" } ?: ""
  fail("Timed out waiting for: $description$stateDescription")
}

private fun Instrumentation.saveFailureScreenshot(description: String) {
  val directory = File(targetContext.externalCacheDir, "test-failures").apply { mkdirs() }
  val fileName = description.replace(Regex("[^A-Za-z0-9]+"), "_") + ".png"

  uiAutomation.takeScreenshot()?.let { bitmap ->
    File(directory, fileName).outputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }
  }
}

internal fun <T> Instrumentation.onMain(block: () -> T): T {
  var result: Result<T>? = null
  runOnMainSync { result = runCatching(block) }
  return result!!.getOrThrow()
}

internal fun String.wordRange(word: String): TextRange {
  val start = indexOf(word)
  check(start >= 0) { "'$word' not found" }
  return TextRange(start, start + word.length)
}
