package app.eduroam.geteduroam.oauth

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.flowWithLifecycle
import app.eduroam.geteduroam.EduTopAppBar
import app.eduroam.geteduroam.R
import app.eduroam.geteduroam.models.Configuration
import app.eduroam.geteduroam.ui.AlertDialogWithSingleButton
import app.eduroam.geteduroam.ui.PrimaryButton
import app.eduroam.geteduroam.webview_fallback.WebViewDataHandlingActivity

@Composable
fun OAuthScreen(
    viewModel: OAuthViewModel,
    configuration: Configuration,
    redirectUri: Uri?,
    goToPrevious: () -> Unit,
    goToWebViewFallback: (Configuration, Uri) -> Unit,
) = EduTopAppBar(onBackClicked = goToPrevious) {
    var oAuthUiStages by rememberSaveable { mutableStateOf(OAuthUiStages()) }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val launcher =
        rememberLauncherForActivityResult(contract = OAuthContract(), onResult = { resultIntent ->
            viewModel.continueWithFetchToken(resultIntent)
            oAuthUiStages = OAuthUiStages(
                isAuthorizationLaunched = false, isFetchingToken = true
            )
        })
    LaunchedEffect(viewModel) {
        viewModel.prepareAppAuth(configuration, context, redirectUri)
    }
    LaunchedEffect(viewModel, lifecycle) {
        snapshotFlow { viewModel.uiState }
            .flowWithLifecycle(lifecycle)
            .collect { state ->
                if (state.oauthStep is OAuthStep.WebViewFallback) {
                    goToWebViewFallback(state.oauthStep.configuration, state.oauthStep.requestUri)
                    viewModel.didGoToNextScreen()
                } else if (state.oauthStep is OAuthStep.GetTokensFromRedirectUri) {
                    launcher.launch(WebViewDataHandlingActivity.createIntent(context, state.oauthStep.authRequest, state.oauthStep.redirectUri))
                    viewModel.didGoToNextScreen()
                }
            }
    }

    if (oAuthUiStages.isFetchingToken && viewModel.uiState.oauthStep is OAuthStep.Authorized) {
        val currentGoToPrevious by rememberUpdatedState(newValue = goToPrevious)
        LaunchedEffect(viewModel) {
            //Clear the uistages to prevent any back stack navigation issues.
            oAuthUiStages = OAuthUiStages()
            currentGoToPrevious()
        }
    }
    OAuthContent(
        uiState = viewModel.uiState,
        isAuthorizationLaunched = oAuthUiStages.isAuthorizationLaunched,
        padding = it,
        launchAuthorization = { intentAvailable ->
//            Timber.w("0 - AppAuth intent available. Launching OAuth")
            oAuthUiStages = OAuthUiStages(
                isAuthorizationLaunched = true,
            )
            viewModel.didGoToNextScreen()
            launcher.launch(intentAvailable)
        },
        dismissError = viewModel::dismissError,
        onRetry = { viewModel.prepareAppAuth(configuration, context, null) },
    )
}

@Composable
private fun OAuthContent(
    uiState: UiState,
    isAuthorizationLaunched: Boolean,
    padding: PaddingValues = PaddingValues(),
    launchAuthorization: (Intent) -> Unit,
    dismissError: () -> Unit,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    if (uiState.error != null) {
        AlertDialogWithSingleButton(
            title = uiState.error.title(context),
            explanation = uiState.error.message(context),
            buttonLabel = stringResource(R.string.button_ok),
            onDismiss = dismissError
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            Text(
                text = stringResource(R.string.oauth_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = stringResource(R.string.oauth_description),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
            if (uiState.oauthStep.isProcessing) {
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            if (uiState.oauthStep is OAuthStep.Initialized && !isAuthorizationLaunched) {
                LaunchedEffect(uiState.oauthStep.intent) {
                    launchAuthorization(uiState.oauthStep.intent)
                }
            }
        }
        if (uiState.oauthStep is OAuthStep.Error) {
            PrimaryButton(
                text = stringResource(R.string.button_retry),
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview
@Composable
fun PreviewOAuthScreenContent() =
    OAuthContent(uiState = UiState(OAuthStep.Loading), isAuthorizationLaunched = false, launchAuthorization = {}, dismissError = { }) {
    }