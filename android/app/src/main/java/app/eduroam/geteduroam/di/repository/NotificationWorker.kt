package app.eduroam.geteduroam.di.repository

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import app.eduroam.geteduroam.MainActivity
import app.eduroam.geteduroam.R
import app.eduroam.geteduroam.Route
import timber.log.Timber

class NotificationWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {
        createNotificationChannel()

        val organizationId = inputData.getString(NotificationRepository.NOTIFICATION_KEY_PROVIDER_ID)

        val tapResultIntent = Intent(context, MainActivity::class.java)
        organizationId?.let {
            tapResultIntent.putExtra(
                NotificationRepository.KEY_EXTRA_PAYLOAD,
                Route.SelectProfile(institutionId = it, customHostUri = null)
            )
        }
        tapResultIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP

        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            0,
            tapResultIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NotificationRepository.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notification_title, context.getString(R.string.name)))
            .setContentText(
                context.getString(
                    R.string.notification_message,
                    NotificationRepository.REMIND_DAYS_BEFORE_EXPIRY,
                    context.getString(R.string.name)
                )
            )
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = NotificationManagerCompat.from(context)
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify(NotificationRepository.NOTIFICATION_ID, notification)
        } else {
            Timber.w("Could not post notification because there was no permission!")
        }

        return Result.success()
    }

    private fun createNotificationChannel() {
        val name = context.getString(R.string.notification_channel_name)
        val description = context.getString(R.string.notification_channel_description)
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(NotificationRepository.NOTIFICATION_CHANNEL_ID, name, importance)
        channel.description = description
        val notificationManager: NotificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
}
