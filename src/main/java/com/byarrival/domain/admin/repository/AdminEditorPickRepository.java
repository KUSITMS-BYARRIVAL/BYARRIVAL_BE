package com.byarrival.domain.admin.repository;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 에디터 픽에 연결할 기사 후보(EDITOR-03·05). 추천 후보이고 숨기지 않았으며 아직 스몰토크가 없는 기사만 읽는다.
 */
@Repository
@RequiredArgsConstructor
public class AdminEditorPickRepository {

    static final int MAX_LINKABLE = 100;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public List<LinkableRow> findLinkable(LocalDate today, LocalDate serviceDate, String keyword) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("dates", List.of(today, serviceDate))
            .addValue("limit", MAX_LINKABLE);
        String keywordCondition = "";
        if (keyword != null && !keyword.isBlank()) {
            keywordCondition = "AND a.title ILIKE :keyword ESCAPE '\\' ";
            params.addValue("keyword", "%" + escapeLike(keyword.strip()) + "%");
        }
        String sql = "SELECT a.id, a.title, a.press_name, a.category_id, a.service_date FROM article a "
            + "WHERE a.service_date IN (:dates) AND a.recommendable AND a.visibility = 'VISIBLE' "
            + "AND a.category_id IS NOT NULL "
            + "AND NOT EXISTS (SELECT 1 FROM smalltalk s WHERE s.article_id = a.id) "
            + keywordCondition
            + "ORDER BY a.service_date DESC, a.category_id, a.id LIMIT :limit";
        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new LinkableRow(rs.getLong("id"),
            rs.getString("title"), rs.getString("press_name"), rs.getLong("category_id"),
            rs.getObject("service_date", LocalDate.class)));
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public record LinkableRow(Long id, String title, String pressName, Long categoryId, LocalDate serviceDate) {
    }
}
