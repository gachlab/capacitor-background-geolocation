// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.network

import android.content.Context
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.gachlab.geolocation.persistence.ConfigDAO

/**
 * WorkManager worker that replaces the JsEvaluator-based headless task.
 *
 * When the host app is killed by an OEM process killer, WorkManager (backed by
 * JobScheduler) can still run. Each wake-up triggers a forced BackgroundSync so
 * that locations stored in the SQLite DB are eventually delivered to the server
 * even if the foreground service never restarts.
 *
 * Scheduled as a PeriodicWork via [BackgroundGeolocationPlugin.registerHeadlessTask].
 */
internal class HeadlessWorker(ctx: Context, params: WorkerParameters) :
    Worker(ctx, params) {

    override fun doWork(): Result {
        val config = ConfigDAO(applicationContext).retrieveConfig()
        if (config?.syncEnabled == false || config?.syncUrl.isNullOrEmpty()) {
            Log.i(TAG, "Sync disabled or no syncUrl — skipping headless run")
            return Result.success()
        }

        val syncReq = OneTimeWorkRequestBuilder<BackgroundSync>()
            .setInputData(workDataOf(BackgroundSync.KEY_FORCED to true))
            .addTag(BackgroundSync.WORK_TAG)
            .build()
        WorkManager.getInstance(applicationContext).enqueue(syncReq)
        Log.i(TAG, "Headless wake-up: enqueued forced BackgroundSync")
        return Result.success()
    }

    companion object {
        private const val TAG = "HeadlessWorker"
        const val WORK_TAG = "gachlab_headless"
        /** Minimum periodic interval. WorkManager enforces ≥15 min regardless. */
        const val DEFAULT_INTERVAL_MS = 15 * 60 * 1000L
    }
}
