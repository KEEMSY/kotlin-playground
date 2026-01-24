package com.playground.webflux.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/delay")
@Tag(name = "Delay", description = "Performance testing APIs")
class DelayController {

    private val logger = LoggerFactory.getLogger(DelayController::class.java)

    @GetMapping("/{ms}")
    @Operation(
        summary = "Simulate I/O delay (non-blocking)",
        description = "Simulates I/O operation using Kotlin coroutine delay(). " +
                "This is NON-BLOCKING - the thread is released during the delay."
    )
    @ApiResponse(responseCode = "200", description = "Delay completed")
    suspend fun delay(
        @Parameter(description = "Delay in milliseconds") @PathVariable ms: Long
    ): ResponseEntity<DelayResponse> {
        val threadName = Thread.currentThread().name
        logger.info("Request started on thread: $threadName, delay: ${ms}ms")

        val startTime = System.currentTimeMillis()

        // This is NON-BLOCKING - the thread is released during the delay
        // Other requests can be processed on this thread while waiting
        delay(ms)

        val elapsed = System.currentTimeMillis() - startTime
        val endThreadName = Thread.currentThread().name
        logger.info("Request completed on thread: $endThreadName, elapsed: ${elapsed}ms")

        return ResponseEntity.ok(
            DelayResponse(
                requestedDelay = ms,
                actualDelay = elapsed,
                startThread = threadName,
                endThread = endThreadName,
                message = "Non-blocking delay completed"
            )
        )
    }

    @GetMapping("/cpu/{iterations}")
    @Operation(
        summary = "Simulate CPU-intensive work",
        description = "Simulates CPU-bound operation. CPU work is inherently blocking."
    )
    @ApiResponse(responseCode = "200", description = "CPU work completed")
    suspend fun cpuWork(
        @Parameter(description = "Number of iterations") @PathVariable iterations: Long
    ): ResponseEntity<CpuWorkResponse> {
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

        return ResponseEntity.ok(
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
    @GetMapping("/blocking/{ms}")
    @Operation(
        summary = "Simulate blocking delay (ANTI-PATTERN)",
        description = "Demonstrates what happens when you use Thread.sleep() in WebFlux. " +
                "WARNING: This is an anti-pattern and will degrade performance!"
    )
    @ApiResponse(responseCode = "200", description = "Blocking delay completed")
    suspend fun blockingDelay(
        @Parameter(description = "Delay in milliseconds") @PathVariable ms: Long
    ): ResponseEntity<DelayResponse> {
        val threadName = Thread.currentThread().name
        logger.warn("BLOCKING delay started on thread: $threadName - THIS IS AN ANTI-PATTERN!")

        val startTime = System.currentTimeMillis()

        // ANTI-PATTERN: Blocking the event loop thread!
        // This will severely degrade performance under load
        Thread.sleep(ms)

        val elapsed = System.currentTimeMillis() - startTime
        logger.warn("BLOCKING delay completed on thread: $threadName, elapsed: ${elapsed}ms")

        return ResponseEntity.ok(
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
