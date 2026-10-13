package com.example.musicsm.ui.rename

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HymnIntroTest {

    @Test
    fun showsAfterUpdatingFromMusicSm() {
        assertTrue(shouldShowHymnIntro(seen = false, firstInstallTime = 1_000L, lastUpdateTime = 5_000L))
    }

    @Test
    fun hiddenOnFreshInstall() {
        assertFalse(shouldShowHymnIntro(seen = false, firstInstallTime = 1_000L, lastUpdateTime = 1_000L))
    }

    @Test
    fun hiddenOnceSeen() {
        assertFalse(shouldShowHymnIntro(seen = true, firstInstallTime = 1_000L, lastUpdateTime = 5_000L))
    }
}
