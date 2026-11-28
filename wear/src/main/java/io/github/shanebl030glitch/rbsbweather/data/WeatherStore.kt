// The watch's cache of the phone's latest snapshot and settings (Stage 3), in app-private storage
// (excluded from backup). Every payload is untrusted: it is checked with the Stage 1 codecs and
// ordering rules before it can replace the cached copy, and a bad or older payload is dropped
// with the cached copy kept. Files are written whole and then swapped in, so a crash or power
// loss never leaves half a file; a damaged file is discarded on load, never crashed on.
// Plain Kotlin (java.io only), unit tested on the JVM. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.data

import io.github.shanebl030glitch.rbsbweather.sync.Arrival
import io.github.shanebl030glitch.rbsbweather.sync.Decoded
import io.github.shanebl030glitch.rbsbweather.sync.Limits
import io.github.shanebl030glitch.rbsbweather.sync.Ordering
import io.github.shanebl030glitch.rbsbweather.sync.PayloadGzip
import io.github.shanebl030glitch.rbsbweather.sync.SettingsCodec
import io.github.shanebl030glitch.rbsbweather.sync.Snapshot
import io.github.shanebl030glitch.rbsbweather.sync.SnapshotCodec
import io.github.shanebl030glitch.rbsbweather.sync.SyncSettings
import java.io.File
import java.io.FileOutputStream

/** What happened to an incoming payload (logged by reason, never with its content). */
sealed interface Outcome {
    /** Checked and saved; it is now the cached copy. */
    data object Saved : Outcome
    /** Valid, but not newer than the cached copy (older or a repeat); dropped. */
    data class Kept(val arrival: Arrival) : Outcome
    /** Failed the sync-format checks; dropped, the cached copy kept. */
    data class Rejected(val reason: String, val path: String) : Outcome
    /** Could not be written (storage full or failing); the cached copy kept. */
    data class WriteFailed(val error: String) : Outcome
}

class WeatherStore(private val dir: File) {
    private val snapshotFile get() = File(dir, SNAPSHOT_FILE)
    private val settingsFile get() = File(dir, SETTINGS_FILE)

    @Volatile private var snapshotCache: Snapshot? = null
    @Volatile private var settingsCache: SyncSettings? = null
    @Volatile private var loaded = false

    @Synchronized private fun ensureLoaded() {
        if (loaded) return
        snapshotCache = readSnapshot()
        settingsCache = readSettings()
        loaded = true
    }

    /** The cached snapshot, or null (none yet, or the file was damaged and discarded). */
    fun snapshot(): Snapshot? { ensureLoaded(); return snapshotCache }

    /** The cached settings, or null until the phone has sent them. */
    fun settings(): SyncSettings? { ensureLoaded(); return settingsCache }

    /** Checks a snapshot payload (gzip JSON bytes, as the phone sends it) and saves it if it is newer. */
    @Synchronized fun offerSnapshot(bytes: ByteArray, nowMs: Long): Outcome {
        ensureLoaded()
        val incoming = when (val d = SnapshotCodec.decode(bytes, nowMs)) {
            is Decoded.Ok -> d.value
            is Decoded.Rejected -> return Outcome.Rejected(d.reason.name, d.path)
        }
        val arrival = Ordering.compare(incoming, snapshotCache)
        if (arrival != Arrival.NEWER) return Outcome.Kept(arrival)
        writeAtomically(snapshotFile, bytes)?.let { return Outcome.WriteFailed(it) }
        snapshotCache = incoming
        return Outcome.Saved
    }

    /** Checks a settings payload and saves it unless it was sent before the cached settings. */
    @Synchronized fun offerSettings(bytes: ByteArray, nowMs: Long): Outcome {
        ensureLoaded()
        val incoming = when (val d = SettingsCodec.decode(bytes, nowMs)) {
            is Decoded.Ok -> d.value
            is Decoded.Rejected -> return Outcome.Rejected(d.reason.name, d.path)
        }
        val cached = settingsCache
        if (cached != null) {
            if (incoming == cached) return Outcome.Kept(Arrival.DUPLICATE)
            if (incoming.sentAt < cached.sentAt) return Outcome.Kept(Arrival.OLDER)
        }
        writeAtomically(settingsFile, bytes)?.let { return Outcome.WriteFailed(it) }
        settingsCache = incoming
        return Outcome.Saved
    }

    /** Deletes the cache (not used by the app; for tests and a future "forget data" option). */
    @Synchronized fun clear() {
        snapshotFile.delete(); settingsFile.delete()
        snapshotCache = null; settingsCache = null; loaded = true
    }

    private fun readBytes(f: File): ByteArray? = try {
        if (!f.isFile || f.length() == 0L || f.length() > Limits.MAX_COMPRESSED_BYTES) null else f.readBytes()
    } catch (e: Exception) { null }

    private fun readSnapshot(): Snapshot? {
        val f = snapshotFile
        if (!f.exists()) return null
        val bytes = readBytes(f)
        // Saved payloads already passed every check on arrival; the future-date check is skipped on
        // reload so a watch clock moved back can't discard good data. Anything unreadable is removed.
        val snap = bytes?.let { b -> textOf(b)?.let { text -> SnapshotCodec.parse(text, nowMs = null).valueOrNull() } }
        if (snap == null) discard(f)
        return snap
    }

    private fun readSettings(): SyncSettings? {
        val f = settingsFile
        if (!f.exists()) return null
        val bytes = readBytes(f)
        val s = bytes?.let { b -> textOf(b)?.let { text -> SettingsCodec.parse(text, nowMs = null).valueOrNull() } }
        if (s == null) discard(f)
        return s
    }

    private fun textOf(bytes: ByteArray): String? = PayloadGzip.decompress(bytes).valueOrNull()

    private fun <T> Decoded<T>.valueOrNull(): T? = when (this) {
        is Decoded.Ok -> value
        is Decoded.Rejected -> null
    }

    private fun discard(f: File) { try { f.delete() } catch (e: Exception) { } }

    /** Writes [bytes] to a temporary file, syncs it to storage, then renames it over [target]. Returns an error or null. */
    private fun writeAtomically(target: File, bytes: ByteArray): String? {
        val tmp = File(dir, target.name + ".tmp")
        return try {
            dir.mkdirs()
            FileOutputStream(tmp).use { out -> out.write(bytes); out.flush(); out.fd.sync() }
            if (!tmp.renameTo(target)) {
                target.delete()
                if (!tmp.renameTo(target)) error("rename failed")
            }
            null
        } catch (e: Exception) {
            try { tmp.delete() } catch (x: Exception) { }
            e.javaClass.simpleName + (e.message?.let { ": $it" } ?: "")
        }
    }

    companion object {
        const val SNAPSHOT_FILE = "snapshot.json.gz"
        const val SETTINGS_FILE = "settings.json.gz"
    }
}
