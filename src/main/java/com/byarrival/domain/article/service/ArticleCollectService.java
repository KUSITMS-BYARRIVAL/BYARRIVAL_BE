package com.byarrival.domain.article.service;

import com.byarrival.domain.article.entity.Article;
import com.byarrival.domain.article.repository.ArticleRepository;
import com.byarrival.domain.category.service.CategoryMappingService;
import com.byarrival.domain.feed.dto.FetchedFeed;
import com.byarrival.domain.feed.dto.RssItem;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
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
 * 파싱한 RSS Item에 카테고리를 매핑해 원본 기사로 저장한다. 이미 저장된 기사(GUID나 URL이 같음)는 건너뛴다.
 * RSS description은 본문이 아니므로(본문은 원문 크롤링으로 가져온다) 원본으로만 보존하고 정제·읽기시간 계산은 하지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleCollectService {

    private final ArticleRepository articleRepository;
    private final CategoryMappingService categoryMappingService;

    /**
     * Feed 하나의 기사를 한 트랜잭션으로 저장한다. 실패하면 이 Feed의 기사만 저장되지 않는다.
     *
     * @return 새로 저장한 기사 수
     */
    @Transactional
    public int saveCollected(FetchedFeed feed, LocalDate serviceDate) {
        List<RssItem> items = feed.items().stream().filter(item -> fitsColumns(feed.feedId(), item)).toList();
        Set<String> knownGuids = new HashSet<>(findExistingGuids(items));
        Set<String> knownUrls = new HashSet<>(findExistingUrls(items));
        Map<String, Long> categoryIds = categoryMappingService.getCategoryIds(feed.feedId());

        List<Article> articles = new ArrayList<>();
        int unmapped = 0;
        for (RssItem item : items) {
            boolean duplicateGuid = item.guid() != null && !knownGuids.add(item.guid());
            if (duplicateGuid || !knownUrls.add(item.link())) {
                continue;
            }
            Article article = toArticle(feed, item, serviceDate, categoryIds);
            if (article.getCategoryId() == null) {
                unmapped++;
            }
            articles.add(article);
        }
        articleRepository.saveAll(articles);

        log.info("[ArticleCollect] feedId={} Item {}건 중 {}건 저장 (중복 {}건, 길이 초과 {}건, 카테고리 미매핑 {}건)",
            feed.feedId(), feed.items().size(), articles.size(), items.size() - articles.size(),
            feed.items().size() - items.size(), unmapped);
        return articles.size();
    }

    private Article toArticle(FetchedFeed feed, RssItem item, LocalDate serviceDate, Map<String, Long> categoryIds) {
        CategoryMatch category = matchCategory(item, feed.rssCategory(), categoryIds);
        return Article.collected()
            .feedId(feed.feedId())
            .categoryId(category.categoryId())
            .guid(item.guid())
            .originalUrl(item.link())
            .title(item.title())
            .pressName(pressNameOf(feed, item))
            .rssCategory(category.rssCategory())
            .publishedAt(item.publishedAt())
            .rawContent(item.content())
            .serviceDate(serviceDate)
            .build();
    }

    // Item의 category를 순서대로 보고 처음 매핑되는 값을 쓴다. Item에 category가 없으면 Feed의 category로 매핑한다.
    private CategoryMatch matchCategory(RssItem item, String feedCategory, Map<String, Long> categoryIds) {
        List<String> candidates = item.categories().isEmpty()
            ? (feedCategory == null ? List.of() : List.of(feedCategory))
            : item.categories();
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

    private record CategoryMatch(String rssCategory, Long categoryId) {
    }
}
