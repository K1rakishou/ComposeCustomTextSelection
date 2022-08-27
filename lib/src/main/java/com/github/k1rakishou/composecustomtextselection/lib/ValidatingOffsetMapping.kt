package com.github.k1rakishou.composecustomtextselection.lib

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation


internal val ValidatingEmptyOffsetMappingIdentity: OffsetMapping = ValidatingOffsetMapping(
  delegate = OffsetMapping.Identity,
  originalLength = 0,
  transformedLength = 0
)

internal fun VisualTransformation.filterWithValidation(text: AnnotatedString): TransformedText {
  return filter(text).let { transformed ->
    TransformedText(
      transformed.text,
      ValidatingOffsetMapping(
        delegate = transformed.offsetMapping,
        originalLength = text.length,
        transformedLength = transformed.text.length
      )
    )
  }
}

private class ValidatingOffsetMapping(
  private val delegate: OffsetMapping,
  private val originalLength: Int,
  private val transformedLength: Int
) : OffsetMapping {

  /**
   * Calls [originalToTransformed][OffsetMapping.originalToTransformed] and throws a detailed
   * exception if the returned value is outside the range of indices [0, [transformedLength]].
   */
  override fun originalToTransformed(offset: Int): Int {
    return delegate.originalToTransformed(offset).also { transformedOffset ->
      check(transformedOffset in 0..transformedLength) {
        "OffsetMapping.originalToTransformed returned invalid mapping: " +
          "$offset -> $transformedOffset is not in range of transformed text " +
          "[0, $transformedLength]"
      }
    }
  }

  /**
   * Calls [transformedToOriginal][OffsetMapping.transformedToOriginal] and throws a detailed
   * exception if the returned value is outside the range of indices [0, [originalLength]].
   */
  override fun transformedToOriginal(offset: Int): Int {
    return delegate.transformedToOriginal(offset).also { originalOffset ->
      check(originalOffset in 0..originalLength) {
        "OffsetMapping.transformedToOriginal returned invalid mapping: " +
          "$offset -> $originalOffset is not in range of original text " +
          "[0, $originalLength]"
      }
    }
  }
}