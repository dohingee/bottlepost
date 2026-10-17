package com.bottlepost.app.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bottlepost.app.BottlepostApp
import java.util.concurrent.TimeUnit

/** Daily automatic backup for Google-linked users who already granted Drive access once. */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val c = (applicationContext as BottlepostApp).container
        if (!c.auth.isGoogleLinked) return Result.success()
        val access = runCatching { c.backup.requestAccess() }.getOrElse { return Result.retry() }
        // Without a prior grant there is no silent token; the user re-enables it from Settings.
        if (access !is DriveAccess.Granted) return Result.success()
        return runCatching {
            c.profiles.markBackedUp(c.backup.backup(access.accessToken))
        }.fold({ Result.success() }, { Result.retry() })
    }

    companion object {
        private const val NAME = "daily-backup"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
