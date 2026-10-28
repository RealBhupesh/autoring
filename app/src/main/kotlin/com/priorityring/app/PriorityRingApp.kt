package com.priorityring.app

import android.app.Application
import com.priorityring.app.runtime.ForegroundCoordinator
import com.priorityring.blocks.android.NotificationChannels
import com.priorityring.engine.alert.AlertController
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * The process-wide Application object. @HiltAndroidApp makes Hilt generate the dependency graph
 * that every Activity, Service and ViewModel draws from.
 *
 * onCreate runs every time Android (re)starts our process — e.g. to bind the notification
 * listener after a reboot or after vivo killed us — so this is where background machinery starts.
 */
@HiltAndroidApp
class PriorityRingApp : Application() {

    @Inject lateinit var coordinator: ForegroundCoordinator

    // Injected only to create it early: it restores a persisted alarm after process death.
    @Inject lateinit var alerts: AlertController

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureCreated(this)
        coordinator.start()
    }
}
