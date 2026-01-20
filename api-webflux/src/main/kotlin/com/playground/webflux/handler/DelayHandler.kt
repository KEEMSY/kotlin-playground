package com.playground.webflux.handler

import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.*

@Component
class DelayHandler {

    private val logger = LoggerFactory.getLogger(DelayHandler::class.java)

    suspend fun delay(request: ServerRequest): ServerResponse {
        val ms = request.pathVariable("ms").toLong()
        val threadName = Thread.currentThread().name
        logger.info("Request started on thread: $threadName, delay: ${ms}ms")

        val startTime = System.currentTimeMillis()

        // This is NON-BLOCKING - the thread is released during the delay
        // Other requests can be processed on this thread while waiting
        delay(ms)

        val elapsed = System.currentTimeMillis() - startTime
        val endThreadName = Thread.currentThread().name
        logger.info("Request completed on thread: $endThreadName, elapsed: ${elapsed}ms")

        return ServerResponse.ok().bodyValueAndAwait(
                DelayResponse(
                        requestedDelay = ms,
                        actualDelay = elapsed,
                        startThread = threadName,
                        endThread = endThreadName,
                        message = "Non-blocking delay completed"
                )
        )
    }

    suspend fun cpuWork(request: ServerRequest): ServerResponse {
        val iterations = request.pathVariable("iterations").toLong()
        val threadName = Thread.currentThread().name
        logger.info("CPU work started on thread: $threadName, iterations: $iterations")

        val startTime = System.currentTimeMillis()

        // CPU-bound work - this is inherently blocking
        // Even in WebFlux, CPU work blocks the current thread
        var result = 0L
        for (i in 0 until iterations) {
            result += i * i
        }

        val elapsed = System.currentTimeMillis() - startTime
        logger.info("CPU work completed on thread: $threadName, elapsed: ${elapsed}ms")

        return ServerResponse.ok().bodyValueAndAwait(
                CpuWorkResponse(
                        iterations = iterations,
                        result = result,
                        elapsed = elapsed,
                        threadName = threadName
                )
        )
    }

    /**
     * Demonstrates what happens when you use blocking code in WebFlux
     * WARNING: This is an ANTI-PATTERN - never use Thread.sleep in WebFlux!
     */
    suspend fun blockingDelay(request: ServerRequest): ServerResponse {
        val ms = request.pathVariable("ms").toLong()
        val threadName = Thread.currentThread().name
        logger.warn("BLOCKING delay started on thread: $threadName - THIS IS AN ANTI-PATTERN!")

        val startTime = System.currentTimeMillis()

        // ANTI-PATTERN: Blocking the event loop thread!
        // This will severely degrade performance under load
        Thread.sleep(ms)

        val elapsed = System.currentTimeMillis() - startTime
        logger.warn("BLOCKING delay completed on thread: $threadName, elapsed: ${elapsed}ms")

        return ServerResponse.ok().bodyValueAndAwait(
                DelayResponse(
                        requestedDelay = ms,
                        actualDelay = elapsed,
                        startThread = threadName,
                        endThread = threadName,
                        message = "BLOCKING delay completed - THIS IS AN ANTI-PATTERN!"
                )
        )
    }
}

data class DelayResponse(
        val requestedDelay: Long,
        val actualDelay: Long,
        val startThread: String,
        val endThread: String,
        val message: String
)

data class CpuWorkResponse(
        val iterations: Long,
        val result: Long,
        val elapsed: Long,
        val threadName: String
)
