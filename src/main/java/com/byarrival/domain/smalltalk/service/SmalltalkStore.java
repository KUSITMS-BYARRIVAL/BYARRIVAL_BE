package com.byarrival.domain.smalltalk.service;

import com.byarrival.domain.smalltalk.entity.Smalltalk;
import com.byarrival.domain.smalltalk.repository.SmalltalkRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스몰토크 생성 입력을 읽고 결과를 저장한다. AI 호출이 트랜잭션 밖에서 일어나도록 따로 묶는다.
 */
@Component
@RequiredArgsConstructor
public class SmalltalkStore {

    private final SmalltalkRepository smalltalkRepository;

    @Transactional(readOnly = true)
    public Set<Long> findArticleIds(LocalDate serviceDate) {
        return Set.copyOf(smalltalkRepository.findArticleIdsByServiceDate(serviceDate));
    }

    /**
     * 스몰토크를 저장한다. 그 사이 같은 기사의 스몰토크가 저장됐으면(배치 재실행이 겹친 경우) uk_smalltalk_article 위반으로
     * DataIntegrityViolationException이 난다. 트랜잭션 밖의 호출부에서 중복으로 처리한다.
     */
    @Transactional
    public void save(Long articleId, LocalDate serviceDate, String hook, LocalDateTime now) {
        smalltalkRepository.saveAndFlush(Smalltalk.create(articleId, serviceDate, hook, now));
    }
}
