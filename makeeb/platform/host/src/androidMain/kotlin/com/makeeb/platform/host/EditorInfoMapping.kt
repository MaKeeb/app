package com.makeeb.platform.host

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.makeeb.core.model.Capitalization
import com.makeeb.core.model.EditorAttributes
import com.makeeb.core.model.FieldType
import com.makeeb.core.model.ImeAction

/** Map the focused field's [EditorInfo] to the platform-neutral [EditorAttributes]. */
fun EditorInfo.toEditorAttributes(): EditorAttributes {
    val inputClass = inputType and InputType.TYPE_MASK_CLASS
    val variation = inputType and InputType.TYPE_MASK_VARIATION
    val flags = inputType and InputType.TYPE_MASK_FLAGS

    val fieldType = when (inputClass) {
        InputType.TYPE_CLASS_NUMBER ->
            if (variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) FieldType.Password else FieldType.Number
        InputType.TYPE_CLASS_PHONE -> FieldType.Phone
        InputType.TYPE_CLASS_DATETIME -> FieldType.DateTime
        InputType.TYPE_CLASS_TEXT -> when (variation) {
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD -> FieldType.Password
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> FieldType.Email
            InputType.TYPE_TEXT_VARIATION_URI -> FieldType.Uri
            InputType.TYPE_TEXT_VARIATION_PERSON_NAME -> FieldType.PersonName
            else -> FieldType.Text
        }
        else -> FieldType.Text
    }

    val capitalization = when {
        inputClass != InputType.TYPE_CLASS_TEXT -> Capitalization.None
        flags and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS != 0 -> Capitalization.Characters
        flags and InputType.TYPE_TEXT_FLAG_CAP_WORDS != 0 -> Capitalization.Words
        flags and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0 -> Capitalization.Sentences
        fieldType == FieldType.PersonName -> Capitalization.Words
        else -> Capitalization.None
    }

    val isTextLike = fieldType == FieldType.Text || fieldType == FieldType.PersonName
    val noSuggestions = flags and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0
    val noLearning = imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0

    return EditorAttributes(
        fieldType = fieldType,
        capitalization = capitalization,
        imeAction = imeAction(),
        isMultiLine = flags and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0,
        autoCorrect = isTextLike && !noSuggestions,
        suggestions = isTextLike && !noSuggestions,
        incognito = noLearning || fieldType == FieldType.Password,
        languageTags = hintLocales?.toLanguageTags()?.split(',')?.filter { it.isNotBlank() }.orEmpty(),
    )
}

private fun EditorInfo.imeAction(): ImeAction {
    if (imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return ImeAction.None
    return when (imeOptions and EditorInfo.IME_MASK_ACTION) {
        EditorInfo.IME_ACTION_GO -> ImeAction.Go
        EditorInfo.IME_ACTION_SEARCH -> ImeAction.Search
        EditorInfo.IME_ACTION_SEND -> ImeAction.Send
        EditorInfo.IME_ACTION_NEXT -> ImeAction.Next
        EditorInfo.IME_ACTION_PREVIOUS -> ImeAction.Previous
        EditorInfo.IME_ACTION_DONE -> ImeAction.Done
        else -> ImeAction.None
    }
}
