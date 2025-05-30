package ru.quipy.payments.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import ru.quipy.common.utils.LeakingBucketRateLimiter
import ru.quipy.common.utils.RateLimiter
import ru.quipy.payments.logic.AdaptiveRateLimiter
import java.time.Duration

@Configuration
class RateLimiterConfig {

    @Bean
    fun adaptiveRateLimiter(): AdaptiveRateLimiter =
        AdaptiveRateLimiter(
            rate = 100000L,
            window = Duration.ofSeconds(10000),
            bucketSize = 10000
        )
}
