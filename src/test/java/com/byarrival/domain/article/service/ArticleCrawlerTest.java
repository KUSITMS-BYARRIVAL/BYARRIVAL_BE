package com.byarrival.domain.article.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.article.config.ArticleCrawlProperties;
import com.byarrival.domain.article.exception.ArticleErrorCode;
import com.byarrival.domain.article.service.ArticleCrawler.CrawlResult;
import com.byarrival.domain.article.service.ArticleCrawler.CrawledArticle;
import com.byarrival.domain.article.service.ArticlePageClient.Page;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;
import tools.jackson.databind.json.JsonMapper;

class ArticleCrawlerTest {

    private static final String LINK = "https://news.google.com/rss/articles/abc";
    private static final String SOURCE_URL = "https://press.example.com/1";
    private static final int MIN_CONTENT_LENGTH = 20;

    private GoogleNewsUrlResolver resolver;
    private ArticlePageClient pageClient;
    private ArticleCrawler crawler;

    @BeforeEach
    void setUp() {
        resolver = mock(GoogleNewsUrlResolver.class);
        pageClient = mock(ArticlePageClient.class);
        ArticleCrawlProperties properties = new ArticleCrawlProperties(Duration.ofSeconds(1), Duration.ofSeconds(1),
            DataSize.ofMegabytes(1), 3, 1, MIN_CONTENT_LENGTH, 0.5, "test-agent");
        crawler = new ArticleCrawler(resolver, pageClient, new ArticleBodyExtractor(JsonMapper.builder().build()),
            new ArticleContentCleaner(), new ArticleTextInspector(properties), properties);
        given(resolver.resolve(LINK)).willReturn(SOURCE_URL);
    }

    @Test
    void 원문_주소의_본문을_정제해_돌려준다() {
        given(pageClient.get(SOURCE_URL)).willReturn(page(SOURCE_URL, "<div><p>" + "가".repeat(30) + "</p></div>"));

        assertThat(crawler.crawl(LINK)).isEqualTo(CrawlResult.success(new CrawledArticle(SOURCE_URL, "가".repeat(30))));
    }

    @Test
    void 리다이렉트를_따라간_최종_주소를_원문_주소로_쓴다() {
        given(pageClient.get(SOURCE_URL))
            .willReturn(page("https://m.press.example.com/1", "<div><p>" + "가".repeat(30) + "</p></div>"));

        assertThat(crawler.crawl(LINK).article().sourceUrl()).isEqualTo("https://m.press.example.com/1");
    }

    @Test
    void 본문이_최소_길이보다_짧으면_제외한다() {
        given(pageClient.get(SOURCE_URL)).willReturn(page(SOURCE_URL, "<div><p>" + "가".repeat(19) + "</p></div>"));

        assertThat(crawler.crawl(LINK)).isEqualTo(CrawlResult.excluded());
    }

    @Test
    void 본문이_한국어가_아니면_제외한다() {
        given(pageClient.get(SOURCE_URL)).willReturn(page(SOURCE_URL,
            "<div><p>Lợi ích của sữa chua đối với người mỡ máu cao 가나다</p></div>"));

        assertThat(crawler.crawl(LINK)).isEqualTo(CrawlResult.excluded());
    }

    @Test
    void 원문_주소를_알아내지_못하면_가져오기_실패로_본다() {
        given(resolver.resolve(LINK)).willThrow(new BusinessException(ArticleErrorCode.ARTICLE_CRAWL_FAILED));

        assertThat(crawler.crawl(LINK)).isEqualTo(CrawlResult.fetchFailure());
    }

    @Test
    void 원문_호출이_실패하면_가져오기_실패로_본다() {
        given(pageClient.get(SOURCE_URL)).willThrow(new BusinessException(ArticleErrorCode.ARTICLE_CRAWL_FAILED));

        assertThat(crawler.crawl(LINK)).isEqualTo(CrawlResult.fetchFailure());
    }

    @Test
    void 원문_주소가_컬럼_길이를_넘으면_제외한다() {
        String longUrl = "https://press.example.com/" + "a".repeat(1000);
        given(pageClient.get(SOURCE_URL)).willReturn(page(longUrl, "<div><p>" + "가".repeat(30) + "</p></div>"));

        assertThat(crawler.crawl(LINK)).isEqualTo(CrawlResult.excluded());
    }

    private Page page(String url, String body) {
        return new Page(url, ("<html><body>" + body + "</body></html>").getBytes(StandardCharsets.UTF_8));
    }
}
