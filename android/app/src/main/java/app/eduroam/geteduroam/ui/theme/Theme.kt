package app.eduroam.geteduroam.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import app.eduroam.geteduroam.BuildConfig

@Suppress("KotlinConstantConditions")
const val IS_EDUROAM = BuildConfig.FLAVOR_brand == "eduroam"
@Suppress("KotlinConstantConditions")
const val IS_GOVROAM = BuildConfig.FLAVOR_brand == "govroam"

fun Context.isChromeOs() : Boolean {
    return packageManager.hasSystemFeature("org.chromium.arc.device_management")
}


private val EduroamLightColorScheme = lightColorScheme(
    primary = eduroamLightThemePrimary,
    surface = eduroamLightThemeSurface,
    onSurface = eduroamLightThemeOnSurface,
    secondary = eduroamLightThemeSecondary,
    primaryContainer = eduroamLightThemePrimaryContainer
)
private val EduroamDarkColorScheme = darkColorScheme(
    primary = eduroamDarkThemePrimary,
    surface = eduroamDarkThemeSurface,
    onSurface = eduroamDarkThemeOnSurface,
    secondary = eduroamDarkThemeSecondary,
    primaryContainer = eduroamDarkThemePrimaryContainer
)

private val GovroamLightColorScheme = lightColorScheme(
    primary = govroamLightThemePrimary,
    surface = govroamLightThemeSurface,
    onSurface = govroamLightThemeOnSurface,
    secondary = govroamLightThemeSecondary,
    primaryContainer = govroamLightThemePrimaryContainer
)
private val GovroamDarkColorScheme = darkColorScheme(
    primary = govroamDarkThemePrimary,
    surface = govroamDarkThemeSurface,
    onSurface = govroamDarkThemeOnSurface,
    secondary = govroamDarkThemeSecondary,
    primaryContainer = govroamDarkThemePrimaryContainer
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        IS_EDUROAM && darkTheme -> EduroamDarkColorScheme
        IS_EDUROAM && !darkTheme -> EduroamLightColorScheme
        IS_GOVROAM && darkTheme -> GovroamDarkColorScheme
        IS_GOVROAM && !darkTheme -> GovroamLightColorScheme
        else -> throw RuntimeException("Unexpected flavor / color scheme!")
    }
    val view = LocalView.current
    val context = LocalContext.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as Activity).window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(
                context.findActivity().window, view
            ).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme, typography = AppTypography, content = content
    )
}

tailrec fun Context.findActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> this.baseContext.findActivity()
    else -> throw IllegalArgumentException("Could not find activity!")
}
