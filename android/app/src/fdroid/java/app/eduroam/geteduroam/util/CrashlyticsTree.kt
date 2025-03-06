package app.eduroam.geteduroam.util

import timber.log.Timber

class CrashlyticsTree: Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        throw RuntimeException("CrashlyticsTree should only be called in the Google Play Store flavor!")
    }
}