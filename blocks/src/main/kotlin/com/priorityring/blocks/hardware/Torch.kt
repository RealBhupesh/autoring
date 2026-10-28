package com.priorityring.blocks.hardware

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.core.content.getSystemService
import com.priorityring.engine.alert.TorchPattern
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Flashlight control via CameraManager.setTorchMode — no CAMERA permission needed.
 *
 * Android facts worth knowing (from the CameraManager docs):
 * - the torch is shared: the camera app or another app can take it or switch it off;
 * - it turns off if the app that switched it on dies, so it only works while our process lives
 *   (the foreground service keeps it alive while an alert is active).
 */
@Singleton
class Torch @Inject constructor(@ApplicationContext context: Context) {
    private val cameraManager = context.getSystemService<CameraManager>()

    private val cameraId: String? by lazy {
        try {
            cameraManager?.cameraIdList?.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (_: Exception) {
            null
        }
    }

    private var job: Job? = null

    val isAvailable: Boolean get() = cameraId != null

    /** Starts the pattern; returns false if the torch couldn't be switched on (e.g. camera in use). */
    @Synchronized
    fun start(scope: CoroutineScope, pattern: TorchPattern): Boolean {
        job?.cancel()
        if (!set(true)) return false
        job = scope.launch {
            try {
                if (pattern == TorchPattern.STEADY) awaitCancellation()
                var on = true
                while (isActive) {
                    delay(if (on) pattern.onMs else pattern.offMs)
                    on = !on
                    set(on)
                }
            } finally {
                set(false)
            }
        }
        return true
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        set(false)
    }

    private fun set(on: Boolean): Boolean {
        val id = cameraId ?: return false
        return try {
            cameraManager?.setTorchMode(id, on)
            true
        } catch (_: CameraAccessException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }
}
