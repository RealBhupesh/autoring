package com.priorityring.blocks.di

import com.priorityring.blocks.BlockCatalog
import com.priorityring.blocks.action.FlashTorchAction
import com.priorityring.blocks.action.RingAlarmAction
import com.priorityring.blocks.action.ShowNotificationAction
import com.priorityring.blocks.action.VibrateAction
import com.priorityring.blocks.android.DeviceVariables
import com.priorityring.engine.variables.BuiltinVariables
import dagger.multibindings.IntoSet
import com.priorityring.blocks.android.AndroidDeviceCapabilities
import com.priorityring.engine.block.Block
import com.priorityring.engine.block.BlockRegistry
import com.priorityring.engine.block.DeviceCapabilities
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BlocksModule {

    @Binds
    abstract fun capabilities(impl: AndroidDeviceCapabilities): DeviceCapabilities

    // ---- Android blocks: one line each. This list is the block registration point. ----
    @Binds @IntoSet abstract fun ringAlarm(b: RingAlarmAction): Block
    @Binds @IntoSet abstract fun vibrate(b: VibrateAction): Block
    @Binds @IntoSet abstract fun flashTorch(b: FlashTorchAction): Block
    @Binds @IntoSet abstract fun showNotification(b: ShowNotificationAction): Block

    @Binds
    abstract fun builtins(impl: DeviceVariables): BuiltinVariables

    /**
     * Android blocks join the catalogue with Hilt "multibinding": each one adds itself to a
     * Set<Block> with `@Binds @IntoSet`. Declaring the set here keeps it valid even if empty.
     */
    @Multibinds
    abstract fun androidBlocks(): Set<Block>

    companion object {
        @Provides
        @Singleton
        fun registry(androidBlocks: Set<@JvmSuppressWildcards Block>): BlockRegistry =
            BlockCatalog.build(androidBlocks.sortedBy { it.meta.id })
    }
}
