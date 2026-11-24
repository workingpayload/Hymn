package com.example.musicsm.ui.support

import androidx.lifecycle.ViewModel
import com.example.musicsm.data.prefs.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import kotlin.random.Random

/** When the support popup shows by itself: every two days, a random moment into a visit. */
internal object SupportPromptSchedule {
    const val INTERVAL_MS = 2L * 24 * 60 * 60 * 1000
    const val MIN_DELAY_MS = 10_000L
    const val MAX_DELAY_MS = 90_000L

    fun isDue(lastShownMs: Long, nowMs: Long): Boolean = nowMs - lastShownMs >= INTERVAL_MS

    /** How long into a visit the popup waits, so it never lands the instant the app opens. */
    fun delayMs(random: Random): Long = random.nextLong(MIN_DELAY_MS, MAX_DELAY_MS + 1)
}

@HiltViewModel
class SupportPromptViewModel @Inject constructor(
    private val preferences: AppPreferences,
) : ViewModel() {

    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    /**
     * Call each time the app comes to the foreground, and cancel when it leaves: a wait cut short
     * by leaving is simply tried again, with a new random delay, on the next visit.
     *
     * The first call only starts the clock, so nobody is asked on their first day.
     */
    suspend fun onForeground() {
        val now = System.currentTimeMillis()
        val last = preferences.supportPromptShownAt
        // Never started, or the device clock went back past the last showing: restart the clock.
        if (last <= 0L || last > now) {
            preferences.supportPromptShownAt = now
            return
        }
        if (!SupportPromptSchedule.isDue(last, now)) return
        delay(SupportPromptSchedule.delayMs(Random.Default))
        preferences.supportPromptShownAt = System.currentTimeMillis()
        _visible.value = true
    }

    fun dismiss() {
        _visible.value = false
    }
}
