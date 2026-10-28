package com.priorityring.blocks.android

import android.content.Context
import android.os.BatteryManager
import androidx.core.content.getSystemService
import com.priorityring.engine.variables.BuiltinVariables
import com.priorityring.engine.variables.ClockVariables
import com.priorityring.engine.variables.CompositeBuiltins
import com.priorityring.engine.variables.VariableSpec
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Built-in variables available in every macro: clock ones from the engine plus device ones. */
@Singleton
class DeviceVariables @Inject constructor(
    @ApplicationContext private val context: Context,
) : BuiltinVariables {
    private val clock = ClockVariables { java.time.ZonedDateTime.now() }
    private val device = BuiltinVariables { name ->
        when (name) {
            "battery" -> context.getSystemService<BatteryManager>()
                ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                ?.takeIf { it in 0..100 }?.toString()
            else -> null
        }
    }
    private val all = CompositeBuiltins(listOf(clock, device))

    override fun get(name: String): String? = all.get(name)

    companion object {
        val SPECS = ClockVariables.SPECS + VariableSpec("battery", "Battery level (%)", "76")
    }
}
