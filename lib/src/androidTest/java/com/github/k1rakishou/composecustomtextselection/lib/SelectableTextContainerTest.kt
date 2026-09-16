package com.github.k1rakishou.composecustomtextselection.lib

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet

@RunWith(AndroidJUnit4::class)
class SelectableTextContainerTest {
  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  private val touch = TouchInjector(instrumentation)
  private val haptics = RecordingHapticFeedback()

  private lateinit var scenario: ActivityScenario<ComponentActivity>
  private lateinit var firstState: SelectableTextState
  private lateinit var secondState: SelectableTextState

  private val toolbarClicks = CopyOnWriteArrayList<Pair<String, SelectedText>>()
  private val toolbarItemCoordinates = ConcurrentHashMap<String, LayoutCoordinates>()
  private val containerClickLabel = mutableStateOf("initial")
  private val containerClicks = CopyOnWriteArrayList<String>()
  private val activePresses = CopyOnWriteArraySet<PressInteraction.Press>()

  @Before
  fun setUp() {
    scenario = ActivityScenario.launch(ComponentActivity::class.java)
    scenario.onActivity { activity ->
      activity.setContent {
        CompositionLocalProvider(LocalHapticFeedback provides haptics) {
          Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            // Leave room above the text for the selection toolbar.
            Spacer(modifier = Modifier.height(180.dp))

            firstState = rememberTextSelectionState(handleSize = HandleSize)
            val clickLabel = containerClickLabel.value
            val interactionSource = remember { MutableInteractionSource() }
            LaunchedEffect(interactionSource) {
              interactionSource.interactions.collect { interaction ->
                when (interaction) {
                  is PressInteraction.Press -> activePresses += interaction
                  is PressInteraction.Release -> activePresses -= interaction.press
                  is PressInteraction.Cancel -> activePresses -= interaction.press
                }
              }
            }

            SelectableTextContainer(
              modifier = Modifier.width(320.dp),
              selectableTextState = firstState,
              handleSize = HandleSize,
              interactionSource = interactionSource,
              onClicked = { containerClicks += clickLabel },
              toolbar = {
                recordingItem(key = CopyItem)
                recordingItem(key = KeepSelectionItem, dismissSelectionOnClick = false)
                recordingItem(key = DisabledItem, enabled = false)
              },
              textContent = { onTextLayout ->
                Text(text = FirstText, fontSize = 20.sp, onTextLayout = onTextLayout)
              }
            )

            Spacer(modifier = Modifier.height(120.dp))

            secondState = rememberTextSelectionState(handleSize = HandleSize)
            SelectableTextContainer(
              modifier = Modifier.width(320.dp),
              selectableTextState = secondState,
              handleSize = HandleSize,
              textContent = { onTextLayout ->
                Text(text = SecondText, fontSize = 20.sp, onTextLayout = onTextLayout)
              }
            )
          }
        }
      }
    }

    instrumentation.waitUntil("text to be laid out") {
      ::firstState.isInitialized &&
        ::secondState.isInitialized &&
        firstState.isReady() &&
        secondState.isReady()
    }

    instrumentation.onMain {
      val screenHeight = instrumentation.targetContext.resources.displayMetrics.heightPixels
      val secondTextCoordinates = checkNotNull(secondState.selectableTextLayoutCoordinates)
      val secondTextBottom = secondTextCoordinates.localToScreen(Offset(0f, secondTextCoordinates.size.height.toFloat())).y

      check(secondTextBottom < screenHeight) {
        "The second text is off screen (bottom: $secondTextBottom, screen height: $screenHeight)"
      }
    }
  }

  @After
  fun tearDown() {
    scenario.close()
  }

  @Test
  fun doubleTapSelectsWord() {
    val three = FirstText.wordRange("three")

    touch.multiTap(firstState.charCenterOnScreen(three.start + 1), count = 2)

    instrumentation.waitUntil("'three' to be selected", currentState = { firstState.selection() }) { firstState.selection() == three }
  }

  @Test
  fun tripleTapSelectsSentence() {
    val secondSentence = TextRange(FirstText.indexOf("Seven"), FirstText.lastIndexOf('.') + 1)

    touch.multiTap(firstState.charCenterOnScreen(FirstText.indexOf("eight")), count = 3)

    instrumentation.waitUntil("second sentence to be selected", currentState = { firstState.selection() }) { firstState.selection() == secondSentence }
  }

  @Test
  fun pressIndicationIsReleasedAfterMultiTap() {
    selectWord(firstState, FirstText, "three")
    Thread.sleep(500)

    assertTrue("Presses were not released: $activePresses", activePresses.isEmpty())

    touch.multiTap(firstState.charCenterOnScreen(FirstText.indexOf("eight")), count = 3)
    instrumentation.waitUntil("sentence to be selected", currentState = { firstState.selection() }) {
      firstState.selection() == TextRange(FirstText.indexOf("Seven"), FirstText.lastIndexOf('.') + 1)
    }
    Thread.sleep(500)

    assertTrue("Presses were not released: $activePresses", activePresses.isEmpty())
  }

  @Test
  fun tapInsideContainerClearsSelection() {
    selectWord(firstState, FirstText, "three")

    touch.tap(firstState.charCenterOnScreen(FirstText.indexOf("eight")))

    instrumentation.waitUntil("selection to be cleared", currentState = { firstState.selection() }) { firstState.selection() == null }
  }

  @Test
  fun clearedSelectionHandlesDoNotInterceptTouches() {
    selectWord(firstState, FirstText, "three")

    // The end handle hangs below the first line, over the text of the second line.
    val (underHandle, wordUnderHandle) = instrumentation.onMain {
      val textLayoutResult = checkNotNull(firstState.textLayoutResult)
      val handleCenter = checkNotNull(firstState.endSelectionHandle.textRelativeHandleBBox(isStartHandle = false)).center
      val offset = textLayoutResult.getOffsetForPosition(handleCenter)
      check(textLayoutResult.getLineForOffset(offset) == 1) { "Expected the handle to be above the second line" }

      val wordBoundary = textLayoutResult.getWordBoundary(offset)
      firstState.localToScreen(handleCenter) to wordBoundary
    }

    touch.tap(firstState.charCenterOnScreen(FirstText.indexOf("One")))
    instrumentation.waitUntil("selection to be cleared", currentState = { firstState.selection() }) {
      firstState.selection() == null
    }
    // Wait long enough for the next taps not to be treated as a continuation of the previous tap.
    Thread.sleep(800)

    touch.multiTap(underHandle, count = 2)

    instrumentation.waitUntil("word under the old handle position to be selected", currentState = { firstState.selection() }) {
      firstState.selection() == wordUnderHandle
    }
  }

  @Test
  fun draggingEndHandleExtendsSelection() {
    val three = FirstText.wordRange("three")
    val five = FirstText.wordRange("five")
    selectWord(firstState, FirstText, "three")

    dragEndHandleTo(firstState, five.start + 2)

    instrumentation.waitUntil("selection to be extended to 'five'", currentState = { firstState.selection() }) {
      val selection = firstState.selection()
      selection != null && selection.start == three.start && selection.end in (five.start + 1)..five.end
    }
  }

  @Test
  fun draggingStartHandleDoesNotMoveEndHandle() {
    val three = FirstText.wordRange("three")
    val five = FirstText.wordRange("five")
    selectWord(firstState, FirstText, "five")

    dragStartHandleTo(firstState, three.start + 2)

    instrumentation.waitUntil("selection start to move to 'three'", currentState = { firstState.selection() }) {
      val selection = firstState.selection()
      selection != null && selection.end == five.end && selection.start in three.start..three.end
    }
  }

  @Test
  fun draggingCrossedHandleMovesTheHandleUnderTheFinger() {
    val one = FirstText.wordRange("One")
    val two = FirstText.wordRange("two")
    val five = FirstText.wordRange("five")
    selectWord(firstState, FirstText, "five")

    // Drag the end handle over the start handle so that the handles get crossed.
    dragEndHandleTo(firstState, two.start + 1)
    instrumentation.waitUntil("handles to cross", currentState = { firstState.selection() }) {
      val selection = firstState.selection()
      selection != null && selection.end == five.start && selection.start in two.start..two.end
    }

    // The handle that is now displayed as the start handle is the one that was dragged. Dragging it again must
    // move it, not the other one.
    dragStartHandleTo(firstState, one.start + 1)
    instrumentation.waitUntil("start handle to move to 'One'", currentState = { firstState.selection() }) {
      val selection = firstState.selection()
      selection != null && selection.end == five.start && selection.start in one.start..one.end
    }
  }

  @Test
  fun selectionSurvivesRepeatedHandleDrags() {
    val two = FirstText.wordRange("two")
    val seven = FirstText.wordRange("Seven")
    selectWord(firstState, FirstText, "four")

    repeat(3) {
      dragStartHandleTo(firstState, two.start + 1)
      dragEndHandleTo(firstState, seven.start + 2)
      dragStartHandleTo(firstState, FirstText.indexOf("three") + 1)
    }

    instrumentation.waitUntil("selection to still exist", currentState = { firstState.selection() }) {
      val selection = firstState.selection()
      selection != null && selection.start < selection.end
    }
  }

  @Test
  fun startingSelectionInAnotherContainerClearsPreviousSelection() {
    selectWord(firstState, FirstText, "three")

    touch.multiTap(secondState.charCenterOnScreen(SecondText.indexOf("delta") + 1), count = 2)

    instrumentation.waitUntil(
      description = "selection to move to the second container",
      currentState = { "first=${firstState.selection()}, second=${secondState.selection()}" }
    ) {
      secondState.selection() == SecondText.wordRange("delta") && firstState.selection() == null
    }
  }

  @Test
  fun startingSelectionPerformsLongPressHapticFeedback() {
    selectWord(firstState, FirstText, "three")

    instrumentation.waitUntil("LongPress haptic feedback") {
      haptics.events.contains(HapticFeedbackType.LongPress)
    }
  }

  @Test
  fun movingHandlePerformsTextHandleMoveHapticFeedback() {
    selectWord(firstState, FirstText, "three")
    haptics.events.clear()

    dragEndHandleTo(firstState, FirstText.indexOf("five") + 2)

    instrumentation.waitUntil("TextHandleMove haptic feedback") {
      haptics.events.contains(HapticFeedbackType.TextHandleMove)
    }
  }

  @Test
  fun toolbarIsShownOnlyWhileTextIsSelected() {
    assertFalse(instrumentation.onMain { isToolbarItemDisplayed(CopyItem) })

    selectWord(firstState, FirstText, "three")
    instrumentation.waitUntil("toolbar to be displayed") { isToolbarItemDisplayed(CopyItem) }

    touch.tap(firstState.charCenterOnScreen(FirstText.indexOf("eight")))
    instrumentation.waitUntil("toolbar to be hidden") { !isToolbarItemDisplayed(CopyItem) }
  }

  @Test
  fun clickingToolbarItemPassesSelectedTextAndClearsSelection() {
    val three = FirstText.wordRange("three")
    selectWord(firstState, FirstText, "three")

    touch.tap(toolbarItemCenterOnScreen(CopyItem))

    instrumentation.waitUntil(
      description = "click to be delivered and selection to be cleared",
      currentState = { "clicks=$toolbarClicks, selection=${firstState.selection()}" }
    ) {
      val (key, selectedText) = toolbarClicks.singleOrNull() ?: return@waitUntil false
      key == CopyItem &&
        selectedText.text.text == "three" &&
        selectedText.range == three &&
        firstState.selection() == null
    }
  }

  @Test
  fun toolbarItemCanKeepSelectionAfterClick() {
    val three = FirstText.wordRange("three")
    selectWord(firstState, FirstText, "three")

    touch.tap(toolbarItemCenterOnScreen(KeepSelectionItem))

    instrumentation.waitUntil(
      description = "click to be delivered",
      currentState = { "clicks=$toolbarClicks, selection=${firstState.selection()}" }
    ) {
      toolbarClicks.singleOrNull()?.first == KeepSelectionItem
    }

    assertEquals(three, instrumentation.onMain { firstState.selection() })
    assertTrue(instrumentation.onMain { isToolbarItemDisplayed(KeepSelectionItem) })
  }

  @Test
  fun disabledToolbarItemIsNotClickable() {
    val three = FirstText.wordRange("three")
    selectWord(firstState, FirstText, "three")

    touch.tap(toolbarItemCenterOnScreen(DisabledItem))
    Thread.sleep(500)

    assertTrue(toolbarClicks.isEmpty())
    assertEquals(three, instrumentation.onMain { firstState.selection() })
  }

  @Test
  fun clickCallbackIsNotStale() {
    val tapPosition = firstState.charCenterOnScreen(FirstText.indexOf("eight"))

    // The gesture detector coroutine starts on the first touch, so interact with the container before changing the
    // callback.
    touch.tap(tapPosition)
    instrumentation.waitUntil("first click to be delivered", currentState = { containerClicks }) {
      containerClicks.lastOrNull() == "initial"
    }

    instrumentation.onMain { containerClickLabel.value = "updated" }
    instrumentation.waitForIdleSync()
    Thread.sleep(800)

    touch.tap(tapPosition)
    instrumentation.waitUntil("second click to be delivered", currentState = { containerClicks }) {
      containerClicks.lastOrNull() == "updated"
    }
  }

  private fun SelectableTextToolbarScope.recordingItem(
    key: String,
    enabled: Boolean = true,
    dismissSelectionOnClick: Boolean = true
  ) {
    item(
      key = key,
      enabled = enabled,
      dismissSelectionOnClick = dismissSelectionOnClick,
      onClick = { selectedText -> toolbarClicks += key to selectedText },
      content = {
        Text(
          modifier = Modifier.onGloballyPositioned { coordinates -> toolbarItemCoordinates[key] = coordinates },
          text = key
        )
      }
    )
  }

  private fun isToolbarItemDisplayed(key: String): Boolean {
    return toolbarItemCoordinates[key]?.isAttached == true
  }

  private fun toolbarItemCenterOnScreen(key: String): Offset {
    instrumentation.waitUntil("toolbar item '$key' to be displayed") { isToolbarItemDisplayed(key) }
    Thread.sleep(300)

    return instrumentation.onMain {
      val coordinates = checkNotNull(toolbarItemCoordinates[key])
      coordinates.localToScreen(Offset(coordinates.size.width / 2f, coordinates.size.height / 2f))
    }
  }

  private fun selectWord(state: SelectableTextState, text: String, word: String) {
    val range = text.wordRange(word)
    touch.multiTap(state.charCenterOnScreen(range.start + 1), count = 2)
    instrumentation.waitUntil("'$word' to be selected", currentState = { state.selection() }) { state.selection() == range }
    // Let the handle popups get positioned.
    instrumentation.waitForIdleSync()
    Thread.sleep(300)
  }

  private fun dragStartHandleTo(state: SelectableTextState, targetCharOffset: Int) {
    dragHandleTo(state, isStartHandle = true, targetCharOffset = targetCharOffset)
  }

  private fun dragEndHandleTo(state: SelectableTextState, targetCharOffset: Int) {
    dragHandleTo(state, isStartHandle = false, targetCharOffset = targetCharOffset)
  }

  /**
   * Grabs the handle at its center and moves the finger by the distance between the character the handle is
   * attached to and the target character.
   */
  private fun dragHandleTo(state: SelectableTextState, isStartHandle: Boolean, targetCharOffset: Int) {
    val (from, to) = instrumentation.onMain {
      val handle = if (isStartHandle) state.startSelectionHandle else state.endSelectionHandle
      val handleCenter = checkNotNull(handle.textRelativeHandleBBox(isStartHandle)).center
      val attachedCharOffset = if (isStartHandle) handle.textOffset else handle.textOffset - 1

      val from = state.localToScreen(handleCenter)
      val delta = state.charCenterOnScreenUnsafe(targetCharOffset) - state.charCenterOnScreenUnsafe(attachedCharOffset)
      from to from + delta
    }

    touch.drag(from, to)
    instrumentation.waitForIdleSync()
    Thread.sleep(300)
  }

  private fun SelectableTextState.isReady(): Boolean {
    return textLayoutResult != null && selectableTextLayoutCoordinates?.isAttached == true
  }

  private fun SelectableTextState.selection(): TextRange? {
    if (dragMode.value == null || !startSelectionHandle.isInitialized || !endSelectionHandle.isInitialized) {
      return null
    }

    return TextRange(startSelectionHandle.textOffset, endSelectionHandle.textOffset)
  }

  private fun SelectableTextState.charCenterOnScreen(offset: Int): Offset {
    return instrumentation.onMain { charCenterOnScreenUnsafe(offset) }
  }

  private fun SelectableTextState.charCenterOnScreenUnsafe(offset: Int): Offset {
    return localToScreen(checkNotNull(textLayoutResult).getBoundingBox(offset).center)
  }

  private fun SelectableTextState.localToScreen(local: Offset): Offset {
    return checkNotNull(selectableTextLayoutCoordinates).localToScreen(local)
  }

  companion object {
    private val HandleSize = 24.dp
    private const val FirstText = "One two three four five six. Seven eight nine ten eleven twelve thirteen."
    private const val CopyItem = "Copy"
    private const val KeepSelectionItem = "Keep"
    private const val DisabledItem = "Disabled"
    private const val SecondText = "Alpha beta gamma delta epsilon."
  }
}
