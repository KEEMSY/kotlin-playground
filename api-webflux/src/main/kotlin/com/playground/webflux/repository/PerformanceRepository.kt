package com.playground.webflux.repository

import com.playground.webflux.entity.Post
import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

@Repository
interface PerformanceRepository : CoroutineCrudRepository<Post, Long> {

    @Query("SELECT pg_sleep(1) IS NULL")
    suspend fun dbSleep(): Boolean

    @Query("SELECT * FROM posts LIMIT 1000")
    fun findAllBulk(): Flow<Post>
}
