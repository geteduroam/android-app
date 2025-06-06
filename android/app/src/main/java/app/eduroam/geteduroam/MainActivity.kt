package app.eduroam.geteduroam

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.navigation.NavController
import app.eduroam.geteduroam.di.repository.NotificationRepository
import app.eduroam.geteduroam.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.coroutines.CoroutineContext

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val job = Job()
    private val coroutineContext: CoroutineContext
        get() = job + Dispatchers.IO

    private val coroutineScope = CoroutineScope(coroutineContext)

    private lateinit var backStack: MutableList<Route>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        setContent {
            AppTheme {
                backStack = MainGraph(
                    closeApp = {
                        this@MainActivity.finish()
                    },
                    openFileUri = {
                        coroutineScope.launch {
                            Timber.d("User has opened an .eap-config file using the built-in button")
                            if (!openFileUri(it)) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@MainActivity, R.string.err_msg_not_a_valid_eap_config_file, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                )
            }
        }
        handleNewIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNewIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    private fun handleNewIntent(intent: Intent?) {
        runOnUiThread {
            if (intent?.dataString?.startsWith("content://") == true) {
                coroutineScope.launch {
                    Timber.d("User has opened an .eap-config file with the app...")
                    if (!openFileUri(intent.data!!)) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, R.string.err_msg_not_a_valid_eap_config_file, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } else if (intent?.hasExtra(NotificationRepository.KEY_EXTRA_PAYLOAD) == true) {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Route.SelectProfile>(NotificationRepository.KEY_EXTRA_PAYLOAD)?.let { payload ->
                    backStack.add(payload)
                }
            } else if (intent?.dataString != null) {
                Timber.w("Unexpected deeplink: ${intent?.dataString ?: "null"}")
            }
        }
    }

    private suspend fun openFileUri(fileUri: Uri): Boolean {
        Route.ConfigureWifi.buildDeepLink(this@MainActivity, fileUri)?.let { entry ->
            return withContext(Dispatchers.Main) {
                backStack.add(entry)
                return@withContext true
            }
        }
        return false
    }
}