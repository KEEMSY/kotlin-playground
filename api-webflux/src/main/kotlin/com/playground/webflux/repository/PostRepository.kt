package com.playground.webflux.repository

import com.playground.webflux.entity.Post
import kotlinx.coroutines.flow.Flow
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

@Repository
interface PostRepository : CoroutineCrudRepository<Post, Long> {
    fun findAllByUserId(userId: Long): Flow<Post>
}
