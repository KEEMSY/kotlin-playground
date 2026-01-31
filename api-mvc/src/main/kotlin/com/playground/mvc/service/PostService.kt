package com.playground.mvc.service

import com.playground.core.exception.NotFoundException
import com.playground.mvc.dto.CreatePostRequest
import com.playground.mvc.dto.PostResponse
import com.playground.mvc.dto.UpdatePostRequest
import com.playground.mvc.entity.Post
import com.playground.mvc.repository.PostRepository
import com.playground.mvc.repository.PostSearchCondition
import com.playground.mvc.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class PostService(
    private val postRepository: PostRepository,
    private val userRepository: UserRepository
) {

    @Transactional
    fun createPost(request: CreatePostRequest): PostResponse {
        val user = userRepository.findById(request.userId)
            .orElseThrow { NotFoundException("User not found with id: ${request.userId}") }

        val post = Post(
            title = request.title,
            content = request.content,
            user = user
        )
        
        user.addPost(post)

        return PostResponse.from(postRepository.save(post))
    }

    fun getPostById(id: Long): PostResponse {
        return postRepository.findById(id)
            .map { PostResponse.from(it) }
            .orElseThrow { NotFoundException("Post not found with id: $id") }
    }

    fun getPostsByUserId(userId: Long): List<PostResponse> {
        return postRepository.findAllByUserIdWithUser(userId)
            .map { PostResponse.from(it) }
    }

    fun searchPosts(condition: PostSearchCondition): List<PostResponse> {
        return postRepository.search(condition)
            .map { PostResponse.from(it) }
    }

    @Transactional
    fun updatePost(id: Long, request: UpdatePostRequest): PostResponse {
        val post = postRepository.findById(id)
            .orElseThrow { NotFoundException("Post not found with id: $id") }

        post.update(request.title, request.content)
        return PostResponse.from(post)
    }

    @Transactional
    fun deletePost(id: Long) {
        if (!postRepository.existsById(id)) {
            throw NotFoundException("Post not found with id: $id")
        }
        postRepository.deleteById(id)
    }
}
