package com.hiddify.hiddify.nativeupdate

import android.app.DownloadManager
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.*

class NativeUpdateJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<Int, Job>()
    override fun onStartJob(params: JobParameters): Boolean {
        NativeAppUpdates.initialize(applicationContext)
        jobs[params.jobId] = scope.launch {
            var retry = false
            var stopped = false
            try {
                NativeAppUpdates.syncDownload()
                if (params.jobId == CHECK_JOB) NativeAppUpdates.checkForUpdates(false)
            } catch (cancelled: CancellationException) { stopped = true; throw cancelled }
              catch (_: Exception) { retry = true }
            finally { withContext(NonCancellable + Dispatchers.Main) {
                jobs.remove(params.jobId)
                if (!stopped) jobFinished(params, retry)
            } }
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { jobs.remove(params.jobId)?.cancel(); return true }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    companion object {
        private const val CHECK_JOB = 4702
        private const val COMPLETE_JOB = 4703
        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if (scheduler.getPendingJob(CHECK_JOB) == null) scheduler.schedule(JobInfo.Builder(CHECK_JOB,
                ComponentName(context, NativeUpdateJob::class.java)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(NativeUpdatePolicy.CHECK_INTERVAL_MS).setPersisted(true).build())
        }
        fun downloaded(context: Context) {
            context.getSystemService(JobScheduler::class.java).schedule(JobInfo.Builder(COMPLETE_JOB,
                ComponentName(context, NativeUpdateJob::class.java)).setPersisted(true).build())
        }
    }
}

class NativeUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NativeAppUpdates.initialize(context)
        when (intent.action) {
            DownloadManager.ACTION_DOWNLOAD_COMPLETE -> {
                val id = context.getSharedPreferences("app_updates", Context.MODE_PRIVATE).getLong("download_id", -1)
                if (id >= 0 && id == intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -2)) NativeUpdateJob.downloaded(context)
            }
            NativeAppUpdates.SKIP_UPDATE -> {
                val result = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try { NativeAppUpdates.skipVersionNow(intent.getStringExtra("identity")) }
                    catch (error: Exception) { android.util.Log.w("AppUpdates", "Could not skip update", error) }
                    finally { result.finish() }
                }
            }
        }
    }
}
