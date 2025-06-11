package app.eduroam.geteduroam.organizations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.eduroam.geteduroam.R
import app.eduroam.geteduroam.config.model.ProviderInfo
import app.eduroam.geteduroam.config.model.localizedMatch
import app.eduroam.geteduroam.extensions.removeNonSpacingMarks
import app.eduroam.geteduroam.ui.LinkifyText

@Composable
fun TermsOfUseDialog(
    providerInfo: ProviderInfo?,
    onConfirmClicked: () -> Unit,
    onDismiss: () -> Unit,
) = Dialog(
    onDismissRequest = onDismiss,
) {
    Surface(
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.terms_of_use_dialog_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.size(16.dp))
            var dialogDescription = stringResource(R.string.terms_of_use_dialog_text)
            providerInfo?.termsOfUse?.localizedMatch()?.let { termsOfUse ->
                dialogDescription += "\n\n" + termsOfUse.trim()
            }
            val topScrimAmount = 24.dp
            val bottomScrimAmount = 92.dp
            Box {
                Column(
                    modifier = Modifier
                        .heightIn(min = 0.dp, max = 400.dp)
                        .verticalScroll(state = rememberScrollState())
                        .padding(top = topScrimAmount, bottom = bottomScrimAmount)
                ) {
                    LinkifyText(
                        text = dialogDescription,
                        color = MaterialTheme.colorScheme.secondary,
                        linkColor = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                // Top scrim
                Surface(
                    modifier = Modifier.fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .height(topScrimAmount)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surface,
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                                )
                            )
                        ),
                    color = Color.Transparent
                ) {}
                // Bottom scrim
                Surface(
                    modifier = Modifier.fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .height(bottomScrimAmount)
                        .background(
                            brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    ),
                    color = Color.Transparent
                ) {}
            }
            Spacer(modifier = Modifier.size(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(weight = 1f, fill = false)
            ) {

                TextButton(onClick = onConfirmClicked) {
                    Text(
                        text = stringResource(R.string.terms_of_use_dialog_agree),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.terms_of_use_dialog_disagree),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}
