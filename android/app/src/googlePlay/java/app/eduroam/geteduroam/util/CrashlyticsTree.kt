package app.eduroam.geteduroam.util

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import timber.log.Timber

/**
 * Log to Crashlytics for release builds.
 */
class CrashlyticsTree : Timber.Tree() {

    private val priorityStr = mapOf(
        Log.VERBOSE to "V",
        Log.DEBUG to "D",
        Log.INFO to "I",
        Log.WARN to "W",
        Log.ERROR to "E",
        Log.ASSERT to "A"
    )

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (t == null) {
            FirebaseCrashlytics.getInstance().log("${priorityStr[priority]}/$tag: $message")
        } else {
            FirebaseCrashlytics.getInstance().log("${priorityStr[priority]}/$tag: $message; $t")
            if (priority == Log.ERROR) {
                FirebaseCrashlytics.getInstance().recordException(t)
            }
        }
    }
}