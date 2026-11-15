package com.byarrival.domain.category;

import static org.assertj.core.api.Assertions.assertThat;

import com.byarrival.support.TestcontainersConfig;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/**
 * 구글 뉴스 섹션 Feed 매핑 마이그레이션(V12)을 직접 다시 실행해 결과를 확인한다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
class GoogleNewsFeedMappingMigrationTest {

    private static final String URL_FORMAT =
        "https://news.google.com/rss/headlines/section/topic/%s?hl=ko&gl=KR&ceid=KR:ko";
    private static final Map<String, String> EXPECTED = Map.of(
        "BUSINESS", "경제·금융",
        "HEALTH", "건강·생활",
        "ENTERTAINMENT", "연예·문화",
        "NATION", "정치·사회",
        "SCIENCE", "과학·환경",
        "TECHNOLOGY", "IT·기술",
        "WORLD", "세계·국제",
        "SPORTS", "스포츠");

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // 다른 테스트가 category·rss_feed를 비울 수 있어 카테고리와 Feed를 먼저 다시 넣는다
        run("V10__insert_service_categories.sql");
        run("V12__insert_google_news_feed_mapping.sql");
        jdbcTemplate.update("DELETE FROM category_mapping WHERE feed_id IN "
            + "(SELECT id FROM rss_feed WHERE feed_url LIKE 'https://news.google.com/rss/headlines/section/topic/%')");
    }

    @Test
    void 구글_섹션_Feed_8개를_각_서비스_카테고리에_매핑한다() {
        run("V12__insert_google_news_feed_mapping.sql");

        EXPECTED.forEach((topic, categoryName) -> assertThat(mappedCategories(topic)).containsExactly(categoryName));
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(DISTINCT m.category_id) FROM category_mapping m JOIN category c ON c.id = m.category_id "
                + "WHERE c.active", Integer.class)).isEqualTo(8);
    }

    @Test
    void 옛_카테고리에_매핑된_기존_Feed는_rss_category를_두고_새_카테고리로_옮긴다() {
        Long oldCategoryId = jdbcTemplate.queryForObject(
            "INSERT INTO category (name, display_order, active) VALUES ('경제', 99, FALSE) "
                + "ON CONFLICT (name) DO UPDATE SET active = FALSE RETURNING id", Long.class);
        Long feedId = feedId("BUSINESS");
        String rssCategory = jdbcTemplate.queryForObject(
            "SELECT rss_category FROM rss_feed WHERE id = ?", String.class, feedId);
        jdbcTemplate.update("INSERT INTO category_mapping (feed_id, rss_category, category_id) VALUES (?, ?, ?)",
            feedId, rssCategory, oldCategoryId);

        run("V12__insert_google_news_feed_mapping.sql");
        run("V12__insert_google_news_feed_mapping.sql");

        assertThat(mappedCategories("BUSINESS")).containsExactly("경제·금융");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT rss_category FROM rss_feed WHERE id = ?", String.class, feedId)).isEqualTo(rssCategory);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM rss_feed WHERE feed_url = ?", Integer.class, URL_FORMAT.formatted("BUSINESS")))
            .isEqualTo(1);
    }

    @Test
    void rss_category가_비어_있는_기존_Feed는_채워서_매핑한다() {
        jdbcTemplate.update("UPDATE rss_feed SET rss_category = NULL WHERE id = ?", feedId("SPORTS"));

        run("V12__insert_google_news_feed_mapping.sql");

        assertThat(jdbcTemplate.queryForObject(
            "SELECT rss_category FROM rss_feed WHERE id = ?", String.class, feedId("SPORTS"))).isEqualTo("SPORTS");
        assertThat(mappedCategories("SPORTS")).containsExactly("스포츠");
    }

    private Long feedId(String topic) {
        return jdbcTemplate.queryForObject(
            "SELECT id FROM rss_feed WHERE feed_url = ?", Long.class, URL_FORMAT.formatted(topic));
    }

    private List<String> mappedCategories(String topic) {
        return jdbcTemplate.queryForList("""
            SELECT c.name FROM category_mapping m
            JOIN rss_feed f ON f.id = m.feed_id AND f.rss_category = m.rss_category
            JOIN category c ON c.id = m.category_id
            WHERE f.feed_url = ?
            """, String.class, URL_FORMAT.formatted(topic));
    }

    private void run(String script) {
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/" + script)).execute(dataSource);
    }
}
