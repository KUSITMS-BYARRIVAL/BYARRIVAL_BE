package com.byarrival.domain.article.service;

import com.byarrival.domain.article.config.ArticleSelectionProperties;
import com.byarrival.domain.article.entity.Article;
import com.byarrival.domain.article.entity.ArticleContent;
import com.byarrival.domain.article.repository.ArticleContentRepository;
import com.byarrival.domain.article.repository.ArticleRepository;
import com.byarrival.domain.article.repository.ArticleRepository.CategoryArticleCount;
import com.byarrival.domain.article.service.ArticleStoryMatcher.Story;
import com.byarrival.domain.category.service.CategoryMappingService;
import com.byarrival.domain.feed.dto.FetchedFeed;
import com.byarrival.domain.feed.dto.RssItem;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기사 수집의 DB 작업. 크롤링(외부 호출) 전에 저장할 후보를 고르고, 크롤링이 끝난 뒤 한 트랜잭션으로 저장한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleSaveService {

    private final ArticleRepository articleRepository;
    private final ArticleContentRepository articleContentRepository;
    private final CategoryMappingService categoryMappingService;
    private final ArticleSelectionProperties selectionProperties;
    private final ArticleTextInspector textInspector;
    private final ArticleStoryMatcher storyMatcher;

    /**
     * Feed의 Item 중 아직 저장되지 않은 기사를 Feed 순서대로 고르고, 카테고리별로 더 저장할 수 있는 수와
     * 같은 서비스일에 이미 고른 기사의 사건을 구한다. 이미 저장된 기사(GUID나 URL이 같음), 컬럼 길이를 넘는 기사,
     * 제목을 읽을 수 없는 기사(외국어, 깨진 글자), 사진 기사([사진]·[포토] 말머리)는 뺀다.
     */
    @Transactional(readOnly = true)
    public CollectPlan plan(FetchedFeed feed, LocalDate serviceDate) {
        List<RssItem> items = feed.items().stream()
            .filter(item -> fitsColumns(feed.feedId(), item))
            .filter(item -> hasReadableTitle(feed.feedId(), item))
            .toList();
        Set<String> knownGuids = new HashSet<>(findExistingGuids(items));
        Set<String> knownUrls = new HashSet<>(findExistingUrls(items));
        Map<String, Long> categoryIds = categoryMappingService.getCategoryIds(feed.feedId());

        List<ArticleCandidate> mapped = new ArrayList<>();
        List<ArticleCandidate> unmapped = new ArrayList<>();
        for (RssItem item : items) {
            boolean duplicateGuid = item.guid() != null && knownGuids.contains(item.guid());
            if (duplicateGuid || knownUrls.contains(item.link())) {
                continue;
            }
            // 두 검사를 모두 통과한 기사만 등록해야, 건너뛴 기사의 GUID 때문에 다른 신규 기사가 빠지지 않는다
            if (item.guid() != null) {
                knownGuids.add(item.guid());
            }
            knownUrls.add(item.link());
            ArticleCandidate candidate = toCandidate(feed, item, categoryIds);
            (candidate.categoryId() == null ? unmapped : mapped).add(candidate);
        }

        int skipped = feed.items().size() - mapped.size() - unmapped.size();
        log.info("[ArticleCollect] feedId={} Item {}건 중 신규 {}건 (카테고리 미매핑 {}건), 제외 {}건 (중복·길이 초과·제목·사진)",
            feed.feedId(), feed.items().size(), mapped.size() + unmapped.size(), unmapped.size(), skipped);
        return new CollectPlan(mapped, unmapped, remainingByCategory(mapped, serviceDate), pickedStories(serviceDate));
    }

    @Transactional(readOnly = true)
    public Set<String> findExistingSourceUrls(Collection<String> sourceUrls) {
        return sourceUrls.isEmpty() ? Set.of() : Set.copyOf(articleRepository.findExistingSourceUrls(sourceUrls));
    }

    /**
     * 기사와 원문 본문을 한 트랜잭션으로 저장한다. 본문이 없는 기사(카테고리 미매핑)는 기사만 저장한다.
     */
    @Transactional
    public int saveAll(List<CollectedArticle> collected) {
        articleRepository.saveAll(collected.stream().map(CollectedArticle::article).toList());
        articleContentRepository.saveAll(collected.stream()
            .filter(item -> item.content() != null)
            .map(item -> ArticleContent.of(item.article(), item.content()))
            .toList());
        return collected.size();
    }

    // 다른 Feed(카테고리)나 이전 실행에서 이미 고른 기사의 사건. 같은 사건을 다시 고르지 않는 데 쓴다.
    private List<Story> pickedStories(LocalDate serviceDate) {
        return articleRepository.findAllByServiceDateAndCategoryIdIsNotNull(serviceDate).stream()
            .map(article -> storyMatcher.storyOf(article.getOriginalUrl(), article.getRawContent(), article.getTitle()))
            .toList();
    }

    private boolean hasReadableTitle(Long feedId, RssItem item) {
        if (!textInspector.isReadableTitle(item.title())) {
            log.warn("[ArticleCollect] feedId={} 제목을 읽을 수 없어 제외 - title={}", feedId, abbreviate(item.title()));
            return false;
        }
        if (textInspector.isPhotoArticle(item.title())) {
            log.info("[ArticleCollect] feedId={} 사진 기사라 제외 - title={}", feedId, abbreviate(item.title()));
            return false;
        }
        return true;
    }

    // 같은 서비스일에 이미 저장한 기사 수만큼 뺀다. 배치를 다시 돌려도 카테고리별 최대 수를 넘지 않는다.
    private Map<Long, Integer> remainingByCategory(List<ArticleCandidate> candidates, LocalDate serviceDate) {
        Set<Long> categoryIds = new HashSet<>();
        candidates.forEach(candidate -> categoryIds.add(candidate.categoryId()));
        Map<Long, Integer> remaining = new HashMap<>();
        if (categoryIds.isEmpty()) {
            return remaining;
        }
        int max = selectionProperties.maxArticlesPerCategory();
        categoryIds.forEach(categoryId -> remaining.put(categoryId, max));
        for (CategoryArticleCount count : articleRepository.countByCategory(serviceDate, categoryIds)) {
            remaining.put(count.getCategoryId(), (int) Math.max(0, max - count.getArticleCount()));
        }
        return remaining;
    }

    private ArticleCandidate toCandidate(FetchedFeed feed, RssItem item, Map<String, Long> categoryIds) {
        CategoryMatch category = matchCategory(item, feed.rssCategory(), categoryIds);
        return new ArticleCandidate(feed.feedId(), category.categoryId(), category.rssCategory(),
            pressNameOf(feed, item), item, storyMatcher.storyOf(item.link(), item.content(), item.title()));
    }

    // Item의 category를 순서대로 보고 처음 매핑되는 값을 쓴다. 하나도 매핑되지 않으면 Feed의 category로 매핑한다.
    private CategoryMatch matchCategory(RssItem item, String feedCategory, Map<String, Long> categoryIds) {
        List<String> candidates = new ArrayList<>(item.categories());
        if (feedCategory != null) {
            candidates.add(feedCategory);
        }
        for (String candidate : candidates) {
            Long categoryId = categoryIds.get(candidate);
            if (categoryId != null) {
                return new CategoryMatch(candidate, categoryId);
            }
        }
        String rssCategory = candidates.stream()
            .filter(candidate -> candidate.length() <= Article.MAX_RSS_CATEGORY_LENGTH)
            .findFirst()
            .orElse(null);
        return new CategoryMatch(rssCategory, null);
    }

    // 여러 언론사를 모은 Feed(구글 뉴스)는 Item의 source가 실제 언론사다
    private String pressNameOf(FetchedFeed feed, RssItem item) {
        String source = item.source();
        if (source == null || source.length() > Article.MAX_PRESS_NAME_LENGTH) {
            return feed.pressName();
        }
        return source;
    }

    private boolean fitsColumns(Long feedId, RssItem item) {
        boolean fits = item.title().length() <= Article.MAX_TITLE_LENGTH
            && item.link().length() <= Article.MAX_ORIGINAL_URL_LENGTH
            && (item.guid() == null || item.guid().length() <= Article.MAX_GUID_LENGTH);
        if (!fits) {
            log.warn("[ArticleCollect] feedId={} 길이 초과로 제외 - url={}", feedId, abbreviate(item.link()));
        }
        return fits;
    }

    private Collection<String> findExistingGuids(List<RssItem> items) {
        List<String> guids = items.stream().map(RssItem::guid).filter(Objects::nonNull).distinct().toList();
        return guids.isEmpty() ? List.of() : articleRepository.findExistingGuids(guids);
    }

    private Collection<String> findExistingUrls(List<RssItem> items) {
        List<String> urls = items.stream().map(RssItem::link).distinct().toList();
        return urls.isEmpty() ? List.of() : articleRepository.findExistingOriginalUrls(urls);
    }

    private String abbreviate(String value) {
        return value.length() <= 200 ? value : value.substring(0, 200) + "...";
    }

    /**
     * @param mapped             카테고리가 매핑된 신규 기사. Feed 순서(주요도 순)를 지킨다.
     * @param unmapped           카테고리가 매핑되지 않은 신규 기사. 추천 대상이 아니라 크롤링하지 않고 검수용으로만 저장한다.
     * @param remainingByCategory 카테고리별로 더 저장할 수 있는 기사 수
     * @param pickedStories      같은 서비스일에 이미 고른 기사의 사건
     */
    public record CollectPlan(
        List<ArticleCandidate> mapped,
        List<ArticleCandidate> unmapped,
        Map<Long, Integer> remainingByCategory,
        List<Story> pickedStories
    ) {

        public CollectPlan {
            mapped = List.copyOf(mapped);
            unmapped = List.copyOf(unmapped);
            remainingByCategory = Map.copyOf(remainingByCategory);
            pickedStories = List.copyOf(pickedStories);
        }
    }

    /**
     * 저장할 기사와 원문 본문. 원문을 크롤링하지 않은 기사는 content가 null이다.
     */
    public record CollectedArticle(Article article, String content) {
    }

    /**
     * 저장 후보 기사. 카테고리가 매핑되지 않았으면 categoryId가 null이다.
     */
    public record ArticleCandidate(Long feedId, Long categoryId, String rssCategory, String pressName, RssItem item,
        Story story) {

        public Article toArticle(LocalDate serviceDate) {
            return Article.collected()
                .feedId(feedId)
                .categoryId(categoryId)
                .guid(item.guid())
                .originalUrl(item.link())
                .title(item.title())
                .pressName(pressName)
                .rssCategory(rssCategory)
                .publishedAt(item.publishedAt())
                .rawContent(item.content())
                .serviceDate(serviceDate)
                .build();
        }
    }

    private record CategoryMatch(String rssCategory, Long categoryId) {
    }
}
