package com.byarrival.domain.smalltalk.service;

import com.byarrival.domain.smalltalk.entity.Smalltalk;
import com.byarrival.domain.smalltalk.repository.SmalltalkRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자의 에디터 픽(스몰토크) 편집(EDITOR-01·03·04·05·07). 입력 검증과 권한 확인은 어드민 쪽에서 한다.
 * 돌려주는 엔티티는 호출하는 쪽의 트랜잭션 안에서 바꾼다.
 */
@Service
@RequiredArgsConstructor
public class SmalltalkEditService {

    private final SmalltalkRepository smalltalkRepository;
    private final SmalltalkQueryService smalltalkQueryService;

    /**
     * 그 노출일의 에디터 픽. 숨긴 것은 넣고 삭제한 것은 뺀다. 앱과 같은 순서다.
     */
    @Transactional(readOnly = true)
    public List<Smalltalk> findEditorPicks(LocalDate serviceDate) {
        return smalltalkQueryService.sorted(smalltalkRepository.findNotDeletedByServiceDate(serviceDate));
    }

    /**
     * 삭제했거나 없으면 비어 있다.
     */
    @Transactional(readOnly = true)
    public Optional<Smalltalk> findEditorPick(Long smalltalkId) {
        return smalltalkRepository.findById(smalltalkId).filter(smalltalk -> !smalltalk.isDeleted());
    }

    /**
     * 기사당 스몰토크는 하나뿐이다(uk_smalltalk_article). 삭제한 것도 자리를 차지한다.
     */
    @Transactional(readOnly = true)
    public boolean isArticleTaken(Long articleId) {
        return smalltalkRepository.existsByArticleId(articleId);
    }

    @Transactional
    public Smalltalk create(Long articleId, LocalDate serviceDate, String hook, Long adminId, LocalDateTime now) {
        return smalltalkRepository.saveAndFlush(Smalltalk.createByAdmin(articleId, serviceDate, hook, adminId, now));
    }

    /**
     * 보낸 순서대로 1부터 순서를 매긴다. 그 노출일의 에디터 픽이 모두 들어 있는지는 호출하는 쪽에서 확인한다.
     */
    @Transactional
    public void reorder(List<Smalltalk> ordered) {
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).reorder(i + 1);
        }
    }
}
