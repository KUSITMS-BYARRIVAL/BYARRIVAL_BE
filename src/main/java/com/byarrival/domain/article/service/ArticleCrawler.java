package com.byarrival.domain.article.service;

import com.byarrival.domain.article.config.ArticleCrawlProperties;
import com.byarrival.domain.article.entity.Article;
import com.byarrival.domain.article.service.ArticlePageClient.Page;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * RSS 기사 링크에서 언론사 원문을 찾아 본문을 정제한다. 실패는 두 가지로 나눠 돌려준다.
 * <ul>
 *   <li>가져오기 실패: 원문 주소를 알아내지 못했거나 원문 호출이 실패했다(네트워크·HTTP 오류). 한 Feed의 시도가 모두
 *       이 실패면 크롤링 장애로 본다.</li>
 *   <li>제외: 원문은 받았지만 본문을 찾지 못했거나(최소 길이 미만), 본문이 한국어가 아니거나, 주소가 너무 길다.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleCrawler {

    private final GoogleNewsUrlResolver googleNewsUrlResolver;
    private final ArticlePageClient pageClient;
    private final ArticleBodyExtractor bodyExtractor;
    private final ArticleContentCleaner contentCleaner;
    private final ArticleTextInspector textInspector;
    private final ArticleCrawlProperties properties;

    public CrawlResult crawl(String link) {
        Page page;
        try {
            page = pageClient.get(googleNewsUrlResolver.resolve(link));
        } catch (RuntimeException e) {
            log.warn("[ArticleCrawl] 원문을 가져오지 못함 - link={} {}", link, e.getMessage());
            return CrawlResult.fetchFailure();
        }
        // #뒤(fragment)는 같은 문서 안의 위치라 원문 주소에서 뺀다. 같은 원문이 중복 저장되지 않게 한다.
        String sourceUrl = withoutFragment(page.url());
        try {
            if (sourceUrl.length() > Article.MAX_SOURCE_URL_LENGTH) {
                log.warn("[ArticleCrawl] 원문 주소가 너무 길어 제외 - link={}", link);
                return CrawlResult.excluded();
            }
            Optional<String> content = bodyExtractor.extract(page.body(), page.charset(), page.url())
                .flatMap(contentCleaner::clean)
                .filter(text -> text.codePointCount(0, text.length()) >= properties.minContentLength());
            if (content.isEmpty()) {
                log.warn("[ArticleCrawl] 본문을 찾지 못함 - sourceUrl={}", page.url());
                return CrawlResult.excluded();
            }
            if (!textInspector.isKorean(content.get())) {
                log.warn("[ArticleCrawl] 한국어 본문이 아니라 제외 - sourceUrl={}", page.url());
                return CrawlResult.excluded();
            }
            return CrawlResult.success(new CrawledArticle(sourceUrl, content.get()));
        } catch (RuntimeException e) {
            // 받은 페이지를 해석하다 난 오류는 이 기사만의 문제로 본다
            log.warn("[ArticleCrawl] 본문을 해석하지 못해 제외 - sourceUrl={} {}", page.url(), e.getMessage());
            return CrawlResult.excluded();
        }
    }

    static String withoutFragment(String url) {
        int hash = url.indexOf('#');
        return hash < 0 ? url : url.substring(0, hash);
    }

    /**
     * @param sourceUrl      리다이렉트를 따라간 뒤의 언론사 원문 주소(fragment 제외)
     * @param cleanedContent 정제한 본문
     */
    public record CrawledArticle(String sourceUrl, String cleanedContent) {
    }

    /**
     * @param article     원문을 확보했으면 그 기사, 아니면 null
     * @param fetchFailed 원문을 가져오지 못했으면 true. 받은 원문이 기준에 맞지 않아 뺀 경우는 false
     */
    public record CrawlResult(CrawledArticle article, boolean fetchFailed) {

        public static CrawlResult success(CrawledArticle article) {
            return new CrawlResult(article, false);
        }

        public static CrawlResult excluded() {
            return new CrawlResult(null, false);
        }

        public static CrawlResult fetchFailure() {
            return new CrawlResult(null, true);
        }
    }
}
