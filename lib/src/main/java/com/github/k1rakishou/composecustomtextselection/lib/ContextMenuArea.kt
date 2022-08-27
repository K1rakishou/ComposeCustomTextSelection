package com.github.k1rakishou.composecustomtextselection.lib


import androidx.compose.runtime.Composable

@Composable
internal fun ContextMenuArea(
  manager: SelectionManager,
  content: @Composable () -> Unit
) {
  content()
}