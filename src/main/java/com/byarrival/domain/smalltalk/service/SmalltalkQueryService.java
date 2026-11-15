package com.byarrival.domain.smalltalk.service;

import com.byarrival.domain.article.entity.Article;
import com.byarrival.domain.article.service.ArticleQueryService;
import com.byarrival.domain.category.service.CategoryMappingService;
import com.byarrival.domain.smalltalk.entity.Smalltalk;
import com.byarrival.domain.smalltalk.repository.SmalltalkRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스몰토크와 연결 뉴스(카테고리·제목·원문 주소)를 함께 읽는다. 보관함(scrap)도 이 조회를 쓴다.
 */
@Service
@RequiredArgsConstructor
public class SmalltalkQueryService {

    private final SmalltalkRepository smalltalkRepository;
    private final ArticleQueryService articleQueryService;
    private final CategoryMappingService categoryMappingService;

    /**
     * 그 서비스일의 스몰토크 중 볼 수 있는 것. 카테고리 화면 순서, 같은 카테고리는 인기 순서다.
     */
    @Transactional(readOnly = true)
    public List<SmalltalkView> findAvailableOf(LocalDate serviceDate) {
        List<Smalltalk> smalltalks = smalltalkRepository.findVisibleByServiceDate(serviceDate);
        Map<Long, SmalltalkView> views = viewsOf(smalltalks,
            Set.copyOf(smalltalks.stream().map(Smalltalk::getId).toList()));
        Map<Long, Integer> categoryOrder = categoryOrder();
        return views.values().stream()
            .filter(SmalltalkView::available)
            .sorted((a, b) -> {
                int byCategory = Integer.compare(categoryOrder.getOrDefault(a.categoryId(), Integer.MAX_VALUE),
                    categoryOrder.getOrDefault(b.categoryId(), Integer.MAX_VALUE));
                return byCategory != 0 ? byCategory : Long.compare(a.articleId(), b.articleId());
            })
            .toList();
    }

    /**
     * @return 스몰토크 ID → 화면 데이터. 없는 ID는 빠진다. 숨겼거나 기반 기사가 숨김·삭제됐으면 available=false
     */
    @Transactional(readOnly = true)
    public Map<Long, SmalltalkView> findAllByIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<Smalltalk> smalltalks = smalltalkRepository.findAllById(ids);
        return viewsOf(smalltalks, Set.copyOf(smalltalkRepository.findVisibleIds(ids)));
    }

    private Map<Long, SmalltalkView> viewsOf(List<Smalltalk> smalltalks, Set<Long> visibleIds) {
        List<Long> articleIds = smalltalks.stream().map(Smalltalk::getArticleId).toList();
        Map<Long, Article> articles = articleQueryService.findAllByIds(articleIds);
        Set<Long> unavailableArticles = articleQueryService.findUnavailableIds(articleIds);
        Map<Long, String> categoryNames = categoryMappingService.getActiveCategoryNames();
        Map<Long, SmalltalkView> views = new LinkedHashMap<>();
        for (Smalltalk smalltalk : smalltalks) {
            Article article = articles.get(smalltalk.getArticleId());
            boolean available = article != null && visibleIds.contains(smalltalk.getId())
                && !unavailableArticles.contains(article.getId());
            views.put(smalltalk.getId(), new SmalltalkView(smalltalk.getId(), smalltalk.getHook(),
                smalltalk.getServiceDate(), smalltalk.getArticleId(),
                article == null ? null : article.getCategoryId(),
                article == null ? null : categoryNames.get(article.getCategoryId()),
                article == null ? null : article.getTitle(),
                available ? article.linkUrl() : null, available, smalltalk.getCreatedAt()));
        }
        return views;
    }

    private Map<Long, Integer> categoryOrder() {
        Map<Long, Integer> order = new LinkedHashMap<>();
        for (Long categoryId : categoryMappingService.getActiveCategoryNames().keySet()) {
            order.put(categoryId, order.size());
        }
        return order;
    }

    /**
     * @param sourceUrl 연결 뉴스 원문 주소. 볼 수 없으면 null
     */
    public record SmalltalkView(Long smalltalkId, String hook, LocalDate serviceDate, Long articleId, Long categoryId,
                                String categoryName, String articleTitle, String sourceUrl, boolean available,
                                LocalDateTime createdAt) {
    }
}
