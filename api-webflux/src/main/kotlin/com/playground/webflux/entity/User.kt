package com.playground.webflux.entity

import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("users")
data class User(
    @Id
    val id: Long? = null,

    @Column("email")
    var email: String,

    @Column("name")
    var name: String,

    @CreatedDate
    @Column("created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @LastModifiedDate
    @Column("updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    fun update(email: String?, name: String?): User {
        return this.copy(
            email = email ?: this.email,
            name = name ?: this.name,
            updatedAt = LocalDateTime.now()
        )
    }
}
