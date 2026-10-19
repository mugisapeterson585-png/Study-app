package com.silentstudy.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.silentstudy.SilentStudyApp
import com.silentstudy.ai.AnswerRequest
import kotlinx.coroutines.flow.first

class OfflineQueueWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "silentstudy_offline_queue_work"

        fun enqueueWork(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<OfflineQueueWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }

    override suspend fun doWork(): Result {
        val app = applicationContext as? SilentStudyApp ?: return Result.failure()
        val dao = app.database.answerDao()
        val pending = dao.getPendingOfflineAnswers()

        if (pending.isEmpty()) return Result.success()

        val aiSettings = app.preferencesRepository.aiSettings.first()

        for (ans in pending) {
            try {
                if (ans.imagePaths.isEmpty()) continue
                val result = app.aiProviderManager.generateAnswer(
                    provider = aiSettings.provider,
                    apiKey = aiSettings.apiKey,
                    model = aiSettings.model,
                    baseUrl = aiSettings.baseUrl,
                    request = AnswerRequest(
                        imagesBase64 = ans.imagePaths,
                        promptNote = null,
                        conciseMode = aiSettings.conciseMode,
                        lazyExplanation = aiSettings.lazyExplanation
                    )
                )

                val updated = ans.copy(
                    subject = result.subject,
                    questionText = result.questionText,
                    shortAnswer = result.shortAnswer,
                    blocks = result.blocks,
                    simpleExplanation = result.simpleExplanation,
                    status = "completed",
                    errorMessage = null
                )
                dao.updateAnswer(updated)

                sendNotification(updated.id, updated.subject)
            } catch (e: Exception) {
                dao.updateStatus(ans.id, "failed", e.message)
            }
        }

        return Result.success()
    }

    private fun sendNotification(id: Long, subject: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "offline_sync_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "SilentStudy Offline Sync",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Answer #$id Ready ($subject)")
            .setContentText("Processed automatically from offline queue.")
            .setAutoCancel(true)
            .build()

        manager.notify((1000 + id).toInt(), notification)
    }
}
