package com.byarrival.domain.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.byarrival.support.TestcontainersConfig;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 요일별 출근 마이그레이션(V13)이 기존 회원 데이터를 어떻게 바꾸는지 확인한다.
 * 별도 스키마를 V12까지 올린 뒤 기존 회원을 넣고 V13을 적용한다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
class WeeklyCommuteMigrationTest {

    private static final String SCHEMA = "weekly_commute_migration_test";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void migrateToV12() {
        dropSchema();
        flyway("12").migrate();
    }

    @AfterEach
    void dropSchema() {
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    @Test
    void 온보딩을_마쳤고_요일별_값이_없는_회원은_월요일부터_금요일까지_기본값으로_채운다() {
        Long userId = insertUser("COMPLETED", "08:10", 40, Map.of());

        flyway("13").migrate();

        Map<String, Object> user = weekly(userId);
        for (String day : new String[]{"mon", "tue", "wed", "thu", "fri"}) {
            assertThat(user.get(day + "_minutes")).isEqualTo(40);
            assertThat(user.get(day + "_start_time")).hasToString("08:10:00");
        }
        assertThat(user.get("sat_minutes")).isNull();
        assertThat(user.get("sun_start_time")).isNull();
    }

    @Test
    void 요일별_값이_있던_회원은_채우지_않고_그_값만_시작_시각과_함께_남긴다() {
        Long userId = insertUser("COMPLETED", "08:10", 40, Map.of("wed_minutes", 25));

        flyway("13").migrate();

        Map<String, Object> user = weekly(userId);
        assertThat(user.get("wed_minutes")).isEqualTo(25);
        assertThat(user.get("wed_start_time")).hasToString("08:10:00");
        assertThat(user.get("mon_minutes")).isNull();
        assertThat(user.get("fri_minutes")).isNull();
    }

    @Test
    void 쉬는_날을_0분으로_둔_회원은_빈_회원으로_보지_않고_0분만_비운다() {
        Long userId = insertUser("COMPLETED", "08:10", 40, Map.of("sat_minutes", 0, "sun_minutes", 0));

        flyway("13").migrate();

        Map<String, Object> user = weekly(userId);
        assertThat(user.get("mon_minutes")).isNull();
        assertThat(user.get("sat_minutes")).isNull();
        assertThat(user.get("sun_minutes")).isNull();
    }

    @Test
    void 온보딩을_마치지_않은_회원은_채우지_않는다() {
        Long userId = insertUser("IN_PROGRESS", "08:10", 40, Map.of());

        flyway("13").migrate();

        assertThat(weekly(userId).get("mon_minutes")).isNull();
    }

    private Flyway flyway(String target) {
        return Flyway.configure()
            .dataSource(dataSource)
            .schemas(SCHEMA)
            .locations("classpath:db/migration")
            .target(target)
            .load();
    }

    private Long insertUser(String onboardingStatus, String commuteStartTime, int defaultMinutes,
        Map<String, Integer> weekdayMinutes) {
        Long userId = jdbcTemplate.queryForObject("""
            INSERT INTO %s.users (provider, provider_user_id, onboarding_status, onboarded_at,
                                  commute_start_time, default_commute_minutes)
            VALUES ('KAKAO', gen_random_uuid()::text, ?, CASE WHEN ? = 'COMPLETED' THEN now() END, ?::time, ?)
            RETURNING id
            """.formatted(SCHEMA), Long.class, onboardingStatus, onboardingStatus, commuteStartTime, defaultMinutes);
        weekdayMinutes.forEach((column, minutes) -> jdbcTemplate.update(
            "UPDATE %s.users SET %s = ? WHERE id = ?".formatted(SCHEMA, column), minutes, userId));
        return userId;
    }

    private Map<String, Object> weekly(Long userId) {
        return jdbcTemplate.queryForMap("SELECT * FROM %s.users WHERE id = ?".formatted(SCHEMA), userId);
    }
}
