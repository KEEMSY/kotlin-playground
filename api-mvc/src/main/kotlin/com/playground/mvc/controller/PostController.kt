package com.playground.mvc.controller

import com.playground.mvc.dto.CreatePostRequest
import com.playground.mvc.dto.PostResponse
import com.playground.mvc.dto.UpdatePostRequest
import com.playground.mvc.repository.PostSearchCondition
import com.playground.mvc.service.PostService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@Tag(name = "Post", description = "Post Management API")
@RestController
@RequestMapping("/api/v1/posts")
class PostController(
    private val postService: PostService
) {

    @Operation(summary = "Create post")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createPost(@Valid @RequestBody request: CreatePostRequest): PostResponse {
        return postService.createPost(request)
    }

    @Operation(summary = "Get post by ID")
    @GetMapping("/{id}")
    fun getPostById(@PathVariable id: Long): PostResponse {
        return postService.getPostById(id)
    }

    @Operation(summary = "Search posts (Dynamic Query)")
    @GetMapping("/search")
    fun searchPosts(condition: PostSearchCondition): List<PostResponse> {
        return postService.searchPosts(condition)
    }

    @Operation(summary = "Get posts by User ID")
    @GetMapping("/user/{userId}")
    fun getPostsByUserId(@PathVariable userId: Long): List<PostResponse> {
        return postService.getPostsByUserId(userId)
    }

    @Operation(summary = "Update post")
    @PutMapping("/{id}")
    fun updatePost(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdatePostRequest
    ): PostResponse {
        return postService.updatePost(id, request)
    }

    @Operation(summary = "Delete post")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deletePost(@PathVariable id: Long) {
        postService.deletePost(id)
    }
}
