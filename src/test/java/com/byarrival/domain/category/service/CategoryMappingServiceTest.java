package com.byarrival.domain.category.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.byarrival.support.TestcontainersConfig;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfig.class)
class CategoryMappingServiceTest {

    @Autowired
    private CategoryMappingService categoryMappingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM category");
    }

    @Test
    void 활성_카테고리_이름을_화면_순서대로_돌려준다() {
        Long society = insertCategory("사회", 2, true);
        Long economy = insertCategory("경제", 1, true);
        insertCategory("폐지", 3, false);

        Map<Long, String> names = categoryMappingService.getActiveCategoryNames();

        assertThat(names).containsExactly(Map.entry(economy, "경제"), Map.entry(society, "사회"));
    }

    private Long insertCategory(String name, int displayOrder, boolean active) {
        return jdbcTemplate.queryForObject(
            "INSERT INTO category (name, display_order, active) VALUES (?, ?, ?) RETURNING id", Long.class, name,
            displayOrder, active);
    }
}
