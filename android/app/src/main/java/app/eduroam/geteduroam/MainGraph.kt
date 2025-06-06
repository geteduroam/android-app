package app.eduroam.geteduroam

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalFocusManager
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSavedStateNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.rememberSceneSetupNavEntryDecorator
import app.eduroam.geteduroam.config.WifiConfigScreen
import app.eduroam.geteduroam.config.WifiConfigViewModel
import app.eduroam.geteduroam.oauth.OAuthScreen
import app.eduroam.geteduroam.organizations.SelectOrganizationScreen
import app.eduroam.geteduroam.profile.SelectProfileScreen
import app.eduroam.geteduroam.webview_fallback.WebViewFallbackScreen

@Composable
fun MainGraph(
    openFileUri: (Uri) -> Unit,
    closeApp: () -> Unit
): MutableList<Route> {
    val viewModelDecorator = rememberViewModelStoreNavEntryDecorator()
    val backStack = remember { mutableStateListOf<Route>(Route.SelectInstitution) }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = { route ->
            when (route) {
                is Route.SelectInstitution -> NavEntry(route) { entry ->
                    val focusManager = LocalFocusManager.current
                    SelectOrganizationScreen(
                        viewModel = hiltViewModel(),
                        openProfileModal = { institutionId ->
                            // Remove the focus from the search field (if it was there)
                            focusManager.clearFocus(force = true)
                            backStack.add(Route.SelectProfile(institutionId = institutionId, customHostUri = null))
                        },
                        goToOAuth = { configuration ->
                            backStack.add(
                                Route.OAuth(
                                    configuration = configuration,
                                    redirectUri = null
                                )
                            )
                        },
                        goToConfigScreen = { configuredOrganization, configuredProfileId, wifiConfigData ->
                            backStack.removeLastOrNull()
                            backStack.add(
                                Route.ConfigureWifi(
                                    configuredOrganization, configuredProfileId, wifiConfigData
                                )
                            )
                        },
                        openFileUri = openFileUri,
                        discoverUrl = {
                            backStack.add(Route.SelectProfile(institutionId = null, customHostUri = it.toString()))
                        }
                    )
                }

                is Route.SelectProfile -> NavEntry(route) { entry ->
                    SelectProfileScreen(
                        viewModel = hiltViewModel(),
                        institutionId = route.institutionId,
                        customHostUri = route.customHostUri,
                        goToOAuth = { configuration ->
                            backStack.add(Route.OAuth(configuration, null))
                        },
                        goToConfigScreen = { configuredOrganization, profileId, provider ->
                            backStack.add(
                                Route.ConfigureWifi(
                                    configuredOrganization,
                                    profileId,
                                    provider
                                )
                            )
                        },
                        goToPrevious = {
                            backStack.removeLastOrNull()
                        })
                }

                is Route.OAuth -> NavEntry(route) { entry ->
                    OAuthScreen(
                        viewModel = hiltViewModel(),
                        configuration = route.configuration,
                        redirectUri = route.redirectUri?.toUri(),
                        goToPrevious = {
                            backStack.removeLastOrNull()
                        },
                        goToWebViewFallback = { configuration, navigationUri ->
                            backStack.add(
                                Route.WebViewFallback(configuration, navigationUri.toString())
                            )
                        }
                    )
                }

                is Route.WebViewFallback -> NavEntry(route) { entry ->
                    WebViewFallbackScreen(
                        viewModel = hiltViewModel(),
                        configuration = route.configuration,
                        urlToLoad = route.urlToLoad,
                        onRedirectUriFound = { configuration, uri ->
                            backStack.removeLastOrNull() // this screen
                            backStack.removeLastOrNull() // OAuth screen
                            backStack.add(Route.OAuth(configuration, uri.toString())) // OAuth screen again
                        },
                        onCancel = {
                            backStack.removeLastOrNull() // OAuth screen
                            backStack.removeLastOrNull() // Profile screen
                        }
                    )
                }
                is Route.ConfigureWifi -> NavEntry(route) {
                    WifiConfigScreen(
                        viewModel = hiltViewModel(),
                        eapIdentityProviderList = route.eapIdentityProviderList,
                        configuredOrganization = route.configuredOrganization,
                        configuredProfileId = route.configuredProfileId,
                        closeApp = closeApp,
                        goBack = {
                            backStack.removeLastOrNull()
                        }
                    )
                }
                else -> throw IllegalArgumentException("Unknown route: $route")
            }
        },
        entryDecorators =
            listOf(
                rememberSceneSetupNavEntryDecorator(),
                rememberSavedStateNavEntryDecorator(),
                viewModelDecorator
            )
    )

    return backStack
}