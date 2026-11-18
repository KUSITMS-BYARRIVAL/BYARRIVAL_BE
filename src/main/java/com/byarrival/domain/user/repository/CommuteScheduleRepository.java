package com.byarrival.domain.user.repository;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 요일별 출근 시각으로 회원을 찾는다. 요일마다 컬럼이 달라서, 정해진 컬럼 이름 중 하나를 골라 쿼리를 만든다.
 */
@Repository
@RequiredArgsConstructor
public class CommuteScheduleRepository {

    private static final Map<DayOfWeek, String> START_TIME_COLUMNS = new EnumMap<>(Map.of(
        DayOfWeek.MONDAY, "mon_start_time",
        DayOfWeek.TUESDAY, "tue_start_time",
        DayOfWeek.WEDNESDAY, "wed_start_time",
        DayOfWeek.THURSDAY, "thu_start_time",
        DayOfWeek.FRIDAY, "fri_start_time",
        DayOfWeek.SATURDAY, "sat_start_time",
        DayOfWeek.SUNDAY, "sun_start_time"));

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * 그 요일 출근 시각이 from~to(양 끝 포함)인 활동 회원. 그 요일을 쉬는 날로 둔 회원은 출근 시각이 없어 빠진다.
     */
    public List<Long> findUserIdsByCommuteStart(DayOfWeek day, LocalTime from, LocalTime to) {
        String column = START_TIME_COLUMNS.get(day);
        String sql = "SELECT id FROM users WHERE status = 'ACTIVE' AND onboarding_status = 'COMPLETED' "
            + "AND " + column + " BETWEEN :from AND :to ORDER BY id";
        return jdbcTemplate.queryForList(sql, new MapSqlParameterSource()
            .addValue("from", from)
            .addValue("to", to), Long.class);
    }
}
