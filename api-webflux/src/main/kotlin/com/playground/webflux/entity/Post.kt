package com.playground.webflux.entity

import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("posts")
data class Post(
    @Id
    val id: Long? = null,

    @Column("title")
    var title: String,

    @Column("content")
    var content: String,

    @Column("user_id")
    val userId: Long,

    @CreatedDate
    @Column("created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @LastModifiedDate
    @Column("updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    fun update(title: String?, content: String?): Post {
        return this.copy(
            title = title ?: this.title,
            content = content ?: this.content,
            updatedAt = LocalDateTime.now()
        )
    }
}
