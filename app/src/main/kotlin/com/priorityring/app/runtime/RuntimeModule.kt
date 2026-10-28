package com.priorityring.app.runtime

import com.priorityring.app.alert.AndroidAlertController
import com.priorityring.engine.alert.AlertController
import com.priorityring.engine.block.BlockRegistry
import com.priorityring.engine.run.MacroEngine
import com.priorityring.engine.variables.BuiltinVariables
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * A coroutine scope that lives as long as the app process. Used for work that must outlive any
 * screen (macro runs, alerts). SupervisorJob: one failing child never cancels the others.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
abstract class RuntimeModule {

    @Binds
    abstract fun alertController(impl: AndroidAlertController): AlertController

    companion object {
        @Provides
        @Singleton
        @ApplicationScope
        fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        @Provides
        @Singleton
        fun engine(
            registry: BlockRegistry,
            @ApplicationScope scope: CoroutineScope,
            history: RunHistory,
            builtins: BuiltinVariables,
        ): MacroEngine = MacroEngine(registry, scope, history, builtins = builtins)
    }
}
