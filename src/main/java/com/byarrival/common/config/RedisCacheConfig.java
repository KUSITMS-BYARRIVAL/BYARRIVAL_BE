package com.byarrival.common.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

/**
 * Redis 캐시 값을 JSON으로 저장하는 캐시 설정 (캐시별 TTL은 기능 티켓에서 정함)
 */
@Configuration
@EnableCaching
public class RedisCacheConfig {

    @Bean
    public RedisCacheConfiguration redisCacheConfiguration() {
        // 역직렬화 대상 타입을 우리 패키지와 JDK 타입으로 제한한다
        BasicPolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
            .allowIfSubType("com.byarrival.")
            .allowIfSubType("java.")
            .build();

        GenericJacksonJsonRedisSerializer valueSerializer = GenericJacksonJsonRedisSerializer.builder()
            .enableDefaultTyping(typeValidator)
            .enableSpringCacheNullValueSupport()
            .build();

        return RedisCacheConfiguration.defaultCacheConfig()
            .serializeValuesWith(SerializationPair.fromSerializer(valueSerializer));
    }
}
