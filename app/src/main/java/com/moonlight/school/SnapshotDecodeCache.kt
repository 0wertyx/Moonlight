package com.moonlight.school

/** Single-entry in-memory memoization of the current persisted snapshot.
 * The preferences remain the source of truth. No additional disk writes. */
internal class SnapshotDecodeCache<T>(private val decode: (String) -> T?) {
    private var key: String? = null
    private var value: T? = null

    @Synchronized
    fun get(raw: String?): T? {
        if (raw != key) {
            value = raw?.let(decode)
            key = raw
        }
        return value
    }
}
