package app.eduroam.geteduroam.organizations

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType.Companion.Password
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.eduroam.geteduroam.R

@Composable
fun PassphraseDialog(
    isRetry: Boolean,
    cancel: () -> Unit,
    done: (String) -> Unit,
) = Dialog(
    onDismissRequest = { },
    properties = DialogProperties(decorFitsSystemWindows = false)
) {
    var passphrase by remember { mutableStateOf("") }
    var passphraseVisible by rememberSaveable { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    Column {
        Surface(
            shape = MaterialTheme.shapes.medium
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(id = R.string.passphrase_dialog_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(modifier = Modifier.size(16.dp))
                Text(text = stringResource(id = R.string.passphrase_dialog_message))
                Spacer(modifier = Modifier.size(16.dp))
                // Username / password input fields need to be LTR even in RTL languages
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    TextField(
                        modifier = Modifier.fillMaxWidth().semantics {
                            contentType = Password
                        },
                        value = passphrase,
                        onValueChange = {
                            passphrase = it
                        },
                        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
                        maxLines = 1,
                        singleLine = true,
                        visualTransformation = if (passphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrect = false, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (passphrase.isNotBlank()) {
                                    done(passphrase)
                                } else {
                                    keyboardController?.hide()
                                }
                            }
                        ),
                        label = {
                            Text(
                                stringResource(id = R.string.passphrase_dialog_label_passphrase),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        trailingIcon = {
                            val image = if (passphraseVisible)
                                Icons.Filled.Visibility
                            else Icons.Filled.VisibilityOff

                            // Please provide localized description for accessibility services
                            val description = if (passphraseVisible) {
                                stringResource(id = R.string.passphrase_accessibility_hide_passphrase)
                            } else {
                                stringResource(id = R.string.passphrase_accessibility_show_passphrase)
                            }

                            IconButton(onClick = { passphraseVisible = !passphraseVisible }) {
                                Icon(imageVector = image, description)
                            }
                        }
                    )
                }
                if (isRetry) {
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = stringResource(id = R.string.passphrase_dialog_incorrect_passphrase),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                Spacer(modifier = Modifier.size(16.dp))
                Row {
                    Button(
                        onClick = {
                            cancel()
                        },
                    ) {
                        Text(stringResource(id = R.string.passphrase_dialog_button_cancel))
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                    Button(
                        onClick = {
                            done(passphrase)
                        },
                        enabled = passphrase.isNotBlank()
                    ) {
                        Text(stringResource(id = R.string.passphrase_dialog_button_enter))
                    }
                }
            }
        }
        // Spacer to slide the dialog up when the keyboard shows
        Spacer(modifier = Modifier
            .navigationBarsPadding()
            .imePadding()
            .size(16.dp))
    }
}

@Preview
@Composable
fun PassphraseDialog_Preview() {
    PassphraseDialog(
        isRetry = true,
        cancel = { },
        done = { _ -> }
    )
}