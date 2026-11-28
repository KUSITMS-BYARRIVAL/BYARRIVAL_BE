package com.byarrival.domain.term.repository;

import com.byarrival.domain.term.entity.TermsType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 약관 종류별 현재 동의 상태와 수신 동의 재확인 안내 기록. 약관 버전과 상관없이 그 종류의 가장 최근 이력이 현재 상태다.
 */
@Repository
@RequiredArgsConstructor
public class MarketingConsentRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public List<ConsentRow> findLatest(Long userId, Collection<TermsType> types) {
        String sql = "SELECT DISTINCT ON (t.terms_type) t.terms_type, a.agreed, a.agreed_at "
            + "FROM user_terms_agreement a JOIN terms t ON t.id = a.terms_id "
            + "WHERE a.user_id = :userId AND t.terms_type IN (:types) "
            + "ORDER BY t.terms_type, a.agreed_at DESC, a.id DESC";
        MapSqlParameterSource params = new MapSqlParameterSource("userId", userId)
            .addValue("types", types.stream().map(Enum::name).toList());
        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new ConsentRow(
            TermsType.valueOf(rs.getString("terms_type")), rs.getBoolean("agreed"),
            rs.getObject("agreed_at", LocalDateTime.class)));
    }

    /**
     * 광고성 정보 수신에 동의한 상태이고, 마지막 동의와 마지막 재확인 안내 중 늦은 시각이 before 이전인 활동 회원.
     */
    public List<ReconfirmTarget> findReconfirmTargets(LocalDateTime before, int limit) {
        String sql = "SELECT x.user_id, x.agreed_at FROM ("
            + "SELECT DISTINCT ON (a.user_id) a.user_id, a.agreed, a.agreed_at FROM user_terms_agreement a "
            + "JOIN terms t ON t.id = a.terms_id WHERE t.terms_type = 'MARKETING' "
            + "ORDER BY a.user_id, a.agreed_at DESC, a.id DESC) x "
            + "JOIN users u ON u.id = x.user_id AND u.status = 'ACTIVE' "
            + "LEFT JOIN marketing_consent_reconfirm r ON r.user_id = x.user_id "
            + "WHERE x.agreed AND GREATEST(x.agreed_at, COALESCE(r.notified_at, x.agreed_at)) <= :before "
            + "ORDER BY x.user_id LIMIT :limit";
        MapSqlParameterSource params = new MapSqlParameterSource("before", before).addValue("limit", limit);
        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new ReconfirmTarget(rs.getLong("user_id"),
            rs.getObject("agreed_at", LocalDateTime.class)));
    }

    /**
     * 재확인 안내 시각을 남긴다. 다른 실행(블루/그린 전환 중 다른 서버)이 이미 남겼으면 남기지 않는다.
     *
     * @param before 이보다 늦게 안내한 기록이 있으면 이미 안내한 것으로 본다
     * @return 이번에 남겼으면 true
     */
    public boolean markReconfirmed(Long userId, LocalDateTime notifiedAt, LocalDateTime before) {
        int updated = jdbcTemplate.update("INSERT INTO marketing_consent_reconfirm (user_id, notified_at) "
                + "VALUES (:userId, :at) ON CONFLICT (user_id) DO UPDATE SET notified_at = EXCLUDED.notified_at "
                + "WHERE marketing_consent_reconfirm.notified_at <= :before",
            new MapSqlParameterSource("userId", userId).addValue("at", notifiedAt).addValue("before", before));
        return updated > 0;
    }

    public record ConsentRow(TermsType type, boolean agreed, LocalDateTime agreedAt) {
    }

    public record ReconfirmTarget(Long userId, LocalDateTime agreedAt) {
    }
}
