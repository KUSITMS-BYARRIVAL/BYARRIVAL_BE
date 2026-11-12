package com.byarrival.domain.route;

import static org.assertj.core.api.Assertions.assertThat;

import com.byarrival.domain.route.repository.CartedArticleCount;
import com.byarrival.domain.route.service.PopularArticleService;
import com.byarrival.domain.route.service.PopularArticleService.PopularArticles;
import com.byarrival.domain.user.entity.SocialProvider;
import com.byarrival.domain.user.entity.User;
import com.byarrival.domain.user.repository.UserRepository;
import com.byarrival.support.TestcontainersConfig;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 많이 담은 뉴스 순위(ARR-02, HOME-05 기준)를 실제 DB로 확인한다. 테스트마다 롤백해 다른 테스트의 사용자·카드가 집계에 섞이지 않게 한다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@Transactional
class PopularArticleIntegrationTest {

    private static final int MIN_USERS = 30;

    @Autowired
    private PopularArticleService popularArticleService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long feedId;
    private Long categoryId;
    private LocalDate serviceDate;

    @BeforeEach
    void setUp() {
        feedId = jdbcTemplate.queryForObject(
            "INSERT INTO rss_feed (press_name, feed_url) VALUES ('테스트', ?) RETURNING id", Long.class,
            "https://example.com/" + UUID.randomUUID());
        categoryId = jdbcTemplate.queryForObject(
            "INSERT INTO category (name, display_order) VALUES (?, 99) RETURNING id", Long.class,
            "테스트-" + UUID.randomUUID().toString().substring(0, 8));
        // 다른 테스트 클래스가 커밋한 노선과 겹치지 않는 먼 서비스일
        serviceDate = LocalDate.of(2030, 1, 1);
    }

    @Test
    void 담은_사용자가_최소_인원보다_적으면_집계중이다() {
        Long article = newArticle("VISIBLE", 10);
        for (int i = 0; i < MIN_USERS - 1; i++) {
            newRoute(List.of(article), List.of());
        }

        PopularArticles popular = popularArticleService.mostCarted(serviceDate, Set.of(), 3);

        assertThat(popular.ranked()).isFalse();
        assertThat(popular.articles()).isEmpty();
    }

    @Test
    void 담은_사용자_수가_많은_순으로_읽은_기사와_숨긴_기사를_빼고_채운다() {
        Long most = newArticle("VISIBLE", 10);
        Long older = newArticle("VISIBLE", 12);
        Long newer = newArticle("VISIBLE", 2);
        Long fewer = newArticle("VISIBLE", 10);
        Long last = newArticle("VISIBLE", 10);
        Long hidden = newArticle("HIDDEN", 10);
        Long removed = newArticle("VISIBLE", 10);
        // 30명이 most를 담고, 그중 일부가 다른 기사도 담는다. removed는 담았다가 뺐다
        for (int i = 0; i < MIN_USERS; i++) {
            List<Long> carted = new ArrayList<>(List.of(most));
            if (i < 20) {
                carted.addAll(List.of(older, newer, hidden));
            }
            if (i < 10) {
                carted.add(fewer);
            }
            if (i < 5) {
                carted.add(last);
            }
            newRoute(carted, List.of(removed));
        }

        PopularArticles popular = popularArticleService.mostCarted(serviceDate, Set.of(most), 3);

        assertThat(popular.ranked()).isTrue();
        // 동률(20명)이면 최신 기사가 먼저다
        assertThat(popular.articles()).extracting(CartedArticleCount::getArticleId).containsExactly(newer, older, fewer);
        assertThat(popular.articles()).extracting(CartedArticleCount::getUserCount).containsExactly(20L, 20L, 10L);
    }

    // 사용자 한 명의 그날 노선. carted는 담은 카드, removed는 담았다 뺀 카드
    private void newRoute(List<Long> carted, List<Long> removed) {
        Long userId = userRepository.save(User.signUp(SocialProvider.KAKAO, UUID.randomUUID().toString())).getId();
        Long routeId = jdbcTemplate.queryForObject("""
            INSERT INTO route (user_id, service_date, route_condition, target_minutes, status, fallback_step, expires_at)
            VALUES (?, ?, 'SUNNY', 10, 'IN_PROGRESS', 0, ?)
            RETURNING id
            """, Long.class, userId, serviceDate, serviceDate.plusDays(1).atTime(4, 0));
        int order = 0;
        for (Long articleId : carted) {
            order++;
            jdbcTemplate.update("""
                INSERT INTO route_card (route_id, article_id, pool_order, difficulty, reading_minutes, recommended,
                    cart_order, carted_at)
                VALUES (?, ?, ?, 'EASY', 2.0, TRUE, ?, ?)
                """, routeId, articleId, order, order, serviceDate.atTime(8, 0));
        }
        for (Long articleId : removed) {
            order++;
            jdbcTemplate.update("""
                INSERT INTO route_card (route_id, article_id, pool_order, difficulty, reading_minutes, recommended)
                VALUES (?, ?, ?, 'EASY', 2.0, TRUE)
                """, routeId, articleId, order);
        }
    }

    private Long newArticle(String visibility, int hoursBeforeServiceDay) {
        return jdbcTemplate.queryForObject("""
            INSERT INTO article (feed_id, category_id, original_url, title, press_name, published_at, service_date,
                difficulty, reading_minutes, summary_sentence1, summary_sentence2, summary_sentence3, summary_one_line,
                process_status, recommendable, visibility)
            VALUES (?, ?, ?, '테스트 기사', '테스트일보', ?, ?, 'EASY', 2.0, '첫 문장', '둘째 문장', '셋째 문장', '한 줄 요약',
                'READY', TRUE, ?)
            RETURNING id
            """, Long.class, feedId, categoryId, "https://news.example.com/" + UUID.randomUUID(),
            serviceDate.atTime(4, 0).minusHours(hoursBeforeServiceDay), serviceDate, visibility);
    }
}
