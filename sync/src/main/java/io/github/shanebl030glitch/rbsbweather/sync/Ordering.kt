// RBSB-Weather sync format v1: which snapshot is newer (approved Stage 1 decision 4).
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.sync

enum class Arrival {
    /** Replace the cached snapshot with this one. */
    NEWER,
    /** The same snapshot again: nothing to do. */
    DUPLICATE,
    /** Older than what the watch has: ignore it. */
    OLDER,
}

/**
 * The rule, in order:
 *  1. No cached snapshot: the arrival is NEWER.
 *  2. A later fetchedAt is NEWER; an earlier one is OLDER (newer weather always wins).
 *  3. Same fetchedAt, different sourceId: NEWER. The phone app was reinstalled (its sequence
 *     started again at 1) and is re-sending the same weather, possibly with updated warnings;
 *     the latest arrival from the new install wins.
 *  4. Same fetchedAt and sourceId: a higher sequence is NEWER (e.g. the warnings changed without a
 *     new forecast fetch), the same is DUPLICATE, a lower one is OLDER.
 *
 * Phone reinstalls are handled by rules 2 and 3: a reinstalled phone's first snapshot has a later
 * fetchedAt (so its restarted sequence never matters) or, if it re-sends cached weather, a new
 * sourceId. Snapshots dated too far in the future never get this far: decode() rejects them
 * (Limits.FUTURE_TOLERANCE_MS), so one bad phone clock cannot block later snapshots for hours.
 */
object Ordering {
    fun compare(incoming: Snapshot, cached: Snapshot?): Arrival {
        if (cached == null) return Arrival.NEWER
        if (incoming.fetchedAt != cached.fetchedAt)
            return if (incoming.fetchedAt > cached.fetchedAt) Arrival.NEWER else Arrival.OLDER
        if (incoming.sourceId != cached.sourceId) return Arrival.NEWER
        return when {
            incoming.sequence > cached.sequence -> Arrival.NEWER
            incoming.sequence == cached.sequence -> Arrival.DUPLICATE
            else -> Arrival.OLDER
        }
    }

    fun shouldReplace(incoming: Snapshot, cached: Snapshot?): Boolean = compare(incoming, cached) == Arrival.NEWER
}
