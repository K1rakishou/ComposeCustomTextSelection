package com.github.k1rakishou.composecustomtextselection.lib

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.util.fastForEach


/**
 * Enables text selection for its direct or indirect children.
 *
 * @sample androidx.compose.foundation.samples.SelectionSample
 */
@Composable
internal fun SelectionContainer(
  modifier: Modifier = Modifier,
  layoutCoordinates: LayoutCoordinates?,
  selectionState: SelectionState,
  selectionRegistrar: SelectionRegistrarImpl,
  content: @Composable () -> Unit
) {
  var selection by remember { mutableStateOf<Selection?>(null) }
  val layoutCoordinatesUpdated by rememberUpdatedState(newValue = layoutCoordinates)

  LaunchedEffect(
    key1 = Unit,
    block = {
      selectionState.selectionEventFlow.collect { selectionEvent ->
        val layoutCoordinatesUpdatedLocal = layoutCoordinatesUpdated
        if (layoutCoordinatesUpdatedLocal == null) {
          return@collect
        }

        when (selectionEvent) {
          is SelectionState.SelectionEvent.Start -> {
            selectionRegistrar.notifySelectionUpdateStart(
              layoutCoordinatesUpdatedLocal,
              selectionEvent.offset,
              SelectionAdjustment.Word
            )
          }
        }
      }
    })

  SelectionContainer(
    modifier = Modifier
      .then(modifier),
    selection = selection,
    registrarImpl = selectionRegistrar,
    onSelectionChange = {
      selection = it
    },
    children = content
  )
}

/**
 * Disables text selection for its direct or indirect children. To use this, simply add this
 * to wrap one or more text composables.
 *
 * @sample androidx.compose.foundation.samples.DisableSelectionSample
 */
@Composable
fun DisableSelection(content: @Composable () -> Unit) {
  CompositionLocalProvider(
    LocalSelectionRegistrar provides null,
    content = content
  )
}

/**
 * Selection Composable.
 *
 * The selection composable wraps composables and let them to be selectable. It paints the selection
 * area with start and end handles.
 */
@Suppress("ComposableLambdaParameterNaming")
@Composable
internal fun SelectionContainer(
  /** A [Modifier] for SelectionContainer. */
  modifier: Modifier = Modifier,
  /** Current Selection status.*/
  selection: Selection?,
  registrarImpl: SelectionRegistrarImpl,
  /** A function containing customized behaviour when selection changes. */
  onSelectionChange: (Selection?) -> Unit,
  children: @Composable () -> Unit
) {
  val manager = remember { SelectionManager(registrarImpl) }

  manager.hapticFeedBack = LocalHapticFeedback.current
  manager.clipboardManager = LocalClipboardManager.current
  manager.textToolbar = LocalTextToolbar.current
  manager.onSelectionChange = onSelectionChange
  manager.selection = selection
  manager.touchMode = true

  ContextMenuArea(manager) {
    CompositionLocalProvider(LocalSelectionRegistrar provides registrarImpl) {
      // Get the layout coordinates of the selection container. This is for hit test of
      // cross-composable selection.
      SimpleLayout(modifier = modifier.then(manager.modifier)) {
        children()
        if (manager.hasFocus) {
          manager.selection?.let {
            listOf(true, false).fastForEach { isStartHandle ->
              val observer = remember(isStartHandle) {
                manager.handleDragObserver(isStartHandle)
              }
              val position = if (isStartHandle) {
                manager.startHandlePosition
              } else {
                manager.endHandlePosition
              }

              val direction = if (isStartHandle) {
                it.start.direction
              } else {
                it.end.direction
              }

              if (position != null) {
                SelectionHandle(
                  position = position,
                  isStartHandle = isStartHandle,
                  direction = direction,
                  handlesCrossed = it.handlesCrossed,
                  modifier = Modifier.pointerInput(observer) {
                    detectDownAndDragGesturesWithObserver(observer)
                  },
                  content = null
                )
              }
            }
          }
        }
      }
    }
  }

  DisposableEffect(manager) {
    onDispose {
      manager.hideSelectionToolbar()
    }
  }
}
