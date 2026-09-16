package dev.still.dns

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import java.util.concurrent.TimeUnit

class FilterUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = AppSettings.load(applicationContext)
        val selected = DownloadableFilter.entries.filter { it.name in settings.enabledSubscriptions }
        if (!settings.autoUpdateFilters || selected.isEmpty()) return Result.success()
        val repository = FilterLibrary.get(applicationContext)
        repository.refresh(selected.map { it.name }.toSet())
        val entries = repository.state.value.entries
        if (selected.any { entries[it]?.list == null || entries[it]?.error != null }) return Result.retry()
        if (settings.notifyFilterUpdates) notifyUpdated()
        return Result.success()
    }

    private fun notifyUpdated() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("filter-updates", "Filter updates", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(applicationContext, 2, Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        manager.notify(2, NotificationCompat.Builder(applicationContext, "filter-updates")
            .setSmallIcon(R.drawable.ic_shield).setContentTitle("Filter lists updated")
            .setContentText("Your enabled DNS lists are ready.").setContentIntent(open).setAutoCancel(true).build())
    }

    companion object {
        fun schedule(context: Context, settings: AppSettings) {
            val manager = WorkManager.getInstance(context)
            if (!settings.autoUpdateFilters || settings.enabledSubscriptions.isEmpty()) {
                manager.cancelUniqueWork("daily-filter-update")
                return
            }
            val request = PeriodicWorkRequestBuilder<FilterUpdateWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
            manager.enqueueUniquePeriodicWork("daily-filter-update", ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
