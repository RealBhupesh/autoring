package com.priorityring.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * The process-wide Application object. @HiltAndroidApp makes Hilt generate the dependency graph
 * that every Activity, Service and ViewModel draws from.
 */
@HiltAndroidApp
class PriorityRingApp : Application()
