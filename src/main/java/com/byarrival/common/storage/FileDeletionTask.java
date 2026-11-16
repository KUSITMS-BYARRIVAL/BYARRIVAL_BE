package com.byarrival.common.storage;

import com.byarrival.common.entity.BaseEntity;
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
 * 지워야 할 저장소 파일. 지운 뒤에만 행을 없애고, 실패하면 남겨 다시 시도한다.
 */
@Entity
@Table(name = "file_deletion_task")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FileDeletionTask extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(nullable = false)
    private int attempts;

    private LocalDateTime lastFailedAt;

    private FileDeletionTask(String url) {
        this.url = url;
    }

    public static FileDeletionTask of(String url) {
        return new FileDeletionTask(url);
    }

    public void recordFailure(LocalDateTime now) {
        this.attempts++;
        this.lastFailedAt = now;
    }
}
