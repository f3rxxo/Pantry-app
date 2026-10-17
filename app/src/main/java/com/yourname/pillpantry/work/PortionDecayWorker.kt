package com.yourname.pillpantry.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import com.yourname.pillpantry.data.PillPantryRepository
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Best-effort daily job that applies the portion decrement in the
 * background, so counts are current even on days the user doesn't open the
 * app. This is a *supplement*, not the source of truth — Android's Doze
 * mode and battery optimization mean WorkManager periodic jobs can be
 * delayed by hours (occasionally longer), so there's no guarantee this
 * fires at exactly 3am. [PillPantryRepository.applyMissedPortionDecrements]
 * is idempotent and catches up any days this job missed the next time the
 * app is opened, which is what actually guarantees correctness.
 */
class PortionDecayWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            PillPantryRepository.from(applicationContext)
                .applyMissedPortionDecrements(context = applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "portion_decay_daily"
        private const val CUTOVER_HOUR = 3 // must match PORTION_CUTOVER_HOUR in PillPantryRepository.kt

        /** Schedules (or re-uses) a daily job targeting ~3am America/Chicago. */
        fun schedule(context: Context) {
            val zone = ZoneId.of("America/Chicago")
            val now = ZonedDateTime.now(zone)
            var nextRun = now.withHour(CUTOVER_HOUR).withMinute(0).withSecond(0).withNano(0)
            if (!nextRun.isAfter(now)) nextRun = nextRun.plusDays(1)
            val initialDelay = Duration.between(now, nextRun)

            val request = PeriodicWorkRequestBuilder<PortionDecayWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(initialDelay.toMinutes(), TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
