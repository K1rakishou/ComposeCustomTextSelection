package com.github.k1rakishou.composecustomtextselection.lib

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.util.fastForEach


/**
 * Enables text selection for its direct or indirect children.
 *
 * @sample androidx.compose.foundation.samples.SelectionSample
 */
@Composable
internal fun SelectionContainer(
  modifier: Modifier = Modifier,
  selection: Selection?,
  selectionRegistrar: SelectionRegistrarImpl,
  configurableTextToolbar: ConfigurableTextToolbar,
  onSelectionChange: (Selection?) -> Unit,
  selectionHandleContent: @Composable ((Offset, Boolean, ResolvedTextDirection, Boolean, Modifier) -> Unit),
  content: @Composable () -> Unit
) {
  val manager = remember { SelectionManager(selectionRegistrar) }

  manager.hapticFeedBack = LocalHapticFeedback.current
  manager.clipboardManager = LocalClipboardManager.current
  manager.textToolbar = configurableTextToolbar
  manager.onSelectionChange = onSelectionChange
  manager.selection = selection
  manager.touchMode = true

  ContextMenuArea(manager) {
    CompositionLocalProvider(LocalSelectionRegistrar provides selectionRegistrar) {
      // Get the layout coordinates of the selection container. This is for hit test of
      // cross-composable selection.
      SimpleLayout(modifier = modifier.then(manager.modifier)) {
        content()
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
                  selectionHandleContent = selectionHandleContent
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