package app.eduroam.geteduroam.ui

import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.core.content.IntentCompat
import app.eduroam.geteduroam.R
import app.eduroam.geteduroam.ui.theme.AppTheme

@Composable
fun HibernationExemptionDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.hibernation_exemption_dialog_title),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.hibernation_exemption_dialog_message, stringResource(R.string.name)),
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.hibernation_exemption_dialog_confirm),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.hibernation_exemption_dialog_dismiss),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    )
}

fun launchManageUnusedAppRestrictionsIntent(context: Context) {
    val intent = IntentCompat.createManageUnusedAppRestrictionsIntent(context, context.packageName)
    context.startActivity(intent)
}

@PreviewLightDark
@Composable
private fun HibernationExemptionDialog_Preview() {
    AppTheme {
        HibernationExemptionDialog(
            onConfirm = {},
            onDismiss = {}
        )
    }
}
