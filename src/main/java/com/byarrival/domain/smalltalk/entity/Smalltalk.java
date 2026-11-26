package com.byarrival.domain.smalltalk.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 대화 소재로 쓰는 한 줄(hook)과 연결 뉴스(TALK-01). 카테고리·제목·원문 주소는 기반 기사에서 읽는다.
 * 기반 기사가 숨김·삭제되면 목록과 상세에서 뺀다(PICK-01, PICK-02).
 * 홈의 에디터 픽이기도 해서 운영자가 추가·수정·숨김·삭제·순서 변경을 한다(EDITOR-01·03·04·05·07).
 * 삭제는 표시만 남긴다. 보관함 저장이 깨지지 않고, 배치 재실행이 그 자리를 다시 채우지 않는다.
 */
@Entity
@Table(name = "smalltalk")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Smalltalk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long articleId;

    @Column(nullable = false)
    private LocalDate serviceDate;

    @Column(nullable = false, length = 100)
    private String hook;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SmalltalkVisibility visibility;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** 운영자가 정한 순서(1부터). 비어 있으면 정한 순서 뒤에 카테고리 순서로 둔다 */
    private Integer sortOrder;

    /** 운영자가 직접 추가했으면 그 운영자. 배치가 만들었으면 null */
    private Long createdByAdminId;

    private LocalDateTime editedAt;

    private Long editedByAdminId;

    private LocalDateTime deletedAt;

    private Long deletedByAdminId;

    private Smalltalk(Long articleId, LocalDate serviceDate, String hook, LocalDateTime now, Long createdByAdminId) {
        this.articleId = articleId;
        this.serviceDate = serviceDate;
        this.hook = hook;
        this.visibility = SmalltalkVisibility.VISIBLE;
        this.createdAt = now;
        this.createdByAdminId = createdByAdminId;
    }

    public static Smalltalk create(Long articleId, LocalDate serviceDate, String hook, LocalDateTime now) {
        return new Smalltalk(articleId, serviceDate, hook, now, null);
    }

    /**
     * 운영자가 직접 추가한다(EDITOR-03). 정한 순서가 없으므로 순서를 바꾸기 전까지는 뒤에 붙는다.
     */
    public static Smalltalk createByAdmin(Long articleId, LocalDate serviceDate, String hook, Long adminId,
        LocalDateTime now) {
        return new Smalltalk(articleId, serviceDate, hook, now, adminId);
    }

    /**
     * 연결 기사·한마디·노출일을 바꾼다(EDITOR-05). 노출일을 옮기면 그날의 순서 뒤에 붙도록 순서를 지운다.
     */
    public void edit(Long articleId, LocalDate serviceDate, String hook, Long adminId, LocalDateTime now) {
        if (!this.serviceDate.equals(serviceDate)) {
            this.sortOrder = null;
        }
        this.articleId = articleId;
        this.serviceDate = serviceDate;
        this.hook = hook;
        this.editedAt = now;
        this.editedByAdminId = adminId;
    }

    public void hide() {
        this.visibility = SmalltalkVisibility.HIDDEN;
    }

    public void restore() {
        this.visibility = SmalltalkVisibility.VISIBLE;
    }

    public void delete(Long adminId, LocalDateTime now) {
        this.deletedAt = now;
        this.deletedByAdminId = adminId;
    }

    public void reorder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isHidden() {
        return visibility == SmalltalkVisibility.HIDDEN;
    }

    /** 운영자가 직접 추가했거나 수정했는지 */
    public boolean isEditedByAdmin() {
        return createdByAdminId != null || editedAt != null;
    }
}
