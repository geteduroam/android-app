package app.eduroam.geteduroam

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalFocusManager
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.eduroam.geteduroam.config.WifiConfigScreen
import app.eduroam.geteduroam.config.WifiConfigViewModel
import app.eduroam.geteduroam.oauth.OAuthScreen
import app.eduroam.geteduroam.oauth.OAuthViewModel
import app.eduroam.geteduroam.organizations.SelectOrganizationScreen
import app.eduroam.geteduroam.organizations.SelectOrganizationViewModel
import app.eduroam.geteduroam.profile.SelectProfileScreen
import app.eduroam.geteduroam.profile.SelectProfileViewModel
import app.eduroam.geteduroam.webview_fallback.WebViewFallbackScreen
import app.eduroam.geteduroam.webview_fallback.WebViewFallbackViewModel

@Composable
fun MainGraph(
    navController: NavHostController = rememberNavController(),
    openFileUri: (Uri) -> Unit,
    closeApp: () -> Unit
) : NavController {
    NavHost(
        navController = navController, startDestination = Route.SelectInstitution
    ) {
        composable<Route.SelectInstitution>(NavTypes.allTypesMap) { entry ->
            val viewModel = hiltViewModel<SelectOrganizationViewModel>(entry)
            val focusManager = LocalFocusManager.current
            SelectOrganizationScreen(
                viewModel = viewModel,
                openProfileModal = { institutionId ->
                    // Remove the focus from the search field (if it was there)
                    focusManager.clearFocus(force = true)
                    navController.navigate(Route.SelectProfile(institutionId = institutionId, customHostUri = null))
                },
                goToOAuth = { configuration ->
                    navController.navigate(
                        Route.OAuth(
                            configuration = configuration,
                            redirectUri = null
                        )
                    )
                },
                goToConfigScreen = { configuredOrganization, configuredProfileId, wifiConfigData ->
                    navController.popBackStack()
                    navController.navigate(
                        Route.ConfigureWifi(
                            configuredOrganization, configuredProfileId, wifiConfigData
                        )
                    )
                },
                openFileUri = openFileUri,
                discoverUrl = {
                    navController.navigate(Route.SelectProfile(institutionId = null, customHostUri = it.toString()))
                }
            )
        }
        composable<Route.SelectProfile>(NavTypes.allTypesMap) { entry ->
            val viewModel = hiltViewModel<SelectProfileViewModel>(entry)
            SelectProfileScreen(
                viewModel = viewModel,
                goToOAuth = { configuration ->
                    navController.navigate(Route.OAuth(configuration, null))
                },
                goToConfigScreen = { configuredOrganization, profileId, provider ->
                    navController.navigate(
                        Route.ConfigureWifi(
                            configuredOrganization,
                            profileId,
                            provider
                        )
                    )
                },
                goToPrevious = {
                    navController.popBackStack()
                })
        }
        composable<Route.OAuth>(NavTypes.allTypesMap) { entry ->
            val viewModel = hiltViewModel<OAuthViewModel>(entry)
            OAuthScreen(
                viewModel = viewModel,
                goToPrevious = {
                    navController.popBackStack()
                },
                goToWebViewFallback = { configuration, navigationUri ->
                    navController.navigate(
                        Route.WebViewFallback(configuration, navigationUri.toString())
                    )
                }
            )
        }
        composable<Route.WebViewFallback>(NavTypes.allTypesMap) { entry ->
            val viewModel = hiltViewModel<WebViewFallbackViewModel>(entry)
            WebViewFallbackScreen(
                viewModel = viewModel,
                onRedirectUriFound = { configuration, uri ->
                    navController.popBackStack() // this screen
                    navController.popBackStack() // OAuth screen
                    navController.navigate(Route.OAuth(configuration, uri.toString())) // OAuth screen again
                },
                onCancel = {
                    navController.navigateUp() // OAuth screen
                    navController.navigateUp() // Profile screen
                }
            )
        }

        composable<Route.ConfigureWifi>(NavTypes.allTypesMap) { entry ->
            val viewModel = hiltViewModel<WifiConfigViewModel>(entry)

            WifiConfigScreen(
                viewModel,
                closeApp = closeApp,
                goBack = {
                    navController.navigateUp()
                }
            )
        }
    }
    return navController
}