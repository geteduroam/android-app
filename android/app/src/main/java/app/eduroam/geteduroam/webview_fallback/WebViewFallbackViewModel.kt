package app.eduroam.geteduroam.webview_fallback

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import app.eduroam.geteduroam.NavTypes
import app.eduroam.geteduroam.Route
import app.eduroam.geteduroam.di.api.GetEduroamApi
import app.eduroam.geteduroam.di.repository.StorageRepository
import app.eduroam.geteduroam.models.Configuration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import net.openid.appauth.AuthorizationRequest
import javax.inject.Inject
import androidx.core.net.toUri

@HiltViewModel
class WebViewFallbackViewModel @Inject constructor(
    private val repository: StorageRepository,
    val api: GetEduroamApi,
): ViewModel() {

    var uiState by mutableStateOf(UiState())
    lateinit var configuration: Configuration

    fun setData(configuration: Configuration, urlToLoad: String) {
        uiState = UiState(startUri = urlToLoad.toUri())
    }

    fun getAuthRequest(): Flow<AuthorizationRequest?> {
        return repository.authRequest
    }
}

data class UiState(
    val startUri: Uri = Uri.EMPTY,
    val didNavigateToRedirectUri: Uri? = null
)