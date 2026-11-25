package com.byarrival.domain.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 어드민의 회원 상세 열람 기록(MEMBER-04). 회원을 파기해도 남도록 회원 ID만 값으로 둔다.
 */
@Entity
@Table(name = "admin_member_access_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminMemberAccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long adminId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDateTime accessedAt;

    private AdminMemberAccessLog(Long adminId, Long userId, LocalDateTime accessedAt) {
        this.adminId = adminId;
        this.userId = userId;
        this.accessedAt = accessedAt;
    }

    public static AdminMemberAccessLog of(Long adminId, Long userId, LocalDateTime accessedAt) {
        return new AdminMemberAccessLog(adminId, userId, accessedAt);
    }
}
