package com.byarrival.domain.batch.entity;

import com.byarrival.common.entity.BaseEntity;
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
 * 서비스일 단위 일일 배치 실행 이력
 */
@Entity
@Table(name = "batch_run")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BatchRun extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate serviceDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BatchRunStatus status;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    private BatchRun(LocalDate serviceDate, LocalDateTime startedAt) {
        this.serviceDate = serviceDate;
        this.status = BatchRunStatus.RUNNING;
        this.startedAt = startedAt;
    }

    public static BatchRun start(LocalDate serviceDate, LocalDateTime startedAt) {
        return new BatchRun(serviceDate, startedAt);
    }

    public void complete(LocalDateTime finishedAt) {
        this.status = BatchRunStatus.COMPLETED;
        this.finishedAt = finishedAt;
    }

    public void fail(LocalDateTime finishedAt, String errorMessage) {
        this.status = BatchRunStatus.FAILED;
        this.finishedAt = finishedAt;
        this.errorMessage = errorMessage;
    }
}
