package com.byarrival.domain.route.service;

import com.byarrival.domain.route.config.PopularArticleProperties;
import com.byarrival.domain.route.repository.CartedArticleCount;
import com.byarrival.domain.route.repository.RouteCardRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 그 서비스일에 노선에 많이 담은 뉴스 순위(ARR-02 완주 화면, HOME-05와 같은 기준).
 * 기사를 1건 이상 담은 사용자가 최소 인원보다 적으면 순위 대신 집계중 상태로 돌려준다(HOME-07).
 */
@Service
@RequiredArgsConstructor
public class PopularArticleService {

    private final RouteCardRepository routeCardRepository;
    private final PopularArticleProperties properties;

    /**
     * @param excludedArticleIds 순위에서 뺄 기사(예: 내가 이미 읽은 기사). 뺀 자리는 다음 순위로 채운다
     */
    @Transactional(readOnly = true)
    public PopularArticles mostCarted(LocalDate serviceDate, Set<Long> excludedArticleIds, int limit) {
        if (routeCardRepository.countCartedUsers(serviceDate) < properties.minUsers()) {
            return new PopularArticles(false, List.of());
        }
        List<CartedArticleCount> ranked = routeCardRepository.findMostCarted(serviceDate,
                limit + excludedArticleIds.size()).stream()
            .filter(count -> !excludedArticleIds.contains(count.getArticleId()))
            .limit(limit)
            .toList();
        return new PopularArticles(true, ranked);
    }

    /**
     * @param ranked   순위를 보여 줄 만큼 담은 사용자가 모였는지. false면 집계중 상태다
     * @param articles 담은 사용자 수가 많은 순
     */
    public record PopularArticles(boolean ranked, List<CartedArticleCount> articles) {
    }
}
