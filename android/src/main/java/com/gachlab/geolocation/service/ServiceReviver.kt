// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.service

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.gachlab.geolocation.persistence.ConfigDAO
import java.util.concurrent.TimeUnit

/**
 * The only watchdog that does not live inside the process it watches.
 *
 * Every other recovery path was owned by the service itself, which is a problem
 * of definition: START_STICKY covers a memory kill and nothing else, the
 * BootReceiver covers a reboot and only with `startOnBoot`, `onTaskRemoved`
 * covers nothing at all, and the in-service watchdog cannot fire once the service
 * is gone. A force-stop — what an OEM battery manager does — disables the
 * package's alarms and receivers, including `BOOT_COMPLETED`, until the user
 * opens the app.
 *
 * That is the shape of a 611-minute blackout measured in production: the service
 * died and stayed dead until a human opened the app the next morning, with
 * `stopOnTerminate: false` and `startOnBoot: true` both set. Neither of them can
 * help, because both are instructions to a process that no longer exists.
 *
 * WorkManager persists its queue in its own database and is rescheduled by the
 * system, so this survives the death of the service and, on most OEMs, of the
 * process. It cannot survive a force-stop either — nothing in the app can, by
 * design — but it closes every case short of that, which is the majority.
 *
 * Deliberately cheap: fifteen minutes is WorkManager's floor for periodic work,
 * and the check is one null test against a static. It is a net, not a heartbeat.
 */
internal class ServiceReviver(appContext: Context, params: WorkerParameters) :
    Worker(appContext, params) {

    override fun doWork(): Result {
        try {
            // `shouldBeRunning` is the state that decides, NOT `startOnBoot`.
            // Those are different questions and conflating them is its own bug:
            // `startOnBoot` is a preference about reboots, while this asks whether
            // tracking was supposed to be on when we last looked. Reviving on the
            // preference alone would restart tracking for a driver who ended their
            // shift — location and battery spent on someone off duty.
            if (!wasSupposedToRun(applicationContext)) return Result.success()
            if (LocationService.instance != null) return Result.success()

            // The other door into `startForeground` (#59). Without this the net
            // is what keeps the crash loop alive: the service refuses to start
            // without the location permission, `shouldBeRunning` stays true
            // because the shift IS supposed to be running, and this worker would
            // ask the platform to start it again every fifteen minutes — each
            // attempt a fresh SecurityException in the driver's face.
            //
            // Returning success rather than retry, and leaving `shouldBeRunning`
            // alone: nothing here failed. Tracking is impossible right now, and
            // the moment the permission comes back this same net resumes the
            // shift with no further bookkeeping.
            if (!ForegroundLocationGate.canStartLocationService(applicationContext)) {
                Log.w(TAG, "not reviving: the app has no location permission")
                return Result.success()
            }

            Log.w(TAG, "service is not running but should be — reviving")
            val intent = Intent(applicationContext, LocationService::class.java)
                .putExtra(LocationService.EXTRA_START_REASON, REASON_REVIVED)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(intent)
            } else {
                applicationContext.startService(intent)
            }
        } catch (e: Exception) {
            // Never fail the work: a failed periodic worker can be dropped from
            // the schedule, and the one thing this must not do is stop watching.
            Log.e(TAG, "revive attempt failed: ${e.message}")
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "BGServiceReviver"
        const val WORK_NAME = "gachlab.bgloc.reviver"
        const val REASON_REVIVED = "revived"

        private const val PREFS = "bgloc_diagnostics"
        private const val KEY_SHOULD_RUN = "should_be_running"

        /**
         * Remember whether tracking is supposed to be on.
         *
         * This is the flag `startOnBoot` was standing in for and should never
         * have been: a reboot after the driver stopped tracking used to start
         * reporting the location of someone off duty, and a reboot during an
         * active shift with `startOnBoot: false` silently lost it. The correct
         * question is "was the service supposed to be running", and until now
         * nothing anywhere recorded the answer.
         */
        fun setShouldBeRunning(context: Context, value: Boolean) {
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_SHOULD_RUN, value).apply()
        }

        fun wasSupposedToRun(context: Context): Boolean =
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_SHOULD_RUN, false)

        /**
         * Arm the net. Idempotent — KEEP leaves an existing schedule alone.
         *
         * Never throws. `WorkManager.getInstance` raises when the host app has
         * disabled its initializer and not initialised it by hand, and a safety
         * net that prevents tracking from starting is worse than no net at all —
         * it would turn a recoverable outage into a guaranteed one.
         */
        fun schedule(context: Context) {
            try {
                val work = PeriodicWorkRequestBuilder<ServiceReviver>(15, TimeUnit.MINUTES)
                    .addTag(WORK_NAME)
                    .build()
                WorkManager.getInstance(context.applicationContext)
                    .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, work)
            } catch (e: Throwable) {
                Log.w(TAG, "could not arm the reviver: ${e.message}")
            }
        }

        /** Stand down. Called when tracking stops on purpose, so the net does not
         *  resurrect a shift the driver ended. Never throws, same reason. */
        fun cancel(context: Context) {
            try {
                WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
            } catch (e: Throwable) {
                Log.w(TAG, "could not stand the reviver down: ${e.message}")
            }
        }
    }
}
