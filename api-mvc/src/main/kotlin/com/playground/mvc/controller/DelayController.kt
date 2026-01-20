package com.playground.mvc.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/delay")
@Tag(name = "Delay", description = "Delay simulation APIs for performance testing")
class DelayController {

    private val logger = LoggerFactory.getLogger(DelayController::class.java)

    @GetMapping("/{ms}")
    @Operation(
            summary = "Simulate I/O delay (blocking)",
            description = "Simulates I/O operation by blocking the thread with Thread.sleep(). " +
                    "In MVC, this blocks one thread from the pool for the duration of the delay."
    )
    fun delay(
            @Parameter(description = "Delay in milliseconds") @PathVariable ms: Long
    ): ResponseEntity<DelayResponse> {
        val threadName = Thread.currentThread().name
        logger.info("Request started on thread: $threadName, delay: ${ms}ms")

        val startTime = System.currentTimeMillis()

        // This BLOCKS the thread - in MVC, one thread is occupied
        Thread.sleep(ms)

        val elapsed = System.currentTimeMillis() - startTime
        logger.info("Request completed on thread: $threadName, elapsed: ${elapsed}ms")

        return ResponseEntity.ok(
                DelayResponse(
                        requestedDelay = ms,
                        actualDelay = elapsed,
                        threadName = threadName,
                        message = "Blocking delay completed"
                )
        )
    }

    @GetMapping("/cpu/{iterations}")
    @Operation(
            summary = "Simulate CPU-intensive work",
            description = "Simulates CPU-bound operation. Both MVC and WebFlux handle this similarly."
    )
    fun cpuWork(
            @Parameter(description = "Number of iterations") @PathVariable iterations: Long
    ): ResponseEntity<CpuWorkResponse> {
        val threadName = Thread.currentThread().name
        logger.info("CPU work started on thread: $threadName, iterations: $iterations")

        val startTime = System.currentTimeMillis()

        // CPU-bound work
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
}

data class DelayResponse(
        val requestedDelay: Long,
        val actualDelay: Long,
        val threadName: String,
        val message: String
)

data class CpuWorkResponse(
        val iterations: Long,
        val result: Long,
        val elapsed: Long,
        val threadName: String
)
