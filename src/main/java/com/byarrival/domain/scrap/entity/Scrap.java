package com.byarrival.domain.scrap.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자가 보관함에 저장한 콘텐츠. 사용자·유형·콘텐츠마다 한 행이며, 보관함에서 지우면 deleted_at만 채운다(되돌리기).
 */
@Entity
@Table(name = "scrap")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Scrap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScrapTargetType targetType;

    @Column(nullable = false)
    private Long targetId;

    // 저장 시각. 보관함 정렬 기준(LIB-02)
    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime deletedAt;

    private Scrap(Long userId, ScrapTargetType targetType, Long targetId, LocalDateTime now) {
        this.userId = userId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.createdAt = now;
    }

    public static Scrap create(Long userId, ScrapTargetType targetType, Long targetId, LocalDateTime now) {
        return new Scrap(userId, targetType, targetId, now);
    }

    public boolean isActive() {
        return deletedAt == null;
    }

    /**
     * 지운 콘텐츠를 다시 저장하면 새로 저장한 것으로 보고 저장 시각을 바꾼다. 이미 저장돼 있으면 그대로 둔다.
     */
    public void save(LocalDateTime now) {
        if (deletedAt != null) {
            deletedAt = null;
            createdAt = now;
        }
    }

    public void delete(LocalDateTime now) {
        if (deletedAt == null) {
            deletedAt = now;
        }
    }

    /**
     * 되돌리기: 원래 저장 시각을 유지한 채 다시 보관함에 둔다.
     */
    public void restore() {
        deletedAt = null;
    }
}
