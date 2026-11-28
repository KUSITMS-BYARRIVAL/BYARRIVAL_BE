package com.byarrival.domain.notification.repository;

import com.byarrival.domain.notification.entity.NotificationType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 자동 알림 발송 이력. 회원 수만큼 나가므로 서비스일·유형별로 묶어 푸시 결과만 센다.
 * 알림은 30일 뒤 지우므로 그보다 오래된 이력은 남지 않는다.
 */
@Repository
@RequiredArgsConstructor
public class AutoNotificationHistoryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public List<AutoHistoryRow> findDaily(Collection<NotificationType> types, LocalDate from, LocalDate to) {
        String sql = "SELECT service_date, type, "
            + "COUNT(*) FILTER (WHERE push_status = 'SENT') AS success, "
            + "COUNT(*) FILTER (WHERE push_status IN ('FAILED', 'NO_DEVICE')) AS failure, "
            + "COUNT(*) FILTER (WHERE push_status = 'PENDING') AS pending "
            + "FROM notification WHERE type IN (:types) AND service_date BETWEEN :from AND :to "
            + "AND push_status <> 'SKIPPED' GROUP BY service_date, type ORDER BY service_date DESC, type";
        MapSqlParameterSource params = new MapSqlParameterSource("types", types.stream().map(Enum::name).toList())
            .addValue("from", from).addValue("to", to);
        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new AutoHistoryRow(
            rs.getObject("service_date", LocalDate.class), NotificationType.valueOf(rs.getString("type")),
            rs.getLong("success"), rs.getLong("failure"), rs.getLong("pending")));
    }

    /**
     * @param pending 아직 푸시 결과가 나오지 않은 수
     */
    public record AutoHistoryRow(LocalDate serviceDate, NotificationType type, long success, long failure,
                                 long pending) {
    }
}
