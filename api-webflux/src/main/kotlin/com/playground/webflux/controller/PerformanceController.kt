package com.playground.webflux.controller

import com.playground.webflux.entity.Post
import com.playground.webflux.service.PerformanceService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Performance", description = "Benchmark endpoints for FastAPI comparison")
@RestController
@RequestMapping("/api/v1/performance")
class PerformanceController(
    private val performanceService: PerformanceService
) {

    @Operation(summary = "DB pg_sleep(1) call")
    @GetMapping("/sleep")
    suspend fun sleep() {
        performanceService.runDbSleep()
    }

    @Operation(summary = "Bulk read 1,000 records")
    @GetMapping("/bulk")
    suspend fun bulk(): List<Post> {
        return performanceService.getBulkPosts()
    }
}
