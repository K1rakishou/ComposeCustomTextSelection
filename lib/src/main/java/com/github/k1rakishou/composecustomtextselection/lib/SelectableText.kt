package com.github.k1rakishou.composecustomtextselection.lib

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InternalFoundationTextApi
import androidx.compose.foundation.text.TextDelegate
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

@Composable
fun SelectableText(
  modifier: Modifier = Modifier,
  text: String,
  selectionState: SelectionState,
  color: Color = Color.Unspecified,
  fontSize: TextUnit = TextUnit.Unspecified,
  fontStyle: FontStyle? = null,
  fontWeight: FontWeight? = null,
  fontFamily: FontFamily? = null,
  letterSpacing: TextUnit = TextUnit.Unspecified,
  textDecoration: TextDecoration? = null,
  textAlign: TextAlign? = null,
  lineHeight: TextUnit = TextUnit.Unspecified,
  overflow: TextOverflow = TextOverflow.Clip,
  softWrap: Boolean = true,
  maxLines: Int = Int.MAX_VALUE,
  onTextLayout: (TextLayoutResult) -> Unit = {},
  style: TextStyle = LocalTextStyle.current
) {
  SelectableText(
    modifier = modifier,
    text = remember(key1 = text) { AnnotatedString(text) },
    selectionState = selectionState,
    color = color,
    fontSize = fontSize,
    fontStyle = fontStyle,
    fontWeight = fontWeight,
    fontFamily = fontFamily,
    letterSpacing = letterSpacing,
    textDecoration = textDecoration,
    textAlign = textAlign,
    lineHeight = lineHeight,
    overflow = overflow,
    softWrap = softWrap,
    maxLines = maxLines,
    onTextLayout = onTextLayout,
    style = style,
  )
}

@Composable
fun SelectableText(
  modifier: Modifier = Modifier,
  text: AnnotatedString,
  selectionState: SelectionState,
  color: Color = Color.Unspecified,
  fontSize: TextUnit = TextUnit.Unspecified,
  fontStyle: FontStyle? = null,
  fontWeight: FontWeight? = null,
  fontFamily: FontFamily? = null,
  letterSpacing: TextUnit = TextUnit.Unspecified,
  textDecoration: TextDecoration? = null,
  textAlign: TextAlign? = null,
  lineHeight: TextUnit = TextUnit.Unspecified,
  overflow: TextOverflow = TextOverflow.Clip,
  softWrap: Boolean = true,
  maxLines: Int = Int.MAX_VALUE,
  onTextLayout: (TextLayoutResult) -> Unit = {},
  style: TextStyle = LocalTextStyle.current
) {
  val selectionRegistrar = remember { SelectionRegistrarImpl() }
  val selectableId = rememberSaveable(text, selectionRegistrar, saver = selectionIdSaver(selectionRegistrar)) {
    selectionRegistrar.nextSelectableId()
  }

  val textState = remember { TextState(selectableId) }

  var layoutCoordinatesMut by remember { mutableStateOf<LayoutCoordinates?>(null) }
  val layoutCoordinates = layoutCoordinatesMut

  DisposableEffect(
    key1 = Unit,
    effect = {
      textState.selectable = selectionRegistrar.subscribe(
        MultiWidgetSelectionDelegate(
          selectableId = textState.selectableId,
          coordinatesCallback = { textState.layoutCoordinates },
          layoutResultCallback = { textState.layoutResult }
        )
      )

      onDispose {
        textState.selectable?.let { selectionRegistrar.unsubscribe(it) }
      }
    })

  SelectionContainer(
    layoutCoordinates = layoutCoordinates,
    selectionState = selectionState,
    selectionRegistrar = selectionRegistrar
  ) {
    val selectionBackgroundColor = LocalTextSelectionColors.current.backgroundColor

    Text(
      modifier = Modifier
        .pointerInput(
          key1 = Unit,
          block = {
            detectTapGestures(
              onDoubleTap = { offset ->
                selectionState.selectionEventFlow.tryEmit(SelectionState.SelectionEvent.Start(offset))
              }
            )
          }
        )
        .drawTextAndSelectionBehind(
          selectionBackgroundColor = selectionBackgroundColor,
          selectionRegistrar = selectionRegistrar,
          textState = textState
        )
        .onGloballyPositioned { coordinates ->
          layoutCoordinatesMut = coordinates
          textState.layoutCoordinates = coordinates

          if (selectionRegistrar.hasSelection(textState.selectableId)) {
            val newGlobalPosition = coordinates.positionInWindow()
            if (newGlobalPosition != textState.previousGlobalPosition) {
              selectionRegistrar.notifyPositionChange(textState.selectableId)
            }

            textState.previousGlobalPosition = newGlobalPosition
          }
        }
        .then(modifier),
      text = text,
      color = color,
      fontSize = fontSize,
      fontStyle = fontStyle,
      fontWeight = fontWeight,
      fontFamily = fontFamily,
      letterSpacing = letterSpacing,
      textDecoration = textDecoration,
      textAlign = textAlign,
      lineHeight = lineHeight,
      overflow = overflow,
      softWrap = softWrap,
      maxLines = maxLines,
      onTextLayout = { textLayoutResult ->
        textState.layoutResult = textLayoutResult
        onTextLayout(textLayoutResult)
      },
      style = style
    )
  }
}

@OptIn(InternalFoundationTextApi::class)
@Stable
private fun Modifier.drawTextAndSelectionBehind(
  selectionBackgroundColor: Color,
  selectionRegistrar: SelectionRegistrar,
  textState: TextState
): Modifier {
  return this
    .graphicsLayer()
    .drawBehind {
      textState.layoutResult?.let {
        textState.drawScopeInvalidation
        val selection = selectionRegistrar.subselections.get(textState.selectableId)

        if (selection != null) {
          val start = if (!selection.handlesCrossed) {
            selection.start.offset
          } else {
            selection.end.offset
          }
          val end = if (!selection.handlesCrossed) {
            selection.end.offset
          } else {
            selection.start.offset
          }

          if (start != end) {
            val selectionPath = it.multiParagraph.getPathForRange(start, end)
            drawPath(selectionPath, selectionBackgroundColor)
          }
        }
        drawIntoCanvas { canvas ->
          TextDelegate.paint(canvas, it)
        }
      }
    }
}

private fun selectionIdSaver(selectionRegistrar: SelectionRegistrar?) = Saver<Long, Long>(
  save = { if (selectionRegistrar.hasSelection(it)) it else null },
  restore = { it }
)

class SelectionState {
  val selectionEventFlow = MutableSharedFlow<SelectionEvent>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )

  sealed class SelectionEvent {
    data class Start(val offset: Offset) : SelectionEvent()
  }
}

private class TextState(
  val selectableId: Long
) {

  /** The [Selectable] associated with this [BasicText]. */
  var selectable: Selectable? = null

  /** The last layout coordinates for the Text's layout, used by selection */
  var layoutCoordinates: LayoutCoordinates? = null

  /** The latest TextLayoutResult calculated in the measure block.*/
  var layoutResult: TextLayoutResult? = null
    set(value) {
      drawScopeInvalidation = Unit
      field = value
    }

  /** The global position calculated during the last notifyPosition callback */
  var previousGlobalPosition: Offset = Offset.Zero

  /** Read in draw scopes to invalidate when layoutResult  */
  var drawScopeInvalidation by mutableStateOf(Unit, neverEqualPolicy())
    private set
}