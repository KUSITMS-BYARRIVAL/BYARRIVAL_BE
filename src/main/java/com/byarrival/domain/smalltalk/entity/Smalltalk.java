package com.byarrival.domain.smalltalk.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private Smalltalk(Long articleId, LocalDate serviceDate, String hook, LocalDateTime now) {
        this.articleId = articleId;
        this.serviceDate = serviceDate;
        this.hook = hook;
        this.createdAt = now;
    }

    public static Smalltalk create(Long articleId, LocalDate serviceDate, String hook, LocalDateTime now) {
        return new Smalltalk(articleId, serviceDate, hook, now);
    }
}
