package com.playground.webflux.entity

import org.komapper.annotation.KomapperColumn
import org.komapper.annotation.KomapperCreatedAt
import org.komapper.annotation.KomapperEntity
import org.komapper.annotation.KomapperId
import org.komapper.annotation.KomapperTable
import org.komapper.annotation.KomapperUpdatedAt
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@KomapperEntity
@KomapperTable("posts")
@Table("posts")
data class Post(
    @KomapperId @Id
    val id: Long? = null,

    @KomapperColumn("title") @Column("title")
    var title: String,

    @KomapperColumn("content") @Column("content")
    var content: String,

    @KomapperColumn("user_id") @Column("user_id")
    val userId: Long,

    @KomapperCreatedAt @CreatedDate
    @KomapperColumn("created_at") @Column("created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @KomapperUpdatedAt @LastModifiedDate
    @KomapperColumn("updated_at") @Column("updated_at")
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
