package com.priorityring.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.priorityring.data.db.MacroDao
import com.priorityring.data.db.PriorityRingDatabase
import com.priorityring.data.db.RunDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt "recipes" for things that can't just have an @Inject constructor (they're built by
 * library factories). Hilt calls these once and shares the result app-wide (@Singleton).
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): PriorityRingDatabase =
        Room.databaseBuilder(context, PriorityRingDatabase::class.java, PriorityRingDatabase.NAME)
            .build()

    @Provides
    fun macroDao(db: PriorityRingDatabase): MacroDao = db.macroDao()

    @Provides
    fun runDao(db: PriorityRingDatabase): RunDao = db.runDao()

    @Provides
    @Singleton
    fun preferences(@ApplicationContext context: Context): DataStore<Preferences> =
        // Exactly one DataStore instance per file is allowed per process, hence @Singleton.
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }
}
