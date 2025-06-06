package app.eduroam.geteduroam.organizations

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.eduroam.geteduroam.R
import app.eduroam.geteduroam.di.api.GetEduroamApi
import app.eduroam.geteduroam.di.repository.StorageRepository
import app.eduroam.geteduroam.extensions.removeNonSpacingMarks
import app.eduroam.geteduroam.models.Organization
import app.eduroam.geteduroam.ui.ErrorData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SelectOrganizationViewModel @Inject constructor(
    private val api: GetEduroamApi,
    private val storageRepository: StorageRepository
) : ViewModel() {

    var uiState by mutableStateOf(UiState())
        private set

    private var allOrganizations by mutableStateOf(emptyList<Organization>())

    val creds: MutableStateFlow<Pair<String?, String?>> =
        MutableStateFlow(Pair<String?, String?>(null, null))

    init {
        uiState = uiState.copy(isLoading = true)
        viewModelScope.launch (Dispatchers.IO) {
            try {
                val configuredOrganization = storageRepository.configuredOrganization.firstOrNull()
                withContext(Dispatchers.Main) {
                    uiState = uiState.copy(configuredOrganization = configuredOrganization)
                }
                val response = api.discover()
                val discoveryResult = response.body()
                if (response.isSuccessful && discoveryResult != null) {
                    withContext(Dispatchers.Main) {
                        allOrganizations = discoveryResult.content.institutions
                        uiState = uiState.copy(isLoading = false)
                        if (uiState.filter.isNotEmpty()) {
                            onSearchTextChange(uiState.filter)
                        }
                    }
                    do {
                        var canImproveSearchWords = false
                        allOrganizations.forEach {
                            val result = it.improveMatchWords()
                            canImproveSearchWords =  canImproveSearchWords || result
                        }
                        withContext(Dispatchers.Main) {
                            if (uiState.filter.isNotEmpty()) {
                                onSearchTextChange(uiState.filter)
                            }
                        }
                    } while (canImproveSearchWords)
                } else {
                    val failReason = "${response.code()}/${response.message()}]${
                        response.errorBody()?.string()
                    }"
                    Timber.w("Failed to load organizations: $failReason")
                    withContext(Dispatchers.Main) {
                        uiState = uiState.copy(
                            isLoading = false,
                            errorData = ErrorData(
                                titleId = R.string.err_title_generic_fail,
                                messageId = R.string.err_msg_generic_unexpected_with_arg,
                                messageArg = failReason
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to get organizations")
                withContext(Dispatchers.Main) {
                    uiState = uiState.copy(
                        isLoading = false,
                        errorData = ErrorData(
                            titleId = R.string.err_title_generic_fail,
                            messageId = R.string.err_msg_generic_unexpected_with_arg,
                            messageArg = "${e.message}/${e.javaClass.name}"
                        )
                    )
                }
            }
        }
    }

    fun onStepCompleted() {}

    fun onOrganizationSelect(organization: Organization) {
        Timber.v("User has selected organization ${organization.name} (ID: ${organization.id})")
        uiState = uiState.copy(selectedOrganization = organization)
    }

    fun onSearchTextChange(filter: String) {
        val filtered = if (filter.isNotBlank()) {
            val normalizedFilter = filter.removeNonSpacingMarks()
            val filterWords = filter.split(" ")
            allOrganizations.filter { organization ->
                if (filterWords.size == 1) {
                    organization.matchWords.any {
                        it.startsWith(normalizedFilter, ignoreCase = true)
                    }
                } else {
                    var containsAll = true
                    for (filterWord in filterWords) {
                        containsAll = containsAll && organization.matchWords.any {
                            it.startsWith(filterWord, ignoreCase = true)
                        }
                    }
                    containsAll
                }
            }.sortedBy { it.getLocalizedName().lowercase() }
        } else {
            emptyList()
        }
        // Search contains at least two dots
        var showConnectCta = filter.count { '.' == it } > 1
        if (showConnectCta) {
            try {
                Uri.parse(filter)
            } catch (ex: Exception) {
                // Not a valid URI!
                showConnectCta = false
            }
        }
        uiState = uiState.copy(
            filter = filter,
            organizations = filtered,
            showConnectCta = showConnectCta
        )
    }

    fun clearDialog() {
    }

    fun clearSelection() {
        uiState = uiState.copy(selectedOrganization = null, filter = "")
    }

    fun configuredProfileModalShown() {
        uiState = uiState.copy(didShowConfiguredOrganization = true)
    }
}