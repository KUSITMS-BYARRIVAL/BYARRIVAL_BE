package com.byarrival.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.byarrival.domain.notification.auto.CommuteNotificationTrigger;
import com.byarrival.domain.notification.auto.RouteIncompleteTrigger;
import com.byarrival.domain.notification.auto.SmalltalkNotificationTrigger;
import com.byarrival.domain.notification.auto.StreakMilestoneListener;
import com.byarrival.domain.route.event.RouteCompletedEvent;
import com.byarrival.domain.user.entity.SocialProvider;
import com.byarrival.domain.user.entity.User;
import com.byarrival.domain.user.repository.UserRepository;
import com.byarrival.support.TestcontainersConfig;
import jakarta.persistence.EntityManager;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자동 알림 6종의 발송 조건을 실제 DB로 확인한다. 작업은 시각을 넘겨 직접 부르고, 다른 테스트와 섞이지 않게 먼 서비스일을 쓴다.
 * 테스트마다 롤백한다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@Transactional
class AutoNotificationFlowIntegrationTest {

    // 2030-01-09는 수요일이다
    private static final LocalDate TODAY = LocalDate.of(2030, 1, 9);
    private static final LocalTime COMMUTE = LocalTime.of(8, 0);
    private static final int GANGNAM = 101;
    private static final int GANGDONG = 102;

    @Autowired
    private CommuteNotificationTrigger commuteTrigger;

    @Autowired
    private RouteIncompleteTrigger routeIncompleteTrigger;

    @Autowired
    private SmalltalkNotificationTrigger smalltalkTrigger;

    @Autowired
    private StreakMilestoneListener streakMilestoneListener;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private Long feedId;
    private Long categoryId;

    @BeforeEach
    void setUp() {
        feedId = jdbcTemplate.queryForObject(
            "INSERT INTO rss_feed (press_name, feed_url) VALUES ('테스트', ?) RETURNING id", Long.class,
            "https://example.com/" + UUID.randomUUID());
        categoryId = jdbcTemplate.queryForObject(
            "INSERT INTO category (name, display_order) VALUES (?, 99) RETURNING id", Long.class,
            "테스트-" + UUID.randomUUID().toString().substring(0, 8));
    }

    @Test
    void 오늘의_노선_준비는_출근_10분_전에_보낸다() {
        Long userId = commuter(DayOfWeek.WEDNESDAY, COMMUTE);

        commuteTrigger.run(TODAY.atTime(7, 50));

        Map<String, Object> sent = notification(userId, "ROUTE_READY");
        assertThat(sent.get("body")).isEqualTo("오늘의 노선, 도착까지 시작해볼까요?");
        assertThat((String) sent.get("title")).endsWith("🚇");
        assertThat(sent.get("params")).isEqualTo("{}");
    }

    @Test
    void 출근_10분_전이_아니거나_이미_출발했으면_노선_준비_알림을_보내지_않는다() {
        Long early = commuter(DayOfWeek.WEDNESDAY, COMMUTE);
        Long departed = commuter(DayOfWeek.WEDNESDAY, COMMUTE);
        route(departed, TODAY, "IN_PROGRESS", TODAY.atTime(7, 30), TODAY.atTime(7, 40));

        commuteTrigger.run(TODAY.atTime(7, 30));
        assertThat(count(early, "ROUTE_READY")).isZero();

        commuteTrigger.run(TODAY.atTime(7, 50));
        assertThat(count(early, "ROUTE_READY")).isEqualTo(1);
        assertThat(count(departed, "ROUTE_READY")).isZero();
    }

    @Test
    void 쉬는_날에는_출근_시각_기준_알림을_보내지_않는다() {
        Long userId = commuter(DayOfWeek.MONDAY, COMMUTE);

        commuteTrigger.run(TODAY.atTime(7, 50));

        assertThat(count(userId, "ROUTE_READY")).isZero();
    }

    @Test
    void 몇_분_늦게_돌아도_허용_시간_안이면_한_번만_보낸다() {
        Long userId = commuter(DayOfWeek.WEDNESDAY, COMMUTE);

        commuteTrigger.run(TODAY.atTime(7, 55));
        commuteTrigger.run(TODAY.atTime(7, 56));

        assertThat(count(userId, "ROUTE_READY")).isEqualTo(1);
    }

    @Test
    void 연속_3일_이상인데_오늘_완주하지_않았으면_출근_3시간_뒤_스트릭_이탈_알림을_보낸다() {
        Long userId = commuter(DayOfWeek.WEDNESDAY, COMMUTE);
        Long shortStreak = commuter(DayOfWeek.WEDNESDAY, COMMUTE);
        // 금·월·화 완주 = 3일 연속(주말은 건너뛴다)
        for (LocalDate day : List.of(TODAY.minusDays(5), TODAY.minusDays(2), TODAY.minusDays(1))) {
            route(userId, day, "COMPLETED", day.atTime(8, 0), day.atTime(8, 5));
        }
        route(shortStreak, TODAY.minusDays(1), "COMPLETED", TODAY.minusDays(1).atTime(8, 0), null);

        commuteTrigger.run(TODAY.atTime(11, 0));

        Map<String, Object> sent = notification(userId, "STREAK_BREAK");
        assertThat(sent.get("body")).isEqualTo("오늘의 노선이 아직 기다리고 있어요.");
        assertThat(count(shortStreak, "STREAK_BREAK")).isZero();
    }

    @Test
    void 오늘_완주했으면_스트릭_이탈_알림을_보내지_않는다() {
        Long userId = commuter(DayOfWeek.WEDNESDAY, COMMUTE);
        for (LocalDate day : List.of(TODAY.minusDays(5), TODAY.minusDays(2), TODAY.minusDays(1), TODAY)) {
            route(userId, day, "COMPLETED", day.atTime(8, 0), day.atTime(8, 5));
        }

        commuteTrigger.run(TODAY.atTime(11, 0));

        assertThat(count(userId, "STREAK_BREAK")).isZero();
    }

    @Test
    void 인기_기사는_같은_지역과_직군에서_30명_이상_담은_기사_중_읽지_않은_첫_기사를_보낸다() {
        Long first = article("1위 기사");
        Long second = article("2위 기사");
        for (int i = 0; i < 31; i++) {
            Long carter = member(GANGNAM, "IT_DATA");
            cart(carter, i < 30 ? List.of(first, second) : List.of(first));
        }
        Long userId = commuter(DayOfWeek.WEDNESDAY, COMMUTE);
        profile(userId, GANGNAM, "IT_DATA");
        read(userId, first);

        commuteTrigger.run(TODAY.atTime(8, 30));

        Map<String, Object> sent = notification(userId, "POPULAR_ARTICLE");
        assertThat(sent.get("title")).isEqualTo("2위 기사");
        assertThat(sent.get("body")).isEqualTo("지금 강남구 IT개발·데이터에서 많이 보고 있어요.");
        assertThat((String) sent.get("params")).contains("\"articleId\":\"" + second + "\"").contains("\"url\"");
    }

    @Test
    void 같은_지역_직군에_인기_기사가_없으면_직군으로_그다음_지역으로_넓힌다() {
        Long design = article("디자인 기사");
        for (int i = 0; i < 30; i++) {
            cart(member(GANGDONG, "DESIGN"), List.of(design));
        }
        Long sameJob = commuter(DayOfWeek.WEDNESDAY, COMMUTE);
        profile(sameJob, GANGNAM, "DESIGN");
        Long sameRegion = commuter(DayOfWeek.WEDNESDAY, COMMUTE);
        profile(sameRegion, GANGDONG, "MEDICAL");
        Long nobody = commuter(DayOfWeek.WEDNESDAY, COMMUTE);
        profile(nobody, GANGNAM, "MEDICAL");

        commuteTrigger.run(TODAY.atTime(8, 30));

        assertThat(notification(sameJob, "POPULAR_ARTICLE").get("body")).isEqualTo("오늘 디자인 직군에서 많이 보고 있어요.");
        assertThat(notification(sameRegion, "POPULAR_ARTICLE").get("body"))
            .isEqualTo("지금 강동구 출근길에서 많이 보고 있어요.");
        assertThat(count(nobody, "POPULAR_ARTICLE")).isZero();
    }

    @Test
    void 담은_사람이_30명보다_적은_기사는_인기_기사가_아니다() {
        Long few = article("덜 담은 기사");
        for (int i = 0; i < 29; i++) {
            cart(member(GANGNAM, "IT_DATA"), List.of(few));
        }
        Long userId = commuter(DayOfWeek.WEDNESDAY, COMMUTE);
        profile(userId, GANGNAM, "IT_DATA");

        commuteTrigger.run(TODAY.atTime(8, 30));

        assertThat(count(userId, "POPULAR_ARTICLE")).isZero();
    }

    @Test
    void 미완주_리마인드는_마지막_활동_120분_뒤에_남은_정거장_수와_함께_보낸다() {
        Long userId = member(null, null);
        Long routeId = route(userId, TODAY, "IN_PROGRESS", TODAY.atTime(8, 0), null);
        Long a = article("읽은 기사");
        Long b = article("남은 기사");
        card(routeId, a, 1, TODAY.atTime(8, 20));
        card(routeId, b, 2, null);
        Long completed = member(null, null);
        route(completed, TODAY, "COMPLETED", TODAY.atTime(8, 0), TODAY.atTime(8, 20));

        routeIncompleteTrigger.run(TODAY.atTime(10, 19));
        assertThat(count(userId, "ROUTE_INCOMPLETE")).isZero();

        routeIncompleteTrigger.run(TODAY.atTime(10, 20));
        Map<String, Object> sent = notification(userId, "ROUTE_INCOMPLETE");
        assertThat(sent.get("body")).isEqualTo("오늘의 노선, 도착까지 이어가볼까요?");
        assertThat(count(completed, "ROUTE_INCOMPLETE")).isZero();
    }

    @Test
    void 출발하지_않고_카드만_고른_노선도_생성_120분_뒤에_미완주_리마인드를_보낸다() {
        Long userId = member(null, null);
        jdbcTemplate.update("""
            INSERT INTO route (user_id, service_date, route_condition, target_minutes, status, fallback_step, expires_at,
                created_at, updated_at)
            VALUES (?, ?, 'SUNNY', 10, 'CREATED', 0, ?, ?, ?)
            """, userId, TODAY, TODAY.plusDays(1).atTime(4, 0), TODAY.atTime(7, 0), TODAY.atTime(7, 0));

        routeIncompleteTrigger.run(TODAY.atTime(9, 0));

        assertThat(count(userId, "ROUTE_INCOMPLETE")).isEqualTo(1);
    }

    @Test
    void 평일_완주로_연속_3일이_되면_연속_출근길_달성_알림을_보낸다() {
        Long userId = member(null, null);
        for (LocalDate day : List.of(TODAY.minusDays(2), TODAY.minusDays(1), TODAY)) {
            route(userId, day, "COMPLETED", day.atTime(8, 0), day.atTime(8, 5));
        }

        streakMilestoneListener.onRouteCompleted(new RouteCompletedEvent(userId, 1L, TODAY));

        Map<String, Object> sent = notification(userId, "STREAK_MILESTONE");
        assertThat((String) sent.get("title")).contains("3일 연속");
        assertThat(sent.get("body")).isEqualTo("최장 기록이에요!");
    }

    @Test
    void 최장_기록보다_짧으면_남은_일수를_알려_주고_달성일이_아니면_보내지_않는다() {
        Long userId = member(null, null);
        // 2주 전 월~금 5일 연속, 지난주는 쉬고, 이번 주 월~수 3일 연속
        LocalDate mondayTwoWeeksAgo = TODAY.minusDays(16);
        for (int i = 0; i < 5; i++) {
            LocalDate day = mondayTwoWeeksAgo.plusDays(i);
            route(userId, day, "COMPLETED", day.atTime(8, 0), day.atTime(8, 5));
        }
        for (LocalDate day : List.of(TODAY.minusDays(2), TODAY.minusDays(1), TODAY)) {
            route(userId, day, "COMPLETED", day.atTime(8, 0), day.atTime(8, 5));
        }
        Long notMilestone = member(null, null);
        for (LocalDate day : List.of(TODAY.minusDays(1), TODAY)) {
            route(notMilestone, day, "COMPLETED", day.atTime(8, 0), day.atTime(8, 5));
        }

        streakMilestoneListener.onRouteCompleted(new RouteCompletedEvent(userId, 1L, TODAY));
        streakMilestoneListener.onRouteCompleted(new RouteCompletedEvent(notMilestone, 2L, TODAY));

        assertThat(notification(userId, "STREAK_MILESTONE").get("body")).isEqualTo("최장 기록 5일까지 2일 남았어요.");
        assertThat(count(notMilestone, "STREAK_MILESTONE")).isZero();
    }

    @Test
    void 스몰토크_알림은_오늘_목록의_첫_번째를_보낸다() {
        Long smalltalkId = jdbcTemplate.queryForObject("""
            INSERT INTO smalltalk (article_id, service_date, hook, visibility) VALUES (?, ?, ?, 'VISIBLE') RETURNING id
            """, Long.class, article("스몰토크 기사"), TODAY, "\"기준금리 동결됐다던데, 혹시 대출 있으세요?\"");
        Long userId = member(null, null);

        smalltalkTrigger.run(TODAY);

        Map<String, Object> sent = notification(userId, "SMALLTALK");
        assertThat(sent.get("body")).isEqualTo("\"기준금리 동결됐다던데, 혹시 대출 있으세요?\"");
        assertThat(sent.get("params")).isEqualTo("{\"smalltalkId\":\"" + smalltalkId + "\"}");
    }

    // 온보딩을 마친 회원. 근무 지역·직군은 null이면 고르지 않은 것이다
    private Long member(Integer sigunguId, String jobGroup) {
        User user = userRepository.saveAndFlush(User.signUp(SocialProvider.KAKAO, UUID.randomUUID().toString()));
        // 아래에서 SQL로 바꾸는 값을 알림 작업이 읽도록 영속성 컨텍스트에서 뗀다
        entityManager.detach(user);
        Long userId = user.getId();
        jdbcTemplate.update("UPDATE users SET onboarding_status = 'COMPLETED', onboarded_at = ? WHERE id = ?",
            LocalDateTime.now(), userId);
        profile(userId, sigunguId, jobGroup);
        return userId;
    }

    // 그 요일에만 출근 시각을 둔 회원. 다른 요일은 쉬는 날이다
    private Long commuter(DayOfWeek day, LocalTime startTime) {
        Long userId = member(null, null);
        String prefix = day.name().substring(0, 3).toLowerCase();
        jdbcTemplate.update("UPDATE users SET " + prefix + "_start_time = ?, " + prefix + "_minutes = 30 WHERE id = ?",
            startTime, userId);
        return userId;
    }

    private void profile(Long userId, Integer sigunguId, String jobGroup) {
        if (sigunguId != null) {
            jdbcTemplate.update("UPDATE users SET region_sigungu_id = ? WHERE id = ?", sigunguId, userId);
        }
        if (jobGroup != null) {
            jdbcTemplate.update("INSERT INTO user_job_group (user_id, job_group, sort_order) VALUES (?, ?, 1)",
                userId, jobGroup);
        }
    }

    private Long route(Long userId, LocalDate serviceDate, String status, LocalDateTime startedAt,
        LocalDateTime completedAt) {
        LocalDateTime createdAt = startedAt.minusMinutes(10);
        return jdbcTemplate.queryForObject("""
            INSERT INTO route (user_id, service_date, route_condition, target_minutes, status, fallback_step, expires_at,
                boarded_at, started_at, completed_at, created_at, updated_at)
            VALUES (?, ?, 'SUNNY', 10, ?, 0, ?, ?, ?, ?, ?, ?)
            RETURNING id
            """, Long.class, userId, serviceDate, status, serviceDate.plusDays(1).atTime(4, 0), createdAt, startedAt,
            completedAt, createdAt, createdAt);
    }

    // 오늘 노선에 기사를 담은 회원
    private void cart(Long userId, List<Long> articleIds) {
        Long routeId = route(userId, TODAY, "IN_PROGRESS", TODAY.atTime(7, 0), null);
        for (int i = 0; i < articleIds.size(); i++) {
            card(routeId, articleIds.get(i), i + 1, null);
        }
    }

    private void card(Long routeId, Long articleId, int order, LocalDateTime readAt) {
        jdbcTemplate.update("""
            INSERT INTO route_card (route_id, article_id, pool_order, difficulty, reading_minutes, recommended,
                cart_order, carted_at, read_at)
            VALUES (?, ?, ?, 'EASY', 2.0, TRUE, ?, ?, ?)
            """, routeId, articleId, order, order, TODAY.atTime(7, 0), readAt);
    }

    // 예전 노선에서 [기사 보러가기]로 읽은 기사
    private void read(Long userId, Long articleId) {
        Long routeId = route(userId, TODAY.minusDays(1), "COMPLETED", TODAY.minusDays(1).atTime(8, 0),
            TODAY.minusDays(1).atTime(8, 10));
        card(routeId, articleId, 1, TODAY.minusDays(1).atTime(8, 5));
    }

    private Long article(String title) {
        return jdbcTemplate.queryForObject("""
            INSERT INTO article (feed_id, category_id, original_url, title, press_name, published_at, service_date,
                difficulty, reading_minutes, summary_sentence1, summary_sentence2, summary_sentence3, summary_one_line,
                process_status, recommendable, visibility)
            VALUES (?, ?, ?, ?, '테스트일보', ?, ?, 'EASY', 2.0, '첫 문장', '둘째 문장', '셋째 문장', '한 줄 요약',
                'READY', TRUE, 'VISIBLE')
            RETURNING id
            """, Long.class, feedId, categoryId, "https://news.example.com/" + UUID.randomUUID(), title,
            TODAY.atTime(3, 0), TODAY);
    }

    private Map<String, Object> notification(Long userId, String type) {
        return jdbcTemplate.queryForMap("SELECT title, body, params FROM notification WHERE user_id = ? AND type = ?",
            userId, type);
    }

    private int count(Long userId, String type) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM notification WHERE user_id = ? AND type = ?",
            Integer.class, userId, type);
    }
}
