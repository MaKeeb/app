package com.makeeb.platform.host

import com.makeeb.core.model.Capitalization
import com.makeeb.core.model.EditorAttributes
import com.makeeb.core.model.FieldType
import com.makeeb.core.model.ImeAction
import platform.UIKit.UIKeyboardTypeASCIICapableNumberPad
import platform.UIKit.UIKeyboardTypeDecimalPad
import platform.UIKit.UIKeyboardTypeEmailAddress
import platform.UIKit.UIKeyboardTypeNamePhonePad
import platform.UIKit.UIKeyboardTypeNumberPad
import platform.UIKit.UIKeyboardTypePhonePad
import platform.UIKit.UIKeyboardTypeURL
import platform.UIKit.UIKeyboardTypeWebSearch
import platform.UIKit.UIReturnKeyType
import platform.UIKit.UITextAutocapitalizationType
import platform.UIKit.UITextAutocorrectionType
import platform.UIKit.UITextDocumentProxyProtocol

/**
 * Map the proxy's input traits to [EditorAttributes]. Secure fields never reach a third-party
 * keyboard on iOS (the system keyboard takes over), but the check stays for completeness.
 */
fun UITextDocumentProxyProtocol.toEditorAttributes(): EditorAttributes {
    val fieldType = when {
        secureTextEntry -> FieldType.Password
        else -> when (keyboardType) {
            UIKeyboardTypeNumberPad, UIKeyboardTypeDecimalPad, UIKeyboardTypeASCIICapableNumberPad -> FieldType.Number
            UIKeyboardTypePhonePad -> FieldType.Phone
            UIKeyboardTypeNamePhonePad -> FieldType.PersonName
            UIKeyboardTypeEmailAddress -> FieldType.Email
            UIKeyboardTypeURL, UIKeyboardTypeWebSearch -> FieldType.Uri
            else -> FieldType.Text
        }
    }
    val capitalization = when (autocapitalizationType) {
        UITextAutocapitalizationType.UITextAutocapitalizationTypeAllCharacters -> Capitalization.Characters
        UITextAutocapitalizationType.UITextAutocapitalizationTypeWords -> Capitalization.Words
        UITextAutocapitalizationType.UITextAutocapitalizationTypeSentences -> Capitalization.Sentences
        else -> Capitalization.None
    }
    val textLike = fieldType == FieldType.Text || fieldType == FieldType.PersonName
    val autoCorrect = textLike &&
        autocorrectionType != UITextAutocorrectionType.UITextAutocorrectionTypeNo

    return EditorAttributes(
        fieldType = fieldType,
        capitalization = capitalization,
        imeAction = returnKeyType.toImeAction(),
        // Return always inserts a newline on iOS; multi-line is not exposed to keyboards.
        isMultiLine = returnKeyType == UIReturnKeyType.UIReturnKeyDefault,
        autoCorrect = autoCorrect,
        suggestions = textLike,
        incognito = fieldType == FieldType.Password,
    )
}

private fun UIReturnKeyType.toImeAction(): ImeAction = when (this) {
    UIReturnKeyType.UIReturnKeyGo -> ImeAction.Go
    UIReturnKeyType.UIReturnKeyGoogle,
    UIReturnKeyType.UIReturnKeyYahoo,
    UIReturnKeyType.UIReturnKeySearch -> ImeAction.Search
    UIReturnKeyType.UIReturnKeySend -> ImeAction.Send
    UIReturnKeyType.UIReturnKeyNext -> ImeAction.Next
    UIReturnKeyType.UIReturnKeyDone -> ImeAction.Done
    UIReturnKeyType.UIReturnKeyJoin -> ImeAction.Join
    UIReturnKeyType.UIReturnKeyRoute -> ImeAction.Route
    UIReturnKeyType.UIReturnKeyContinue -> ImeAction.Continue
    UIReturnKeyType.UIReturnKeyEmergencyCall -> ImeAction.EmergencyCall
    else -> ImeAction.None
}
