package com.capsconc.arcshield.capture

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

// Circular buffer for one sensor channel. Holds samples within a rolling
// time window of [capacityNanos]. Samples older than
// (newest_timestamp − capacityNanos) are evicted on every offer().
//
// Thread-safe for concurrent producers (one per channel) and one extractor
// consumer. Uses a read-write lock so extraction doesn't block offers.
class ChannelRingBuffer<T>(
    val capacityNanos: Long,
    val timestampOf: (T) -> Long,
) {
    private val data = ArrayDeque<T>()
    private val lock = ReentrantReadWriteLock()

    fun offer(item: T) {
        val ts = timestampOf(item)
        lock.write {
            data.addLast(item)
            while (data.size > 1 && timestampOf(data.first()) < ts - capacityNanos) {
                data.removeFirst()
            }
        }
    }

    // Returns all samples with timestamp in [startNanos, endNanos], sorted
    // ascending by timestamp. Sorted on every extraction because samples from
    // some sources (e.g. ring-reconnect back-fill) may arrive out of order.
    fun extract(startNanos: Long, endNanos: Long): List<T> {
        lock.read {
            return data
                .filter { timestampOf(it) in startNanos..endNanos }
                .sortedBy { timestampOf(it) }
        }
    }

    fun clear() {
        lock.write { data.clear() }
    }

    val size: Int
        get() = lock.read { data.size }
}
