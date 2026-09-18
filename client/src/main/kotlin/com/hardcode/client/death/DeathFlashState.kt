package com.hardcode.client.death

/** Tracks the death red-flash's fade-out, timed by wall clock rather than client ticks. */
object DeathFlashState {
    private const val DURATION_MILLIS = 1500L

    @Volatile
    private var triggeredAtMillis: Long = 0L

    fun trigger() {
        triggeredAtMillis = System.currentTimeMillis()
    }

    /** 1f right when triggered, fading linearly to 0f over [DURATION_MILLIS]. */
    fun currentAlpha(): Float {
        val elapsed = System.currentTimeMillis() - triggeredAtMillis
        if (elapsed < 0 || elapsed > DURATION_MILLIS) return 0f
        return 1f - (elapsed.toFloat() / DURATION_MILLIS.toFloat())
    }
}
