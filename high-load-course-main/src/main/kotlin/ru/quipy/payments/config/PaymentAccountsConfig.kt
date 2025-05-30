package ru.quipy.payments.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import ru.quipy.common.utils.RateLimiter
import ru.quipy.core.EventSourcingService
import ru.quipy.payments.api.PaymentAggregate
import ru.quipy.payments.logic.AdaptiveRateLimiter
import ru.quipy.payments.logic.PaymentAccountProperties
import ru.quipy.payments.logic.PaymentAggregateState
import ru.quipy.payments.logic.PaymentExternalSystemAdapter
import ru.quipy.payments.logic.PaymentExternalSystemAdapterImpl
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.*

@Configuration
@EnableScheduling
class PaymentAccountsConfig {

    companion object {
        private val javaClient = HttpClient.newBuilder().build()
        private val mapper = ObjectMapper().registerKotlinModule().registerModules(JavaTimeModule())
    }

    @Value("\${payment.hostPort}")
    lateinit var paymentProviderHostPort: String

    private val allowedAccounts = setOf("acc-9")

    @Bean
    fun adaptiveRateLimiter(): AdaptiveRateLimiter =
        AdaptiveRateLimiter(
            rate = 100L,
            window = Duration.ofSeconds(1),
            bucketSize = 100
        )

    @Scheduled(fixedDelay = 5000)
    fun adjustRateLimiter() {
        val load = getCurrentLoad()

        val newRate = when {
            load < 0.5 -> 200L
            load < 0.75 -> 100L
            load < 0.9 -> 50L
            else -> 20L
        }

        val newBucketSize = (newRate * 2).toInt()
        val newWindow = Duration.ofSeconds(1)

        adaptiveRateLimiter().update(newRate, newWindow, newBucketSize)
    }

    private fun getCurrentLoad(): Double {
        return Math.random()
    }

    @Bean
    fun accountAdapters(
        paymentService: EventSourcingService<UUID, PaymentAggregate, PaymentAggregateState>,
        rateLimiter: RateLimiter
    ): List<PaymentExternalSystemAdapter> {
        val request = HttpRequest.newBuilder()
            .uri(URI("http://${paymentProviderHostPort}/external/accounts?serviceName=onlineStore"))
            .GET()
            .build()

        val resp = javaClient.send(request, HttpResponse.BodyHandlers.ofString())

        println("\nPayment accounts list:")
        return mapper.readValue<List<PaymentAccountProperties>>(
            resp.body(),
            mapper.typeFactory.constructCollectionType(List::class.java, PaymentAccountProperties::class.java)
        )
            .filter { it.accountName in allowedAccounts }
            .onEach(::println)
            .map { PaymentExternalSystemAdapterImpl(it, paymentService, rateLimiter) }
    }
}