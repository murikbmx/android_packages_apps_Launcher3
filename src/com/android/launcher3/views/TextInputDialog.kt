/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.views

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.launcher3.R
import kotlinx.coroutines.flow.MutableStateFlow

/** Type alias for a dialog asking for a single line of text, such as a name. */
typealias TextInputDialog = Dialog<TextInputDialogViewModel>

/** Model for a dialog asking for a single line of text, such as a name. */
class TextInputDialogViewModel(
    activityContext: ActivityContext,
    title: String,
    initialText: String,
    positiveButton: String,
    onSave: (String) -> Unit,
) :
    DialogViewModel<TextInputDialogViewModel>(
        title = title,
        content = { viewModel -> TextInputDialogView(viewModel) },
        neutralButton =
            activityContext
                .asContext()
                .getString(R.string.home_screen_files_rename_dialog_neutral_button),
        positiveButton = positiveButton,
        onPositiveButtonClick = onPositiveButtonClick@{ viewModel ->
                val text = viewModel.text.value.text.trim()
                if (text.isEmpty()) {
                    return@onPositiveButtonClick false
                }
                onSave(text)
                return@onPositiveButtonClick true
            },
    ) {

    // The entered text. Updated via user interaction with the dialog.
    val text =
        MutableStateFlow(
            TextFieldValue(text = initialText, selection = TextRange(0, initialText.length))
        )
}

/** Composable which renders the content view for the text input dialog. */
@Composable
fun DialogScope.TextInputDialogView(viewModel: TextInputDialogViewModel) {
    val textFieldFocusRequester = remember { FocusRequester() }
    val textFieldValue by viewModel.text.collectAsStateWithLifecycle()

    OutlinedTextField(
        modifier =
            Modifier.fillMaxWidth().focusRequester(textFieldFocusRequester).onFocusChanged {
                // Select the whole text on focus so it can be replaced right away
                val value = viewModel.text.value
                viewModel.text.value =
                    value.copy(
                        selection =
                            if (it.isFocused) TextRange(0, value.text.length) else TextRange.Zero
                    )
            },
        value = textFieldValue,
        onValueChange = { viewModel.text.value = it },
        keyboardActions =
            KeyboardActions(
                onDone = {
                    if (viewModel.onPositiveButtonClick?.invoke(viewModel) == true) {
                        dismiss(animate = true)
                    }
                }
            ),
        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
        singleLine = true,
        trailingIcon = {
            IconButton(
                onClick = {
                    viewModel.text.value = TextFieldValue()
                    textFieldFocusRequester.requestFocus()
                }
            ) {
                Icon(
                    painterResource(R.drawable.ic_home_screen_files_rename_dialog_clear),
                    stringResource(R.string.home_screen_files_rename_dialog_clear_button),
                )
            }
        },
    )

    // The text field should receive initial focus.
    LaunchedEffect(Unit) { textFieldFocusRequester.requestFocus() }
}
