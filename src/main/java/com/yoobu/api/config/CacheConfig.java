package com.yoobu.api.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
                CacheNames.TENANT_CONFIG,
                CacheNames.TENANT_SERVICES
        );
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(60, TimeUnit.SECONDS));
        return manager;
    }

    /*
     * --- Redis migration guide ---
     *
     * Current provider: Caffeine (in-process, zero ops overhead).
     * All cache logic uses Spring Cache abstraction — @Cacheable / @CacheEvict
     * annotations and CacheNames constants are provider-agnostic and stay unchanged.
     *
     * When ready to switch to Redis:
     *
     * 1. In pom.xml:
     *    - Remove: spring-boot-starter-cache, caffeine
     *    - Add:    spring-boot-starter-data-redis
     *
     * 2. In application.yml:
     *    - Remove: spring.cache.type, spring.caffeine.spec
     *    - Add:
     *        spring:
     *          cache:
     *            type: redis
     *            redis:
     *              time-to-live: 60000   # ms, applies to all caches by default
     *          data:
     *            redis:
     *              url: ${REDIS_URL}
     *
     * 3. Delete this CacheConfig class — Spring Boot auto-configures RedisCacheManager.
     *    For per-cache TTL or custom serializer, replace with a RedisCacheManagerBuilderCustomizer bean.
     *
     * 4. Ensure cached return types (TenantConfigResponse, ServiceResponse) are
     *    serializable. Prefer Jackson-based serialization (default in Spring Data Redis).
     */
}
