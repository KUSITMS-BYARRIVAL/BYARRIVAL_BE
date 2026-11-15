package com.byarrival.domain.category;

import static org.assertj.core.api.Assertions.assertThat;

import com.byarrival.support.TestcontainersConfig;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/**
 * 서비스 카테고리 seed 마이그레이션(V10)을 직접 다시 실행해 결과를 확인한다.
 * 다른 테스트가 category를 지울 수 있어 Flyway가 넣은 행 대신 스크립트를 실행한다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
class CategorySeedMigrationTest {

    private static final List<String> SERVICE_CATEGORIES =
        List.of("경제·금융", "건강·생활", "연예·문화", "정치·사회", "과학·환경", "IT·기술", "세계·국제", "스포츠");

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void clear() {
        // Feed 매핑(V12)이 카테고리를 참조하므로 먼저 지운다
        jdbcTemplate.update("DELETE FROM category_mapping");
        jdbcTemplate.update("DELETE FROM category");
    }

    @Test
    void 서비스_카테고리_8개를_화면_순서대로_활성_상태로_넣는다() {
        runSeed();

        assertThat(activeNamesInOrder()).containsExactlyElementsOf(SERVICE_CATEGORIES);
    }

    @Test
    void 다시_실행해도_중복되지_않고_순서와_활성_상태를_바로잡는다() {
        jdbcTemplate.update("INSERT INTO category (name, display_order, active) VALUES ('스포츠', 1, FALSE)");

        runSeed();
        runSeed();

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM category", Integer.class)).isEqualTo(8);
        assertThat(activeNamesInOrder()).containsExactlyElementsOf(SERVICE_CATEGORIES);
    }

    @Test
    void 서비스_카테고리가_아닌_기존_카테고리는_지우지_않고_비활성화한다() {
        jdbcTemplate.update("INSERT INTO category (name, display_order, active) VALUES ('경제', 1, TRUE)");

        runSeed();

        assertThat(jdbcTemplate.queryForObject(
            "SELECT active FROM category WHERE name = '경제'", Boolean.class)).isFalse();
        assertThat(activeNamesInOrder()).containsExactlyElementsOf(SERVICE_CATEGORIES);
    }

    private void runSeed() {
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V10__insert_service_categories.sql"))
            .execute(dataSource);
    }

    private List<String> activeNamesInOrder() {
        return jdbcTemplate.queryForList(
            "SELECT name FROM category WHERE active ORDER BY display_order", String.class);
    }
}
