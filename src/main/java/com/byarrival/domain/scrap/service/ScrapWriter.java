package com.byarrival.domain.scrap.service;

import com.byarrival.domain.scrap.entity.Scrap;
import com.byarrival.domain.scrap.entity.ScrapTargetType;
import com.byarrival.domain.scrap.repository.ScrapRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 저장 한 건을 한 트랜잭션으로 쓴다. 같은 콘텐츠를 동시에 저장해 유니크 제약에 걸리면 {@link ScrapService}가 다시 부른다.
 */
@Component
@RequiredArgsConstructor
class ScrapWriter {

    private final ScrapRepository scrapRepository;

    @Transactional
    public Scrap save(Long userId, ScrapTargetType targetType, Long targetId, LocalDateTime now) {
        Scrap scrap = scrapRepository.findByUserIdAndTargetTypeAndTargetId(userId, targetType, targetId)
            .orElse(null);
        if (scrap == null) {
            return scrapRepository.saveAndFlush(Scrap.create(userId, targetType, targetId, now));
        }
        scrap.save(now);
        return scrap;
    }
}
