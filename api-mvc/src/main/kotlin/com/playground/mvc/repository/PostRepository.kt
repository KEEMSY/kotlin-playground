package com.playground.mvc.repository

import com.playground.mvc.entity.Post
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface PostRepository : JpaRepository<Post, Long>, PostRepositoryCustom {
    
    @Query("select p from Post p join fetch p.user where p.user.id = :userId")
    fun findAllByUserIdWithUser(userId: Long): List<Post>

    fun findAllByUserId(userId: Long): List<Post>
}
