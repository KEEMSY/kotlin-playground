package com.playground.webflux.entity

import java.time.LocalDateTime
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

@KomapperEntity
@KomapperTable("users")
@Table("users")
data class User(
        @KomapperId @Id val id: Long? = null,
        @KomapperColumn("email") @Column("email") var email: String,
        @KomapperColumn("name") @Column("name") var name: String,
        @KomapperCreatedAt @CreatedDate @Column("created_at") val createdAt: LocalDateTime = LocalDateTime.now(),
        @KomapperUpdatedAt @LastModifiedDate @Column("updated_at") var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    fun update(email: String?, name: String?): User {
        return this.copy(
                email = email ?: this.email,
                name = name ?: this.name,
                updatedAt = LocalDateTime.now()
        )
    }
}
