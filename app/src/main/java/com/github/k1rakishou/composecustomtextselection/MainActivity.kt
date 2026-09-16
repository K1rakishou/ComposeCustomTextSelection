package com.github.k1rakishou.composecustomtextselection

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.k1rakishou.composecustomtextselection.lib.DefaultSelectionHandlePainter
import com.github.k1rakishou.composecustomtextselection.lib.SelectableTextContainer
import com.github.k1rakishou.composecustomtextselection.lib.rememberTextSelectionState
import com.github.k1rakishou.composecustomtextselection.ui.theme.ComposeCustomTextSelectionTheme
import com.github.k1rakishou.composecustomtextselection.lib.SelectableTextToolbarDefaults


class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      ComposeCustomTextSelectionTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colors.background
        ) {
          Content()
        }
      }
    }
  }
}

private val text = """
Lorem ipsum dolor sit amet, consectetur adipiscing elit. Praesent justo nulla, dictum in ornare hendrerit, mattis nec nisi. Vestibulum consequat velit eu magna feugiat, sed porttitor ipsum bibendum. Nam vitae lectus risus. Maecenas in sem turpis. Fusce nec justo sed ante accumsan laoreet. Quisque varius est sit amet elit sagittis, id facilisis sapien tincidunt. Sed tristique maximus dolor, congue scelerisque nulla sodales eget. Sed et massa pulvinar, mollis felis in, finibus quam. Mauris augue nisi, mattis vel eleifend vel, mollis ac velit. Nulla facilisi. Aenean fringilla neque ac nisl finibus fringilla. Donec a accumsan purus. Ut eu nisl neque. Praesent imperdiet eros ac massa tempor, nec porta nibh feugiat.

Proin finibus tellus nec est euismod auctor. Suspendisse nunc dolor, ultricies vestibulum tincidunt id, hendrerit id lorem. Maecenas at mollis massa, ac ultricies odio. In dignissim nulla vel elit cursus vehicula. Morbi nulla ipsum, scelerisque id leo et, fermentum gravida ligula. Etiam sem neque, efficitur et venenatis et, maximus at sapien. Pellentesque vel arcu feugiat metus tristique rhoncus. Donec maximus eu ligula nec pharetra. Donec pellentesque, tellus vitae dictum porta, tellus arcu accumsan mauris, id ornare mauris dui sed nisi. Phasellus ultricies nec tellus at ullamcorper. Morbi ac lorem enim.

Suspendisse lacus nulla, convallis a blandit accumsan, pellentesque vel ante. Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vestibulum a quam libero. Phasellus pulvinar vestibulum libero. Phasellus blandit ornare massa quis molestie. In enim turpis, pulvinar eu suscipit eu, ultrices id turpis. Duis ut rhoncus leo.

Curabitur laoreet efficitur commodo. Nunc condimentum ligula ut lacus vestibulum, eu imperdiet tellus hendrerit. Nam sed magna interdum, elementum urna tincidunt, ultrices lacus. Mauris sed ultricies lectus. Donec in massa at libero feugiat elementum. Suspendisse ac neque eget lorem ornare elementum. Morbi hendrerit purus nibh, eu auctor lorem vehicula vitae. Morbi et magna ultrices, molestie arcu vitae, lacinia nibh. Curabitur nec feugiat nulla. Phasellus at lectus luctus ipsum lobortis dapibus ac sit amet velit. Sed libero neque, placerat egestas tempus et, lobortis a est. Proin rutrum, purus in vulputate vehicula, elit odio vulputate ex, ut rhoncus ligula mauris at augue.

Integer sit amet massa non orci accumsan molestie posuere venenatis nunc. Nulla non tempor lectus, et efficitur augue. Quisque consectetur ac ligula a convallis. Vivamus tincidunt eu massa a feugiat. Pellentesque arcu nisi, consectetur eget mi eu, dapibus interdum arcu. Sed felis urna, malesuada quis interdum vel, luctus mollis arcu. Nam id lobortis lacus. Phasellus pharetra congue leo sit amet elementum. Proin feugiat elit elit, id pulvinar nulla congue quis. Aenean eget dapibus nibh, sit amet bibendum felis. Morbi eu cursus ipsum. Duis a volutpat nisl. Quisque vulputate enim id cursus sodales. Suspendisse potenti. 
  """.trimIndent()

@Composable
fun Content() {
  val context = LocalContext.current

  Row(
    modifier = Modifier
      .fillMaxSize()
      .padding(vertical = 24.dp, horizontal = 8.dp),
  ) {
    Column(
      modifier = Modifier
        .fillMaxHeight()
        .weight(0.5f)
        .verticalScroll(rememberScrollState())
    ) {
      Spacer(modifier = Modifier.height(32.dp))
      Text(text = "CustomSelectableText", fontSize = 20.sp)
      Spacer(modifier = Modifier.height(16.dp))

      CustomSelectableText(
        copySelectedText = { selectedText ->
          println("selected text: ${selectedText.text}")
          Toast.makeText(context, selectedText.text, Toast.LENGTH_LONG).show()
        }
      )
    }

    Spacer(modifier = Modifier.height(8.dp))

    Column(
      modifier = Modifier
        .fillMaxHeight()
        .weight(0.5f)
        .verticalScroll(rememberScrollState())
    ) {
      Spacer(modifier = Modifier.height(32.dp))
      Text(text = "AndroidSelectableText", fontSize = 20.sp)
      Spacer(modifier = Modifier.height(16.dp))
      AndroidSelectableText()
    }
  }
}

@Composable
private fun CustomSelectableText(
  copySelectedText: (AnnotatedString) -> Unit
) {
  val handleSize = 28.dp
  val handleColor = remember { Color(0xFF0093afL) }
  val toolbarBgColor = remember { Color(0xFFe1e2ed) }
  val selectionColor = remember { Color(0xA00067a5L) }

  val startHandlePainter = remember {
    DefaultSelectionHandlePainter(
      color = handleColor,
      isLeftHandle = true,
    )
  }
  val endHandlePainter = remember {
    DefaultSelectionHandlePainter(
      color = handleColor,
      isLeftHandle = false,
    )
  }

  val textSelectionState = rememberTextSelectionState(
    selectionColor = selectionColor,
    handleSize = handleSize,
    debugMode = false
  )
  val copySelectedTextUpdated by rememberUpdatedState(newValue = copySelectedText)

  SelectableTextContainer(
    modifier = Modifier
      .wrapContentHeight(),
    selectableTextState = textSelectionState,
    handleSize = handleSize,
    startSelectionHandlePainter = startHandlePainter,
    endSelectionHandlePainter = endHandlePainter,
    onClicked = { println("TTTAAA onClicked") },
    onLongClicked = { println("TTTAAA onLongClicked") },
    toolbarColors = SelectableTextToolbarDefaults.colors(
      backgroundColor = toolbarBgColor,
      contentColor = Color.Black
    ),
    toolbar = {
      item(key = "copy", text = "Copy") { selectedText ->
        copySelectedTextUpdated(selectedText.text)
      }
      item(key = "search", text = "Search") { selectedText ->
        println("selected range: ${selectedText.range}")
      }
    },
    textContent = { onTextLayout ->
      Text(
        text = text,
        onTextLayout = { textLayoutResult ->
          onTextLayout(textLayoutResult)
        }
      )
    }
  )
}

@Composable
private fun AndroidSelectableText() {
  SelectionContainer(
    modifier = Modifier
      .wrapContentHeight()
      .combinedClickable(
        onClick = {},
        onLongClick = {}
      ),
  ) {
    Text(text = text)
  }
}