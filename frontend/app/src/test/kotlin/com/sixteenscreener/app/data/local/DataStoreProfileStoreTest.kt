package com.sixteenscreener.app.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.sixteenscreener.app.domain.model.ActiveProfile
import com.sixteenscreener.app.domain.model.ProfileId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreProfileStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val profile = ActiveProfile(ProfileId("3f2b8c1e-6d4a-4f7e-9b1a-2c5d8e7f9a10"), "Viktor")

    private fun TestScope.store() = DataStoreProfileStore(
        PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { folder.root.resolve("test.preferences_pb") }),
    )

    @Test
    fun `no profile is stored on first start`() = runTest {
        assertNull(store().profile.first())
    }

    @Test
    fun `saved profile is read back`() = runTest {
        val store = store()

        store.save(profile)

        assertEquals(profile, store.profile.first())
    }

    @Test
    fun `saving replaces the stored profile`() = runTest {
        val store = store()
        val other = ActiveProfile(ProfileId("other"), "Anna")

        store.save(profile)
        store.save(other)

        assertEquals(other, store.profile.first())
    }

    @Test
    fun `clear removes the stored profile with the same ID`() = runTest {
        val store = store()
        store.save(profile)

        store.clear(profile.id)

        assertNull(store.profile.first())
    }

    @Test
    fun `clear keeps a profile stored under another ID`() = runTest {
        val store = store()
        store.save(profile)

        store.clear(ProfileId("stale"))

        assertEquals(profile, store.profile.first())
    }
}
