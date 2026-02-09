package com.playground.mvc.config

import com.playground.infra.lock.DistributedLockService
import com.playground.infra.lock.lettuce.LettuceLockService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate

@Configuration
class RedisConfig {

    @Bean
    fun stringRedisTemplate(connectionFactory: RedisConnectionFactory): StringRedisTemplate {
        return StringRedisTemplate(connectionFactory)
    }

    @Bean
    fun distributedLockService(stringRedisTemplate: StringRedisTemplate): DistributedLockService {
        return LettuceLockService(stringRedisTemplate)
    }
}
