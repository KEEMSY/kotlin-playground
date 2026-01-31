package com.playground.mvc.repository

import com.playground.mvc.entity.Post
import com.playground.mvc.entity.QPost.post
import com.playground.mvc.entity.QUser.user
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository

@Repository
class PostRepositoryImpl(
    private val queryFactory: JPAQueryFactory
) : PostRepositoryCustom {

    override fun search(condition: PostSearchCondition): List<Post> {
        return queryFactory
            .selectFrom(post)
            .leftJoin(post.user, user).fetchJoin()
            .where(
                titleContains(condition.title),
                contentContains(condition.content),
                authorNameEq(condition.authorName)
            )
            .fetch()
    }

    private fun titleContains(title: String?): BooleanExpression? {
        return title?.let { post.title.contains(it) }
    }

    private fun contentContains(content: String?): BooleanExpression? {
        return content?.let { post.content.contains(it) }
    }

    private fun authorNameEq(authorName: String?): BooleanExpression? {
        return authorName?.let { user.name.eq(it) }
    }
}
