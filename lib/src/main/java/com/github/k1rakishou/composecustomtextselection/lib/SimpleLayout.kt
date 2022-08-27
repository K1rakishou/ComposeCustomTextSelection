package com.github.k1rakishou.composecustomtextselection.lib

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap
import kotlin.math.max

/**
 * Selection is transparent in terms of measurement and layout and passes the same constraints to
 * the children.
 */
@Composable
internal fun SimpleLayout(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  Layout(modifier = modifier, content = content) { measurables, constraints ->
    val placeables = measurables.fastMap { measurable ->
      measurable.measure(constraints)
    }

    val width = placeables.fold(0) { maxWidth, placeable ->
      max(maxWidth, (placeable.width))
    }

    val height = placeables.fold(0) { minWidth, placeable ->
      max(minWidth, (placeable.height))
    }

    layout(width, height) {
      placeables.fastForEach { placeable ->
        placeable.place(0, 0)
      }
    }
  }
}
