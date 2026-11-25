package com.byarrival.domain.admin.repository;

import com.byarrival.domain.admin.dto.request.AdminMemberSearchRequest;
import com.byarrival.domain.user.entity.SocialProvider;
import com.byarrival.domain.user.entity.TravelType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 어드민 회원 목록 조회(MEMBER-01). 탈퇴 회원은 제외하고 화면에 필요한 값만 읽는다.
 */
@Repository
@RequiredArgsConstructor
public class AdminMemberRepository {

    private static final int MAX_ID_DIGITS = 18;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public long count(AdminMemberSearchRequest search) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users u " + where(search, params), params,
            Long.class);
        return total == null ? 0 : total;
    }

    public List<MemberRow> findPage(AdminMemberSearchRequest search, int limit, long offset) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = "SELECT u.id, u.nickname, u.provider, u.travel_type, u.created_at FROM users u "
            + where(search, params) + "ORDER BY u.created_at DESC, u.id DESC LIMIT :limit OFFSET :offset";
        params.addValue("limit", limit).addValue("offset", offset);
        return jdbcTemplate.query(sql, params, (rs, rowNum) -> {
            String travelType = rs.getString("travel_type");
            return new MemberRow(rs.getLong("id"), rs.getString("nickname"),
                SocialProvider.valueOf(rs.getString("provider")),
                travelType == null ? null : TravelType.valueOf(travelType),
                rs.getObject("created_at", LocalDateTime.class));
        });
    }

    private static String where(AdminMemberSearchRequest search, MapSqlParameterSource params) {
        List<String> conditions = new ArrayList<>(List.of("u.status = 'ACTIVE'"));
        if (search.keyword() != null && !search.keyword().isBlank()) {
            String keyword = search.keyword().strip();
            params.addValue("keyword", "%" + escapeLike(keyword) + "%");
            String memberNumber = keyword.startsWith("#") ? keyword.substring(1) : keyword;
            if (memberNumber.matches("\\d{1," + MAX_ID_DIGITS + "}")) {
                conditions.add("(u.nickname ILIKE :keyword ESCAPE '\\' OR u.id = :memberId)");
                params.addValue("memberId", Long.valueOf(memberNumber));
            } else {
                conditions.add("u.nickname ILIKE :keyword ESCAPE '\\'");
            }
        }
        if (search.travelType() != null) {
            conditions.add("u.travel_type = :travelType");
            params.addValue("travelType", search.travelType().name());
        }
        if (search.provider() != null) {
            conditions.add("u.provider = :provider");
            params.addValue("provider", search.provider().name());
        }
        return "WHERE " + String.join(" AND ", conditions) + " ";
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public record MemberRow(Long id, String nickname, SocialProvider provider, TravelType travelType,
                            LocalDateTime joinedAt) {
    }
}
