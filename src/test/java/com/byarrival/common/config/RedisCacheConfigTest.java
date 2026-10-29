package com.byarrival.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

class RedisCacheConfigTest {

    private final SerializationPair<Object> valuePair =
        new RedisCacheConfig().redisCacheConfiguration().getValueSerializationPair();

    record CachedValue(Long id, String title, LocalDateTime createdAt, List<String> tags) {
    }

    @Test
    void 캐시_값은_원래_타입으로_복원된다() {
        CachedValue value = new CachedValue(1L, "제목", LocalDateTime.of(2026, 10, 29, 4, 0), List.of("경제"));

        Object restored = valuePair.read(valuePair.write(value));

        assertThat(restored).isEqualTo(value);
    }
}
