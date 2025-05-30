package ru.quipy.payments.logic

import org.springframework.stereotype.Component
import ru.quipy.common.utils.LeakingBucketRateLimiter
import ru.quipy.common.utils.RateLimiter
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

class AdaptiveRateLimiter(
    private val rate: Long,
    private val window: Duration,
    private val bucketSize: Int
) : RateLimiter {

    private val delegateRef = AtomicReference<RateLimiter>(
        LeakingBucketRateLimiter(rate, window, bucketSize)
    )

    fun update(rate: Long, window: Duration, bucketSize: Int) {
        delegateRef.set(LeakingBucketRateLimiter(rate, window, bucketSize))
    }

    override fun tick(): Boolean = delegateRef.get().tick()
}