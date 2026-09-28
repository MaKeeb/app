package com.makeeb.core.model

/**
 * The platform-neutral description of the focused text field. Android builds it from
 * `EditorInfo`, iOS from the `UITextDocumentProxy` input traits (see `:platform:host`).
 */
data class EditorAttributes(
    val fieldType: FieldType = FieldType.Text,
    val capitalization: Capitalization = Capitalization.Sentences,
    val imeAction: ImeAction = ImeAction.None,
    val isMultiLine: Boolean = false,
    /** The field allows autocorrection (false for e-mail, URL, code fields…). */
    val autoCorrect: Boolean = true,
    /** The field allows a suggestion strip. */
    val suggestions: Boolean = true,
    /**
     * The app asked for no learning from this field (Android `IME_FLAG_NO_PERSONALIZED_LEARNING`;
     * always true for passwords). Nothing typed here may reach the user dictionary or history.
     */
    val incognito: Boolean = false,
    /** BCP 47 language hints from the app, most preferred first. */
    val languageTags: List<String> = emptyList(),
) {
    val isPassword: Boolean get() = fieldType == FieldType.Password

    /** The layout to open with when this field gains focus. */
    val initialMode: KeyboardMode
        get() = when (fieldType) {
            FieldType.Number -> KeyboardMode.Numeric
            FieldType.Phone -> KeyboardMode.Phone
            else -> KeyboardMode.Letters
        }

    companion object {
        val Default = EditorAttributes()
    }
}

enum class FieldType {
    Text,
    Email,
    Uri,
    Number,
    Phone,
    DateTime,
    Password,
    PersonName,
}

enum class Capitalization {
    None,
    Characters,
    Words,
    Sentences,
}

/** The action the Enter key performs. [None] means the key inserts a newline. */
enum class ImeAction {
    None,
    Go,
    Search,
    Send,
    Next,
    Previous,
    Done,
    Join,
    Route,
    Continue,
    EmergencyCall,
}
