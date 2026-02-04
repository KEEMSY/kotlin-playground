package com.playground.webflux.service

import com.playground.webflux.entity.Post
import com.playground.webflux.repository.PerformanceRepository
import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service

@Service
class PerformanceService(
    private val performanceRepository: PerformanceRepository
) {

    suspend fun runDbSleep() {
        performanceRepository.dbSleep()
    }

    suspend fun getBulkPosts(): List<Post> {
        return performanceRepository.findAllBulk().toList()
    }
}
