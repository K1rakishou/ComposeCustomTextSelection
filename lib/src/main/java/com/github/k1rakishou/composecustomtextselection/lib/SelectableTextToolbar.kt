package com.github.k1rakishou.composecustomtextselection.lib

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Card
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt

/**
 * Text selected at the moment a toolbar item was clicked.
 */
@Immutable
data class SelectedText(
  val text: AnnotatedString,
  val range: TextRange
)

@DslMarker
annotation class SelectableTextToolbarDsl

/**
 * Builds the items of the selection toolbar:
 * ```
 * SelectableTextContainer(
 *   ...
 *   toolbar = {
 *     item(key = "copy", text = "Copy") { selectedText -> clipboard.setText(selectedText.text) }
 *     item(key = "quote", text = "Quote", dismissSelectionOnClick = false) { selectedText -> quote(selectedText) }
 *   }
 * )
 * ```
 */
@SelectableTextToolbarDsl
class SelectableTextToolbarScope internal constructor() {
  internal val items = mutableListOf<SelectableTextToolbarItem>()

  /**
   * @param key unique (within the toolbar) key of the item.
   * @param dismissSelectionOnClick whether the selection (and the toolbar) should be cleared after the click.
   */
  fun item(
    key: Any,
    text: String,
    icon: Painter? = null,
    enabled: Boolean = true,
    dismissSelectionOnClick: Boolean = true,
    onClick: (SelectedText) -> Unit
  ) {
    item(
      key = key,
      enabled = enabled,
      dismissSelectionOnClick = dismissSelectionOnClick,
      onClick = onClick,
      content = {
        if (icon != null) {
          Icon(
            modifier = Modifier.size(18.dp),
            painter = icon,
            contentDescription = null
          )

          Spacer(modifier = Modifier.width(6.dp))
        }

        Text(text = text)
      }
    )
  }

  /**
   * Item with custom content. [LocalContentColor] is set according to [SelectableTextToolbarColors] and [enabled].
   */
  fun item(
    key: Any,
    enabled: Boolean = true,
    dismissSelectionOnClick: Boolean = true,
    onClick: (SelectedText) -> Unit,
    content: @Composable RowScope.() -> Unit
  ) {
    check(items.none { it.key == key }) { "Toolbar item with key '$key' was already added" }

    items += SelectableTextToolbarItem(
      key = key,
      enabled = enabled,
      dismissSelectionOnClick = dismissSelectionOnClick,
      onClick = onClick,
      content = content
    )
  }
}

internal class SelectableTextToolbarItem(
  val key: Any,
  val enabled: Boolean,
  val dismissSelectionOnClick: Boolean,
  val onClick: (SelectedText) -> Unit,
  val content: @Composable RowScope.() -> Unit
)

internal fun buildSelectableTextToolbarItems(
  builder: SelectableTextToolbarScope.() -> Unit
): List<SelectableTextToolbarItem> {
  return SelectableTextToolbarScope().apply(builder).items
}

@Immutable
data class SelectableTextToolbarColors(
  val backgroundColor: Color,
  val contentColor: Color,
  val disabledContentColor: Color
)

object SelectableTextToolbarDefaults {

  @Composable
  fun colors(
    backgroundColor: Color = MaterialTheme.colors.surface,
    contentColor: Color = MaterialTheme.colors.onSurface,
    disabledContentColor: Color = contentColor.copy(alpha = ContentAlpha.disabled)
  ): SelectableTextToolbarColors {
    return SelectableTextToolbarColors(
      backgroundColor = backgroundColor,
      contentColor = contentColor,
      disabledContentColor = disabledContentColor
    )
  }

}

@Composable
internal fun SelectableTextToolbar(
  selectableTextState: SelectableTextState,
  items: List<SelectableTextToolbarItem>,
  colors: SelectableTextToolbarColors
) {
  val density = LocalDensity.current

  val popupPositionProvider = remember(density) {
    object : PopupPositionProvider {
      override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
      ): IntOffset {
        val textLayoutResult = selectableTextState.textLayoutResult
          ?: return IntOffset.Zero
        val textCoords = selectableTextState.selectableTextLayoutCoordinates
          ?: return IntOffset.Zero

        val start = selectableTextState.startSelectionHandle.textOffset
        val end = selectableTextState.endSelectionHandle.textOffset

        if (start < 0 || end < 0 || start > end) {
          return IntOffset.Zero
        }

        val path = textLayoutResult.getPathForRange(start, end)
        if (path.isEmpty) {
          return IntOffset.Zero
        }

        val selectionBoundsLocal = path.getBounds()

        val topLeftWindow = textCoords.localToWindow(selectionBoundsLocal.topLeft)
        val bottomRightWindow = textCoords.localToWindow(selectionBoundsLocal.bottomRight)
        val selectionInWindow = Rect(topLeftWindow, bottomRightWindow)

        val verticalGap = with(density) { 8.dp.roundToPx() }
        val screenPadding = with(density) { 8.dp.roundToPx() }

        val aboveY = selectionInWindow.top.roundToInt() - verticalGap - popupContentSize.height
        val belowY = selectionInWindow.bottom.roundToInt() + verticalGap

        val y = when {
          aboveY >= screenPadding -> aboveY
          belowY + popupContentSize.height <= windowSize.height - screenPadding -> belowY
          else -> screenPadding
        }

        val centerX = (selectionInWindow.left + selectionInWindow.width / 2f).roundToInt()
        val unclampedX = centerX - popupContentSize.width / 2
        val maxX = windowSize.width - popupContentSize.width - screenPadding
        val x = unclampedX.coerceIn(screenPadding, maxX.coerceAtLeast(screenPadding))

        return IntOffset(x, y)
      }
    }
  }

  Popup(
    popupPositionProvider = popupPositionProvider,
    properties = PopupProperties(
      excludeFromSystemGesture = true,
      clippingEnabled = false
    )
  ) {
    Card(
      backgroundColor = colors.backgroundColor,
      contentColor = colors.contentColor
    ) {
      FlowRow(
        horizontalArrangement = Arrangement.SpaceEvenly
      ) {
        for (item in items) {
          key(item.key) {
            SelectableTextToolbarItemElement(
              selectableTextState = selectableTextState,
              item = item,
              colors = colors
            )
          }
        }
      }
    }
  }
}

@Composable
private fun SelectableTextToolbarItemElement(
  selectableTextState: SelectableTextState,
  item: SelectableTextToolbarItem,
  colors: SelectableTextToolbarColors
) {
  val contentColor = if (item.enabled) colors.contentColor else colors.disabledContentColor

  Row(
    modifier = Modifier
      .clickable(
        enabled = item.enabled,
        onClick = {
          val selectedText = selectableTextState.selectedText
          val selectedTextRange = selectableTextState.selectedTextRange
          if (selectedText == null || selectedTextRange == null) {
            return@clickable
          }

          item.onClick(SelectedText(text = selectedText, range = selectedTextRange))

          if (item.dismissSelectionOnClick) {
            selectableTextState.resetEverything()
          }
        }
      )
      .padding(horizontal = 12.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    // Material components apply LocalContentAlpha on top of LocalContentColor, so both have to be provided for the
    // disabled color to take effect.
    CompositionLocalProvider(
      LocalContentColor provides contentColor,
      LocalContentAlpha provides contentColor.alpha
    ) {
      item.content(this)
    }
  }
}
