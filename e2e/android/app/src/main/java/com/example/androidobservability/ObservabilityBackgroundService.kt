package com.example.androidobservability

import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class ObservabilityBackgroundService : Service() {

    private val serviceScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loggingJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        loggingJob?.cancel()
        loggingJob = serviceScope.launchObservabilityLoggingTask(serviceType = "background") {
            stopSelf(startId)
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        loggingJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
