package com.byarrival.domain.scrap.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.article.entity.Article;
import com.byarrival.domain.article.service.ArticleQueryService;
import com.byarrival.domain.category.service.CategoryMappingService;
import com.byarrival.domain.scrap.dto.response.LibraryArticleResponse;
import com.byarrival.domain.scrap.dto.response.LibraryCategoryResponse;
import com.byarrival.domain.scrap.dto.response.LibraryCountResponse;
import com.byarrival.domain.scrap.dto.response.LibraryResponse;
import com.byarrival.domain.scrap.dto.response.LibrarySort;
import com.byarrival.domain.scrap.dto.response.LibraryType;
import com.byarrival.domain.scrap.dto.response.ScrapStatusResponse;
import com.byarrival.domain.scrap.entity.Scrap;
import com.byarrival.domain.scrap.entity.ScrapTargetType;
import com.byarrival.domain.scrap.exception.ScrapErrorCode;
import com.byarrival.domain.scrap.repository.ScrapRepository;
import com.byarrival.domain.scrap.repository.ScrapRepository.TypeCount;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보관함(LIB-01~05)과 기사 저장(READ-04)
 */
@Service
@RequiredArgsConstructor
public class ScrapService {

    private final ScrapRepository scrapRepository;
    private final ScrapWriter scrapWriter;
    private final ArticleQueryService articleQueryService;
    private final CategoryMappingService categoryMappingService;
    private final Clock clock;

    /**
     * 기사를 보관함에 저장한다. 이미 저장돼 있으면 그대로 둔다. 숨겼거나 원문이 삭제된 기사는 저장할 수 없다.
     */
    public ScrapStatusResponse saveArticle(Long userId, Long articleId) {
        Article article = articleQueryService.findAllByIds(List.of(articleId)).get(articleId);
        if (article == null || article.getCategoryId() == null
            || !articleQueryService.findUnavailableIds(List.of(articleId)).isEmpty()) {
            throw new BusinessException(ScrapErrorCode.SCRAP_TARGET_NOT_FOUND);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            scrapWriter.save(userId, ScrapTargetType.ARTICLE, articleId, now);
        } catch (DataIntegrityViolationException e) {
            // 같은 기사를 동시에 저장한 경우. 먼저 들어간 행을 살린다
            scrapWriter.save(userId, ScrapTargetType.ARTICLE, articleId, now);
        }
        return new ScrapStatusResponse(articleId, true);
    }

    /**
     * 기사 저장을 해제한다(READ-04 토글). 저장돼 있지 않으면 그대로 둔다.
     */
    @Transactional
    public ScrapStatusResponse unsaveArticle(Long userId, Long articleId) {
        scrapRepository.findByUserIdAndTargetTypeAndTargetId(userId, ScrapTargetType.ARTICLE, articleId)
            .ifPresent(scrap -> scrap.delete(LocalDateTime.now(clock)));
        return new ScrapStatusResponse(articleId, false);
    }

    /**
     * @return 주어진 기사 중 사용자가 보관함에 저장한 기사 ID
     */
    @Transactional(readOnly = true)
    public Set<Long> findScrappedArticleIds(Long userId, Collection<Long> articleIds) {
        if (articleIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(scrapRepository.findActiveTargetIds(userId, ScrapTargetType.ARTICLE, articleIds));
    }

    /**
     * 보관함에서 지운다(LIB-04). 이미 지운 항목이면 그대로 둔다.
     */
    @Transactional
    public void delete(Long userId, Long scrapId) {
        findOwned(userId, scrapId).delete(LocalDateTime.now(clock));
    }

    /**
     * 삭제 [되돌리기]: 원래 저장 시각 그대로 보관함에 다시 둔다.
     */
    @Transactional
    public void restore(Long userId, Long scrapId) {
        findOwned(userId, scrapId).restore();
    }

    /**
     * 탭별 목록과 저장 개수(LIB-01). 기사 탭은 저장 시점 정렬(LIB-02)과 카테고리 필터(LIB-03)를 적용한다.
     *
     * @param categoryId null이면 전체
     */
    @Transactional(readOnly = true)
    public LibraryResponse getLibrary(Long userId, LibraryType type, LibrarySort sort, Long categoryId) {
        LibraryCountResponse counts = countByType(userId);
        if (type != LibraryType.ARTICLE) {
            throw new BusinessException(ScrapErrorCode.SCRAP_TYPE_NOT_SUPPORTED);
        }
        List<Scrap> scraps = scrapRepository.findActive(userId, ScrapTargetType.ARTICLE);
        Set<Long> articleIds = scraps.stream().map(Scrap::getTargetId).collect(Collectors.toSet());
        Map<Long, Article> articles = articleQueryService.findAllByIds(articleIds);
        Set<Long> unavailable = articleQueryService.findUnavailableIds(articleIds);
        Map<Long, String> categoryNames = categoryMappingService.getActiveCategoryNames();

        // 원문 기사 행이 없어진 저장(기사 삭제)은 보여 줄 내용이 없어 뺀다
        List<Scrap> visible = scraps.stream().filter(scrap -> articles.containsKey(scrap.getTargetId())).toList();
        List<LibraryCategoryResponse> categories = categoriesOf(visible, articles, categoryNames);

        Comparator<Scrap> order = Comparator.comparing(Scrap::getCreatedAt).thenComparing(Scrap::getId);
        List<LibraryArticleResponse> items = visible.stream()
            .filter(scrap -> categoryId == null || categoryId.equals(articles.get(scrap.getTargetId()).getCategoryId()))
            .sorted(sort == LibrarySort.OLDEST ? order : order.reversed())
            .map(scrap -> toArticle(scrap, articles.get(scrap.getTargetId()), unavailable, categoryNames))
            .toList();
        return new LibraryResponse(type, counts, categories, items);
    }

    private Scrap findOwned(Long userId, Long scrapId) {
        Scrap scrap = scrapRepository.findByIdAndUserId(scrapId, userId)
            .orElseThrow(() -> new BusinessException(ScrapErrorCode.SCRAP_NOT_FOUND));
        if (scrap.getTargetType() == ScrapTargetType.TERM) {
            throw new BusinessException(ScrapErrorCode.SCRAP_NOT_FOUND);
        }
        return scrap;
    }

    private LibraryCountResponse countByType(Long userId) {
        Map<ScrapTargetType, Long> counts = new EnumMap<>(ScrapTargetType.class);
        for (TypeCount count : scrapRepository.countActiveByType(userId)) {
            counts.put(count.getTargetType(), count.getScrapCount());
        }
        return new LibraryCountResponse(counts.getOrDefault(ScrapTargetType.ARTICLE, 0L),
            counts.getOrDefault(ScrapTargetType.SMALLTALK, 0L));
    }

    // 저장 기사가 있는 카테고리만 화면 순서(display_order)대로
    private static List<LibraryCategoryResponse> categoriesOf(List<Scrap> scraps, Map<Long, Article> articles,
        Map<Long, String> categoryNames) {
        Map<Long, Long> counts = new HashMap<>();
        for (Scrap scrap : scraps) {
            counts.merge(articles.get(scrap.getTargetId()).getCategoryId(), 1L, Long::sum);
        }
        return categoryNames.entrySet().stream()
            .filter(category -> counts.containsKey(category.getKey()))
            .map(category -> new LibraryCategoryResponse(category.getKey(), category.getValue(),
                counts.get(category.getKey())))
            .toList();
    }

    private static LibraryArticleResponse toArticle(Scrap scrap, Article article, Set<Long> unavailable,
        Map<Long, String> categoryNames) {
        boolean available = !unavailable.contains(article.getId());
        Integer minutes = article.getReadingMinutes() == null ? null
            : article.getReadingMinutes().setScale(0, RoundingMode.CEILING).intValue();
        return new LibraryArticleResponse(scrap.getId(), article.getId(), available, article.getTitle(),
            article.getPressName(), article.getCategoryId(), categoryNames.get(article.getCategoryId()),
            article.getSummaryOneLine(), minutes, available ? article.linkUrl() : null, scrap.getCreatedAt());
    }
}
