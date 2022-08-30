package com.github.k1rakishou.composecustomtextselection.lib

import android.graphics.drawable.Drawable
import android.os.Build
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.annotation.DoNotInline
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.AnnotatedString

class ConfigurableTextToolbar(
  private val view: View,
  private val selectionToolbarMenu: SelectionToolbarMenu
) {
  private var actionMode: ActionMode? = null
  private val textActionModeCallback: ConfigurableTextActionModeCallback = ConfigurableTextActionModeCallback(
    selectionToolbarMenu = selectionToolbarMenu,
    onActionModeDestroy = { actionMode = null }
  )

  fun updateSelectedTextCallback(selectedText: (() -> AnnotatedString?)?) {
    selectionToolbarMenu.updateSelectedTextCallback(selectedText)
  }

  fun updateHideSelectionToolbarCallback(hideSelectionToolbar: (() -> Unit)?) {
    selectionToolbarMenu.updateHideSelectionToolbarCallback(hideSelectionToolbar)
  }

  var status: TextToolbarStatus = TextToolbarStatus.Hidden
    private set

  fun showMenu(
    rect: Rect,
  ) {
    textActionModeCallback.rect = rect

    if (actionMode == null) {
      status = TextToolbarStatus.Shown
      actionMode = if (Build.VERSION.SDK_INT >= 23) {
        TextToolbarHelperMethods.startActionMode(
          view,
          ConfigurableFloatingTextActionModeCallback(textActionModeCallback),
          ActionMode.TYPE_FLOATING
        )
      } else {
        view.startActionMode(
          ConfigurablePrimaryTextActionModeCallback(textActionModeCallback)
        )
      }
    } else {
      actionMode?.invalidate()
    }
  }

  fun hide() {
    status = TextToolbarStatus.Hidden
    actionMode?.finish()
    actionMode = null
  }
}

class SelectionToolbarMenu(
  private val items: List<Item>,
) {
  private var selectedText: (() -> AnnotatedString?)? = null
  private var hideSelectionToolbar: (() -> Unit)? = null

  fun updateSelectedTextCallback(selectedText: (() -> AnnotatedString?)?) {
    this.selectedText = selectedText
  }

  fun updateHideSelectionToolbarCallback(hideSelectionToolbar: (() -> Unit)?) {
    this.hideSelectionToolbar = hideSelectionToolbar
  }

  fun build(menu: Menu): Boolean {
    if (items.isEmpty()) {
      return false
    }

    items.forEach { item ->
      val menuItem = menu.add(0, item.id, item.order, item.text)

      with(menuItem) {
        if (item.icon != null) {
          setIcon(item.icon)
        }

        setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
      }
    }

    return true
  }

  fun onClick(id: Int) {
    val selectedText = selectedText?.invoke()
      ?: return

    val item = items.firstOrNull { menuItem -> menuItem.id == id }
      ?: return

    item.callback.invoke(selectedText)
    hideSelectionToolbar?.invoke()
  }

  class Item(
    val id: Int,
    val order: Int,
    val text: CharSequence,
    val icon: Drawable? = null,
    val callback: (AnnotatedString) -> Unit
  )

}


internal class ConfigurablePrimaryTextActionModeCallback(
  private val callback: ConfigurableTextActionModeCallback
) : ActionMode.Callback {
  override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
    return callback.onActionItemClicked(mode, item)
  }

  override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
    return callback.onCreateActionMode(mode, menu)
  }

  override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
    return callback.onPrepareActionMode(mode, menu)
  }

  override fun onDestroyActionMode(mode: ActionMode?) {
    callback.onDestroyActionMode()
  }
}

@RequiresApi(23)
internal class ConfigurableFloatingTextActionModeCallback(
  private val callback: ConfigurableTextActionModeCallback
) : ActionMode.Callback2() {
  override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
    return callback.onActionItemClicked(mode, item)
  }

  override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
    return callback.onCreateActionMode(mode, menu)
  }

  override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
    return callback.onPrepareActionMode(mode, menu)
  }

  override fun onDestroyActionMode(mode: ActionMode?) {
    callback.onDestroyActionMode()
  }

  override fun onGetContentRect(
    mode: ActionMode?,
    view: View?,
    outRect: android.graphics.Rect?
  ) {
    val rect = callback.rect
    outRect?.set(
      rect.left.toInt(),
      rect.top.toInt(),
      rect.right.toInt(),
      rect.bottom.toInt()
    )
  }

}

class ConfigurableTextActionModeCallback(
  private val selectionToolbarMenu: SelectionToolbarMenu,
  val onActionModeDestroy: (() -> Unit)? = null,
  var rect: Rect = Rect.Zero,
) {
  fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
    requireNotNull(menu)
    requireNotNull(mode)

    return selectionToolbarMenu.build(menu)
  }

  // this method is called to populate new menu items when the actionMode was invalidated
  fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
    if (mode == null || menu == null) return false
    return updateMenuItems(menu)
  }

  fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
    item?.let { item -> selectionToolbarMenu.onClick(item.itemId) }
    mode?.finish()
    return true
  }

  fun onDestroyActionMode() {
    onActionModeDestroy?.invoke()
  }

  private fun updateMenuItems(menu: Menu): Boolean {
    menu.clear()
    return selectionToolbarMenu.build(menu)
  }
}

/**
 * This class is here to ensure that the classes that use this API will get verified and can be
 * AOT compiled. It is expected that this class will soft-fail verification, but the classes
 * which use this method will pass.
 */
@RequiresApi(23)
internal object TextToolbarHelperMethods {
  @RequiresApi(23)
  @DoNotInline
  fun startActionMode(
    view: View,
    actionModeCallback: ActionMode.Callback,
    type: Int
  ): ActionMode? {
    return view.startActionMode(
      actionModeCallback,
      type
    )
  }

  @RequiresApi(23)
  @DoNotInline
  fun invalidateContentRect(actionMode: ActionMode) {
    actionMode.invalidateContentRect()
  }
}