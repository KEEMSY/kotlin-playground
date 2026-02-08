package com.playground.webflux.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.core.deleteAndAwait
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@Tag(name = "Cache", description = "Cache Management APIs for testing consistency")
@RestController
@RequestMapping("/api/v1/cache")
class CacheController(
    private val redisTemplate: ReactiveRedisTemplate<String, Any>
) {

    @Operation(summary = "Manual purge of user cache")
    @DeleteMapping("/users/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun purgeUserCache(@PathVariable userId: Long) {
        val cacheKey = "playground:user:profile:$userId"
        redisTemplate.deleteAndAwait(cacheKey)
    }

    @Operation(summary = "Clear all cache (DANGEROUS - Testing only)")
    @DeleteMapping("/clear-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun clearAllCache() {
        val keys = redisTemplate.keys("*")
        redisTemplate.delete(keys).subscribe()
    }
}
