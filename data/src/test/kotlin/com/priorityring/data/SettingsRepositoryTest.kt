package com.priorityring.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.priorityring.data.settings.AppSettings
import com.priorityring.data.settings.SettingsRepository
import com.priorityring.data.settings.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** DataStore is plain JVM code, so this runs as a fast local test with a temp file. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun repo(scope: TestScope) = SettingsRepository(
        PreferenceDataStoreFactory.create(scope = scope.backgroundScope) {
            tmp.newFile("test.preferences_pb").also { it.delete() }
        },
    )

    @Test
    fun `defaults are privacy-friendly`() = runTest(UnconfinedTestDispatcher()) {
        val s = repo(this).settings.first()
        assertEquals(AppSettings(), s)
        assertEquals(false, s.storeNotificationText)
        assertEquals(7, s.retentionDays)
        assertEquals(ThemeMode.DARK, s.themeMode)
    }

    @Test
    fun `values persist`() = runTest(UnconfinedTestDispatcher()) {
        val r = repo(this)
        r.setMasterEnabled(false)
        r.setRetentionDays(30)
        r.setThemeMode(ThemeMode.LIGHT)
        val s = r.settings.first()
        assertEquals(false, s.masterEnabled)
        assertEquals(30, s.retentionDays)
        assertEquals(ThemeMode.LIGHT, s.themeMode)
    }

    @Test
    fun `invalid retention is rejected`() = runTest(UnconfinedTestDispatcher()) {
        val r = repo(this)
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { r.setRetentionDays(3) } }
    }
}
