package com.byarrival.domain.home.service;

import com.byarrival.common.time.ServiceDay;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.OptionalLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 서비스일별 접속자를 Redis Sorted Set에 둔다. 멤버는 회원 ID, 점수는 마지막 접속 시각(epoch ms)이다.
 * 지금은 그날 접속한 회원 수(ZCARD)를 쓰고, 실시간 접속자로 바꿀 때는 같은 키에서 최근 1시간 점수 범위(ZCOUNT)를 세면 된다.
 * 화면 표시용 값이라 저장·조회에 실패해도 요청은 실패시키지 않는다.
 */
@Slf4j
@Component
public class TodayVisitorStore {

    private static final String KEY_PREFIX = "home:visitors:";
    // 서비스일이 끝난 뒤에도 하루 더 남겨 04:00 직후 조회·확인에 쓸 수 있게 한다
    private static final Duration KEEP_AFTER_SERVICE_DAY = Duration.ofDays(1);

    private final StringRedisTemplate redisTemplate;
    private final ZoneId zone;

    public TodayVisitorStore(StringRedisTemplate redisTemplate, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.zone = clock.getZone();
    }

    /**
     * @return 저장했으면 true. Redis에 실패하면 false라 다음 요청에서 다시 시도한다
     */
    public boolean record(Long userId, LocalDate serviceDate, Instant accessedAt) {
        String key = key(serviceDate);
        try {
            redisTemplate.opsForZSet().add(key, String.valueOf(userId), accessedAt.toEpochMilli());
            Instant expireAt = ServiceDay.endOf(serviceDate).atZone(zone).toInstant().plus(KEEP_AFTER_SERVICE_DAY);
            redisTemplate.expireAt(key, expireAt);
            return true;
        } catch (RuntimeException e) {
            log.warn("[Visitor] 접속 기록 실패: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * 그 서비스일에 한 번이라도 API를 부른 회원 수. 조회에 실패하면 비어 있다.
     */
    public OptionalLong count(LocalDate serviceDate) {
        try {
            Long count = redisTemplate.opsForZSet().zCard(key(serviceDate));
            return count == null ? OptionalLong.empty() : OptionalLong.of(count);
        } catch (RuntimeException e) {
            log.warn("[Visitor] 접속자 수 조회 실패: {}", e.getClass().getSimpleName());
            return OptionalLong.empty();
        }
    }

    private static String key(LocalDate serviceDate) {
        return KEY_PREFIX + serviceDate;
    }
}
