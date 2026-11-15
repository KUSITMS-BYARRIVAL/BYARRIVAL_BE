package com.byarrival.domain.route.service;

import com.byarrival.domain.article.entity.Article;
import com.byarrival.domain.article.service.ArticleQueryService;
import com.byarrival.domain.category.service.CategoryMappingService;
import com.byarrival.domain.route.config.PopularArticleProperties;
import com.byarrival.domain.route.dto.response.PopularArticleResponse;
import com.byarrival.domain.route.dto.response.PopularArticlesResponse;
import com.byarrival.domain.route.repository.CartedArticleCount;
import com.byarrival.domain.route.repository.RouteCardRepository;
import com.byarrival.domain.user.entity.JobGroup;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private final ArticleQueryService articleQueryService;
    private final CategoryMappingService categoryMappingService;

    /**
     * @param excludedArticleIds 순위에서 뺄 기사(예: 내가 이미 읽은 기사). 뺀 자리는 다음 순위로 채운다
     */
    @Transactional(readOnly = true)
    public PopularArticles mostCarted(LocalDate serviceDate, Set<Long> excludedArticleIds, int limit) {
        return mostCarted(serviceDate, null, excludedArticleIds, limit);
    }

    /**
     * @param jobGroup 이 직군을 고른 사용자가 담은 것만 센다. null이면 전체 사용자 기준이다(HOME-05 칩)
     */
    @Transactional(readOnly = true)
    public PopularArticles mostCarted(LocalDate serviceDate, JobGroup jobGroup, Set<Long> excludedArticleIds,
        int limit) {
        long cartedUsers = jobGroup == null
            ? routeCardRepository.countCartedUsers(serviceDate)
            : routeCardRepository.countCartedUsersInJobGroup(serviceDate, jobGroup.name());
        if (cartedUsers < properties.minUsers()) {
            return new PopularArticles(false, List.of());
        }
        int fetchSize = limit + excludedArticleIds.size();
        List<CartedArticleCount> counts = jobGroup == null
            ? routeCardRepository.findMostCarted(serviceDate, fetchSize)
            : routeCardRepository.findMostCartedInJobGroup(serviceDate, jobGroup.name(), fetchSize);
        List<CartedArticleCount> ranked = counts.stream()
            .filter(count -> !excludedArticleIds.contains(count.getArticleId()))
            .limit(limit)
            .toList();
        return new PopularArticles(true, ranked);
    }

    /**
     * 순위를 기사 정보와 함께 화면 응답으로 바꾼다. 집계중이면 순위 없이 집계중 상태다.
     */
    @Transactional(readOnly = true)
    public PopularArticlesResponse toResponse(PopularArticles popular) {
        if (!popular.ranked()) {
            return PopularArticlesResponse.aggregating();
        }
        Map<Long, Article> articles = articleQueryService.findAllByIds(
            popular.articles().stream().map(CartedArticleCount::getArticleId).toList());
        Map<Long, String> categoryNames = categoryMappingService.getActiveCategoryNames();
        List<PopularArticleResponse> ranked = new ArrayList<>();
        for (CartedArticleCount count : popular.articles()) {
            Article article = articles.get(count.getArticleId());
            ranked.add(new PopularArticleResponse(ranked.size() + 1, article.getId(), article.getTitle(),
                article.getPressName(), article.getCategoryId(), categoryNames.get(article.getCategoryId()),
                article.getReadingMinutes() == null ? null
                    : article.getReadingMinutes().setScale(0, RoundingMode.CEILING).intValue(),
                count.getUserCount(), urlOf(article)));
        }
        return PopularArticlesResponse.ranked(ranked);
    }

    // 크롤링한 원문 주소가 없으면 수집한 링크로 연다
    private static String urlOf(Article article) {
        return article.getSourceUrl() != null ? article.getSourceUrl() : article.getOriginalUrl();
    }

    /**
     * @param ranked   순위를 보여 줄 만큼 담은 사용자가 모였는지. false면 집계중 상태다
     * @param articles 담은 사용자 수가 많은 순
     */
    public record PopularArticles(boolean ranked, List<CartedArticleCount> articles) {
    }
}
