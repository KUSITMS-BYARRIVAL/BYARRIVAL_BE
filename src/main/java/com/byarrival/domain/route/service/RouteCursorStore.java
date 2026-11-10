package com.byarrival.domain.route.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 후보 카드 화면에서 마지막으로 본 카드 순번을 노선 만료 시각까지 Redis에 둔다.
 * 화면 편의용 값이라 저장·조회에 실패해도 요청은 실패시키지 않고 첫 카드부터 보여 준다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RouteCursorStore {

    private static final String KEY_PREFIX = "route:cursor:";

    private final StringRedisTemplate redisTemplate;

    public void save(Long routeId, int cardOrder, LocalDateTime now, LocalDateTime expiresAt) {
        Duration ttl = Duration.between(now, expiresAt);
        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key(routeId), String.valueOf(cardOrder), ttl);
        } catch (RuntimeException e) {
            log.warn("[RouteCursor] routeId={} 카드 위치 저장 실패: {}", routeId, e.getClass().getSimpleName());
        }
    }

    public Optional<Integer> find(Long routeId) {
        try {
            String value = redisTemplate.opsForValue().get(key(routeId));
            return Optional.ofNullable(value).map(Integer::valueOf);
        } catch (RuntimeException e) {
            log.warn("[RouteCursor] routeId={} 카드 위치 조회 실패: {}", routeId, e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private String key(Long routeId) {
        return KEY_PREFIX + routeId;
    }
}
