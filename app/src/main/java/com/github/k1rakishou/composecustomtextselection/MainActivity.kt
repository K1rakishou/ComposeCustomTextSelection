package com.github.k1rakishou.composecustomtextselection

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.composecustomtextselection.lib.SelectableText
import com.github.k1rakishou.composecustomtextselection.lib.SelectionState
import com.github.k1rakishou.composecustomtextselection.lib.textSelectionAfterDoubleTap
import com.github.k1rakishou.composecustomtextselection.ui.theme.ComposeCustomTextSelectionTheme


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
    Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed non augue dapibus, imperdiet ipsum non, feugiat elit. In convallis lobortis nisl in sodales. Fusce cursus mauris at tortor porta aliquet. Quisque tincidunt arcu facilisis, sodales dui non, euismod tortor. Suspendisse sodales commodo mauris sodales fringilla. Nunc ac justo convallis enim feugiat condimentum. Quisque urna sem, semper eu tristique vel, commodo non diam.

    Quisque cursus sapien eu malesuada facilisis. Aenean sem lorem, vestibulum vel mi id, pellentesque congue lorem. In pulvinar sollicitudin massa. Fusce a lorem finibus dui auctor imperdiet. Mauris consequat dui ac dapibus tristique. Donec augue elit, maximus ut nunc non, bibendum rhoncus tellus. Etiam molestie ut odio eget mattis. Quisque ultrices lectus nulla, mattis mattis urna mattis nec. Nulla tincidunt ornare felis vel eleifend. Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vestibulum in est odio. Etiam posuere nisl metus, vitae bibendum ligula blandit et.

    Nunc est dui, varius nec dolor ac, ultrices maximus ligula. Fusce sollicitudin non lectus vel aliquam. Maecenas porta lacinia libero ac accumsan. Vestibulum ante ipsum primis in faucibus orci luctus et ultrices posuere cubilia curae; Integer hendrerit tempor urna vehicula vestibulum. Aenean mollis augue sed leo pretium, eget consectetur dui pharetra. Integer eu turpis nec sem malesuada vulputate id nec nisl. Integer eu imperdiet odio.

    Vestibulum tellus lacus, mollis et ultrices ut, fermentum vitae metus. Morbi ornare odio id arcu cursus interdum. Donec tellus arcu, tincidunt id neque et, lacinia blandit ligula. Ut posuere mi quis nunc porttitor, quis condimentum elit pharetra. Aenean in ex convallis, aliquam urna vel, elementum turpis. Nulla at dapibus urna, sed tristique metus. Morbi volutpat magna vel rhoncus tempor. Phasellus at gravida urna, non mattis odio.

    Quisque turpis massa, ornare vel tincidunt non, viverra quis libero. Sed fringilla feugiat dolor, ac lacinia lectus interdum et. Curabitur elementum nulla at mattis tempor. Praesent sed facilisis augue, in vulputate massa. Curabitur non aliquam est. Curabitur feugiat, nisi id mattis dignissim, tortor nunc feugiat diam, sit amet congue ligula arcu vel nibh. Sed sem lorem, faucibus et cursus et, cursus eu odio. Curabitur ligula purus, aliquam ut lectus in, luctus gravida dui. Donec condimentum eleifend suscipit. Vivamus semper facilisis dolor non dapibus. Proin quis felis sed nunc porta scelerisque eu sed urna.
  """.trimIndent()

@Composable
fun Content() {
  Column(modifier = Modifier.fillMaxSize()) {
    CustomSelectableText()

    Spacer(modifier = Modifier.height(8.dp))

    SelectionContainer(
      modifier = Modifier
        .height(300.dp)
        .background(Color.Green.copy(alpha = 0.3f)),
    ) {
      Text(text = text)
    }
  }
}

@Composable
private fun CustomSelectableText() {
  val selectionState = remember { SelectionState() }

  SelectableText(
    modifier = Modifier
      .pointerInput(
        key1 = Unit,
        block = { textSelectionAfterDoubleTap(selectionState) }
      )
      .height(300.dp)
      .background(Color.Red.copy(alpha = 0.3f)),
    text = text,
    selectionState = selectionState
  )
}