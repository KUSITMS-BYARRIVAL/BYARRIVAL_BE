package com.byarrival.domain.notification.repository;

import com.byarrival.domain.notification.entity.BroadcastKind;
import com.byarrival.domain.notification.entity.BroadcastTarget;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 어드민 알림을 실제로 받을 회원(NOTI-01). 예상 인원과 발송 대상이 같은 조건을 쓴다.
 * <ul>
 *     <li>활동 중이고 온보딩을 마친 회원. 이용 제한 중인 회원은 뺀다.</li>
 *     <li>알림 수신 설정(ACTIVITY)을 껐거나 OS 알림 권한을 거부한 회원은 뺀다.</li>
 *     <li>이벤트는 광고성 정보 수신 동의자만, 야간(21~08시)에는 야간 광고성 정보 수신 동의자만 받는다.</li>
 * </ul>
 */
@Repository
@RequiredArgsConstructor
public class BroadcastRecipientRepository {

    private static final String LATEST_CONSENT = "COALESCE((SELECT a.agreed FROM user_terms_agreement a "
        + "JOIN terms t ON t.id = a.terms_id WHERE a.user_id = u.id AND t.terms_type = '%s' "
        + "ORDER BY a.agreed_at DESC, a.id DESC LIMIT 1), FALSE)";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public List<Long> findRecipients(BroadcastKind kind, BroadcastTarget target, boolean night, LocalDateTime now) {
        MapSqlParameterSource params = new MapSqlParameterSource("now", now);
        return jdbcTemplate.queryForList("SELECT u.id FROM users u " + where(kind, target, night, params)
            + "ORDER BY u.id", params, Long.class);
    }

    public long count(BroadcastKind kind, BroadcastTarget target, boolean night, LocalDateTime now) {
        MapSqlParameterSource params = new MapSqlParameterSource("now", now);
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users u " + where(kind, target, night, params),
            params, Long.class);
        return count == null ? 0 : count;
    }

    private static String where(BroadcastKind kind, BroadcastTarget target, boolean night,
        MapSqlParameterSource params) {
        StringBuilder where = new StringBuilder()
            .append("WHERE u.status = 'ACTIVE' AND u.onboarding_status = 'COMPLETED' ")
            .append("AND (u.notification_permission IS NULL OR u.notification_permission <> 'DENIED') ")
            .append("AND NOT EXISTS (SELECT 1 FROM user_notification_setting s WHERE s.user_id = u.id ")
            .append("AND s.category = 'ACTIVITY' AND NOT s.enabled) ")
            .append("AND NOT EXISTS (SELECT 1 FROM user_restriction r WHERE r.user_id = u.id ")
            .append("AND r.released_at IS NULL AND r.starts_at <= :now AND (r.ends_at IS NULL OR r.ends_at > :now)) ");
        if (target.getTravelType() != null) {
            where.append("AND u.travel_type = :travelType ");
            params.addValue("travelType", target.getTravelType().name());
        }
        if (kind == BroadcastKind.EVENT) {
            where.append("AND ").append(LATEST_CONSENT.formatted("MARKETING")).append(" ");
            if (night) {
                where.append("AND ").append(LATEST_CONSENT.formatted("MARKETING_NIGHT")).append(" ");
            }
        }
        return where.toString();
    }
}
