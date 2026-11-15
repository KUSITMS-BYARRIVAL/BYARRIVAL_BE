package com.byarrival.domain.smalltalk.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.common.time.ServiceDay;
import com.byarrival.domain.scrap.service.ScrapService;
import com.byarrival.domain.smalltalk.dto.response.SmalltalkListResponse;
import com.byarrival.domain.smalltalk.dto.response.SmalltalkResponse;
import com.byarrival.domain.smalltalk.exception.SmalltalkErrorCode;
import com.byarrival.domain.smalltalk.service.SmalltalkQueryService.SmalltalkView;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스몰토크 목록·상세(PICK-01, PICK-02)
 */
@Service
@RequiredArgsConstructor
public class SmalltalkService {

    private final SmalltalkQueryService smalltalkQueryService;
    private final ScrapService scrapService;
    private final Clock clock;

    /**
     * 오늘(서비스일) 스몰토크. 숨겼거나 연결 뉴스가 숨김·삭제된 스몰토크는 뺀다.
     */
    @Transactional(readOnly = true)
    public SmalltalkListResponse getToday(Long userId) {
        LocalDate today = ServiceDay.today(clock);
        List<SmalltalkView> views = smalltalkQueryService.findAvailableOf(today);
        Set<Long> scrapped = scrapService.findScrappedSmalltalkIds(userId,
            views.stream().map(SmalltalkView::smalltalkId).toList());
        return new SmalltalkListResponse(today, views.stream()
            .map(view -> SmalltalkResponse.of(view, scrapped.contains(view.smalltalkId())))
            .toList());
    }

    /**
     * 상세. 보관함에서 지난 스몰토크도 연다. 숨겼거나 연결 뉴스가 숨김·삭제됐으면 SMALLTALK_NOT_FOUND.
     */
    @Transactional(readOnly = true)
    public SmalltalkResponse get(Long userId, Long smalltalkId) {
        SmalltalkView view = smalltalkQueryService.findAllByIds(List.of(smalltalkId)).get(smalltalkId);
        if (view == null || !view.available()) {
            throw new BusinessException(SmalltalkErrorCode.SMALLTALK_NOT_FOUND);
        }
        boolean scrapped = !scrapService.findScrappedSmalltalkIds(userId, List.of(smalltalkId)).isEmpty();
        return SmalltalkResponse.of(view, scrapped);
    }
}
