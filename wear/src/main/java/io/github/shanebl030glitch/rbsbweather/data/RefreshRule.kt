// When the watch asks the phone for fresh weather (plan section 6, Stage 3 decision D4): when the
// app opens with no data or data more than 45 minutes old, and never more than once every 10
// minutes. Plain Kotlin, unit tested. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.data

object RefreshRule {
    const val MIN_AGE_MS = 45 * 60_000L
    const val MIN_INTERVAL_MS = 10 * 60_000L

    /**
     * dataAgeMs: age of the cached snapshot (from fetchedAt), or null when there is none.
     * lastRequestMs: when the last request was sent, or null. A clock moved back (last request
     * "in the future") counts as long ago, so a changed clock can't block requests for good.
     */
    fun shouldRequest(dataAgeMs: Long?, lastRequestMs: Long?, nowMs: Long): Boolean {
        if (dataAgeMs != null && dataAgeMs <= MIN_AGE_MS) return false
        if (lastRequestMs == null || lastRequestMs > nowMs) return true
        return nowMs - lastRequestMs >= MIN_INTERVAL_MS
    }
}
