package com.example.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.CallTrackApplication

class CrmSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val tag = "CrmSyncWorker"

    override suspend fun doWork(): Result {
        Log.d(tag, "WorkManager periodic sync triggered.")
        val app = applicationContext as? CallTrackApplication ?: return Result.failure()
        val repository = app.callEventRepository

        return try {
            val syncedCount = repository.syncUnsyncedEvents()
            Log.d(tag, "WorkManager successfully synced $syncedCount pending events.")
            Result.success()
        } catch (e: Exception) {
            Log.e(tag, "Error during offline event recovery sync: ${e.message}", e)
            Result.retry()
        }
    }
}
