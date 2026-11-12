package com.byarrival.domain.quiz.entity;

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
import org.hibernate.annotations.ColumnTransformer;

/**
 * 기사 1건에서 만든 객관식 퀴즈(AI-03, AI-04). 선택지는 JSON 배열 [{"seq":1,"content":"..."}]로 둔다.
 */
@Entity
@Table(name = "quiz")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Quiz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long articleId;

    @Column(nullable = false, length = 300)
    private String question;

    @ColumnTransformer(write = "CAST(? AS jsonb)")
    @Column(nullable = false, columnDefinition = "jsonb")
    private String choices;

    @Column(nullable = false)
    private int answerSeq;

    @Column(nullable = false, length = 500)
    private String explanation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuizStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private Quiz(Long articleId, String question, String choices, int answerSeq, String explanation,
        LocalDateTime now) {
        this.articleId = articleId;
        this.question = question;
        this.choices = choices;
        this.answerSeq = answerSeq;
        this.explanation = explanation;
        this.status = QuizStatus.ACTIVE;
        this.createdAt = now;
    }

    /**
     * @param choicesJson 검증을 마친 선택지 JSON 배열
     */
    public static Quiz create(Long articleId, String question, String choicesJson, int answerSeq, String explanation,
        LocalDateTime now) {
        return new Quiz(articleId, question, choicesJson, answerSeq, explanation, now);
    }

    public boolean isCorrect(int selectedSeq) {
        return answerSeq == selectedSeq;
    }
}
