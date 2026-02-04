package com.playground.webflux.service

import com.playground.core.exception.NotFoundException
import com.playground.core.util.logger
import com.playground.webflux.dto.*
import com.playground.webflux.entity.Post
import com.playground.webflux.entity.User
import com.playground.webflux.entity.post
import com.playground.webflux.entity.user
import com.playground.webflux.repository.PostRepository
import com.playground.webflux.repository.UserRepository
import kotlinx.coroutines.flow.*
import org.komapper.core.dsl.Meta
import org.komapper.core.dsl.QueryDsl
import org.komapper.core.dsl.operator.and
import org.komapper.r2dbc.R2dbcDatabase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class PostService(
    private val postRepository: PostRepository,
    private val userRepository: UserRepository,
    private val db: R2dbcDatabase
) {
    private val log = logger()

    suspend fun searchPosts(condition: PostSearchCondition): List<PostResponse> {
        log.info("Searching posts with Komapper: $condition")
        
        val p = Meta.post
        val u = Meta.user

        val posts = db.runQuery {
            QueryDsl.from(p)
                .innerJoin(u) { p.userId eq u.id }
                .where {
                    if (!condition.title.isNullOrBlank()) p.title contains condition.title
                    if (!condition.content.isNullOrBlank()) p.content contains condition.content
                    if (!condition.authorName.isNullOrBlank()) u.name eq condition.authorName
                }
        }

        val userIds = posts.map { it.userId }.distinct()
        val users = userRepository.findAllById(userIds).toList()
        val userMap = users.associateBy { it.id }

        return posts.map { post ->
            val user = userMap[post.userId]
                ?: throw NotFoundException("User not found for post: ${post.id}")
            PostResponse.from(post, user)
        }
    }

    @Transactional
    suspend fun createPost(request: CreatePostRequest): PostResponse {
        log.info("Creating post for user: ${request.userId}")
        
        val user = userRepository.findById(request.userId)
            ?: throw NotFoundException("User not found with id: ${request.userId}")

        val post = postRepository.save(request.toEntity())
        return PostResponse.from(post, user)
    }

    suspend fun getPostById(id: Long): PostResponse {
        log.info("Fetching post with id: $id")
        val post = postRepository.findById(id)
            ?: throw NotFoundException("Post not found with id: $id")
        
        val user = userRepository.findById(post.userId)
            ?: throw NotFoundException("User not found with id: ${post.userId}")
            
        return PostResponse.from(post, user)
    }

    fun getPostsByUserId(userId: Long): Flow<PostResponse> {
        log.info("Fetching posts for user: $userId")
        return flow {
            val user = userRepository.findById(userId)
                ?: throw NotFoundException("User not found with id: $userId")

            postRepository.findAllByUserId(userId)
                .collect { post ->
                    emit(PostResponse.from(post, user))
                }
        }
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    fun getAllPosts(): Flow<PostResponse> {
        log.info("Fetching all posts with parallel author fetching")
        return postRepository.findAll()
            .flatMapMerge(concurrency = 16) { post ->
                flow {
                    val user = userRepository.findById(post.userId)
                        ?: throw NotFoundException("User not found with id: ${post.userId}")
                    emit(PostResponse.from(post, user))
                }
            }
    }

    suspend fun getAllPostsBatch(): List<PostResponse> {
        log.info("Fetching all posts with Batch Loading (Map matching)")
        
        val posts = postRepository.findAll().toList()
        if (posts.isEmpty()) return emptyList()

        val userIds = posts.map { it.userId }.distinct()
        val users = userRepository.findAllById(userIds).toList()
        val userMap = users.associateBy { it.id }

        return posts.map { post ->
            val user = userMap[post.userId]
                ?: throw NotFoundException("User not found with id: ${post.userId}")
            PostResponse.from(post, user)
        }
    }

    @Transactional
    suspend fun updatePost(id: Long, request: UpdatePostRequest): PostResponse {
        log.info("Updating post with id: $id")
        val post = postRepository.findById(id)
            ?: throw NotFoundException("Post not found with id: $id")
            
        val user = userRepository.findById(post.userId)
            ?: throw NotFoundException("User not found with id: ${post.userId}")

        val updatedPost = postRepository.save(post.update(request.title, request.content))
        return PostResponse.from(updatedPost, user)
    }

    @Transactional
    suspend fun deletePost(id: Long) {
        log.info("Deleting post with id: $id")
        if (!postRepository.existsById(id)) {
            throw NotFoundException("Post not found with id: $id")
        }
        postRepository.deleteById(id)
    }
}
