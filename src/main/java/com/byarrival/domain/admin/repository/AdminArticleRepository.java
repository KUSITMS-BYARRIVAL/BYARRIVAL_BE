package com.byarrival.domain.admin.repository;

import com.byarrival.domain.admin.dto.request.AdminArticleSearchRequest;
import com.byarrival.domain.admin.entity.AdminArticleState;
import com.byarrival.domain.admin.entity.ArticleHideReason;
import com.byarrival.domain.admin.entity.ArticleVisibilityAction;
import com.byarrival.domain.article.entity.ArticleDifficulty;
import com.byarrival.domain.article.entity.ArticleProcessStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 어드민 기사 화면 조회. 기사·퀴즈·노선 담기·배치 기록을 함께 봐야 하고 visibility가 엔티티에 없어 SQL로 읽는다.
 */
@Repository
@RequiredArgsConstructor
public class AdminArticleRepository {

    private static final String HAS_QUIZ = "EXISTS (SELECT 1 FROM quiz q WHERE q.article_id = a.id AND q.status = 'ACTIVE')";
    private static final String STATE = """
        CASE
            WHEN a.visibility = 'HIDDEN' THEN 'HIDDEN'
            WHEN a.visibility = 'DELETED' THEN 'ERROR'
            WHEN a.duplicate_of_article_id IS NOT NULL THEN 'DUPLICATE'
            WHEN EXISTS (SELECT 1 FROM batch_run b WHERE b.service_date = a.service_date AND b.status = 'RUNNING')
                THEN CASE WHEN a.process_status = 'READY' AND a.recommendable AND %1$s
                    THEN 'EXPOSED' ELSE 'PROCESSING' END
            WHEN a.process_status <> 'READY' THEN 'ERROR'
            WHEN NOT a.recommendable THEN 'PROCESSING'
            WHEN NOT %1$s THEN 'ERROR'
            ELSE 'EXPOSED'
        END
        """.formatted(HAS_QUIZ);
    private static final String ROW_COLUMNS = """
        a.id, a.title, a.press_name, a.category_id, c.name AS category_name, a.difficulty, a.process_status,
        a.created_at, a.summary_sentence1 IS NOT NULL AS has_summary, %s AS has_quiz, %s AS state,
        (SELECT COUNT(DISTINCT r.user_id) FROM route_card rc JOIN route r ON r.id = rc.route_id
            WHERE rc.article_id = a.id AND rc.cart_order IS NOT NULL) AS view_count
        """.formatted(HAS_QUIZ, STATE);

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public Map<AdminArticleState, Long> countByState(AdminArticleSearchRequest search) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = "SELECT state, COUNT(*) AS article_count FROM (SELECT " + STATE + " AS state FROM article a "
            + where(search, params) + ") x GROUP BY state";
        Map<AdminArticleState, Long> counts = new EnumMap<>(AdminArticleState.class);
        jdbcTemplate.query(sql, params, rs -> {
            counts.put(AdminArticleState.valueOf(rs.getString("state")), rs.getLong("article_count"));
        });
        return counts;
    }

    public List<ArticleRow> findPage(AdminArticleSearchRequest search, int limit, long offset) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String inner = "SELECT a.id, " + STATE + " AS state, a.created_at FROM article a " + where(search, params);
        String tabCondition = "";
        if (search.tab().state() != null) {
            tabCondition = "WHERE x.state = :tabState ";
            params.addValue("tabState", search.tab().state().name());
        }
        String sql = "SELECT " + ROW_COLUMNS + "FROM (SELECT x.id, x.created_at FROM (" + inner + ") x " + tabCondition
            + "ORDER BY x.created_at DESC, x.id DESC LIMIT :limit OFFSET :offset) p "
            + "JOIN article a ON a.id = p.id LEFT JOIN category c ON c.id = a.category_id "
            + "ORDER BY p.created_at DESC, p.id DESC";
        params.addValue("limit", limit).addValue("offset", offset);
        return jdbcTemplate.query(sql, params, (rs, rowNum) -> toRow(rs));
    }

    public Optional<ArticleRow> findRow(Long articleId) {
        String sql = "SELECT " + ROW_COLUMNS + "FROM article a LEFT JOIN category c ON c.id = a.category_id "
            + "WHERE a.id = :id";
        return jdbcTemplate.query(sql, Map.of("id", articleId), (rs, rowNum) -> toRow(rs)).stream().findFirst();
    }

    public List<String> findPressNames() {
        return jdbcTemplate.queryForList("SELECT DISTINCT press_name FROM article ORDER BY press_name", Map.of(),
            String.class);
    }

    public Optional<QuizRow> findActiveQuiz(Long articleId) {
        String sql = "SELECT question, choices::text AS choices, answer_seq, explanation FROM quiz "
            + "WHERE article_id = :id AND status = 'ACTIVE' ORDER BY id DESC LIMIT 1";
        return jdbcTemplate.query(sql, Map.of("id", articleId), (rs, rowNum) -> new QuizRow(rs.getString("question"),
            rs.getString("choices"), rs.getInt("answer_seq"), rs.getString("explanation"))).stream().findFirst();
    }

    public List<HistoryRow> findHistory(Long articleId) {
        String sql = "SELECT h.action, h.reason, h.memo, h.created_at, ad.email FROM article_visibility_history h "
            + "JOIN admin_account ad ON ad.id = h.admin_id WHERE h.article_id = :id ORDER BY h.id DESC";
        return jdbcTemplate.query(sql, Map.of("id", articleId), (rs, rowNum) -> {
            String reason = rs.getString("reason");
            return new HistoryRow(ArticleVisibilityAction.valueOf(rs.getString("action")),
                reason == null ? null : ArticleHideReason.valueOf(reason), rs.getString("memo"),
                rs.getString("email"), rs.getObject("created_at", LocalDateTime.class));
        });
    }

    public Optional<String> findVisibility(Long articleId) {
        return jdbcTemplate.queryForList("SELECT visibility FROM article WHERE id = :id", Map.of("id", articleId),
            String.class).stream().findFirst();
    }

    /**
     * 조건에 맞는 기사만 한 문장으로 바꾼다. 같은 요청이 겹쳐도 실제로 바뀐 기사만 돌려주므로 이력이 중복되지 않는다.
     *
     * @return 실제로 바뀐 기사 ID
     */
    public List<Long> changeVisibility(Collection<Long> articleIds, String from, String to, LocalDateTime now) {
        String sql = "UPDATE article SET visibility = :to, updated_at = :now "
            + "WHERE id IN (:ids) AND visibility = :from RETURNING id";
        return jdbcTemplate.queryForList(sql, new MapSqlParameterSource()
            .addValue("to", to)
            .addValue("from", from)
            .addValue("now", now)
            .addValue("ids", articleIds), Long.class);
    }

    private static String where(AdminArticleSearchRequest search, MapSqlParameterSource params) {
        List<String> conditions = new ArrayList<>();
        if (search.keyword() != null && !search.keyword().isBlank()) {
            conditions.add("a.title ILIKE :keyword ESCAPE '\\'");
            params.addValue("keyword", "%" + escapeLike(search.keyword().strip()) + "%");
        }
        if (search.pressName() != null && !search.pressName().isBlank()) {
            conditions.add("a.press_name = :pressName");
            params.addValue("pressName", search.pressName().strip());
        }
        if (search.categoryId() != null) {
            conditions.add("a.category_id = :categoryId");
            params.addValue("categoryId", search.categoryId());
        }
        if (search.difficulty() != null) {
            conditions.add("a.difficulty = :difficulty");
            params.addValue("difficulty", search.difficulty().name());
        }
        if (search.processStatus() != null) {
            conditions.add("a.process_status = :processStatus");
            params.addValue("processStatus", search.processStatus().name());
        }
        if (search.collectedFrom() != null) {
            conditions.add("a.created_at >= :collectedFrom");
            params.addValue("collectedFrom", search.collectedFrom().atStartOfDay());
        }
        if (search.collectedTo() != null) {
            conditions.add("a.created_at < :collectedBefore");
            params.addValue("collectedBefore", search.collectedTo().plusDays(1).atStartOfDay());
        }
        return conditions.isEmpty() ? "" : "WHERE " + String.join(" AND ", conditions) + " ";
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static ArticleRow toRow(ResultSet rs) throws SQLException {
        String difficulty = rs.getString("difficulty");
        return new ArticleRow(rs.getLong("id"), rs.getString("title"), rs.getString("press_name"),
            rs.getObject("category_id", Long.class), rs.getString("category_name"),
            difficulty == null ? null : ArticleDifficulty.valueOf(difficulty),
            ArticleProcessStatus.valueOf(rs.getString("process_status")),
            rs.getObject("created_at", LocalDateTime.class), rs.getBoolean("has_summary"), rs.getBoolean("has_quiz"),
            AdminArticleState.valueOf(rs.getString("state")), rs.getLong("view_count"));
    }

    public record ArticleRow(Long id, String title, String pressName, Long categoryId, String categoryName,
                             ArticleDifficulty difficulty, ArticleProcessStatus processStatus,
                             LocalDateTime collectedAt, boolean hasSummary, boolean hasQuiz, AdminArticleState state,
                             long viewCount) {
    }

    public record QuizRow(String question, String choicesJson, int answerSeq, String explanation) {
    }

    public record HistoryRow(ArticleVisibilityAction action, ArticleHideReason reason, String memo, String adminEmail,
                             LocalDateTime createdAt) {
    }
}
