package app.eduroam.geteduroam.di.repository

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.content.PackageManagerCompat
import androidx.core.content.UnusedAppRestrictionsConstants
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import app.eduroam.geteduroam.R
import app.eduroam.geteduroam.config.model.EAPIdentityProvider
import app.eduroam.geteduroam.config.requiresUsernamePrompt
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.Date
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit


class NotificationRepository(
    @ApplicationContext private val context: Context
){

    companion object {
        const val NOTIFICATION_KEY_PROVIDER_ID = "provider_id"
        const val NOTIFICATION_CHANNEL_ID = "reconfiguration_reminders"
        const val REMIND_DAYS_BEFORE_EXPIRY = 5
        const val NOTIFICATION_ID = 100
        const val KEY_EXTRA_PAYLOAD = "extra_payload"
        const val WORK_TAG = "notification_reminder"
    }

    fun shouldRequestPushPermission(provider: EAPIdentityProvider, organizationId: String): Boolean {
        if (organizationId.isEmpty()) {
            return false
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            // We already have the permission
            return false
        }
        // Check if we actually need it
        return getReminderDate(provider) != null
    }

    private fun getReminderDate(provider: EAPIdentityProvider): Date? {
        if (provider.requiresUsernamePrompt()) {
            // Only OAuth users require notification reminders
            return null
        }
        val validUntil = provider.validUntil ?: return null
        val now = Date()
        val expiryReminderAtMs = validUntil.time - REMIND_DAYS_BEFORE_EXPIRY * 24 * 60 * 60 * 1000
        if (expiryReminderAtMs > now.time) {
            return Date(expiryReminderAtMs)
        }
        return null
    }
    fun scheduleNotificationIfNeeded(provider: EAPIdentityProvider, organizationId: String) {
        if (organizationId.isEmpty()) {
            return
        }
        val reminderDate = getReminderDate(provider) ?: return
        createNotificationChannel()
        Timber.i("Posting reminder to date: $reminderDate")
        postNotificationAtDate(reminderDate, organizationId)
    }

    /**
     * Returns true if the user should be asked to disable app hibernation restrictions.
     *
     * App hibernation force-stops the app after months of inactivity, cancelling all scheduled
     * WorkManager jobs and preventing notifications from being delivered. Because this app is
     * typically only opened once for initial setup and then again at certificate renewal time,
     * it is a prime candidate for hibernation — which is exactly when the reminder notification
     * must still fire.
     *
     * Returns false when:
     * - There is no reminder to schedule (no valid expiry date, or the profile doesn't need reminders)
     * - The feature is not available on this device (e.g. Android < 6, or no Play Services on F-Droid builds)
     * - The app is already exempt
     */
    fun shouldRequestHibernationExemption(provider: EAPIdentityProvider, organizationId: String): Boolean {
        if (getReminderDate(provider) == null || organizationId.isEmpty()) {
            Timber.tag("Hibernate").i( "No expiration date or organization ID known, dont check hibernation")
            return false
        }
        return try {
            val future: ListenableFuture<Int> =
                PackageManagerCompat.getUnusedAppRestrictionsStatus(context)
            when (future.get()) {
                UnusedAppRestrictionsConstants.FEATURE_NOT_AVAILABLE -> false
                UnusedAppRestrictionsConstants.DISABLED -> false
                UnusedAppRestrictionsConstants.API_30_BACKPORT,
                UnusedAppRestrictionsConstants.API_30,
                UnusedAppRestrictionsConstants.API_31 -> true
                else -> false
            }
        } catch (e: ExecutionException) {
            Timber.tag("Hibernate").w(e, "Failed to check unused app restrictions status")
            false
        } catch (e: InterruptedException) {
            Timber.tag("Hibernate").w(e, "Interrupted while checking unused app restrictions status")
            false
        }
    }

    private fun postNotificationAtDate(reminderDate: Date, organizationId: String) {
        val delayMillis = reminderDate.time - System.currentTimeMillis()
        if (delayMillis <= 0) {
            Timber.w("Reminder date is in the past, not scheduling notification")
            return
        }

        val inputData = Data.Builder()
            .putString(NOTIFICATION_KEY_PROVIDER_ID, organizationId)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<NotificationWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(inputData)
            .addTag(WORK_TAG)
            .build()

        // Use unique work to replace any previously scheduled notification
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_TAG,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    private fun createNotificationChannel() {
        val name = context.getString(R.string.notification_channel_name)
        val description = context.getString(R.string.notification_channel_description)
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance)
        channel.description = description
        val notificationManager: NotificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
}