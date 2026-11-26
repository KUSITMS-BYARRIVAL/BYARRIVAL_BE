package com.byarrival.domain.admin.repository;

import com.byarrival.domain.admin.dto.request.AdminMemberSearchRequest;
import com.byarrival.domain.admin.entity.AdminMemberStatus;
import com.byarrival.domain.user.entity.JobGroup;
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
    // 지금 적용 중인 이용 제한(UserRestrictionRepository.findActive와 같은 조건)
    private static final String RESTRICTED = "EXISTS (SELECT 1 FROM user_restriction r WHERE r.user_id = u.id "
        + "AND r.released_at IS NULL AND r.starts_at <= :now AND (r.ends_at IS NULL OR r.ends_at > :now))";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public long count(AdminMemberSearchRequest search, LocalDateTime now) {
        MapSqlParameterSource params = new MapSqlParameterSource("now", now);
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users u " + where(search, params), params,
            Long.class);
        return total == null ? 0 : total;
    }

    public List<MemberRow> findPage(AdminMemberSearchRequest search, LocalDateTime now, int limit, long offset) {
        MapSqlParameterSource params = new MapSqlParameterSource("now", now);
        // 직군은 처음 고른 하나만 쓴다(지금은 1개만 고를 수 있다)
        String sql = "SELECT u.id, u.nickname, u.provider, u.travel_type, u.created_at, j.job_group, "
            + "sd.name AS sido_name, sg.name AS sigungu_name, " + RESTRICTED + " AS restricted FROM users u "
            + "LEFT JOIN user_job_group j ON j.user_id = u.id AND j.sort_order = 1 "
            + "LEFT JOIN region_sigungu sg ON sg.id = u.region_sigungu_id "
            + "LEFT JOIN region_sido sd ON sd.id = sg.sido_id "
            + where(search, params) + "ORDER BY u.created_at DESC, u.id DESC LIMIT :limit OFFSET :offset";
        params.addValue("limit", limit).addValue("offset", offset);
        return jdbcTemplate.query(sql, params, (rs, rowNum) -> {
            String travelType = rs.getString("travel_type");
            String jobGroup = rs.getString("job_group");
            return new MemberRow(rs.getLong("id"), rs.getString("nickname"),
                SocialProvider.valueOf(rs.getString("provider")),
                travelType == null ? null : TravelType.valueOf(travelType),
                rs.getObject("created_at", LocalDateTime.class),
                jobGroup == null ? null : JobGroup.valueOf(jobGroup),
                rs.getString("sido_name"), rs.getString("sigungu_name"),
                rs.getBoolean("restricted") ? AdminMemberStatus.RESTRICTED : AdminMemberStatus.NORMAL);
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
        if (search.status() == AdminMemberStatus.RESTRICTED) {
            conditions.add(RESTRICTED);
        } else if (search.status() == AdminMemberStatus.NORMAL) {
            conditions.add("NOT " + RESTRICTED);
        }
        return "WHERE " + String.join(" AND ", conditions) + " ";
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public record MemberRow(Long id, String nickname, SocialProvider provider, TravelType travelType,
                            LocalDateTime joinedAt, JobGroup jobGroup, String sidoName, String sigunguName,
                            AdminMemberStatus status) {
    }
}
