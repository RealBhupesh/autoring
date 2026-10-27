package com.priorityring.blocks.di

import com.priorityring.blocks.BlockCatalog
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

    /**
     * Android blocks join the catalogue with Hilt "multibinding": each one adds itself to a
     * Set<Block> with `@Binds @IntoSet`. Declaring the set here keeps it valid while empty (M1).
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
